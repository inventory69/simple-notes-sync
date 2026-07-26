package dev.dettmer.simplenotes.storage

import android.content.Context
import dev.dettmer.simplenotes.models.DeletionTracker
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.utils.DeviceIdGenerator
import dev.dettmer.simplenotes.utils.Logger
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

// Abbau: TECH_DEBT_ROADMAP.md §4 (Bestand, keinem Refactoring-Slice zugeordnet)
@Suppress("TooManyFunctions")
class NotesStorage(private val context: Context) {
    companion object {
        private const val TAG = "NotesStorage"

        // 🔒 v1.7.2 (IMPL_001): Mutex für thread-sichere Deletion Tracker Operationen
        private val deletionTrackerMutex = Mutex()

        // 🔧 Perf: begrenzte Parallelität beim Einlesen vieler Notiz-Dateien
        // (verhindert tausende gleichzeitig offene File-Handles bei sehr vielen Notizen)
        private const val PARALLEL_READ_LIMIT = 64
    }

    private val readSemaphore = Semaphore(PARALLEL_READ_LIMIT)

    // ─── In-memory cache for loadAllNotes (REF-023) ───────────────────────
    private val cacheMutex = Mutex()
    private var cachedNotes: List<Note>? = null
    private var cacheTimestamp: Long = 0L
    private val cacheTtlMs = 2000L
    private val cacheVersion = AtomicLong(0L)

    private fun invalidateCache() {
        cacheVersion.incrementAndGet()
        cachedNotes = null
        cacheTimestamp = 0L
    }
    // ─────────────────────────────────────────────────────────────────────

    private val notesDir: File = File(context.filesDir, "notes").apply {
        if (!exists()) mkdirs()
    }

    suspend fun saveNote(note: Note) = withContext(Dispatchers.IO) {
        val file = File(notesDir, "${note.id}.json")
        file.writeText(note.toJson())
        invalidateCache()
    }

    suspend fun loadNote(id: String): Note? = withContext(Dispatchers.IO) {
        loadNoteSync(id)
    }

    /**
     * 🆕 v2.7.0 (Folders): Verschiebt eine Notiz in einen Ordner (oder zurück nach Root).
     * Setzt folderName, bumpt updatedAt (für updatedAt-basierte Konfliktauflösung) und markiert
     * PENDING. No-op, wenn die Notiz fehlt oder bereits im Zielordner liegt.
     * 🆕 v2.8.0 (Local-Only Folders): [newStatus] erlaubt LOCAL_ONLY als Zielstatus,
     * wenn der Zielordner vom Sync ausgeschlossen ist.
     */
    suspend fun moveNote(
        noteId: String,
        targetFolder: String?,
        newStatus: dev.dettmer.simplenotes.models.SyncStatus = dev.dettmer.simplenotes.models.SyncStatus.PENDING
    ) = withContext(Dispatchers.IO) {
        val note = loadNoteSync(noteId) ?: return@withContext
        if (note.folderName == targetFolder) return@withContext
        saveNote(
            note.copy(
                folderName = targetFolder,
                updatedAt = System.currentTimeMillis(),
                syncStatus = newStatus
            )
        )
    }

    /**
     * Synchronous variant for contexts where suspend is not available
     * (e.g. Glance provideContent composable). Caller is responsible for
     * ensuring this is NOT called on the main thread.
     */
    fun loadNoteSync(id: String): Note? {
        val file = File(notesDir, "$id.json")
        return if (file.exists()) {
            try {
                Note.fromJson(file.readText())
            } catch (_: java.io.FileNotFoundException) {
                null
            }
        } else {
            null
        }
    }

