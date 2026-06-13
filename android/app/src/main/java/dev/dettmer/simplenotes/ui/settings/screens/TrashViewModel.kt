package dev.dettmer.simplenotes.ui.settings.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.storage.FolderStore
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.storage.TrashManager
import dev.dettmer.simplenotes.sync.PendingServerDeletions
import dev.dettmer.simplenotes.sync.SyncScheduler
import dev.dettmer.simplenotes.widget.WidgetUpdateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 🆕 v2.9.0 (Trash): Dünner ViewModel für den Papierkorb-Screen. Die eigentliche Logik liegt im
 * [TrashManager]; dieser ViewModel hält nur den Listen-State und löst nach Mutationen Reload,
 * Widget-Refresh und Sync aus. Beim Start werden abgelaufene Einträge automatisch gepurged.
 */
class TrashViewModel(application: Application) : AndroidViewModel(application) {
    private val ioDispatcher = Dispatchers.IO
    private val storage = NotesStorage(application)
    private val folderStore = FolderStore(application)
    private val trashManager = TrashManager(
        storage = storage,
        pendingServerDeletions = PendingServerDeletions(application),
        folderStore = folderStore
    )
    private val syncScheduler by lazy { SyncScheduler(application) }

    private val _trashedNotes = MutableStateFlow<List<Note>>(emptyList())
    val trashedNotes: StateFlow<List<Note>> = _trashedNotes.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            // Auto-Purge abgelaufener Einträge beim Öffnen des Papierkorbs.
            val purged = trashManager.purgeExpired()
            if (purged > 0) syncScheduler.triggerOnSaveSync(reason = "trashAutoPurge")
            reload()
            withContext(Dispatchers.Main) { _isReady.value = true }
        }
    }

    private suspend fun reload() {
        val notes = storage.loadTrashedNotes(forceReload = true)
            .sortedByDescending { it.trashedAt ?: 0L }
        withContext(Dispatchers.Main) { _trashedNotes.value = notes }
    }

    fun restore(note: Note) {
        viewModelScope.launch(ioDispatcher) {
            trashManager.restore(note, folderStore.load())
            reload()
            afterMutation()
        }
    }

    fun purge(note: Note) {
        viewModelScope.launch(ioDispatcher) {
            trashManager.purge(listOf(note))
            reload()
            afterMutation()
        }
    }

    fun emptyTrash() {
        viewModelScope.launch(ioDispatcher) {
            trashManager.emptyTrash()
            reload()
            afterMutation()
        }
    }

    private suspend fun afterMutation() {
        WidgetUpdateHelper.refreshAllWidgets(getApplication())
        syncScheduler.triggerOnSaveSync(reason = "trash")
    }
}
