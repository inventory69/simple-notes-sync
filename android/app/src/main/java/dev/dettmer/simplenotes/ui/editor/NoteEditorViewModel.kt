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
import dev.dettmer.simplenotes.sync.SyncWorker
import dev.dettmer.simplenotes.sync.WebDavSyncService
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.DeviceIdGenerator
import dev.dettmer.simplenotes.utils.Logger
import dev.dettmer.simplenotes.utils.NoteShareHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel for NoteEditor Compose Screen
 * v1.5.0: Jetpack Compose NoteEditor Redesign
 * 
 * Manages note editing state including title, content, and checklist items.
 */
@Suppress("LargeClass")  // 🔧 v1.10.0: Detekt compliance — many features, deliberate design
class NoteEditorViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "NoteEditorViewModel"
        const val ARG_NOTE_ID = "noteId"
        const val ARG_NOTE_TYPE = "noteType"
        private const val CALENDAR_TITLE_FALLBACK_MAX_LENGTH = 50  // 🆕 v1.10.0-Papa
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
    
    // 🔀 v1.8.0 (IMPL_020): Letzte Checklist-Sortierung (Session-Scope)
    private val _lastChecklistSortOption = MutableStateFlow(ChecklistSortOption.MANUAL)
    val lastChecklistSortOption: StateFlow<ChecklistSortOption> = _lastChecklistSortOption.asStateFlow()
    
    // ═══════════════════════════════════════════════════════════════════════
    // Events
    // ═══════════════════════════════════════════════════════════════════════
    
    private val _events = MutableSharedFlow<NoteEditorEvent>()
    val events: SharedFlow<NoteEditorEvent> = _events.asSharedFlow()

    // ═══════════════════════════════════════════════════════════════════════
    // 🆕 v1.9.0 (F14): Explicit scroll actions for check/un-check
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Sealed class for scroll actions after checking/un-checking a checklist item.
     * - [ScrollToTop]: Scroll the LazyColumn to index 0 (used on un-check).
     * - [NoScroll]: Explicitly do nothing — keeps scroll position stable (used on check).
     */
    sealed class ChecklistScrollAction {
        /** Un-check: scroll list to the very top. */
        object ScrollToTop : ChecklistScrollAction()
        /** Check: do not scroll — keep viewport exactly where it is. */
        object NoScroll : ChecklistScrollAction()
    }

    private val _checklistScrollAction = MutableSharedFlow<ChecklistScrollAction>(extraBufferCapacity = 1)
    val checklistScrollAction: SharedFlow<ChecklistScrollAction> = _checklistScrollAction.asSharedFlow()
    
    // Internal state
    private var existingNote: Note? = null
    private var currentNoteType: NoteType = NoteType.TEXT

    // v2.0.0: Callback to read the latest content directly from Compose TextFieldState.
    // Avoids snapshotFlow race condition when the user presses back before the next frame.
    var contentProvider: (() -> String)? = null
    
    // 🛡️ v1.8.2 (IMPL_17): Trackt ob User ungespeicherte Checklist-Edits hat.
    // Wenn true, überspringt reloadFromStorage() das Neuladen, damit onResume()
    // (Notification Shade, App-Switcher etc.) keine User-Änderungen überschreibt.
    private var hasUnsavedChecklistEdits = false

    // 🆕 v1.9.0: Autosave with debounce
    private val autosaveEnabled = prefs.getBoolean(
        Constants.KEY_AUTOSAVE_ENABLED, Constants.DEFAULT_AUTOSAVE_ENABLED
    )
    private var autosaveJob: kotlinx.coroutines.Job? = null
    private var isDirty = false  // 🆕 v1.9.0: only autosave when content has actually changed

    // 🆕 v1.9.0: Autosave indicator — briefly visible after a successful autosave
    private val _autosaveIndicatorVisible = MutableStateFlow(false)
    val autosaveIndicatorVisible: StateFlow<Boolean> = _autosaveIndicatorVisible.asStateFlow()

    // 🆕 v1.10.0: Undo/Redo
    private val undoRedoManager = UndoRedoManager()
    val canUndo: StateFlow<Boolean> = undoRedoManager.canUndo
    val canRedo: StateFlow<Boolean> = undoRedoManager.canRedo
    private var snapshotDebounceJob: kotlinx.coroutines.Job? = null
    private var isRestoringSnapshot = false
    // 🔧 v1.10.0: Snapshot des zuletzt gespeicherten Zustands.
    // Wird beim Öffnen und nach jedem erfolgreichen Save gesetzt.
    // Ermöglicht isDirty-Reset wenn Undo den Originalzustand wiederherstellt.
    private var savedSnapshot: EditorSnapshot? = null

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
        undoRedoManager.clear()  // 🆕 v1.10.0: No cross-note undo
        savedSnapshot = currentSnapshot()  // 🔧 v1.10.0: Referenz-Snapshot für isDirty-Reset
    }

    private fun loadChecklistData(note: Note) {
        // 🆕 v1.8.1 (IMPL_03): Gespeicherte Sortierung laden
        note.checklistSortOption?.let { sortName ->
            _lastChecklistSortOption.value = parseSortOption(sortName)
        }
        
        val rawItems = note.checklistItems?.sortedBy { it.order }.orEmpty()
        // 🆕 v1.9.0 (F04): Backward compat — old notes have all originalOrder == 0 (Gson default)
        val isPreF04Note = rawItems.all { it.originalOrder == 0 }
        // 🆕 v1.11.0: Backward compat — old notes have no createdAt (Gson default = 0)
        val isPreCreatedAtNote = rawItems.all { it.createdAt == 0L }
        val items = rawItems.mapIndexed { index, raw ->
            ChecklistItemState(
                id = raw.id,
                text = raw.text,
                isChecked = raw.isChecked,
                order = raw.order,
                originalOrder = if (isPreF04Note) raw.order else raw.originalOrder,
                createdAt = if (isPreCreatedAtNote) index.toLong() else raw.createdAt
            )
        }
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
        undoRedoManager.clear()  // 🆕 v1.10.0: No cross-note undo
        savedSnapshot = currentSnapshot()  // 🔧 v1.10.0: Referenz-Snapshot für isDirty-Reset
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
        if (title == _uiState.value.title) return  // 🆕 v1.9.0: no-op guard — hydration, not a user edit
        if (isRestoringSnapshot) return  // 🆕 v1.10.0: Suppress during undo/redo restore
        pushUndoSnapshotDebounced()  // 🆕 v1.10.0: Capture state before this edit
        isDirty = true
        _uiState.update { it.copy(title = title) }
        scheduleAutosave()  // 🆕 v1.9.0
    }
    
    fun updateContent(content: String) {
        if (content == _uiState.value.content) return  // 🆕 v1.9.0: no-op guard — hydration, not a user edit
        if (isRestoringSnapshot) return  // 🆕 v1.10.0: Suppress during undo/redo restore
        pushUndoSnapshotDebounced()  // 🆕 v1.10.0: Capture state before this edit
        isDirty = true
        _uiState.update { it.copy(content = content) }
        scheduleAutosave()  // 🆕 v1.9.0
    }
    
    fun updateChecklistItemText(itemId: String, newText: String) {
        // 🔧 v1.10.0: No-Op Guard — verhindert false positive isDirty bei Cursor-Repositionierung.
        // BasicTextField feuert onValueChange auch bei reiner Selection-Änderung (gleicher Text).
        val currentText = _checklistItems.value.find { it.id == itemId }?.text
        if (newText == currentText) return

        pushUndoSnapshotDebounced()  // 🆕 v1.10.0
        isDirty = true
        hasUnsavedChecklistEdits = true  // 🛡️ v1.8.2 (IMPL_17)
        _checklistItems.update { items ->
            items.map { item ->
                if (item.id == itemId) item.copy(text = newText) else item
            }
        }
        scheduleAutosave()  // 🆕 v1.9.0
    }
    
    /**
     * 🆕 v1.8.0 (IMPL_017): Sortiert Checklist-Items mit Unchecked oben, Checked unten.
     * Stabile Sortierung: Relative Reihenfolge innerhalb jeder Gruppe bleibt erhalten.
     */
    /**
     * Sortiert Checklist-Items basierend auf der aktuellen Sortier-Option.
     * 🆕 v1.8.1 (IMPL_03-FIX): Berücksichtigt jetzt _lastChecklistSortOption
     * anstatt immer unchecked-first zu sortieren.
     * 🆕 v1.9.0 (F04): Unchecked items werden nach originalOrder sortiert für Position-Restore.
     */
    private fun sortChecklistItems(items: List<ChecklistItemState>): List<ChecklistItemState> {
        val sorted = when (_lastChecklistSortOption.value) {
            ChecklistSortOption.MANUAL,
            ChecklistSortOption.UNCHECKED_FIRST -> {
                // 🆕 v1.9.0 (F04): Sort by originalOrder to restore un-checked item's original position
                val unchecked = items.filter { !it.isChecked }.sortedBy { it.originalOrder }
                val checked = items.filter { it.isChecked }.sortedBy { it.originalOrder }
                unchecked + checked
            }
            ChecklistSortOption.CREATION_DATE -> {
                // 🆕 v1.11.0: Sort by creation timestamp — oldest first (ascending)
                val unchecked = items.filter { !it.isChecked }.sortedBy { it.createdAt }
                val checked = items.filter { it.isChecked }.sortedBy { it.createdAt }
                unchecked + checked
            }
            ChecklistSortOption.CREATION_DATE_DESC -> {
                // 🆕 v1.11.0: Sort by creation timestamp — newest first (descending)
                val unchecked = items.filter { !it.isChecked }.sortedByDescending { it.createdAt }
                val checked = items.filter { it.isChecked }.sortedByDescending { it.createdAt }
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

    /**
     * 🆕 v1.9.0 (F14): Toggles the checked state of a checklist item and emits a scroll action.
     * - Un-check → emits [ChecklistScrollAction.ScrollToTop]: scroll to the top of the list.
     * - Check → emits [ChecklistScrollAction.NoScroll]: keep scroll position exactly as-is.
     *
     * Scroll stability for the first-visible-item case is handled in the UI layer via
     * LazyListState.requestScrollToItem(0) which overrides LazyColumn’s key-tracking
     * during the layout pass.
     */
    fun updateChecklistItemChecked(itemId: String, isChecked: Boolean) {
        // 🔧 v1.10.0: Defensive No-Op Guard — skip wenn State identisch
        val currentItem = _checklistItems.value.find { it.id == itemId }
        if (currentItem?.isChecked == isChecked) return

        pushUndoSnapshot()  // 🆕 v1.10.0
        isDirty = true  // 🆕 v1.9.0: checking/unchecking is an edit
        hasUnsavedChecklistEdits = true  // 🛡️ v1.8.2 (IMPL_17)
        _checklistItems.update { items ->
            val updatedItems = items.map { item ->
                if (item.id == itemId) item.copy(isChecked = isChecked) else item
            }
            // 🆕 v1.8.0 (IMPL_017 + IMPL_020): Auto-Sort nur bei MANUAL und UNCHECKED_FIRST
            val currentSort = _lastChecklistSortOption.value
            if (currentSort == ChecklistSortOption.MANUAL ||
                currentSort == ChecklistSortOption.UNCHECKED_FIRST ||
                currentSort == ChecklistSortOption.CREATION_DATE ||
                currentSort == ChecklistSortOption.CREATION_DATE_DESC) {
                sortChecklistItems(updatedItems)
            } else {
                // Bei anderen Sortierungen (alphabetisch, checked first) nicht auto-sortieren
                updatedItems.mapIndexed { index, item -> item.copy(order = index) }
            }
        }
        // 🆕 v1.9.0 (F14): Emit scroll action — outside update{} to ensure state is committed first
        if (!isChecked) {
            _checklistScrollAction.tryEmit(ChecklistScrollAction.ScrollToTop)
        } else {
            _checklistScrollAction.tryEmit(ChecklistScrollAction.NoScroll)
        }
        scheduleAutosave()  // 🆕 v1.9.0: checked/unchecked counts as an edit
    }
    
    /**
     * 🆕 v1.8.1 (IMPL_15): Fügt ein neues Item nach dem angegebenen Item ein.
     *
     * Guard: Bei MANUAL/UNCHECKED_FIRST wird sichergestellt, dass das neue (unchecked)
     * Item nicht innerhalb der checked-Sektion eingefügt wird. Falls das Trigger-Item
     * checked ist, wird stattdessen vor dem ersten checked Item eingefügt.
     */
    fun addChecklistItemAfter(afterItemId: String): String {
        pushUndoSnapshot()  // 🆕 v1.10.0
        hasUnsavedChecklistEdits = true  // 🛡️ v1.8.2 (IMPL_17)
        val newItem = ChecklistItemState.createEmpty(0)
        _checklistItems.update { items ->
            val index = items.indexOfFirst { it.id == afterItemId }
            if (index >= 0) {
                val currentSort = _lastChecklistSortOption.value
                val hasSeparator = currentSort == ChecklistSortOption.MANUAL ||
                        currentSort == ChecklistSortOption.UNCHECKED_FIRST ||
                        currentSort == ChecklistSortOption.CREATION_DATE ||
                        currentSort == ChecklistSortOption.CREATION_DATE_DESC

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
                // 🐛 v1.9.0 (F12): Set originalOrder = index for ALL items after insert.
                // Prevents newly added items from jumping to position 0 after save/reopen.
                newList.mapIndexed { i, item -> item.copy(order = i, originalOrder = i) }
            } else {
                // 🐛 v1.9.0 (F12): Also cement originalOrder in the fallback branch
                val appended = items + newItem.copy(order = items.size, originalOrder = items.size)
                appended.mapIndexed { i, item -> item.copy(order = i, originalOrder = i) }
            }
        }
        isDirty = true  // 🆕 v1.10.0-P2: Adding an item is an edit
        // 🔧 v1.11.0: Kein Autosave bei leerem Item — verhindert Save-Indikator für nicht-gespeichertes Item.
        // Autosave wird erst durch updateChecklistItemText() getriggert, wenn User Text eingibt.
        // isDirty=true bleibt gesetzt, damit saveOnBack() bei Verlassen trotzdem greift.
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
        pushUndoSnapshot()  // 🆕 v1.10.0
        hasUnsavedChecklistEdits = true  // 🛡️ v1.8.2 (IMPL_17)
        val newItem = ChecklistItemState.createEmpty(0)
        _checklistItems.update { items ->
            val insertIndex = calculateInsertIndexForNewItem(items)
            val newList = items.toMutableList()
            newList.add(insertIndex, newItem)
            // 🐛 v1.9.0 (F12): Set originalOrder = index for ALL items after insert.
            // Prevents newly added items from jumping to position 0 after save/reopen.
            newList.mapIndexed { i, item -> item.copy(order = i, originalOrder = i) }
        }
        isDirty = true  // 🆕 v1.10.0-P2: Adding an item is an edit
        // 🔧 v1.11.0: Kein Autosave bei leerem Item — konsistent mit addChecklistItemAfter()
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
            ChecklistSortOption.UNCHECKED_FIRST,
            ChecklistSortOption.CREATION_DATE,
            ChecklistSortOption.CREATION_DATE_DESC -> {
                val firstCheckedIndex = items.indexOfFirst { it.isChecked }
                if (firstCheckedIndex >= 0) firstCheckedIndex else items.size
            }
            else -> items.size
        }
    }
    
    fun deleteChecklistItem(itemId: String) {
        pushUndoSnapshot()  // 🆕 v1.10.0
        // 🔧 v1.11.0: Prüfe ob gelöschtes Item leer war — kein Autosave nötig wenn nie gespeichert
        val deletedItem = _checklistItems.value.find { it.id == itemId }
        val wasEmpty = deletedItem?.text?.isBlank() != false
        isDirty = true  // 🆕 v1.10.0-P2: Deletion is an edit
        hasUnsavedChecklistEdits = true  // 🛡️ v1.8.2 (IMPL_17)
        _checklistItems.update { items ->
            val filtered = items.filter { it.id != itemId }
            // Ensure at least one item exists
            if (filtered.isEmpty()) {
                listOf(ChecklistItemState.createEmpty(0))
            } else {
                // 🐛 v1.9.0 (F12): Cement originalOrder after deletion to keep baseline consistent
                filtered.mapIndexed { index, item -> item.copy(order = index, originalOrder = index) }
            }
        }
        // 🔧 v1.11.0: Autosave nur wenn gelöschtes Item Text hatte (= auf Disk existierte)
        if (!wasEmpty) {
            scheduleAutosave()
        }
    }
    
    fun moveChecklistItem(fromIndex: Int, toIndex: Int) {
        pushUndoSnapshot()  // 🆕 v1.10.0
        isDirty = true  // 🆕 v1.10.0-P2: Moving an item is an edit
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
            // 🆕 v1.9.0 (F04): Update originalOrder on manual reorder to cement new position
            mutableList.mapIndexed { index, i -> i.copy(order = index, originalOrder = index) }
        }
        scheduleAutosave()  // 🆕 v1.10.0-P2: Trigger autosave on item move
    }
    
    /**
     * 🔀 v1.8.0 (IMPL_020): Sortiert Checklist-Items nach gewählter Option.
     * Einmalige Aktion (nicht persistiert) — User kann danach per Drag & Drop feinjustieren.
     */
    fun sortChecklistItems(option: ChecklistSortOption) {
        pushUndoSnapshot()  // 🆕 v1.10.0
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

                ChecklistSortOption.CREATION_DATE -> {
                    // 🆕 v1.11.0: Unchecked-Items nach Erstellungszeit sortiert (aufsteigend), dann Checked
                    val unchecked = items.filter { !it.isChecked }.sortedBy { it.createdAt }
                    val checked = items.filter { it.isChecked }.sortedBy { it.createdAt }
                    unchecked + checked
                }

                ChecklistSortOption.CREATION_DATE_DESC -> {
                    // 🆕 v1.11.0: Neueste zuerst (absteigend), dann Checked
                    val unchecked = items.filter { !it.isChecked }.sortedByDescending { it.createdAt }
                    val checked = items.filter { it.isChecked }.sortedByDescending { it.createdAt }
                    unchecked + checked
                }
            }
            
            // 🆕 v1.9.0 (F04): Explicit sort resets originalOrder baseline to new positions
            sorted.mapIndexed { index, item -> item.copy(order = index, originalOrder = index) }
        }
    }
    
    fun saveNote() {
        autosaveJob?.cancel()  // 🆕 v1.9.0: manual save supersedes pending autosave
        viewModelScope.launch {
            val saved = performSave()
            if (!saved) return@launch

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
     * 🆕 v1.10.0: Silent save for back-navigation.
     *
     * Speichert ungesicherte Änderungen lokal, OHNE:
     * - Sync zu triggern (User verlässt den Editor, kein expliziter „Speichern“-Intent)
     * - Navigation-Event zu emittieren (Activity handled finish() selbst)
     * - Toast/Indicator anzuzeigen
     *
     * @return true wenn gespeichert wurde (oder nichts zu speichern war), false bei Fehler
     */
    @Suppress("ReturnCount")
    fun saveOnBack(): Boolean {
        if (!autosaveEnabled) {
            Logger.d(TAG, "⏭️ saveOnBack: autosave disabled — skipping")
            return true
        }

        // v2.0.0: Flush latest content from Compose TextFieldState BEFORE the
        // isDirty check — snapshotFlow may not have propagated the last keystrokes yet
        if (currentNoteType == NoteType.TEXT) {
            contentProvider?.invoke()?.let { latestContent ->
                if (latestContent != _uiState.value.content) {
                    _uiState.update { it.copy(content = latestContent) }
                    isDirty = true
                }
            }
        }

        if (!isDirty) {
            Logger.d(TAG, "⏭️ saveOnBack: nothing dirty — skipping")
            return true
        }

        autosaveJob?.cancel()  // Pending autosave superseded

        return try {
            val state = _uiState.value
            val title = state.title.trim()

            when (currentNoteType) {
                NoteType.TEXT -> {
                    val content = state.content.trim()
                    if (title.isEmpty() && content.isEmpty()) return true

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
                    existingNote = note
                }
                NoteType.CHECKLIST -> {
                    hasUnsavedChecklistEdits = false
                    val validItems = _checklistItems.value
                        .filter { it.text.isNotBlank() }
                        .mapIndexed { index, item ->
                            ChecklistItem(
                                id = item.id,
                                text = item.text,
                                isChecked = item.isChecked,
                                order = index,
                                originalOrder = item.originalOrder,
                                createdAt = item.createdAt  // 🆕 v1.11.0
                            )
                        }
                    if (title.isEmpty() && validItems.isEmpty()) return true

                    // 🔧 v1.11.0: No-Change-Guard — kein Save wenn nur leere Items hinzugefügt
                    // wurden und sich der tatsächliche Inhalt nicht geändert hat.
                    // Verhindert fälschliches PENDING-Flag beim Verlassen der Notiz.
                    if (existingNote != null) {
                        val existingItems = existingNote!!.checklistItems.orEmpty()
                        val noContentChange = existingNote!!.title == title &&
                            existingItems.size == validItems.size &&
                            existingItems.zip(validItems).all { (old, new) ->
                                old.id == new.id &&
                                old.text == new.text &&
                                old.isChecked == new.isChecked &&
                                old.order == new.order
                            }
                        if (noContentChange) {
                            Logger.d(TAG, "⏭️ saveOnBack: no effective change after filtering empty items — skipping")
                            isDirty = false
                            return true
                        }
                    }

                    val note = existingNote?.copy(
                        title = title,
                        content = "",
                        noteType = NoteType.CHECKLIST,
                        checklistItems = validItems,
                        checklistSortOption = _lastChecklistSortOption.value.name,
                        updatedAt = System.currentTimeMillis(),
                        syncStatus = SyncStatus.PENDING
                    ) ?: Note(
                        title = title,
                        content = "",
                        noteType = NoteType.CHECKLIST,
                        checklistItems = validItems,
                        checklistSortOption = _lastChecklistSortOption.value.name,
                        deviceId = DeviceIdGenerator.getDeviceId(getApplication()),
                        syncStatus = SyncStatus.LOCAL_ONLY
                    )
                    storage.saveNote(note)
                    existingNote = note
                }
            }
            isDirty = false
            savedSnapshot = currentSnapshot()  // 🔧 v1.10.0: Update Referenz-Snapshot nach Save
            Logger.d(TAG, "💾 saveOnBack: saved successfully")
            true
        } catch (e: Exception) {
            Logger.e(TAG, "saveOnBack failed: ${e.message}")
            false
        }
    }

    /**
     * 🆕 v1.9.0: Internal save logic shared by manual save and autosave.
     * @param silent When true (autosave), empty-note check silently returns false without Toast.
     * Returns true if the note was saved, false if it was empty (nothing to save).
     */
    private suspend fun performSave(silent: Boolean = false): Boolean {
        val state = _uiState.value
        val title = state.title.trim()

        when (currentNoteType) {
            NoteType.TEXT -> {
                val content = state.content.trim()

                if (title.isEmpty() && content.isEmpty()) {
                    if (!silent) _events.emit(NoteEditorEvent.ShowToast(ToastMessage.NOTE_IS_EMPTY))
                    return false
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
                existingNote = note  // 🆕 v1.9.0: keep reference current so next save is an update
            }

            NoteType.CHECKLIST -> {

                // 🛡️ v1.8.2 (IMPL_17): Flag zurücksetzen — gespeicherter Stand ist jetzt aktuell
                hasUnsavedChecklistEdits = false
                // 🆕 v1.9.0 (F04): Preserve originalOrder for position restore
                val validItems = _checklistItems.value
                    .filter { it.text.isNotBlank() }
                    .mapIndexed { index, item ->
                        ChecklistItem(
                            id = item.id,
                            text = item.text,
                            isChecked = item.isChecked,
                            order = index,
                            originalOrder = item.originalOrder,
                            createdAt = item.createdAt  // 🆕 v1.11.0
                        )
                    }

                if (title.isEmpty() && validItems.isEmpty()) {
                    if (!silent) _events.emit(NoteEditorEvent.ShowToast(ToastMessage.NOTE_IS_EMPTY))
                    return false
                }

                // 🔧 v1.11.0: No-Change-Guard — kein erneutes Speichern wenn sich nichts geändert hat.
                // Verhindert falschen Autosave-Indikator wenn nur leere Items hinzugefügt wurden.
                if (silent && existingNote != null) {
                    val existingItems = existingNote!!.checklistItems.orEmpty()
                    val existingTitle = existingNote!!.title
                    val noContentChange = existingTitle == title &&
                        existingItems.size == validItems.size &&
                        existingItems.zip(validItems).all { (old, new) ->
                            old.id == new.id &&
                            old.text == new.text &&
                            old.isChecked == new.isChecked &&
                            old.order == new.order
                        }
                    if (noContentChange) {
                        Logger.d(TAG, "⏭️ Autosave: no effective change after filtering empty items — skipping")
                        return false
                    }
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
                existingNote = note  // 🆕 v1.9.0: keep reference current so next save is an update
            }
        }
        isDirty = false  // 🆕 v1.9.0: note is clean after any successful save
        savedSnapshot = currentSnapshot()  // 🔧 v1.10.0: Update Referenz-Snapshot nach Save
        return true
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🆕 v1.10.0: Undo/Redo
    // ═══════════════════════════════════════════════════════════════════════

    private fun currentSnapshot(): EditorSnapshot = EditorSnapshot(
        title = _uiState.value.title,
        content = _uiState.value.content,
        checklistItems = _checklistItems.value.toList()
    )

    private fun pushUndoSnapshot() {
        undoRedoManager.pushUndo(currentSnapshot())
    }

    /**
     * Debounced snapshot: captures state ONCE per burst of rapid edits (e.g. typing).
     * First call in a burst immediately records a snapshot; subsequent calls within
     * [Constants.UNDO_SNAPSHOT_DEBOUNCE_MS] are ignored. The window resets after the delay.
     */
    private fun pushUndoSnapshotDebounced() {
        if (snapshotDebounceJob == null || !snapshotDebounceJob!!.isActive) {
            pushUndoSnapshot()  // Capture state BEFORE this burst of edits
        }
        snapshotDebounceJob?.cancel()
        snapshotDebounceJob = viewModelScope.launch {
            kotlinx.coroutines.delay(Constants.UNDO_SNAPSHOT_DEBOUNCE_MS)
            snapshotDebounceJob = null
        }
    }

    fun undo() {
        val snapshot = undoRedoManager.undo(currentSnapshot()) ?: return
        applySnapshot(snapshot)
    }

    fun redo() {
        val snapshot = undoRedoManager.redo(currentSnapshot()) ?: return
        applySnapshot(snapshot)
    }

    private fun applySnapshot(snapshot: EditorSnapshot) {
        isRestoringSnapshot = true
        _uiState.update { it.copy(title = snapshot.title, content = snapshot.content) }
        _checklistItems.value = snapshot.checklistItems

        // 🔧 v1.10.0: isDirty-Reset wenn Undo/Redo den gespeicherten Zustand wiederherstellt
        val isBackToSaved = savedSnapshot?.let { saved ->
            snapshot.title == saved.title &&
                snapshot.content == saved.content &&
                snapshot.checklistItems == saved.checklistItems
        } ?: false

        if (isBackToSaved) {
            isDirty = false
            autosaveJob?.cancel()  // Kein Autosave nötig — Zustand = gespeicherter Zustand
        } else {
            isDirty = true
            scheduleAutosave()  // Undo/Redo auf Zwischen-Stand → Autosave nötig
        }

        viewModelScope.launch {
            if (currentNoteType == NoteType.TEXT) {
                _events.emit(NoteEditorEvent.RestoreContent(snapshot.content))
            }
            kotlinx.coroutines.delay(Constants.SNAPSHOT_RESTORE_GUARD_DELAY_MS)
            isRestoringSnapshot = false
        }
    }

    /**
     * 🆕 v1.9.0: Schedules a silent autosave after AUTOSAVE_DEBOUNCE_MS.
     * Called on every text-edit action. Cancels any previous pending autosave.
     * Does NOT trigger sync, widget update, or navigation — only local disk save.
     */
    private fun scheduleAutosave() {
        if (!autosaveEnabled) return
        if (!isDirty) return  // 🆕 v1.9.0: no changes since last save → skip
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(Constants.AUTOSAVE_DEBOUNCE_MS)
            val saved = performSave(silent = true)
            if (saved) {
                Logger.d(TAG, "💾 Autosave completed")
                _autosaveIndicatorVisible.value = true
                kotlinx.coroutines.delay(Constants.AUTOSAVE_INDICATOR_DURATION_MS)
                _autosaveIndicatorVisible.value = false
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // 🆕 v1.10.0-Papa: Calendar Export & Share
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * 🆕 v1.10.0-Papa: Opens the current note as a calendar event via ACTION_INSERT intent.
     * Does nothing if the note is completely empty.
     */
    fun openInCalendar() {
        viewModelScope.launch {
            val state = _uiState.value
            val content = NoteShareHelper.formatAsPlainText(
                noteType = state.noteType,
                textContent = state.content,
                checklistItems = _checklistItems.value
            )
            val title = state.title.trim()
            if (title.isBlank() && content.isBlank()) {
                _events.emit(NoteEditorEvent.ShowToast(ToastMessage.NOTE_IS_EMPTY))
                return@launch
            }
            val calTitle = title.ifBlank { content.take(CALENDAR_TITLE_FALLBACK_MAX_LENGTH) }
            _events.emit(NoteEditorEvent.OpenCalendar(title = calTitle, description = content))
        }
    }

    /**
     * 🆕 v1.10.0-Papa: Shares the current note as plain text via ACTION_SEND intent.
     */
    fun shareAsText() {
        viewModelScope.launch {
            val state = _uiState.value
            val content = NoteShareHelper.formatAsPlainText(
                noteType = state.noteType,
                textContent = state.content,
                checklistItems = _checklistItems.value
            )
            val title = state.title.trim()
            if (title.isBlank() && content.isBlank()) {
                _events.emit(NoteEditorEvent.ShowToast(ToastMessage.NOTE_IS_EMPTY))
                return@launch
            }
            val shareTitle = title.ifBlank { content.take(CALENDAR_TITLE_FALLBACK_MAX_LENGTH) }
            _events.emit(NoteEditorEvent.ShareAsText(title = shareTitle, text = content))
        }
    }

    /**
     * 🆕 v1.10.0-Papa: Triggers PDF generation and share.
     * Emits [NoteEditorEvent.ShareAsPdf]; the Activity handles generation via PdfExporter.
     */
    fun shareAsPdf() {
        viewModelScope.launch {
            val state = _uiState.value
            val content = NoteShareHelper.formatAsPlainText(
                noteType = state.noteType,
                textContent = state.content,
                checklistItems = _checklistItems.value
            )
            val title = state.title.trim()
            if (title.isBlank() && content.isBlank()) {
                _events.emit(NoteEditorEvent.ShowToast(ToastMessage.NOTE_IS_EMPTY))
                return@launch
            }
            val pdfTitle = title.ifBlank { content.take(CALENDAR_TITLE_FALLBACK_MAX_LENGTH) }
            _events.emit(NoteEditorEvent.ShareAsPdf(title = pdfTitle))
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
                // 🆕 v1.10.0-P2: Don't delete here — let ComposeMainActivity handle deletion
                // so MainViewModel can show the undo snackbar.
                _events.emit(NoteEditorEvent.NoteDeleteRequested(note.id, deleteOnServer))
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
            val rawFreshItems = freshNote.checklistItems?.sortedBy { it.order }.orEmpty()
            // 🆕 v1.9.0 (F04): Backward compat — old notes have all originalOrder == 0
            val isPreF04Fresh = rawFreshItems.all { it.originalOrder == 0 }
            // 🆕 v1.11.0: Backward compat — old notes have no createdAt (Gson default = 0)
            val isPreCreatedAtFresh = rawFreshItems.all { it.createdAt == 0L }
            val freshItems = rawFreshItems.mapIndexed { index, raw ->
                ChecklistItemState(
                    id = raw.id,
                    text = raw.text,
                    isChecked = raw.isChecked,
                    order = raw.order,
                    originalOrder = if (isPreF04Fresh) raw.order else raw.originalOrder,
                    createdAt = if (isPreCreatedAtFresh) index.toLong() else raw.createdAt
                )
            }
            if (freshItems.isEmpty()) return

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
    val order: Int = 0,
    val originalOrder: Int = order,  // 🆕 v1.9.0 (F04): Position restore on un-check
    val createdAt: Long = System.currentTimeMillis()  // 🆕 v1.11.0: Timestamp for sort-by-creation-date
) {
    companion object {
        // 🆕 v1.11.0: Monoton steigender Timestamp — verhindert gleiche createdAt-Werte
        // wenn mehrere Items in derselben Millisekunde erstellt werden (z.B. schnelles Enter-Drücken).
        // maxOf(now, last+1) garantiert: jedes neue Item hat einen strikt größeren Wert als das vorige.
        private var lastCreatedAt = 0L

        fun createEmpty(order: Int): ChecklistItemState {
            val now = System.currentTimeMillis()
            lastCreatedAt = maxOf(now, lastCreatedAt + 1)
            return ChecklistItemState(
                id = UUID.randomUUID().toString(),
                text = "",
                isChecked = false,
                order = order,
                originalOrder = order,
                createdAt = lastCreatedAt
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
    data class RestoreContent(val content: String) : NoteEditorEvent  // 🆕 v1.10.0: Undo/Redo
    /** 🆕 v1.10.0-P2: Signals Activity to set result and finish so MainViewModel shows undo snackbar. */
    data class NoteDeleteRequested(val noteId: String, val deleteFromServer: Boolean) : NoteEditorEvent
    // 🆕 v1.10.0-Papa: Calendar & Share events
    data class OpenCalendar(val title: String, val description: String) : NoteEditorEvent
    data class ShareAsText(val title: String, val text: String) : NoteEditorEvent
    data class ShareAsPdf(val title: String) : NoteEditorEvent
}
