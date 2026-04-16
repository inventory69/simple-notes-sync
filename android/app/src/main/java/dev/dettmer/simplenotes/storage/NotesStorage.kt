package dev.dettmer.simplenotes.storage

import android.content.Context
import dev.dettmer.simplenotes.models.DeletionTracker
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.utils.DeviceIdGenerator
import dev.dettmer.simplenotes.utils.Logger
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class NotesStorage(private val context: Context) {
    companion object {
        private const val TAG = "NotesStorage"

        // 🔒 v1.7.2 (IMPL_001): Mutex für thread-sichere Deletion Tracker Operationen
        private val deletionTrackerMutex = Mutex()
    }

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
            if (!forceReload && cachedNotes != null &&
                System.currentTimeMillis() - cacheTimestamp < cacheTtlMs
            ) {
                return@withLock cachedNotes!!
            }
            val versionBefore = cacheVersion.get()
            val notes = notesDir.listFiles()
                ?.filter { it.extension == "json" }
                ?.mapNotNull { file ->
                    try {
                        Note.fromJson(file.readText())
                    } catch (_: java.io.FileNotFoundException) {
                        // File was deleted between listFiles() and readText() — skip it
                        null
                    }
                }
                .orEmpty()
            // Only populate cache if no invalidation happened during disk read
            if (cacheVersion.get() == versionBefore) {
                cachedNotes = notes
                cacheTimestamp = System.currentTimeMillis()
            }
            notes
        }
    }

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

    suspend fun deleteAllNotes(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val notes = notesDir.listFiles()
                ?.filter { it.extension == "json" }
                ?.mapNotNull { Note.fromJson(it.readText()) }
                .orEmpty()
            val deviceId = DeviceIdGenerator.getDeviceId(context)

            for (note in notes) {
                val file = File(notesDir, "${note.id}.json")
                if (file.delete()) {
                    trackDeletionSafe(note.id, deviceId)
                }
            }

            invalidateCache()
            Logger.d(TAG, "🗑️ Deleted all notes (${notes.size} notes)")
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
        } catch (e: Exception) {
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
     * Legacy-Methode ohne Mutex-Schutz.
     * Verwendet für synchrone Aufrufe wo Coroutines nicht verfügbar sind.
     *
     * @deprecated Verwende trackDeletionSafe() für Thread-Safety wo möglich
     */
    @Deprecated(
        "Use trackDeletionSafe() for thread-safety",
        ReplaceWith("trackDeletionSafe(noteId, deviceId)")
    )
    fun trackDeletion(noteId: String, deviceId: String) {
        val tracker = loadDeletionTracker()
        tracker.addDeletion(noteId, deviceId)
        saveDeletionTracker(tracker)
        Logger.d(TAG, "📝 Tracked deletion: $noteId")
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
