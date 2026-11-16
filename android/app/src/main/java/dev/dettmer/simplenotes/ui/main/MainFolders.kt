package dev.dettmer.simplenotes.ui.main

import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.Folder
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.storage.FolderStore
import dev.dettmer.simplenotes.storage.NoteMutations
import dev.dettmer.simplenotes.sync.SyncStateManager
import dev.dettmer.simplenotes.sync.WebDavSyncService
import dev.dettmer.simplenotes.utils.ActivityLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 🆕 v2.7.0 (Folders): Ordner der Hauptliste: welcher gerade offen ist, welche es gibt, welche
 * nur lokal sind (v2.8.0), und alles, was Ordner anlegt, umbenennt, verschiebt oder vom Sync
 * ausnimmt. Löschen mit Undo liegt in [MainTrash], das hier [deleteFolders]/[restoreFolders] nutzt.
 *
 * [onFolderShown] lädt Sortierung und Einklappzustand des angezeigten Ordners.
 */
class MainFolders(
    private val ctx: MainContext,
    private val folderStore: FolderStore,
    private val noteMutations: NoteMutations,
    private val sync: MainSyncController,
    private val selection: MainSelection,
    private val onFolderShown: (String?) -> Unit
) {
    private val ioDispatcher = Dispatchers.IO

    // null = Root-Ansicht
    private val _currentFolder = MutableStateFlow<String?>(null)
    val currentFolder: StateFlow<String?> = _currentFolder.asStateFlow()

    private val _list = MutableStateFlow<List<Folder>>(emptyList())
    val list: StateFlow<List<Folder>> = _list.asStateFlow()

    // 🆕 v2.8.0: gerätespezifische Ordner, die nicht synchronisiert werden
    private val _localOnlyFolderNames = MutableStateFlow(folderStore.getLocalOnlyFolderNames())
    val localOnlyFolderNames: StateFlow<Set<String>> = _localOnlyFolderNames.asStateFlow()

    /** Zeigt [folder] an (null = Root). */
    fun show(folder: String?) {
        _currentFolder.value = folder
        onFolderShown(folder)
    }

    fun enterFolder(name: String) = show(name)

    fun goToRoot() = show(null)

    /** Liest Ordnerliste und Nur-lokal-Markierungen neu aus dem [FolderStore]. */
    suspend fun reloadFromStore() {
        _localOnlyFolderNames.value = folderStore.getLocalOnlyFolderNames()
        _list.value = folderStore.loadFolders()
    }

    /** Ordner neu laden (onResume / nach Sync). Scrollt nach oben, wenn aus 0 Ordnern N werden. */
    fun refreshFolders() {
        ctx.scope.launch {
            val before = _list.value
            val after = withContext(ioDispatcher) { folderStore.loadFolders() }
            _list.value = after
            if (before.isEmpty() && after.isNotEmpty() && _currentFolder.value == null) ctx.requestScrollToTop()
        }
    }

    /**
     * 🆕 v2.8.0: [localOnly] markiert den Ordner VOR dem Anlegen als „nur lokal", damit kein
     * zwischenzeitlicher Sync ihn hochlädt. Bestehende Ordner werden nie still ausgeschlossen.
     */
    fun createFolder(name: String, localOnly: Boolean = false) {
        val trimmed = name.trim()
        val markLocalOnly = localOnly && _list.value.none { it.name.equals(trimmed, ignoreCase = true) }
        ctx.scope.launch {
            withContext(ioDispatcher) {
                if (markLocalOnly) {
                    folderStore.setLocalOnly(trimmed, true)
                    _localOnlyFolderNames.value = folderStore.getLocalOnlyFolderNames()
                }
                folderStore.addFolder(trimmed, dirty = !markLocalOnly)
            }
            _list.value = folderStore.loadFolders()
            if (!markLocalOnly) ctx.triggerOnSaveSync()
        }
    }

    /**
     * 🆕 v2.8.0: Ordner vom Sync ausschließen. Alle Notizen darin → LOCAL_ONLY, der lokale Stand
     * bleibt vollständig. [removeFromServer] = true: Server-Kopien werden gelöscht (offline-fähig
     * über die Queue), leere Verzeichnisse abgeräumt und der folders.json-Eintrag als Tombstone
     * propagiert. false: „Auf Server behalten".
     */
    fun excludeFoldersFromSync(folderNames: Set<String>, removeFromServer: Boolean) {
        if (folderNames.isEmpty()) return
        val updated = _localOnlyFolderNames.value + folderNames
        folderStore.setLocalOnlyFolderNames(updated)
        if (removeFromServer) folderStore.setServerRemovalQueue(folderStore.getServerRemovalQueue() + folderNames)
        _localOnlyFolderNames.value = updated
        selection.clearSelection()

        ctx.scope.launch(ioDispatcher) {
            val deletions = noteMutations.makeLocalOnly(folderNames).takeIf { removeFromServer }.orEmpty()
            ctx.reloadNotes(forceReload = true)
            if (!removeFromServer) return@launch
            if (deletions.isNotEmpty()) {
                sync.removeFromServer(deletions, folderNames)
            } else {
                SyncStateManager.showInfo(ctx.string(R.string.snackbar_folder_removed_from_server))
            }
            // Tombstone in folders.json propagieren; stiller Sync überschreibt das Banner nicht.
            ctx.triggerOnSaveSync()
        }
    }

    /**
     * 🆕 v2.8.0: Ordner wieder in den Sync aufnehmen. touch() bumpt updatedAt des Ordner-Eintrags,
     * damit ein Server-Tombstone ihn nicht sofort wieder löscht.
     */
    fun includeFoldersInSync(folderNames: Set<String>) {
        if (folderNames.isEmpty()) return
        val updated = _localOnlyFolderNames.value - folderNames
        folderStore.setLocalOnlyFolderNames(updated)
        folderStore.setServerRemovalQueue(folderStore.getServerRemovalQueue() - folderNames)
        _localOnlyFolderNames.value = updated
        selection.clearSelection()

        ctx.scope.launch(ioDispatcher) {
            folderNames.forEach { folderStore.touch(it) }
            noteMutations.includeInSync(folderNames)
            ctx.reloadNotes(forceReload = true)
            val isReachable = try {
                WebDavSyncService(ctx.app).isServerReachable(userAction = true)
            } catch (_: Exception) {
                false
            }
            if (isReachable) {
                sync.triggerManualSync(ActivityLog.Trigger.FOLDER_INCLUDE)
            } else {
                SyncStateManager.showInfo(ctx.string(R.string.snackbar_folder_sync_queued))
                ctx.triggerOnSaveSync()
            }
        }
    }

    /** Umbenennen; ein nur-lokaler Ordner behält Markierung und LOCAL_ONLY-Status (v2.8.0). */
    fun renameFolder(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.equals(oldName, ignoreCase = true) || _list.value.any { it.name.equals(trimmed, ignoreCase = true) }) return
        val localOnly = _localOnlyFolderNames.value.any { it.equals(oldName, ignoreCase = true) }
        ctx.scope.launch {
            withContext(ioDispatcher) { noteMutations.renameFolder(oldName, trimmed, localOnly) }
            reloadFromStore()
            if (_currentFolder.value == oldName) show(trimmed)
            afterNoteMove()
        }
    }

    /** Ziel nur lokal → die Notizen verlassen den Sync (LOCAL_ONLY, Server-Kopie wird gelöscht). */
    fun moveSelectedNotesTo(targetFolder: String?) {
        val ids = selection.selectedNotes.value.toList()
        if (ids.isEmpty()) return
        val targetLocalOnly = targetFolder != null && _localOnlyFolderNames.value.any { it.equals(targetFolder, ignoreCase = true) }
        ctx.scope.launch {
            withContext(ioDispatcher) { noteMutations.moveNotes(ids, targetFolder, targetLocalOnly) }
            _list.value = folderStore.loadFolders()
            afterNoteMove()
        }
    }

    private suspend fun afterNoteMove() {
        selection.clearSelection()
        ctx.reloadNotes(forceReload = true)
        ctx.triggerOnSaveSync()
        ctx.refreshWidgets()
    }

    /**
     * Teil von „Auswahl löschen": Notizen behalten → nach Root (alter Server-Pfad wird gelöscht),
     * Ordner tombstonen. Nur-lokale Ordner werden hart entfernt, ohne Tombstone zum Server.
     */
    suspend fun deleteFolders(names: Set<String>, keptNotes: List<Note>, localOnly: Set<String>) {
        withContext(ioDispatcher) {
            noteMutations.moveNotes(keptNotes.map { it.id }, target = null)
            names.forEach { name ->
                folderStore.deleteFolder(name, propagateToServer = name !in localOnly)
                ActivityLog.log(ActivityLog.Op.FOLDER_DELETE, ActivityLog.Src.LOCAL, folder = name)
            }
        }
        reloadFromStore()
        if (_currentFolder.value in names) show(null)
    }

    /** Undo zu [deleteFolders]. */
    suspend fun restoreFolders(names: Set<String>, keptNotes: List<Note>, localOnly: Set<String>) {
        withContext(ioDispatcher) {
            noteMutations.moveBack(keptNotes, localOnly)
            names.forEach { name ->
                // Markierung VOR addFolder wiederherstellen, damit kein Sync-Lauf den Ordner hochlädt.
                val wasLocalOnly = name in localOnly
                if (wasLocalOnly) folderStore.setLocalOnly(name, true)
                folderStore.addFolder(name, dirty = !wasLocalOnly)
            }
        }
        reloadFromStore()
    }
}
