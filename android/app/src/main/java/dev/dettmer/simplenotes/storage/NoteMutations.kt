package dev.dettmer.simplenotes.storage

import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.sync.PendingServerDeletions

/**
 * Änderungen, die die Hauptliste an mehreren Notizen auf einmal vornimmt: Farbe, Anheften,
 * Archivieren, Ordner vom Sync aus- und wieder einschließen.
 *
 * Alle laufen über [NotesStorage.updateNote], also auf dem Stand der Platte. Die angezeigte Liste
 * kann eine inzwischen gespeicherte Fassung (Editor, Widget, Sync) noch nicht kennen; wer ihre
 * Kopie zurückschreibt, überschreibt diese Fassung. Von übergebenen Notizen zählt deshalb nur die
 * ID. Plain class wie [TrashManager]; Snackbar, Widgets und Sync-Trigger bleiben im ViewModel.
 */
class NoteMutations(
    private val storage: NotesStorage,
    private val folderStore: FolderStore,
    private val pendingServerDeletions: PendingServerDeletions,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    suspend fun setColor(ids: Collection<String>, hex: String?) {
        edit(ids) { it.copy(color = hex) }
    }

    suspend fun setPinned(ids: Collection<String>, pinned: Boolean?) {
        edit(ids) { it.copy(isPinned = pinned) }
    }

    /** @return die Fassungen vor der Änderung, als Payload für [restoreArchived]. */
    suspend fun setArchived(ids: Collection<String>, archived: Boolean): List<Note> {
        val archivedAt = if (archived) clock() else null
        val before = mutableListOf<Note>()
        edit(ids) { note ->
            before += note
            note.copy(archivedAt = archivedAt)
        }
        return before
    }

    /**
     * Undo zu [setArchived]: setzt nur `archivedAt` zurück, mit frischem `updatedAt` (LWW-sicher,
     * falls inzwischen gesynct wurde). Alles andere bleibt wie auf der Platte.
     */
    suspend fun restoreArchived(originals: List<Note>) {
        originals.forEach { original -> edit(listOf(original.id)) { it.copy(archivedAt = original.archivedAt) } }
    }

    /**
     * 🆕 v2.8.0 (Local-Only Folders): Notizen in [folderNames] werden LOCAL_ONLY, der lokale Stand
     * bleibt vollständig erhalten.
     *
     * @return Löschaufträge für die Notizen, die (potenziell) eine Server-Kopie haben, gelesen VOR
     * der Umstellung. LOCAL_ONLY war nie oben, DELETED_ON_SERVER ist dort schon weg.
     */
    suspend fun makeLocalOnly(folderNames: Set<String>): List<PendingServerDeletions.PendingDeletion> {
        val deletions = mutableListOf<PendingServerDeletions.PendingDeletion>()
        idsIn(folderNames).forEach { id ->
            storage.updateNote(id) { note ->
                if (note.folderName !in folderNames) return@updateNote null
                if (note.syncStatus in SERVER_RESIDENT) {
                    deletions += PendingServerDeletions.PendingDeletion(note.id, note.folderName)
                }
                note.takeIf { it.syncStatus != SyncStatus.LOCAL_ONLY }?.copy(syncStatus = SyncStatus.LOCAL_ONLY)
            }
        }
        return deletions
    }

    /**
     * 🆕 v2.8.0: Ordner wieder in den Sync aufnehmen. LOCAL_ONLY → PENDING (Upload beim nächsten
     * Sync), und noch eingereihte Server-Löschungen dieser Notizen fallen weg. Sonst würde ein
     * offline ausgeschlossener und wieder aufgenommener Ordner beim nächsten Sync trotzdem gelöscht.
     */
    suspend fun includeInSync(folderNames: Set<String>) {
        val ids = idsIn(folderNames)
        ids.forEach { id ->
            storage.updateNote(id) { note ->
                note.takeIf { it.syncStatus == SyncStatus.LOCAL_ONLY }?.copy(syncStatus = SyncStatus.PENDING)
            }
        }
        if (ids.isNotEmpty()) pendingServerDeletions.remove(ids)
    }

    private suspend fun idsIn(folderNames: Set<String>): List<String> =
        storage.loadAllNotes(forceReload = true).filter { it.folderName in folderNames }.map { it.id }

    /** Eine Bearbeitung aus der Liste: `updatedAt` neu, Status wie beim Speichern im Editor. */
    private suspend fun edit(ids: Collection<String>, change: (Note) -> Note) {
        val now = clock()
        ids.forEach { id ->
            storage.updateNote(id) { note ->
                change(note).copy(updatedAt = now, syncStatus = statusAfterEdit(note.folderName))
            }
        }
    }

    // Im nur-lokalen Ordner bleibt eine Notiz LOCAL_ONLY. PENDING käme dort nie hoch und stünde
    // dauerhaft als "ausstehend" da (Uploader filtert nur-lokale Ordner aus).
    private fun statusAfterEdit(folderName: String?): SyncStatus =
        if (folderStore.isLocalOnly(folderName)) SyncStatus.LOCAL_ONLY else SyncStatus.PENDING

    private companion object {
        val SERVER_RESIDENT = setOf(SyncStatus.SYNCED, SyncStatus.PENDING, SyncStatus.CONFLICT)
    }
}
