package dev.dettmer.simplenotes.storage

import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.sync.PendingServerDeletions
import dev.dettmer.simplenotes.utils.ActivityLog
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger

/**
 * 🆕 v2.9.0 (Trash): Kapselt die Papierkorb-Logik (in den Papierkorb verschieben, wiederherstellen,
 * endgültig löschen, Auto-Purge nach 30 Tagen).
 *
 * Plain class ohne Android-Abhängigkeiten — die [clock] ist injizierbar, damit die 30-Tage-Grenze
 * deterministisch testbar ist. Sync-Trigger und Widget-Updates passieren bewusst NICHT hier, sondern
 * in den aufrufenden ViewModels (TrashManager bleibt eine reine Storage-Operation).
 *
 * Trash-Semantik: In den Papierkorb verschieben = `trashedAt` setzen + `updatedAt` bumpen → synct als
 * normale Notiz-Änderung (Last-Writer-Wins). Endgültiges Löschen nutzt den bestehenden
 * „Überall löschen"-Pfad ([PendingServerDeletions] + DeletionTracker via [NotesStorage.deleteNote]).
 */
class TrashManager(
    private val storage: NotesStorage,
    private val pendingServerDeletions: PendingServerDeletions,
    private val folderStore: FolderStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val retentionMs: () -> Long = { Constants.TRASH_RETENTION_MS }
) {
    companion object {
        private const val TAG = "TrashManager"

        /** Status, deren Notizen (potenziell) eine Server-Kopie haben und beim Purge gelöscht werden müssen. */
        private val SERVER_RESIDENT = setOf(SyncStatus.SYNCED, SyncStatus.PENDING, SyncStatus.CONFLICT)
    }

    /**
     * Verschiebt [notes] in den Papierkorb. Setzt `trashedAt`/`updatedAt` und markiert PENDING,
     * außer die Notiz ist LOCAL_ONLY oder liegt in einem nur-lokalen Ordner — dann bleibt sie lokal.
     * Notizen aus einem Ordner in [toRootFrom] landen dabei auf Root (gelöschter Ordner).
     *
     * Von [notes] zählt nur die ID: geändert wird der Stand auf der Platte, nicht die übergebene
     * Kopie, die aus einer älteren Liste stammen kann.
     *
     * @return die Fassungen von der Platte vor der Änderung, als Payload für [undoMoveToTrash].
     */
    suspend fun moveToTrash(notes: List<Note>, toRootFrom: Set<String> = emptySet()): List<Note> {
        val now = clock()
        return notes.mapNotNull { requested ->
            var before: Note? = null
            val trashed = storage.updateNote(requested.id) { note ->
                before = note
                val folder = note.folderName?.takeIf { it !in toRootFrom }
                note.copy(
                    trashedAt = now,
                    updatedAt = now,
                    folderName = folder,
                    syncStatus = targetStatusFor(note.syncStatus, folder)
                )
            } ?: return@mapNotNull null
            Logger.d(TAG, "🗑️ Moved to trash: ${trashed.id} ('${trashed.title}')")
            ActivityLog.log(
                ActivityLog.Op.TRASH,
                ActivityLog.Src.LOCAL,
                id = trashed.id,
                title = trashed.title,
                folder = before?.folderName
            )
            before
        }
    }

    /**
     * Macht [moveToTrash] rückgängig: Papierkorb-Markierung, Ordner, `updatedAt` und Status kommen
     * aus [originals] zurück, der Inhalt bleibt wie auf der Platte. Läuft innerhalb des
     * Undo-Fensters, also bevor der Sync die Markierung hochgeladen hat.
     */
    suspend fun undoMoveToTrash(originals: List<Note>) {
        originals.forEach { original ->
            storage.updateNote(original.id) { note ->
                note.copy(
                    trashedAt = original.trashedAt,
                    folderName = original.folderName,
                    updatedAt = original.updatedAt,
                    syncStatus = original.syncStatus
                )
            }
        }
    }

    /**
     * Nach dem Löschen eines Ordners: Notizen daraus liegen jetzt auf Root im Papierkorb, ihre
     * Server-Kopie am alten Pfad muss weg. Sonst taucht der gelöschte Ordner über die
     * zurückgebliebene Datei beim nächsten Sync wieder auf. [originals] sind die Fassungen vor
     * [moveToTrash]; LOCAL_ONLY und nur-lokale Ordner hatten nie eine Server-Kopie.
     */
    suspend fun queueOldPathDeletions(originals: List<Note>, deletedFolders: Set<String>) {
        val deletions = originals
            .filter {
                it.folderName != null &&
                    it.folderName in deletedFolders &&
                    it.syncStatus != SyncStatus.LOCAL_ONLY &&
                    !folderStore.isLocalOnly(it.folderName)
            }
            .map { PendingServerDeletions.PendingDeletion(it.id, it.folderName) }
        if (deletions.isNotEmpty()) pendingServerDeletions.add(deletions)
    }

    /**
     * Stellt eine Notiz aus dem Papierkorb wieder her. `trashedAt = null`, `updatedAt` gebumpt,
     * Status PENDING (re-upload) bzw. LOCAL_ONLY für nur-lokale Ziele. Existiert der ursprüngliche
     * Ordner nicht mehr (Abgleich gegen [knownFolders], case-insensitive), landet die Notiz in Root.
     */
    suspend fun restore(note: Note, knownFolders: List<String>) {
        val now = clock()
        val restored = storage.updateNote(note.id) { current ->
            val resolvedFolder = current.folderName?.takeIf { folder ->
                knownFolders.any { it.equals(folder, ignoreCase = true) }
            }
            current.copy(
                trashedAt = null,
                folderName = resolvedFolder,
                updatedAt = now,
                syncStatus = targetStatusFor(current.syncStatus, resolvedFolder)
            )
        } ?: return
        Logger.d(TAG, "♻️ Restored from trash: ${restored.id} → folder '${restored.folderName}'")
        ActivityLog.log(
            ActivityLog.Op.RESTORE,
            ActivityLog.Src.LOCAL,
            id = restored.id,
            title = restored.title,
            folder = restored.folderName
        )
    }

    /**
     * Löscht [notes] endgültig. Notizen mit (potenzieller) Server-Kopie werden über die
     * [PendingServerDeletions]-Queue zum Server-Delete eingeplant (offline-fähig, 404-tolerant in
     * Sync-Phase 4.5); lokal werden alle immer hart gelöscht (schreibt DeletionTracker gegen Zombies).
     * DELETED_ON_SERVER- und LOCAL_ONLY-Notizen werden nur lokal entfernt.
     */
    suspend fun purge(notes: List<Note>, why: String? = null) {
        val deletions = notes
            .filter { it.syncStatus in SERVER_RESIDENT && !folderStore.isLocalOnly(it.folderName) }
            .map { PendingServerDeletions.PendingDeletion(it.id, it.folderName) }
        if (deletions.isNotEmpty()) {
            pendingServerDeletions.add(deletions)
        }
        storage.deleteNotes(notes.map { it.id })
        Logger.d(TAG, "🔥 Purged ${notes.size} note(s) from trash")
        notes.forEach { note ->
            ActivityLog.log(
                ActivityLog.Op.PURGE,
                ActivityLog.Src.LOCAL,
                id = note.id,
                title = note.title,
                folder = note.folderName,
                why = why
            )
        }
    }

    /**
     * Löscht alle Notizen, deren Aufbewahrungsfrist abgelaufen ist
     * (`now - trashedAt >= TRASH_RETENTION_MS`). Idempotent über Geräte hinweg, da `trashedAt`
     * der geteilte (synchronisierte) Zeitstempel ist.
     *
     * @return Anzahl der endgültig gelöschten Notizen.
     */
    suspend fun purgeExpired(): Int {
        val now = clock()
        val threshold = retentionMs()
        val expired = storage.loadTrashedNotes(forceReload = true)
            .filter { it.trashedAt != null && now - it.trashedAt >= threshold }
        if (expired.isNotEmpty()) {
            purge(expired, why = "auto_purge_expired")
            Logger.d(TAG, "⏰ Auto-purged ${expired.size} expired note(s) from trash")
        }
        return expired.size
    }

    /**
     * Leert den gesamten Papierkorb (endgültiges Löschen aller getrashten Notizen).
     *
     * @return Anzahl der gelöschten Notizen.
     */
    suspend fun emptyTrash(): Int {
        val trashed = storage.loadTrashedNotes(forceReload = true)
        if (trashed.isNotEmpty()) {
            purge(trashed)
        }
        return trashed.size
    }

    private fun targetStatusFor(current: SyncStatus, folderName: String?): SyncStatus =
        if (current == SyncStatus.LOCAL_ONLY || folderStore.isLocalOnly(folderName)) {
            SyncStatus.LOCAL_ONLY
        } else {
            SyncStatus.PENDING
        }
}
