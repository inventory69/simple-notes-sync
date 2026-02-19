package dev.dettmer.simplenotes.ui.editor

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.dettmer.simplenotes.models.ChecklistItem
import dev.dettmer.simplenotes.models.ChecklistSortOption
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.SyncStateManager
import dev.dettmer.simplenotes.sync.SyncWorker
import dev.dettmer.simplenotes.sync.WebDavSyncService
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.DeviceIdGenerator
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.CoroutineDispatcher
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

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    
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
    
    // 🔀 v1.8.0 (IMPL_020): Letzte Checklist-Sortierung (Session-Scope)
    private val _lastChecklistSortOption = MutableStateFlow(ChecklistSortOption.MANUAL)
    val lastChecklistSortOption: StateFlow<ChecklistSortOption> = _lastChecklistSortOption.asStateFlow()
    
    // ═══════════════════════════════════════════════════════════════════════
    // Events
    // ═══════════════════════════════════════════════════════════════════════
    
    private val _events = MutableSharedFlow<NoteEditorEvent>()
    val events: SharedFlow<NoteEditorEvent> = _events.asSharedFlow()
    
    // Internal state
    private var existingNote: Note? = null
    private var currentNoteType: NoteType = NoteType.TEXT
    
    // 🛡️ v1.8.2 (IMPL_17): Trackt ob User ungespeicherte Checklist-Edits hat.
    // Wenn true, überspringt reloadFromStorage() das Neuladen, damit onResume()
    // (Notification Shade, App-Switcher etc.) keine User-Änderungen überschreibt.
    private var hasUnsavedChecklistEdits = false
    
    init {
        loadNote()
    }
    
    private fun loadNote() {
        val noteId = savedStateHandle.get<String>(ARG_NOTE_ID)
        val noteTypeString = savedStateHandle.get<String>(ARG_NOTE_TYPE) ?: NoteType.TEXT.name
        
        if (noteId != null) {
            loadExistingNote(noteId)
        } else {
            initNewNote(noteTypeString)
        }
    }

    private fun loadExistingNote(noteId: String) {
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
                loadChecklistData(note)
            }
        }
    }

    private fun loadChecklistData(note: Note) {
        // 🆕 v1.8.1 (IMPL_03): Gespeicherte Sortierung laden
        note.checklistSortOption?.let { sortName ->
            _lastChecklistSortOption.value = parseSortOption(sortName)
        }
        
        val items = note.checklistItems?.sortedBy { it.order }?.map {
            ChecklistItemState(
                id = it.id,
                text = it.text,
                isChecked = it.isChecked,
                order = it.order
            )
        }.orEmpty()
        // 🆕 v1.8.0 (IMPL_017): Sortierung sicherstellen (falls alte Daten unsortiert sind)
        _checklistItems.value = sortChecklistItems(items)
    }

    private fun initNewNote(noteTypeString: String) {
        currentNoteType = try {
            NoteType.valueOf(noteTypeString)
        } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
            Logger.w(TAG, "Invalid note type '$noteTypeString', defaulting to TEXT")
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

    /**
     * Safely parse a ChecklistSortOption from its string name.
     * Falls back to MANUAL if the name is unknown (e.g., from older app versions).
     */
    private fun parseSortOption(sortName: String): ChecklistSortOption {
        return try {
            ChecklistSortOption.valueOf(sortName)
        } catch (@Suppress("SwallowedException") e: IllegalArgumentException) {
            Logger.w(TAG, "Unknown sort option '$sortName', using MANUAL")
            ChecklistSortOption.MANUAL
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
        hasUnsavedChecklistEdits = true  // 🛡️ v1.8.2 (IMPL_17)
        _checklistItems.update { items ->
            items.map { item ->
                if (item.id == itemId) item.copy(text = newText) else item
            }
        }
    }
    
    /**
     * 🆕 v1.8.0 (IMPL_017): Sortiert Checklist-Items mit Unchecked oben, Checked unten.
     * Stabile Sortierung: Relative Reihenfolge innerhalb jeder Gruppe bleibt erhalten.
     */
    /**
     * Sortiert Checklist-Items basierend auf der aktuellen Sortier-Option.
     * 🆕 v1.8.1 (IMPL_03-FIX): Berücksichtigt jetzt _lastChecklistSortOption
     * anstatt immer unchecked-first zu sortieren.
     */
    private fun sortChecklistItems(items: List<ChecklistItemState>): List<ChecklistItemState> {
        val sorted = when (_lastChecklistSortOption.value) {
            ChecklistSortOption.MANUAL,
            ChecklistSortOption.UNCHECKED_FIRST -> {
                val unchecked = items.filter { !it.isChecked }
                val checked = items.filter { it.isChecked }
                unchecked + checked
            }
            ChecklistSortOption.CHECKED_FIRST ->
                items.sortedByDescending { it.isChecked }
            ChecklistSortOption.ALPHABETICAL_ASC ->
                items.sortedBy { it.text.lowercase() }
            ChecklistSortOption.ALPHABETICAL_DESC ->
                items.sortedByDescending { it.text.lowercase() }
        }

        return sorted.mapIndexed { index, item ->
            item.copy(order = index)
        }
    }

    fun updateChecklistItemChecked(itemId: String, isChecked: Boolean) {
        hasUnsavedChecklistEdits = true  // 🛡️ v1.8.2 (IMPL_17)
        _checklistItems.update { items ->
            val updatedItems = items.map { item ->
                if (item.id == itemId) item.copy(isChecked = isChecked) else item
            }
            // 🆕 v1.8.0 (IMPL_017 + IMPL_020): Auto-Sort nur bei MANUAL und UNCHECKED_FIRST
            val currentSort = _lastChecklistSortOption.value
            if (currentSort == ChecklistSortOption.MANUAL || currentSort == ChecklistSortOption.UNCHECKED_FIRST) {
                sortChecklistItems(updatedItems)
            } else {
                // Bei anderen Sortierungen (alphabetisch, checked first) nicht auto-sortieren
                updatedItems.mapIndexed { index, item -> item.copy(order = index) }
            }
        }
    }
    
    /**
     * 🆕 v1.8.1 (IMPL_15): Fügt ein neues Item nach dem angegebenen Item ein.
     *
     * Guard: Bei MANUAL/UNCHECKED_FIRST wird sichergestellt, dass das neue (unchecked)
     * Item nicht innerhalb der checked-Sektion eingefügt wird. Falls das Trigger-Item
     * checked ist, wird stattdessen vor dem ersten checked Item eingefügt.
     */
    fun addChecklistItemAfter(afterItemId: String): String {
        hasUnsavedChecklistEdits = true  // 🛡️ v1.8.2 (IMPL_17)
        val newItem = ChecklistItemState.createEmpty(0)
        _checklistItems.update { items ->
            val index = items.indexOfFirst { it.id == afterItemId }
            if (index >= 0) {
                val currentSort = _lastChecklistSortOption.value
                val hasSeparator = currentSort == ChecklistSortOption.MANUAL ||
                        currentSort == ChecklistSortOption.UNCHECKED_FIRST

                // 🆕 v1.8.1 (IMPL_15): Wenn das Trigger-Item checked ist und ein Separator
                // existiert, darf das neue unchecked Item nicht in die checked-Sektion.
                // → Stattdessen vor dem ersten checked Item einfügen.
                val effectiveIndex = if (hasSeparator && items[index].isChecked) {
                    val firstCheckedIndex = items.indexOfFirst { it.isChecked }
                    if (firstCheckedIndex >= 0) firstCheckedIndex else index + 1
                } else {
                    index + 1
                }

                val newList = items.toMutableList()
                newList.add(effectiveIndex, newItem)
                // Update order values
                newList.mapIndexed { i, item -> item.copy(order = i) }
            } else {
                items + newItem.copy(order = items.size)
            }
        }
        return newItem.id
    }

    /**
     * 🆕 v1.8.1 (IMPL_15): Fügt ein neues Item an der semantisch korrekten Position ein.
     *
     * Bei MANUAL/UNCHECKED_FIRST: Vor dem ersten checked Item (= direkt über dem Separator).
     * Bei allen anderen Modi: Am Ende der Liste (kein Separator sichtbar).
     *
     * Verhindert, dass checked Items über den Separator springen oder das neue Item
     * unter dem Separator erscheint.
     */
    fun addChecklistItemAtEnd(): String {
        hasUnsavedChecklistEdits = true  // 🛡️ v1.8.2 (IMPL_17)
        val newItem = ChecklistItemState.createEmpty(0)
        _checklistItems.update { items ->
            val insertIndex = calculateInsertIndexForNewItem(items)
            val newList = items.toMutableList()
            newList.add(insertIndex, newItem)
            newList.mapIndexed { i, item -> item.copy(order = i) }
        }
        return newItem.id
    }

    /**
     * 🆕 v1.8.1 (IMPL_15): Berechnet die korrekte Insert-Position für ein neues unchecked Item.
     *
     * - MANUAL / UNCHECKED_FIRST: Vor dem ersten checked Item (direkt über dem Separator)
     * - Alle anderen Modi: Am Ende der Liste (kein Separator, kein visuelles Problem)
     *
     * Falls keine checked Items existieren, wird am Ende eingefügt.
     */
    private fun calculateInsertIndexForNewItem(items: List<ChecklistItemState>): Int {
        val currentSort = _lastChecklistSortOption.value
        return when (currentSort) {
            ChecklistSortOption.MANUAL,
            ChecklistSortOption.UNCHECKED_FIRST -> {
                val firstCheckedIndex = items.indexOfFirst { it.isChecked }
                if (firstCheckedIndex >= 0) firstCheckedIndex else items.size
            }
            else -> items.size
        }
    }
    
    fun deleteChecklistItem(itemId: String) {
        hasUnsavedChecklistEdits = true  // 🛡️ v1.8.2 (IMPL_17)
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
        hasUnsavedChecklistEdits = true  // 🛡️ v1.8.2 (IMPL_17)
        _checklistItems.update { items ->
            val fromItem = items.getOrNull(fromIndex) ?: return@update items
            val toItem = items.getOrNull(toIndex) ?: return@update items

            val mutableList = items.toMutableList()
            val item = mutableList.removeAt(fromIndex)

            // 🆕 v1.8.1 IMPL_14: Cross-Boundary Move mit Auto-Toggle
            // Wenn ein Item die Grenze überschreitet, wird es automatisch checked/unchecked.
            val movedItem = if (fromItem.isChecked != toItem.isChecked) {
                item.copy(isChecked = toItem.isChecked)
            } else {
                item
            }

            mutableList.add(toIndex, movedItem)
            // Update order values
            mutableList.mapIndexed { index, i -> i.copy(order = index) }
        }
    }
    
    /**
     * 🔀 v1.8.0 (IMPL_020): Sortiert Checklist-Items nach gewählter Option.
     * Einmalige Aktion (nicht persistiert) — User kann danach per Drag & Drop feinjustieren.
     */
    fun sortChecklistItems(option: ChecklistSortOption) {
        hasUnsavedChecklistEdits = true  // 🛡️ v1.8.2 (IMPL_17)
        // Merke die Auswahl für diesen Editor-Session
        _lastChecklistSortOption.value = option
        
        _checklistItems.update { items ->
            val sorted = when (option) {
                // Bei MANUAL: Sortiere nach checked/unchecked, damit Separator korrekt platziert wird
                ChecklistSortOption.MANUAL -> items.sortedBy { it.isChecked }
                
                ChecklistSortOption.ALPHABETICAL_ASC -> 
                    items.sortedBy { it.text.lowercase() }
                
                ChecklistSortOption.ALPHABETICAL_DESC -> 
                    items.sortedByDescending { it.text.lowercase() }
                
                ChecklistSortOption.UNCHECKED_FIRST -> 
                    items.sortedBy { it.isChecked }
                
                ChecklistSortOption.CHECKED_FIRST -> 
                    items.sortedByDescending { it.isChecked }
            }
            
            // Order-Werte neu zuweisen
            sorted.mapIndexed { index, item -> item.copy(order = index) }
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
                    
                    val note = existingNote?.copy(
                            title = title,
                            content = content,
                            noteType = NoteType.TEXT,
                            checklistItems = null,
                            updatedAt = System.currentTimeMillis(),
                            syncStatus = SyncStatus.PENDING
                        ) ?: Note(
                            title = title,
                            content = content,
                            noteType = NoteType.TEXT,
                            checklistItems = null,
                            deviceId = DeviceIdGenerator.getDeviceId(getApplication()),
                            syncStatus = SyncStatus.LOCAL_ONLY
                        )
                    
                    storage.saveNote(note)
                }
                
                NoteType.CHECKLIST -> {
                    // 🛡️ v1.8.2 (IMPL_17): Flag zurücksetzen — gespeicherter Stand ist jetzt aktuell
                    hasUnsavedChecklistEdits = false
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
                    
                    val note = existingNote?.copy(
                            title = title,
                            content = "", // Empty for checklists
                            noteType = NoteType.CHECKLIST,
                            checklistItems = validItems,
                            checklistSortOption = _lastChecklistSortOption.value.name,  // 🆕 v1.8.1 (IMPL_03)
                            updatedAt = System.currentTimeMillis(),
                            syncStatus = SyncStatus.PENDING
                        ) ?: Note(
                            title = title,
                            content = "",
                            noteType = NoteType.CHECKLIST,
                            checklistItems = validItems,
                            checklistSortOption = _lastChecklistSortOption.value.name,  // 🆕 v1.8.1 (IMPL_03)
                            deviceId = DeviceIdGenerator.getDeviceId(getApplication()),
                            syncStatus = SyncStatus.LOCAL_ONLY
                        )
                    
                    storage.saveNote(note)
                }
            }
            
            // 🆕 v1.8.1 (IMPL_12): NOTE_SAVED Toast entfernt — NavigateBack ist ausreichend

            // 🌟 v1.6.0: Trigger onSave Sync
            triggerOnSaveSync()

            // 🆕 v1.8.0: Betroffene Widgets aktualisieren
            try {
                val glanceManager = androidx.glance.appwidget.GlanceAppWidgetManager(getApplication())
                val glanceIds = glanceManager.getGlanceIds(dev.dettmer.simplenotes.widget.NoteWidget::class.java)
                glanceIds.forEach { id ->
                    dev.dettmer.simplenotes.widget.NoteWidget().update(getApplication(), id)
                }
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to update widgets: ${e.message}")
            }

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
                        val success = withContext(ioDispatcher) {
                            webdavService.deleteNoteFromServer(noteId)
                        }
                        // 🆕 v1.8.1 (IMPL_12): Banner-Feedback statt stiller Log-Einträge
                        if (success) {
                            Logger.d(TAG, "Note $noteId deleted from server")
                            SyncStateManager.showInfo(
                                getApplication<Application>().getString(
                                    dev.dettmer.simplenotes.R.string.snackbar_deleted_from_server
                                )
                            )
                        } else {
                            Logger.w(TAG, "Failed to delete note $noteId from server")
                            SyncStateManager.showError(
                                getApplication<Application>().getString(
                                    dev.dettmer.simplenotes.R.string.snackbar_server_delete_failed
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Logger.e(TAG, "Error deleting note from server: ${e.message}")
                        SyncStateManager.showError(
                            getApplication<Application>().getString(
                                dev.dettmer.simplenotes.R.string.snackbar_server_error,
                                e.message.orEmpty()
                            )
                        )
                    }
                }
                
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

    /**
     * 🆕 v1.8.0 (IMPL_025): Reload Note aus Storage nach Resume
     *
     * Wird aufgerufen wenn die Activity aus dem Hintergrund zurückkehrt.
     * Liest den aktuellen Note-Stand von Disk und aktualisiert den ViewModel-State.
     *
     * Wird nur für existierende Checklist-Notes benötigt (neue Notes haben keinen
     * externen Schreiber). Relevant für Widget-Checklist-Toggles.
     *
     * Nur checklistItems werden aktualisiert — nicht title oder content,
     * damit ungespeicherte Text-Änderungen im Editor nicht verloren gehen.
     */
    fun reloadFromStorage() {
        // 🛡️ v1.8.2 (IMPL_17): Nicht neuladen wenn User ungespeicherte Checklist-Edits hat.
        // Verhindert Datenverlust bei onResume() (Notification Shade, App-Switcher etc.)
        if (hasUnsavedChecklistEdits) return
        
        val noteId = savedStateHandle.get<String>(ARG_NOTE_ID) ?: return

        val freshNote = storage.loadNote(noteId) ?: return

        // Nur Checklist-Items aktualisieren
        if (freshNote.noteType == NoteType.CHECKLIST) {
            val freshItems = freshNote.checklistItems?.sortedBy { it.order }?.map {
                ChecklistItemState(
                    id = it.id,
                    text = it.text,
                    isChecked = it.isChecked,
                    order = it.order
                )
            } ?: return

            _checklistItems.value = sortChecklistItems(freshItems)
            // existingNote aktualisieren damit beim Speichern der richtige
            // Basis-State verwendet wird
            existingNote = freshNote
        }
    }
    
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
            .addTag(Constants.SYNC_ONSAVE_TAG)  // 🆕 v1.8.1 (IMPL_08B): Bypassed globalen Cooldown
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
