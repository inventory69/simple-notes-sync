package dev.dettmer.simplenotes.storage

import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.sync.PendingServerDeletions
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
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        private const val TAG = "TrashManager"

        /** Status, deren Notizen (potenziell) eine Server-Kopie haben und beim Purge gelöscht werden müssen. */
        private val SERVER_RESIDENT = setOf(SyncStatus.SYNCED, SyncStatus.PENDING, SyncStatus.CONFLICT)
    }

    /**
     * Verschiebt [notes] in den Papierkorb. Setzt `trashedAt`/`updatedAt` und markiert PENDING,
     * außer die Notiz ist LOCAL_ONLY oder liegt in einem nur-lokalen Ordner — dann bleibt sie lokal.
     *
     * @return die unveränderten Originale als Undo-Payload.
     */
    suspend fun moveToTrash(notes: List<Note>): List<Note> {
        val now = clock()
        notes.forEach { note ->
            storage.saveNote(
                note.copy(
                    trashedAt = now,
                    updatedAt = now,
                    syncStatus = targetStatusFor(note.syncStatus, note.folderName)
                )
            )
            Logger.d(TAG, "🗑️ Moved to trash: ${note.id} ('${note.title}')")
        }
        return notes
    }

    /**
     * Stellt eine Notiz aus dem Papierkorb wieder her. `trashedAt = null`, `updatedAt` gebumpt,
     * Status PENDING (re-upload) bzw. LOCAL_ONLY für nur-lokale Ziele. Existiert der ursprüngliche
     * Ordner nicht mehr (Abgleich gegen [knownFolders], case-insensitive), landet die Notiz in Root.
     */
    suspend fun restore(note: Note, knownFolders: List<String>) {
        val resolvedFolder = note.folderName?.takeIf { folder ->
            knownFolders.any { it.equals(folder, ignoreCase = true) }
        }
        val now = clock()
        storage.saveNote(
            note.copy(
                trashedAt = null,
                folderName = resolvedFolder,
                updatedAt = now,
                syncStatus = restoreStatusFor(note.syncStatus, resolvedFolder)
            )
        )
        Logger.d(TAG, "♻️ Restored from trash: ${note.id} → folder '$resolvedFolder'")
    }

    /**
     * Löscht [notes] endgültig. Notizen mit (potenzieller) Server-Kopie werden über die
     * [PendingServerDeletions]-Queue zum Server-Delete eingeplant (offline-fähig, 404-tolerant in
     * Sync-Phase 4.5); lokal werden alle immer hart gelöscht (schreibt DeletionTracker gegen Zombies).
     * DELETED_ON_SERVER- und LOCAL_ONLY-Notizen werden nur lokal entfernt.
     */
    suspend fun purge(notes: List<Note>) {
        val deletions = notes
            .filter { it.syncStatus in SERVER_RESIDENT && !folderStore.isLocalOnly(it.folderName) }
            .map { PendingServerDeletions.PendingDeletion(it.id, it.folderName) }
        if (deletions.isNotEmpty()) {
            pendingServerDeletions.add(deletions)
        }
        notes.forEach { note ->
            storage.deleteNote(note.id)
            Logger.d(TAG, "🔥 Purged from trash: ${note.id} ('${note.title}')")
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
        val expired = storage.loadTrashedNotes(forceReload = true)
            .filter { it.trashedAt != null && now - it.trashedAt >= Constants.TRASH_RETENTION_MS }
        if (expired.isNotEmpty()) {
            purge(expired)
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

    private fun restoreStatusFor(current: SyncStatus, resolvedFolder: String?): SyncStatus =
        if (current == SyncStatus.LOCAL_ONLY || folderStore.isLocalOnly(resolvedFolder)) {
            SyncStatus.LOCAL_ONLY
        } else {
            SyncStatus.PENDING
        }
}
