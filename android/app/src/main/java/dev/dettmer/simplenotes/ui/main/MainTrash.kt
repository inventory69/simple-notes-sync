package dev.dettmer.simplenotes.ui.main

import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.storage.TrashManager
import dev.dettmer.simplenotes.utils.trashRetentionDays
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 🆕 v2.9.0 (Trash): Löschen aus der Hauptliste heißt „in den Papierkorb", mit Undo-Snackbar.
 *
 * Notizen im Undo-Fenster sind schon im Papierkorb, bleiben aber bis zum Ablauf in [hiddenIds]
 * und damit aus der Liste ausgeblendet. Erst danach wird gesynct (die Markierung hochgeladen),
 * bei Aufbewahrung 0 endgültig gelöscht.
 */
class MainTrash(
    private val ctx: MainContext,
    private val trashManager: TrashManager,
    private val folders: MainFolders,
    private val selection: MainSelection,
    private val sync: MainSyncController
) {
    private val ioDispatcher = Dispatchers.IO

    private val _hiddenIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenIds: StateFlow<Set<String>> = _hiddenIds.asStateFlow()

    fun moveSelectedToTrash() {
        val selectedIds = selection.selectedNotes.value
        val selectedNotes = ctx.notes().filter { it.id in selectedIds }
        if (selectedNotes.isEmpty()) return
        _hiddenIds.update { it + selectedIds }
        val count = selectedNotes.size
        val message = ctx.plural(
            if (ctx.prefs.trashRetentionDays() == 0) R.plurals.snackbar_notes_deleted_permanently else R.plurals.snackbar_notes_trashed,
            count
        )
        ctx.scope.launch {
            val originals = withContext(ioDispatcher) { trashManager.moveToTrash(selectedNotes) }
            selection.clearSelection()
            afterTrash(message, originals)
        }
    }

    /** Eine einzelne Notiz (Ersatz für das frühere harte Löschen). */
    fun moveToTrash(note: Note) {
        _hiddenIds.update { it + note.id }
        val message = ctx.string(
            if (ctx.prefs.trashRetentionDays() == 0) R.string.snackbar_note_deleted_permanently else R.string.snackbar_note_trashed,
            note.title
        )
        ctx.scope.launch {
            val originals = withContext(ioDispatcher) { trashManager.moveToTrash(listOf(note)) }
            afterTrash(message, originals)
        }
    }

    /** Im Editor gelöscht: die (noch vorhandene) Notiz in den Papierkorb, mit Undo-Snackbar. */
    fun moveToTrashFromEditor(noteId: String) {
        ctx.scope.launch {
            val note = withContext(ioDispatcher) { NotesStorage(ctx.app).loadNote(noteId) } ?: return@launch
            moveToTrash(note)
        }
    }

    private suspend fun afterTrash(message: String, originals: List<Note>) {
        ctx.reloadNotes()
        ctx.showUndo(message) { undoTrash(originals) }
        ctx.refreshWidgets()
        delay(SNACKBAR_UNDO_DELAY_MS)
        finalizeTrashOrPurge(originals)
    }

    /** Undo vor Ablauf des Fensters, also bevor ein Sync die Markierung hochgeladen hat. */
    private fun undoTrash(originals: List<Note>) {
        _hiddenIds.update { it - originals.map { note -> note.id }.toSet() }
        ctx.scope.launch {
            withContext(ioDispatcher) { trashManager.undoMoveToTrash(originals) }
            ctx.reloadNotes()
            ctx.refreshWidgets()
        }
    }

    /** Nach Ablauf des Undo-Fensters: bei Aufbewahrung 0 endgültig löschen, sonst getrasht lassen. */
    private suspend fun finalizeTrashOrPurge(candidates: List<Note>) {
        val stillPending = candidates.filter { it.id in _hiddenIds.value }
        if (stillPending.isEmpty()) return
        if (ctx.prefs.trashRetentionDays() == 0) {
            withContext(ioDispatcher) { trashManager.purge(stillPending) }
        }
        _hiddenIds.update { it - stillPending.map { note -> note.id }.toSet() }
        ctx.triggerOnSaveSync()
    }

    /**
     * 🆕 v2.7.0 / v2.9.0: Unified Delete für Notizen und Ordner, mit Undo. Notizen wandern in den
     * Papierkorb; Ordner werden gelöscht (auf dem Server tombstoned, außer nur-lokale). Notizen aus
     * gelöschten Ordnern landen im Papierkorb auf Root; ihr alter Server-Pfad wird zum Löschen
     * vorgemerkt, sonst belebt der nächste Sync den Ordner per `discoveredFolders` wieder.
     */
    fun deleteSelection(keepContainedNotes: Boolean) {
        val noteIds = selection.selectedNotes.value
        val folderNames = selection.selectedFolders.value
        // 🆕 v2.8.0: Markierung für Undo sichern (deleteFolder räumt sie auf).
        val localOnlyDeleted = folderNames.filter { name ->
            folders.localOnlyFolderNames.value.any { it.equals(name, ignoreCase = true) }
        }.toSet()
        val notes = ctx.notes()
        val notesInFolders = notes.filter { it.folderName in folderNames }
        val notesToTrash = (notes.filter { it.id in noteIds } + if (!keepContainedNotes) notesInFolders else emptyList())
            .distinctBy { it.id }
        val notesToRoot = if (keepContainedNotes) notesInFolders.filter { it.id !in noteIds } else emptyList()
        if (notesToTrash.isEmpty() && folderNames.isEmpty()) return

        val trashIds = notesToTrash.map { it.id }.toSet()
        _hiddenIds.update { it + trashIds }
        selection.clearSelection()
        val message = deleteMessage(folderNames.size, notesToTrash.size)

        ctx.scope.launch {
            val trashed = withContext(ioDispatcher) { trashManager.moveToTrash(notesToTrash, toRootFrom = folderNames) }
            folders.deleteFolders(folderNames, notesToRoot, localOnlyDeleted)
            ctx.reloadNotes()
            ctx.showUndo(message) { undoDeleteSelection(trashed, folderNames, notesToRoot, localOnlyDeleted) }
            ctx.refreshWidgets()

            delay(SNACKBAR_UNDO_DELAY_MS)
            val stillPending = trashIds.filter { it in _hiddenIds.value }.toSet()
            if (stillPending.isEmpty() && folderNames.isEmpty()) return@launch
            trashManager.queueOldPathDeletions(trashed.filter { it.id in stillPending }, folderNames)
            _hiddenIds.update { it - stillPending }
            if (ctx.hasServerConfig() && !ctx.isOfflineMode()) {
                sync.deleteEmptyServerFolders(folderNames - localOnlyDeleted)
            }
            ctx.triggerOnSaveSync()
        }
    }

    private fun deleteMessage(folderCount: Int, noteCount: Int): String = when {
        folderCount > 0 && noteCount > 0 ->
            ctx.plural(R.plurals.snackbar_folders_deleted, folderCount) + " · " + ctx.plural(R.plurals.snackbar_notes_trashed, noteCount)
        folderCount > 0 -> ctx.plural(R.plurals.snackbar_folders_deleted, folderCount)
        else -> ctx.plural(R.plurals.snackbar_notes_trashed, noteCount)
    }

    private fun undoDeleteSelection(trashed: List<Note>, deletedFolders: Set<String>, keptNotes: List<Note>, localOnly: Set<String>) {
        _hiddenIds.update { it - trashed.map { n -> n.id }.toSet() }
        ctx.scope.launch {
            withContext(ioDispatcher) { trashManager.undoMoveToTrash(trashed) }
            folders.restoreFolders(deletedFolders, keptNotes, localOnly)
            ctx.reloadNotes()
        }
    }

    private companion object {
        const val SNACKBAR_UNDO_DELAY_MS = 3500L
    }
}
