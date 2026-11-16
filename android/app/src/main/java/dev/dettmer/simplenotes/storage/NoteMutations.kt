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

    /**
     * Verschiebt Notizen nach [target] (null = Root). Ziel nur lokal → LOCAL_ONLY statt PENDING
     * (PENDING käme dort nie hoch). Eine Server-Kopie am alten Pfad wird zum Löschen vorgemerkt:
     * als Relocation, oder als echte Löschung, wenn die Notiz den Sync verlässt (wie Desktop).
     */
    suspend fun moveNotes(ids: Collection<String>, target: String?, targetLocalOnly: Boolean = false) {
        val status = if (targetLocalOnly) SyncStatus.LOCAL_ONLY else SyncStatus.PENDING
        ids.forEach { relocate(it, target, status, isMove = !targetLocalOnly) }
        if (target != null) folderStore.addFolder(target)
    }

    /**
     * 🆕 v2.7.0: Benennt einen Ordner um und nimmt seine Notizen mit. Ein nur-lokaler Ordner
     * behält Markierung und LOCAL_ONLY-Status, auf dem Server ist dann nichts umzuziehen.
     */
    suspend fun renameFolder(oldName: String, newName: String, localOnly: Boolean) {
        val status = if (localOnly) SyncStatus.LOCAL_ONLY else SyncStatus.PENDING
        // Wie die Liste: Notizen im Papierkorb behalten ihren alten Ordnernamen.
        storage.loadNonTrashedNotes(forceReload = true).filter { it.folderName == oldName }
            .forEach { relocate(it.id, newName, status, isMove = true, queueServerCopy = !localOnly) }
        folderStore.rename(oldName, newName)
    }

    /**
     * Undo für Notizen, die beim Löschen ihres Ordners nach Root gewandert sind: zurück an den
     * alten Platz. Die vorgemerkte Server-Löschung des alten Pfads verwirft der Sync selbst,
     * sobald die Notiz wieder dort liegt (WebDavSyncService.processPendingServerDeletions).
     */
    suspend fun moveBack(originals: List<Note>, localOnlyFolders: Set<String>) {
        originals.forEach { note ->
            val intoLocalOnly = note.folderName != null && note.folderName in localOnlyFolders
            storage.moveNote(note.id, note.folderName, if (intoLocalOnly) SyncStatus.LOCAL_ONLY else SyncStatus.PENDING)
        }
    }

    private suspend fun relocate(
        id: String,
        target: String?,
        status: SyncStatus,
        isMove: Boolean,
        queueServerCopy: Boolean = true
    ) {
        var before: Note? = null
        storage.updateNote(id) { note ->
            before = note
            note.takeIf { it.folderName != target }?.copy(folderName = target, updatedAt = clock(), syncStatus = status)
        }
        val old = before ?: return
        if (queueServerCopy && old.folderName != target && old.syncStatus != SyncStatus.LOCAL_ONLY) {
            pendingServerDeletions.add(listOf(PendingServerDeletions.PendingDeletion(id, old.folderName, isMove = isMove)))
        }
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