    /**
     * Lädt alle Notizen aus dem lokalen Speicher.
     *
     * 🔀 v1.8.0: Sortierung entfernt — wird jetzt im ViewModel durchgeführt,
     * damit der User die Sortierung konfigurieren kann.
     * 🗃️ v2.3.0 (REF-023): In-memory cache with 2s TTL to avoid redundant
     * file reads on every onResume. Cache is invalidated on save/delete.
     *
     * @param forceReload Skip the cache and always read from disk (e.g. after sync).
     */
    suspend fun loadAllNotes(forceReload: Boolean = false): List<Note> = withContext(Dispatchers.IO) {
        cacheMutex.withLock {
            val cached = cachedNotes
            if (!forceReload &&
                cached != null &&
                System.currentTimeMillis() - cacheTimestamp < cacheTtlMs
            ) {
                return@withLock cached
            }
            val versionBefore = cacheVersion.get()
            val files = notesDir.listFiles()?.filter { it.extension == "json" }.orEmpty()
            // 🔧 Perf: parallele Reads (statt sequenziell) — mit vielen tausend Notiz-Dateien
            // ist ein Read pro Datei nacheinander der dominante Cold-Start-Kostenfaktor.
            val notes = coroutineScope {
                files.map { file ->
                    async {
                        readSemaphore.withPermit {
                            try {
                                Note.fromJson(file.readText())
                            } catch (_: java.io.FileNotFoundException) {
                                // File was deleted between listFiles() and readText() — skip it
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            // Only populate cache if no invalidation happened during disk read
            if (cacheVersion.get() == versionBefore) {
                cachedNotes = notes
                cacheTimestamp = System.currentTimeMillis()
            }
            notes
        }
    }

    /**
     * 🆕 v2.9.0 (Trash) / 🆕 v2.11.0 (Archive): Lädt nur aktive Notizen —
     * weder im Papierkorb noch archiviert. Choke-Point für Widgets, Widget-Config-Picker,
     * Share-Picker und Checklisten-Ziel-Picker. `loadAllNotes()` bleibt für
     * Sync/Backup/Repair unverändert (die brauchen auch getrashte/archivierte Notizen).
     */
    suspend fun loadActiveNotes(forceReload: Boolean = false): List<Note> =
        loadAllNotes(forceReload).filter { it.trashedAt == null && it.archivedAt == null }

    /**
     * 🆕 v2.11.0 (Archive): Aktive + archivierte Notizen (nur Papierkorb ausgeschlossen).
     * Für die Hauptliste (Archiv-Chip filtert im ViewModel).
     */
    suspend fun loadNonTrashedNotes(forceReload: Boolean = false): List<Note> =
        loadAllNotes(forceReload).filter { it.trashedAt == null }

    /**
     * 🆕 v2.9.0 (Trash): Lädt nur Notizen im Papierkorb (trashedAt != null).
     */
    suspend fun loadTrashedNotes(forceReload: Boolean = false): List<Note> =
        loadAllNotes(forceReload).filter { it.trashedAt != null }

    suspend fun deleteNote(id: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(notesDir, "$id.json")
        val deleted = file.delete()

        if (deleted) {
            Logger.d(TAG, "🗑️ Deleted note: $id")
            invalidateCache()

            // Track deletion to prevent zombie notes
            val deviceId = DeviceIdGenerator.getDeviceId(context)
            trackDeletionSafe(id, deviceId)
        }

        deleted
    }

    /**
     * 🔧 Perf/Batch: Löscht mehrere Notizen in einem Durchgang. Statt pro Notiz die komplette
     * Tombstone-Datei neu zu lesen+schreiben (O(n²) beim Leeren tausender Notizen), werden alle
     * Dateien gelöscht und die Deletion-Tracker-Datei genau **einmal** aktualisiert.
     *
     * Läuft in [NonCancellable]: Datei-Löschung + Tombstone-Schreiben durchlaufen atomar, damit
     * ein Abbruch (z.B. Zurück-Navigieren während des Löschens) keine gelöschten Dateien ohne
     * Tombstone hinterlässt (→ Zombie-Notizen beim nächsten Sync).
     *
     * @return Anzahl der tatsächlich gelöschten Dateien.
     */
    suspend fun deleteNotes(ids: List<String>): Int = withContext(Dispatchers.IO + NonCancellable) {
        if (ids.isEmpty()) return@withContext 0
        val deletedIds = ids.filter { File(notesDir, "$it.json").delete() }
        if (deletedIds.isNotEmpty()) {
            invalidateCache()
            trackDeletionsSafe(deletedIds, DeviceIdGenerator.getDeviceId(context))
        }
        Logger.d(TAG, "🗑️ Deleted ${deletedIds.size}/${ids.size} notes (batch)")
        deletedIds.size
    }

    suspend fun deleteAllNotes(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val ids = notesDir.listFiles()
                ?.filter { it.extension == "json" }
                ?.map { it.nameWithoutExtension }
                .orEmpty()
            val deleted = deleteNotes(ids)
            Logger.d(TAG, "🗑️ Deleted all notes ($deleted notes)")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to delete all notes", e)
            false
        }
    }

    // === Deletion Tracking ===

    private fun getDeletionTrackerFile(): File {
        return File(context.filesDir, "deleted_notes.json")
    }

    fun loadDeletionTracker(): DeletionTracker {
        val file = getDeletionTrackerFile()
        if (!file.exists()) {
            return DeletionTracker()
        }

        return try {
            val json = file.readText()
            DeletionTracker.fromJson(json) ?: DeletionTracker()
        } catch (e: IOException) {
            Logger.e(TAG, "Failed to load deletion tracker", e)
            DeletionTracker()
        }
    }

    fun saveDeletionTracker(tracker: DeletionTracker) {
        try {
            val file = getDeletionTrackerFile()
            file.writeText(tracker.toJson())

            if (tracker.deletedNotes.size > 1000) {
                Logger.w(TAG, "⚠️ Deletion tracker large: ${tracker.deletedNotes.size} entries")
            }

            Logger.d(TAG, "✅ Deletion tracker saved (${tracker.deletedNotes.size} entries)")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save deletion tracker", e)
        }
    }

    /**
     * 🔒 v1.7.2 (IMPL_001): Thread-sichere Deletion-Tracking mit Mutex
     *
     * Verhindert Race Conditions bei Batch-Deletes durch exklusiven Zugriff
     * auf den Deletion Tracker.
     *
     * @param noteId ID der gelöschten Notiz
     * @param deviceId Geräte-ID für Konflikt-Erkennung
     */
    suspend fun trackDeletionSafe(noteId: String, deviceId: String) {
        deletionTrackerMutex.withLock {
            val tracker = loadDeletionTracker()
            tracker.addDeletion(noteId, deviceId)
            saveDeletionTracker(tracker)
            Logger.d(TAG, "📝 Tracked deletion (mutex-protected): $noteId")
        }
    }

    /**
     * 🔧 Batch-Variante von [trackDeletionSafe]: trackt mehrere IDs unter **einem** Mutex-Lock mit
     * genau **einem** Load/Save der Tombstone-Datei (statt N Read/Write-Zyklen).
     */
    suspend fun trackDeletionsSafe(ids: List<String>, deviceId: String) {
        if (ids.isEmpty()) return
        deletionTrackerMutex.withLock {
            val tracker = loadDeletionTracker()
            ids.forEach { tracker.addDeletion(it, deviceId) }
            saveDeletionTracker(tracker)
            Logger.d(TAG, "📝 Tracked ${ids.size} deletions (mutex-protected, batch)")
        }
    }

    fun isNoteDeleted(noteId: String): Boolean {
        val tracker = loadDeletionTracker()
        return tracker.isDeleted(noteId)
    }

    fun clearDeletionTracker() {
        saveDeletionTracker(DeletionTracker())
        Logger.d(TAG, "🗑️ Deletion tracker cleared")
    }

    /**
     * 🔄 v1.7.0: Reset all sync statuses to PENDING when server changes
     * This ensures notes are uploaded to the new server on next sync
     */
    suspend fun resetAllSyncStatusToPending(): Int = withContext(Dispatchers.IO) {
        val notes = notesDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { Note.fromJson(it.readText()) }
            .orEmpty()
        var updatedCount = 0

        notes.forEach { note ->
            // 🔧 v1.9.0: Auch DELETED_ON_SERVER → PENDING zurücksetzen
            // Notizen die auf dem alten Server gelöscht wurden, müssen auf den neuen Server
            // hochgeladen werden — der neue Server hat keine Kenntnis der alten Löschung.
            if (note.syncStatus == dev.dettmer.simplenotes.models.SyncStatus.SYNCED ||
                note.syncStatus == dev.dettmer.simplenotes.models.SyncStatus.DELETED_ON_SERVER
            ) {
                val updatedNote = note.copy(syncStatus = dev.dettmer.simplenotes.models.SyncStatus.PENDING)
                val file = File(notesDir, "${updatedNote.id}.json")
                file.writeText(updatedNote.toJson())
                updatedCount++
            }
        }

        invalidateCache()
        Logger.d(TAG, "🔄 Reset sync status for $updatedCount notes to PENDING")
        updatedCount
    }

    fun getNotesDir(): File = notesDir
}
