package dev.dettmer.simplenotes.ui.editor

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.dettmer.simplenotes.models.ChecklistItem
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.SyncWorker
import dev.dettmer.simplenotes.sync.WebDavSyncService
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.DeviceIdGenerator
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * ViewModel for NoteEditor Compose Screen
 * v1.5.0: Jetpack Compose NoteEditor Redesign
 * 
 * Manages note editing state including title, content, and checklist items.
 */
class NoteEditorViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "NoteEditorViewModel"
        const val ARG_NOTE_ID = "noteId"
        const val ARG_NOTE_TYPE = "noteType"
    }
    
    private val storage = NotesStorage(application)
    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    
    // ═══════════════════════════════════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════════════════════════════════
    
    private val _uiState = MutableStateFlow(NoteEditorUiState())
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()
    
    private val _checklistItems = MutableStateFlow<List<ChecklistItemState>>(emptyList())
    val checklistItems: StateFlow<List<ChecklistItemState>> = _checklistItems.asStateFlow()
    
    // 🌟 v1.6.0: Offline Mode State
    private val _isOfflineMode = MutableStateFlow(
        prefs.getBoolean(Constants.KEY_OFFLINE_MODE, true)
    )
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()
    
    // ═══════════════════════════════════════════════════════════════════════
    // Events
    // ═══════════════════════════════════════════════════════════════════════
    
    private val _events = MutableSharedFlow<NoteEditorEvent>()
    val events: SharedFlow<NoteEditorEvent> = _events.asSharedFlow()
    
    // Internal state
    private var existingNote: Note? = null
    private var currentNoteType: NoteType = NoteType.TEXT
    
    init {
        loadNote()
    }
    
    private fun loadNote() {
        val noteId = savedStateHandle.get<String>(ARG_NOTE_ID)
        val noteTypeString = savedStateHandle.get<String>(ARG_NOTE_TYPE) ?: NoteType.TEXT.name
        
        if (noteId != null) {
            // Load existing note
            existingNote = storage.loadNote(noteId)
            existingNote?.let { note ->
                currentNoteType = note.noteType
                _uiState.update { state ->
                    state.copy(
                        title = note.title,
                        content = note.content,
                        noteType = note.noteType,
                        isNewNote = false,
                        toolbarTitle = if (note.noteType == NoteType.CHECKLIST) {
                            ToolbarTitle.EDIT_CHECKLIST
                        } else {
                            ToolbarTitle.EDIT_NOTE
                        }
                    )
                }
                
                if (note.noteType == NoteType.CHECKLIST) {
                    val items = note.checklistItems?.sortedBy { it.order }?.map { 
                        ChecklistItemState(
                            id = it.id,
                            text = it.text,
                            isChecked = it.isChecked,
                            order = it.order
                        )
                    } ?: emptyList()
                    _checklistItems.value = items
                }
            }
        } else {
            // New note
            currentNoteType = try {
                NoteType.valueOf(noteTypeString)
            } catch (e: IllegalArgumentException) {
                Logger.w(TAG, "Invalid note type '$noteTypeString', defaulting to TEXT: ${e.message}")
                NoteType.TEXT
            }
            
            _uiState.update { state ->
                state.copy(
                    noteType = currentNoteType,
                    isNewNote = true,
                    toolbarTitle = if (currentNoteType == NoteType.CHECKLIST) {
                        ToolbarTitle.NEW_CHECKLIST
                    } else {
                        ToolbarTitle.NEW_NOTE
                    }
                )
            }
            
            // Add first empty item for new checklists
            if (currentNoteType == NoteType.CHECKLIST) {
                _checklistItems.value = listOf(ChecklistItemState.createEmpty(0))
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Actions
    // ═══════════════════════════════════════════════════════════════════════
    
    fun updateTitle(title: String) {
        _uiState.update { it.copy(title = title) }
    }
    
    fun updateContent(content: String) {
        _uiState.update { it.copy(content = content) }
    }
    
    fun updateChecklistItemText(itemId: String, newText: String) {
        _checklistItems.update { items ->
            items.map { item ->
                if (item.id == itemId) item.copy(text = newText) else item
            }
        }
    }
    
    fun updateChecklistItemChecked(itemId: String, isChecked: Boolean) {
        _checklistItems.update { items ->
            items.map { item ->
                if (item.id == itemId) item.copy(isChecked = isChecked) else item
            }
        }
    }
    
    fun addChecklistItemAfter(afterItemId: String): String {
        val newItem = ChecklistItemState.createEmpty(0)
        _checklistItems.update { items ->
            val index = items.indexOfFirst { it.id == afterItemId }
            if (index >= 0) {
                val newList = items.toMutableList()
                newList.add(index + 1, newItem)
                // Update order values
                newList.mapIndexed { i, item -> item.copy(order = i) }
            } else {
                items + newItem.copy(order = items.size)
            }
        }
        return newItem.id
    }
    
    fun addChecklistItemAtEnd(): String {
        val newItem = ChecklistItemState.createEmpty(_checklistItems.value.size)
        _checklistItems.update { items -> items + newItem }
        return newItem.id
    }
    
    fun deleteChecklistItem(itemId: String) {
        _checklistItems.update { items ->
            val filtered = items.filter { it.id != itemId }
            // Ensure at least one item exists
            if (filtered.isEmpty()) {
                listOf(ChecklistItemState.createEmpty(0))
            } else {
                // Update order values
                filtered.mapIndexed { index, item -> item.copy(order = index) }
            }
        }
    }
    
    fun moveChecklistItem(fromIndex: Int, toIndex: Int) {
        _checklistItems.update { items ->
            val mutableList = items.toMutableList()
            val item = mutableList.removeAt(fromIndex)
            mutableList.add(toIndex, item)
            // Update order values
            mutableList.mapIndexed { index, i -> i.copy(order = index) }
        }
    }
    
    fun saveNote() {
        viewModelScope.launch {
            val state = _uiState.value
            val title = state.title.trim()
            
            when (currentNoteType) {
                NoteType.TEXT -> {
                    val content = state.content.trim()
                    
                    if (title.isEmpty() && content.isEmpty()) {
                        _events.emit(NoteEditorEvent.ShowToast(ToastMessage.NOTE_IS_EMPTY))
                        return@launch
                    }
                    
                    val note = if (existingNote != null) {
                        existingNote!!.copy(
                            title = title,
                            content = content,
                            noteType = NoteType.TEXT,
                            checklistItems = null,
                            updatedAt = System.currentTimeMillis(),
                            syncStatus = SyncStatus.PENDING
                        )
                    } else {
                        Note(
                            title = title,
                            content = content,
                            noteType = NoteType.TEXT,
                            checklistItems = null,
                            deviceId = DeviceIdGenerator.getDeviceId(getApplication()),
                            syncStatus = SyncStatus.LOCAL_ONLY
                        )
                    }
                    
                    storage.saveNote(note)
                }
                
                NoteType.CHECKLIST -> {
                    // Filter empty items
                    val validItems = _checklistItems.value
                        .filter { it.text.isNotBlank() }
                        .mapIndexed { index, item ->
                            ChecklistItem(
                                id = item.id,
                                text = item.text,
                                isChecked = item.isChecked,
                                order = index
                            )
                        }
                    
                    if (title.isEmpty() && validItems.isEmpty()) {
                        _events.emit(NoteEditorEvent.ShowToast(ToastMessage.NOTE_IS_EMPTY))
                        return@launch
                    }
                    
                    val note = if (existingNote != null) {
                        existingNote!!.copy(
                            title = title,
                            content = "", // Empty for checklists
                            noteType = NoteType.CHECKLIST,
                            checklistItems = validItems,
                            updatedAt = System.currentTimeMillis(),
                            syncStatus = SyncStatus.PENDING
                        )
                    } else {
                        Note(
                            title = title,
                            content = "",
                            noteType = NoteType.CHECKLIST,
                            checklistItems = validItems,
                            deviceId = DeviceIdGenerator.getDeviceId(getApplication()),
                            syncStatus = SyncStatus.LOCAL_ONLY
                        )
                    }
                    
                    storage.saveNote(note)
                }
            }
            
            _events.emit(NoteEditorEvent.ShowToast(ToastMessage.NOTE_SAVED))
            
            // 🌟 v1.6.0: Trigger onSave Sync
            triggerOnSaveSync()
            
            _events.emit(NoteEditorEvent.NavigateBack)
        }
    }
    
    /**
     * Delete the current note
     * @param deleteOnServer if true, also triggers server deletion; if false, only deletes locally
     * v1.5.0: Added deleteOnServer parameter for unified delete dialog
     */
    fun deleteNote(deleteOnServer: Boolean = true) {
        viewModelScope.launch {
            existingNote?.let { note ->
                val noteId = note.id
                
                // Delete locally first
                storage.deleteNote(noteId)
                
                // Delete from server if requested
                if (deleteOnServer) {
                    try {
                        val webdavService = WebDavSyncService(getApplication())
                        val success = withContext(Dispatchers.IO) {
                            webdavService.deleteNoteFromServer(noteId)
                        }
                        if (success) {
                            Logger.d(TAG, "Note $noteId deleted from server")
                        } else {
                            Logger.w(TAG, "Failed to delete note $noteId from server")
                        }
                    } catch (e: Exception) {
                        Logger.e(TAG, "Error deleting note from server: ${e.message}")
                    }
                }
                
                _events.emit(NoteEditorEvent.ShowToast(ToastMessage.NOTE_DELETED))
                _events.emit(NoteEditorEvent.NavigateBack)
            }
        }
    }
    
    fun showDeleteConfirmation() {
        viewModelScope.launch {
            _events.emit(NoteEditorEvent.ShowDeleteConfirmation)
        }
    }
    
    fun canDelete(): Boolean = existingNote != null
    
    // ═══════════════════════════════════════════════════════════════════════════
    // 🌟 v1.6.0: Sync Trigger - onSave
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Triggers sync after saving a note (if enabled and server configured)
     * v1.6.0: New configurable sync trigger
     * v1.7.0: Uses central canSync() gate for WiFi-only check
     * 
     * Separate throttling (5 seconds) to prevent spam when saving multiple times
     */
    private fun triggerOnSaveSync() {
        // Check 1: Trigger enabled?
        if (!prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_ON_SAVE, Constants.DEFAULT_TRIGGER_ON_SAVE)) {
            Logger.d(TAG, "⏭️ onSave sync disabled - skipping")
            return
        }
        
        // 🆕 v1.7.0: Zentrale Sync-Gate Prüfung (inkl. WiFi-Only, Offline Mode, Server Config)
        val syncService = WebDavSyncService(getApplication())
        val gateResult = syncService.canSync()
        if (!gateResult.canSync) {
            if (gateResult.isBlockedByWifiOnly) {
                Logger.d(TAG, "⏭️ onSave sync blocked: WiFi-only mode, not on WiFi")
            } else {
                Logger.d(TAG, "⏭️ onSave sync blocked: ${gateResult.blockReason ?: "offline/no server"}")
            }
            return
        }
        
        // Check 2: Throttling (5 seconds) to prevent spam
        val lastOnSaveSyncTime = prefs.getLong(Constants.PREF_LAST_ON_SAVE_SYNC_TIME, 0)
        val now = System.currentTimeMillis()
        val timeSinceLastSync = now - lastOnSaveSyncTime
        
        if (timeSinceLastSync < Constants.MIN_ON_SAVE_SYNC_INTERVAL_MS) {
            val remainingSeconds = (Constants.MIN_ON_SAVE_SYNC_INTERVAL_MS - timeSinceLastSync) / 1000
            Logger.d(TAG, "⏳ onSave sync throttled - wait ${remainingSeconds}s")
            return
        }
        
        // Update last sync time
        prefs.edit().putLong(Constants.PREF_LAST_ON_SAVE_SYNC_TIME, now).apply()
        
        // Trigger sync via WorkManager
        Logger.d(TAG, "📤 Triggering onSave sync")
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .addTag(Constants.SYNC_WORK_TAG)
            .build()
        WorkManager.getInstance(getApplication()).enqueue(syncRequest)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// State Classes
// ═══════════════════════════════════════════════════════════════════════════

data class NoteEditorUiState(
    val title: String = "",
    val content: String = "",
    val noteType: NoteType = NoteType.TEXT,
    val isNewNote: Boolean = true,
    val toolbarTitle: ToolbarTitle = ToolbarTitle.NEW_NOTE
)

data class ChecklistItemState(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val isChecked: Boolean = false,
    val order: Int = 0
) {
    companion object {
        fun createEmpty(order: Int): ChecklistItemState {
            return ChecklistItemState(
                id = UUID.randomUUID().toString(),
                text = "",
                isChecked = false,
                order = order
            )
        }
    }
}

enum class ToolbarTitle {
    NEW_NOTE,
    EDIT_NOTE,
    NEW_CHECKLIST,
    EDIT_CHECKLIST
}

enum class ToastMessage {
    NOTE_IS_EMPTY,
    NOTE_SAVED,
    NOTE_DELETED
}

sealed interface NoteEditorEvent {
    data class ShowToast(val message: ToastMessage) : NoteEditorEvent
    data object NavigateBack : NoteEditorEvent
    data object ShowDeleteConfirmation : NoteEditorEvent
}
