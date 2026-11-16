package dev.dettmer.simplenotes.ui.editor

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.images.ImageCompressionMode
import dev.dettmer.simplenotes.images.ImageProcessor
import dev.dettmer.simplenotes.models.ChecklistItem
import dev.dettmer.simplenotes.models.ChecklistNesting
import dev.dettmer.simplenotes.models.ChecklistSortOption
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.AssetStore
import dev.dettmer.simplenotes.storage.FolderStore
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.SyncConflictResolver
import dev.dettmer.simplenotes.sync.SyncScheduler
import dev.dettmer.simplenotes.ui.editor.components.NoteStatsMode
import dev.dettmer.simplenotes.ui.editor.components.WordCounterVisibility
import dev.dettmer.simplenotes.utils.ActivityLog
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.DeviceIdGenerator
import dev.dettmer.simplenotes.utils.Logger
import dev.dettmer.simplenotes.utils.NoteShareHelper
import dev.dettmer.simplenotes.utils.toEnumOrDefault
import dev.dettmer.simplenotes.widget.WidgetUpdateHelper
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * ViewModel for NoteEditor Compose Screen
 * v1.5.0: Jetpack Compose NoteEditor Redesign
 *
 * Manages note editing state including title, content, and checklist items.
 */
// 🔧 v1.10.0: Detekt compliance — many features, deliberate design
// 🔧 v2.2.0: Feature 06 adds 4 new functions
@Suppress("LargeClass", "TooManyFunctions")
class NoteEditorViewModel(application: Application, private val savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "NoteEditorViewModel"
        const val ARG_NOTE_ID = "noteId"
        const val ARG_NOTE_TYPE = "noteType"
        const val ARG_FOLDER = "folderName"
        const val ARG_SHARED_TEXT = "sharedText" // 🆕 v2.2.0
        const val ARG_SHARED_SUBJECT = "sharedSubject" // 🆕 v2.2.0
        const val ARG_APPEND_TO_NOTE_ID = "appendToNoteId" // 🆕 v2.6.0
        private const val CALENDAR_TITLE_FALLBACK_MAX_LENGTH = 50 // 🆕 v1.10.0-Papa
    }

    private val storage = NotesStorage(application)
    private val folderStore = FolderStore(application)
    private val assetStore = AssetStore(application)
    private val imageProcessor = ImageProcessor(application)
    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    // 🆕 v2.16.0: die beiden Wege aus einem Sync-Konflikt heraus.
    private val conflictResolver = SyncConflictResolver(application, storage)
    private val initialFolderName: String? = savedStateHandle.get<String>(ARG_FOLDER)

    // ═══════════════════════════════════════════════════════════════════════
    // State
    // ═══════════════════════════════════════════════════════════════════════

    private val _uiState = MutableStateFlow(
        NoteEditorUiState(
            defaultStartInPreviewMode = prefs.getBoolean(
                Constants.KEY_DEFAULT_START_IN_PREVIEW_MODE,
                Constants.DEFAULT_START_IN_PREVIEW_MODE
            ),
            // 🆕 Issue #156
            checklistStartInReadMode = prefs.getBoolean(
                Constants.KEY_CHECKLIST_START_IN_READ_MODE,
                Constants.DEFAULT_CHECKLIST_START_IN_READ_MODE
            ),
            // 🆕 v2.11.0: Cursor-Start für neue Notizen
            newNoteFocusContent = prefs.getBoolean(
                Constants.KEY_NEW_NOTE_FOCUS_CONTENT,
                Constants.DEFAULT_NEW_NOTE_FOCUS_CONTENT
            ),
            // 🆕 (#126-Nachgang): Wortzähler-Sichtbarkeit aus den Einstellungen
            wordCounterVisibility = prefs
                .getString(Constants.KEY_WORD_COUNTER_VISIBILITY, Constants.DEFAULT_WORD_COUNTER_VISIBILITY)
                .toEnumOrDefault(WordCounterVisibility.valueOf(Constants.DEFAULT_WORD_COUNTER_VISIBILITY)),
            // #131 (E11)
            formattingBarEnabled = prefs.getBoolean(Constants.KEY_FORMATTING_BAR, Constants.DEFAULT_FORMATTING_BAR),
            titleFieldEnabled = prefs.getBoolean(Constants.KEY_TITLE_FIELD, Constants.DEFAULT_TITLE_FIELD)
        )
    )
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    // 🆕 (#126-Nachgang): Anzeigemodus der Statistik-Pille. Anders als der Settings-Schalter ein
    // StateFlow — der Zyklus muss im laufenden Editor sofort wirken.
    private val _noteStatsMode = MutableStateFlow(
        prefs.getString(Constants.KEY_NOTE_STATS_MODE, Constants.DEFAULT_NOTE_STATS_MODE)
            .toEnumOrDefault(NoteStatsMode.valueOf(Constants.DEFAULT_NOTE_STATS_MODE))
    )
    val noteStatsMode: StateFlow<NoteStatsMode> = _noteStatsMode.asStateFlow()

    /** Tippen auf die Pille: Wörter → Zeichen → versteckt → Wörter. Gilt notizübergreifend. */
    fun cycleNoteStatsMode() {
        val next = _noteStatsMode.value.next()
        prefs.edit { putString(Constants.KEY_NOTE_STATS_MODE, next.name) }
        _noteStatsMode.value = next
    }

    private val _checklistItems = MutableStateFlow<List<ChecklistItemState>>(emptyList())
    val checklistItems: StateFlow<List<ChecklistItemState>> = _checklistItems.asStateFlow()

    // 🌟 v1.6.0: Offline Mode State
    private val _isOfflineMode = MutableStateFlow(
        prefs.getBoolean(Constants.KEY_OFFLINE_MODE, Constants.DEFAULT_OFFLINE_MODE)
    )
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    // 🔀 v1.8.0 (IMPL_020): Letzte Checklist-Sortierung (Session-Scope)
    private val _lastChecklistSortOption = MutableStateFlow(ChecklistSortOption.MANUAL)
    val lastChecklistSortOption: StateFlow<ChecklistSortOption> = _lastChecklistSortOption.asStateFlow()

    // 🆕 Bild-Attachments: Verarbeitung läuft auf IO — Toolbar zeigt währenddessen einen Spinner
    private val _isAttachingImage = MutableStateFlow(false)
    val isAttachingImage: StateFlow<Boolean> = _isAttachingImage.asStateFlow()

    // ═══════════════════════════════════════════════════════════════════════
    // Events
    // ═══════════════════════════════════════════════════════════════════════

    private val _events = MutableSharedFlow<NoteEditorEvent>()
    val events: SharedFlow<NoteEditorEvent> = _events.asSharedFlow()

    private val _showSnackbar = MutableSharedFlow<String>()
    val showSnackbar: SharedFlow<String> = _showSnackbar.asSharedFlow()

    fun emitSnackbar(message: String) {
        viewModelScope.launch {
            _showSnackbar.emit(message)
        }
    }

    /**
     * 🆕 Bild-Attachments: Verarbeitet ein per Photo-Picker gewähltes Bild (Kompression je
     * nach Setting, EXIF-Rotation) und speichert es content-adressiert im AssetStore.
     * Gibt den Asset-Dateinamen zurück, oder null bei Fehler (Snackbar wird intern emittiert).
     * Läuft auf IO; [isAttachingImage] steuert den Toolbar-Spinner in der Zwischenzeit.
     */
    // 🆕 Bild-Attachments: Standardgröße für neu eingefügte Bilder (S/M/L/XL-Einstellung).
    // Wie scrollTopOnUncheck beim VM-Bau gelesen — der Editor-VM wird pro geöffneter Notiz neu erstellt.
    val defaultImageSizePercent = prefs.getInt(
        Constants.KEY_DEFAULT_IMAGE_SIZE_PERCENT,
        Constants.DEFAULT_DEFAULT_IMAGE_SIZE_PERCENT
    )

    suspend fun attachImage(uri: Uri): String? {
        _isAttachingImage.value = true
        return try {
            val mode = prefs.getString(Constants.KEY_IMAGE_COMPRESSION_MODE, Constants.DEFAULT_IMAGE_COMPRESSION_MODE)
                .toEnumOrDefault(ImageCompressionMode.valueOf(Constants.DEFAULT_IMAGE_COMPRESSION_MODE))
            val processed = imageProcessor.process(uri, mode)
            assetStore.saveAsset(processed.bytes, processed.ext)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to attach image", e)
            emitSnackbar(getApplication<Application>().getString(R.string.image_attach_failed))
            null
        } finally {
            _isAttachingImage.value = false
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🆕 v1.9.0 (F14): Explicit scroll actions for check/un-check
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Sealed class for scroll actions after checking/un-checking a checklist item.
     * - [ScrollToItem]: Scroll the LazyColumn to where the un-checked item landed.
     * - [NoScroll]: Explicitly do nothing — keeps scroll position stable (used on check).
     */
    sealed class ChecklistScrollAction {
        /** Un-check: [index] is the item's position after the sort (visual = data index, the separator follows the unchecked items). */
        data class ScrollToItem(val index: Int) : ChecklistScrollAction()

        /** Check: do not scroll — keep viewport exactly where it is. */
        object NoScroll : ChecklistScrollAction()
    }

    private val _checklistScrollAction = MutableSharedFlow<ChecklistScrollAction>(extraBufferCapacity = 1)
    val checklistScrollAction: SharedFlow<ChecklistScrollAction> = _checklistScrollAction.asSharedFlow()

    // 🆕 Issue #112: Wenn aus, bleibt der Viewport beim Un-Check stehen (→ NoScroll).
    // Wie autosaveEnabled beim VM-Bau gelesen — der Editor-VM wird pro geöffneter Notiz neu erstellt.
    // 🔧 v2.13.0: Auch von der UI gelesen — der Collapse-Pfad committet dann ohne Settle-Delay
    // und ohne Expand am Ziel, damit der Scroll zum Ziel früh und über ruhigem Layout läuft.
    val scrollTopOnUncheck = prefs.getBoolean(
        Constants.KEY_CHECKLIST_SCROLL_TOP_ON_UNCHECK,
        Constants.DEFAULT_CHECKLIST_SCROLL_TOP_ON_UNCHECK
    )

    // Internal state
    // v2.3.0 (REF-012): backed by MutableStateFlow for thread-safe access;
    // delegated property syntax preserves all call sites unchanged.
    private val _existingNote = MutableStateFlow<Note?>(null)
    private var existingNote: Note?
        get() = _existingNote.value
        set(value) {
            _existingNote.value = value
        }
    private var currentNoteType: NoteType = NoteType.TEXT
    private var isConverting = false

    // 🆕 Issue #114: Notiz wurde ungespeichert verworfen (deleteNote() ohne existingNote) —
    // verhindert, dass saveOnBack()/scheduleAutosave() sie danach doch noch auf Platte schreiben.
    private var isDiscarded = false

    // v2.0.0: Callback to read the latest content directly from Compose TextFieldState.
    // Avoids snapshotFlow race condition when the user presses back before the next frame.
    var contentProvider: (() -> String)? = null

    // 🛡️ v1.8.2 (IMPL_17): Trackt ob User ungespeicherte Checklist-Edits hat.
    // Wenn true, überspringt reloadFromStorage() das Neuladen, damit onResume()
    // (Notification Shade, App-Switcher etc.) keine User-Änderungen überschreibt.
    // v2.3.0 (REF-012): MutableStateFlow-backed for thread-safety.
    private val _hasUnsavedChecklistEdits = MutableStateFlow(false)
    private var hasUnsavedChecklistEdits: Boolean
        get() = _hasUnsavedChecklistEdits.value
        set(value) {
            _hasUnsavedChecklistEdits.value = value
        }

    // 🆕 v1.9.0: Autosave with debounce
    private val autosaveEnabled = prefs.getBoolean(
        Constants.KEY_AUTOSAVE_ENABLED,
        Constants.DEFAULT_AUTOSAVE_ENABLED
    )
    private var autosaveJob: kotlinx.coroutines.Job? = null

    // v2.3.0 (REF-012): MutableStateFlow-backed for thread-safety.
    private val _isDirty = MutableStateFlow(false)
    private var isDirty: Boolean
        get() = _isDirty.value
        set(value) {
            _isDirty.value = value
        } // 🆕 v1.9.0: only autosave when content has actually changed

    // UI-Beta (E5): Speichern-Knopf zeigt Haken (gespeichert) oder Diskette (offene Änderungen).
    val hasUnsavedChanges: StateFlow<Boolean> = _isDirty.asStateFlow()

    // UI-Beta (E4): „Edited …“ unter dem Titel; Zeitpunkt der zuletzt gespeicherten Fassung.
    val lastEditedAt: StateFlow<Long?> = _existingNote
        .map { it?.editedAt }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // 🆕 v1.9.0: Autosave indicator — briefly visible after a successful autosave
    private val _autosaveIndicatorVisible = MutableStateFlow(false)
    val autosaveIndicatorVisible: StateFlow<Boolean> = _autosaveIndicatorVisible.asStateFlow()

    // 🆕 v1.10.0: Undo/Redo
    private val undoRedoManager = UndoRedoManager()
    val canUndo: StateFlow<Boolean> = undoRedoManager.canUndo
    val canRedo: StateFlow<Boolean> = undoRedoManager.canRedo
    private var snapshotDebounceJob: kotlinx.coroutines.Job? = null

    // v2.3.0 (REF-012): MutableStateFlow-backed for thread-safety.
    private val _isRestoringSnapshot = MutableStateFlow(false)
    private var isRestoringSnapshot: Boolean
        get() = _isRestoringSnapshot.value
        set(value) {
            _isRestoringSnapshot.value = value
        }

    // 🔧 v1.10.0: Snapshot des zuletzt gespeicherten Zustands.
    // Wird beim Öffnen und nach jedem erfolgreichen Save gesetzt.
    // Ermöglicht isDirty-Reset wenn Undo den Originalzustand wiederherstellt.
    private var savedSnapshot: EditorSnapshot? = null

    init {
        loadNote()
    }

    private fun loadNote() {
        val appendToNoteId = savedStateHandle.get<String>(ARG_APPEND_TO_NOTE_ID)
        val noteId = savedStateHandle.get<String>(ARG_NOTE_ID)
        val noteTypeString = savedStateHandle.get<String>(ARG_NOTE_TYPE) ?: NoteType.TEXT.name

        when {
            appendToNoteId != null -> {
                _uiState.update { it.copy(isLoading = true, isNewNote = false) }
                viewModelScope.launch { loadAndAppendSharedText(appendToNoteId) }
            }
            noteId != null -> {
                // Mark loading and correct isNewNote to prevent flash of wrong editor
                // type and wrong LaunchedEffect triggers during async load
                _uiState.update { it.copy(isLoading = true, isNewNote = false) }
                viewModelScope.launch { loadExistingNote(noteId) }
            }
            else -> initNewNote(noteTypeString)
        }
    }

    private suspend fun loadExistingNote(noteId: String) {
        existingNote = withContext(Dispatchers.IO) { storage.loadNote(noteId) }
        existingNote?.let { showLoadedNote(it) } ?: run {
            // Note not found — clear loading state
            _uiState.update { it.copy(isLoading = false) }
        }
        undoRedoManager.clear() // 🆕 v1.10.0: No cross-note undo
        savedSnapshot = currentSnapshot() // 🔧 v1.10.0: Referenz-Snapshot für isDirty-Reset
    }

    private suspend fun loadAndAppendSharedText(noteId: String) {
        val note = withContext(Dispatchers.IO) { storage.loadNote(noteId) }
        if (note == null) {
            initNewNote(NoteType.TEXT.name)
            return
        }

        existingNote = note
        showLoadedNote(note)

        val sharedText = savedStateHandle.get<String>(ARG_SHARED_TEXT).orEmpty().trim()
        val sharedSubject = savedStateHandle.get<String>(ARG_SHARED_SUBJECT).orEmpty().trim()
        val appendText = listOf(sharedSubject, sharedText).filter { it.isNotBlank() }.joinToString("\n")

        if (appendText.isNotBlank()) {
            when (note.noteType) {
                NoteType.TEXT -> {
                    val separator = if (note.content.isNotBlank()) "\n\n" else ""
                    _uiState.update { it.copy(content = it.content + separator + appendText) }
                }
                NoteType.CHECKLIST -> {
                    val parsedItems = parseSharedTextAsChecklist(appendText)
                    val existing = _checklistItems.value
                    val maxOrder = existing.maxOfOrNull { it.order } ?: -1
                    val reindexed = parsedItems.mapIndexed { i, item ->
                        item.copy(order = maxOrder + 1 + i, originalOrder = maxOrder + 1 + i)
                    }
                    _checklistItems.value = existing + reindexed
                }
            }
            isDirty = true
        }

        undoRedoManager.clear()
        savedSnapshot = currentSnapshot()
    }

    /** Zeigt eine gespeicherte Notiz im Editor: Kopfzustand, bei Checklisten auch die Einträge. */
    private fun showLoadedNote(note: Note) {
        currentNoteType = note.noteType
        _uiState.update { state ->
            state.copy(
                title = note.title,
                content = note.content,
                noteType = note.noteType,
                isNewNote = false,
                isLoading = false,
                color = note.color, // 🆕 v2.5.0
                isArchived = note.isArchived, // 🆕 v2.11.0 (Archive)
                hasConflict = note.syncStatus == SyncStatus.CONFLICT, // 🆕 v2.16.0
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

    private fun loadChecklistData(note: Note) {
        // 🆕 v1.8.1 (IMPL_03): Gespeicherte Sortierung laden
        note.checklistSortOption?.let { sortName ->
            _lastChecklistSortOption.value = parseSortOption(sortName)
        }

        // 🆕 v1.8.0 (IMPL_017): Sortierung sicherstellen (falls alte Daten unsortiert sind)
        _checklistItems.value = sortChecklistItems(note.checklistItems.orEmpty().toEditorStates())
    }

    private fun initNewNote(noteTypeString: String) {
        currentNoteType = noteTypeString.toEnumOrDefault(NoteType.TEXT)

        // 🆕 v2.11.0: Standard-Farbe für neue Notizen (Einstellung; null = keine)
        val defaultColor = prefs.getString(Constants.KEY_DEFAULT_NOTE_COLOR, Constants.DEFAULT_NOTE_COLOR)
            ?.takeIf { it.isNotEmpty() }

        _uiState.update { state ->
            state.copy(
                noteType = currentNoteType,
                isNewNote = true,
                toolbarTitle = if (currentNoteType == NoteType.CHECKLIST) {
                    ToolbarTitle.NEW_CHECKLIST
                } else {
                    ToolbarTitle.NEW_NOTE
                },
                color = defaultColor // 🆕 v2.11.0
            )
        }

        // Add first empty item for new checklists
        if (currentNoteType == NoteType.CHECKLIST) {
            _checklistItems.value = listOf(ChecklistItemState.createEmpty(0))
        }

        // 🆕 v2.2.0: Share Intent — vorausgefüllter Content aus anderer App
        val sharedText = savedStateHandle.get<String>(ARG_SHARED_TEXT)
        val sharedSubject = savedStateHandle.get<String>(ARG_SHARED_SUBJECT)
        if (!sharedText.isNullOrBlank() || !sharedSubject.isNullOrBlank()) {
            val rawSubject = sharedSubject?.trim().orEmpty()
            val rawText = sharedText?.trim().orEmpty()
            // v2.2.0-fix: extract title from first line when EXTRA_SUBJECT is absent
            val (titleText, bodyText) = if (rawSubject.isNotBlank()) {
                rawSubject to rawText
            } else {
                extractSharedTitle(rawText, currentNoteType == NoteType.CHECKLIST)
            }
            if (currentNoteType == NoteType.CHECKLIST) {
                if (bodyText.isNotBlank()) {
                    _checklistItems.value = parseSharedTextAsChecklist(bodyText)
                }
                _uiState.update { state -> state.copy(title = titleText) }
            } else {
                // TEXT-Notiz: Title + Content setzen
                _uiState.update { state ->
                    state.copy(title = titleText, content = bodyText)
                }
            }
            isDirty = true // Share-Content zählt als ungespeicherte Änderung
        }

        undoRedoManager.clear() // 🆕 v1.10.0: No cross-note undo
        savedSnapshot = currentSnapshot() // 🔧 v1.10.0: Referenz-Snapshot für isDirty-Reset
    }

    /**
     * v2.2.0-fix: Extracts title and body from shared text when EXTRA_SUBJECT is absent.
     *
     * Text notes: first line = title, rest = content (always, if 2+ lines).
     * Checklists: first line = title only when it doesn't look like a list item AND
     *   is followed by a blank line (“heap-newline” heading pattern). Otherwise all
     *   lines are treated as items.
     */
    private fun extractSharedTitle(text: String, isChecklist: Boolean): Pair<String, String> {
        if (text.isBlank()) return "" to text
        val lines = text.lines()
        val firstLine = lines.first().trim()
        if (lines.size < 2 || firstLine.isBlank()) return "" to text

        return if (isChecklist) {
            // Don't treat a list-item line as a title
            val listItemRegex = Regex("""^[-*•✓☑✔]\s+.*""")
            if (listItemRegex.matches(firstLine)) return "" to text
            // Extract as title when separated by a blank line, or when next line is a list item
            val secondLine = lines.getOrNull(1)?.trim() ?: ""
            val bodyStart = when {
                secondLine.isBlank() -> 2
                listItemRegex.matches(secondLine) -> 1
                else -> null
            }
            if (bodyStart != null) {
                firstLine to lines.drop(bodyStart).joinToString("\n").trim()
            } else {
                "" to text
            }
        } else {
            // Text note: first line = title, rest = content
            firstLine to lines.drop(1).joinToString("\n").trimStart('\n').trim()
        }
    }

    private fun parseSharedTextAsChecklist(text: String): List<ChecklistItemState> {
        val lines = text.trim().lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return _checklistItems.value
        return lines.mapIndexed { index, line -> normalizeLineToChecklistItem(line, index) }
    }

    private fun parseContentAsChecklistItems(content: String): List<ChecklistItemState> {
        val lines = content.trim().lines().filter { it.isNotBlank() }
        return lines.mapIndexed { index, line -> normalizeLineToChecklistItem(line, index) }
    }

    private fun parseSortOption(sortName: String): ChecklistSortOption =
        sortName.toEnumOrDefault(ChecklistSortOption.MANUAL)

    // ═══════════════════════════════════════════════════════════════════════
    // Actions
    // ═══════════════════════════════════════════════════════════════════════

    fun updateTitle(title: String) {
        if (title == _uiState.value.title) return // 🆕 v1.9.0: no-op guard — hydration, not a user edit
        if (isRestoringSnapshot) return // 🆕 v1.10.0: Suppress during undo/redo restore
        pushUndoSnapshotDebounced() // 🆕 v1.10.0: Capture state before this edit
        isDirty = true
        _uiState.update { it.copy(title = title) }
        scheduleAutosave() // 🆕 v1.9.0
    }

    fun updateContent(content: String) {
        if (content == _uiState.value.content) return // 🆕 v1.9.0: no-op guard — hydration, not a user edit
        if (isRestoringSnapshot) return // 🆕 v1.10.0: Suppress during undo/redo restore
        pushUndoSnapshotDebounced() // 🆕 v1.10.0: Capture state before this edit
        isDirty = true
        _uiState.update { it.copy(content = content) }
        scheduleAutosave() // 🆕 v1.9.0
    }

    fun convertNoteType() {
        if (isConverting) return
        isConverting = true
        autosaveJob?.cancel()
        hasUnsavedChecklistEdits = false
        pushUndoSnapshot()
        viewModelScope.launch {
            try {
                val current = _uiState.value
                val isNew = current.isNewNote
                when (current.noteType) {
                    NoteType.TEXT -> {
                        // Flush latest TextFieldState content — snapshotFlow may lag by one frame
                        val latestContent = contentProvider?.invoke()?.also { latest ->
                            if (latest != _uiState.value.content) {
                                _uiState.update { it.copy(content = latest) }
                            }
                        } ?: _uiState.value.content
                        val rawItems = parseContentAsChecklistItems(latestContent)
                        val items = if (rawItems.isEmpty()) listOf(ChecklistItemState.createEmpty(0)) else rawItems
                        _checklistItems.value = sortChecklistItems(items)
                        currentNoteType = NoteType.CHECKLIST
                        val newTitle = if (isNew) ToolbarTitle.NEW_CHECKLIST else ToolbarTitle.EDIT_CHECKLIST
                        _uiState.update { it.copy(noteType = NoteType.CHECKLIST, content = "", toolbarTitle = newTitle) }
                    }
                    NoteType.CHECKLIST -> {
                        // Preserve GFM checkbox syntax so round-trip TEXT→CHECKLIST restores states
                        // Unterpunkte: leere Oberpunkte geben ihre Unterpunkte frei statt sie der Gruppe davor zu vererben
                        val text = _checklistItems.value
                            .nested { ChecklistNesting.withoutBlank(it) }
                            .sortedBy { it.order }
                            .joinToString("\n") { item ->
                                // Unterpunkte: zwei Leerzeichen Einzug je Ebene, normalizeLineToChecklistItem liest sie zurück
                                val box = if (item.isChecked) "- [x]" else "- [ ]"
                                "${ChecklistNesting.TEXT_INDENT.repeat(item.indentationLevel)}$box ${item.text}"
                            }
                        _checklistItems.value = emptyList()
                        currentNoteType = NoteType.TEXT
                        val newTitle = if (isNew) ToolbarTitle.NEW_NOTE else ToolbarTitle.EDIT_NOTE
                        _uiState.update { it.copy(noteType = NoteType.TEXT, content = text, toolbarTitle = newTitle) }
                        _events.emit(NoteEditorEvent.RestoreContent(text))
                        _events.emit(NoteEditorEvent.ActivatePreviewMode)
                        _events.emit(NoteEditorEvent.RequestContentFocus)
                    }
                }
                isDirty = true
                performSave(silent = true)
            } finally {
                isConverting = false
            }
        }
    }

    fun updateChecklistItemText(itemId: String, newText: String) {
        // 🔧 v1.10.0: No-Op Guard — verhindert false positive isDirty bei Cursor-Repositionierung.
        // BasicTextField feuert onValueChange auch bei reiner Selection-Änderung (gleicher Text).
        val currentText = _checklistItems.value.find { it.id == itemId }?.text
        if (newText == currentText) return

        pushUndoSnapshotDebounced() // 🆕 v1.10.0
        isDirty = true
        hasUnsavedChecklistEdits = true // 🛡️ v1.8.2 (IMPL_17)
        _checklistItems.update { items ->
            items.map { item ->
                if (item.id == itemId) item.copy(text = newText) else item
            }
        }
        scheduleAutosave() // 🆕 v1.9.0
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
    private fun sortChecklistItems(items: List<ChecklistItemState>): List<ChecklistItemState> =
        sortChecklistStates(items, _lastChecklistSortOption.value)

    /**
     * 🆕 v1.9.0 (F14): Toggles the checked state of a checklist item and emits a scroll action.
     * - Un-check → emits [ChecklistScrollAction.ScrollToItem] with the item's new index.
     * - Check → emits [ChecklistScrollAction.NoScroll]: keep scroll position exactly as-is.
     *
     * 🆕 Issue #112: Ist [Constants.KEY_CHECKLIST_SCROLL_TOP_ON_UNCHECK] aus, emittiert auch
     * der Un-Check [ChecklistScrollAction.NoScroll] — die Ansicht bleibt stehen.
     *
     * Scroll stability for the first-visible-item case is handled in the UI layer via
     * LazyListState.requestScrollToItem(0) which overrides LazyColumn’s key-tracking
     * during the layout pass.
     */
    fun updateChecklistItemChecked(itemId: String, isChecked: Boolean) {
        // 🔧 v1.10.0: Defensive No-Op Guard — skip wenn State identisch
        val currentItem = _checklistItems.value.find { it.id == itemId }
        if (currentItem?.isChecked == isChecked) return

        pushUndoSnapshot() // 🆕 v1.10.0
        isDirty = true // 🆕 v1.9.0: checking/unchecking is an edit
        hasUnsavedChecklistEdits = true // 🛡️ v1.8.2 (IMPL_17)

        // 🆕 v2.5.0: isChecked-Update UND Sort laufen im selben State-Snapshot.
        // Vorher (uncommitted): Sort wurde 350 ms verzögert, damit die Checkbox-Animation
        // „Zeit zum Spielen" hatte. Folge: separatorVisualIndex (aus uncheckedCount abgeleitet)
        // sprang sofort, Items folgten erst 350 ms später → ein Nachbar-Item rutschte
        // visuell über/unter den Separator, obwohl er nicht angefasst wurde.
        // Lösung: Sort sofort. Die UI-Animation (Flash + Scale-Pop in ChecklistItemRow,
        // Placement-Spring via LazyColumn.animateItem in NoteEditorScreen) läuft parallel
        // und überlebt die Reorder, weil der Item-Key (item.id) stabil bleibt.
        _checklistItems.update { items ->
            // Unterpunkte: Gruppenregeln (Oberpunkt abhaken hakt Unterpunkte mit ab usw.)
            val updatedItems = items.nested { ChecklistNesting.toggle(it, itemId, isChecked) }
            // 🆕 v1.8.0 (IMPL_017 + IMPL_020): Auto-Sort nur bei MANUAL und UNCHECKED_FIRST
            if (separatorShown()) {
                sortChecklistItems(updatedItems)
            } else {
                // Bei anderen Sortierungen (alphabetisch, checked first) nicht auto-sortieren
                updatedItems.mapIndexed { index, item -> item.copy(order = index) }
            }
        }
        // 🆕 v1.9.0 (F14): Emit scroll action — outside update{} to ensure state is committed first
        if (!isChecked && scrollTopOnUncheck) {
            // Unterpunkte: Ziel ist der Oberpunkt der wandernden Gruppe, sonst stünde er knapp über dem Bild
            val items = _checklistItems.value
            val index = items.indexOfFirst { it.id == itemId }
            val target = if (index >= 0) ChecklistNesting.groupStart(items, index) else index
            _checklistScrollAction.tryEmit(ChecklistScrollAction.ScrollToItem(target))
        } else {
            _checklistScrollAction.tryEmit(ChecklistScrollAction.NoScroll)
        }
        scheduleAutosave() // 🆕 v1.9.0: checked/unchecked counts as an edit
    }

    /**
     * 🆕 v1.8.1 (IMPL_15): Fügt ein neues Item nach dem angegebenen Item ein.
     *
     * Guard: Bei MANUAL/UNCHECKED_FIRST wird sichergestellt, dass das neue (unchecked)
     * Item nicht innerhalb der checked-Sektion eingefügt wird. Falls das Trigger-Item
     * checked ist, wird stattdessen vor dem ersten checked Item eingefügt.
     */
    fun addChecklistItemAfter(afterItemId: String): String {
        pushUndoSnapshot() // 🆕 v1.10.0
        hasUnsavedChecklistEdits = true // 🛡️ v1.8.2 (IMPL_17)
        val newItem = ChecklistItemState.createEmpty(0)
        _checklistItems.update { items ->
            val index = items.indexOfFirst { it.id == afterItemId }
            if (index >= 0) {
                // 🆕 v1.8.1 (IMPL_15): Wenn das Trigger-Item im erledigten Abschnitt steht und ein
                // Separator existiert, darf das neue unchecked Item nicht in die checked-Sektion.
                // → Stattdessen vor der ersten abgehakten Gruppe einfügen.
                // Unterpunkte: die neue Zeile erbt die Ebene (Enter auf einem Oberpunkt mit
                // Unterpunkten erzeugt einen ersten Unterpunkt).
                val (effectiveIndex, level) = placeNewOpenRow(items, index, ChecklistNesting.levelForNewRowAfter(items, index))

                val newList = items.toMutableList()
                newList.add(effectiveIndex, newItem.copy(indentationLevel = level))
                // 🐛 v1.9.0 (F12): Set originalOrder = index for ALL items after insert.
                // Prevents newly added items from jumping to position 0 after save/reopen.
                newList.mapIndexed { i, item -> item.copy(order = i, originalOrder = i) }
            } else {
                // 🐛 v1.9.0 (F12): Also cement originalOrder in the fallback branch
                val appended = items + newItem.copy(order = items.size, originalOrder = items.size)
                appended.mapIndexed { i, item -> item.copy(order = i, originalOrder = i) }
            }
        }
        isDirty = true // 🆕 v1.10.0-P2: Adding an item is an edit
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
        pushUndoSnapshot() // 🆕 v1.10.0
        hasUnsavedChecklistEdits = true // 🛡️ v1.8.2 (IMPL_17)
        val newItem = ChecklistItemState.createEmpty(0)
        _checklistItems.update { items ->
            val insertIndex = calculateInsertIndexForNewItem(items)
            val newList = items.toMutableList()
            newList.add(insertIndex, newItem)
            // 🐛 v1.9.0 (F12): Set originalOrder = index for ALL items after insert.
            // Prevents newly added items from jumping to position 0 after save/reopen.
            newList.mapIndexed { i, item -> item.copy(order = i, originalOrder = i) }
        }
        isDirty = true // 🆕 v1.10.0-P2: Adding an item is an edit
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
    private fun calculateInsertIndexForNewItem(items: List<ChecklistItemState>): Int =
        if (separatorShown()) ChecklistNesting.openRowCount(items) else items.size

    /** Sortierungen mit Trenner zwischen offenen und erledigten Einträgen (Auto-Sort beim Abhaken). */
    private fun separatorShown(): Boolean = when (_lastChecklistSortOption.value) {
        ChecklistSortOption.MANUAL,
        ChecklistSortOption.UNCHECKED_FIRST,
        ChecklistSortOption.CREATION_DATE,
        ChecklistSortOption.CREATION_DATE_DESC -> true
        else -> false
    }

    /**
     * Position und Ebene einer neuen offenen Zeile hinter [index]. Steht [index] im erledigten
     * Abschnitt, darf die Zeile nicht hinein: mit Trenner vor die erste abgehakte Gruppe, ohne
     * Trenner hinter die Gruppe (ein offener Unterpunkt unter abgehaktem Oberpunkt bräche die Invariante).
     */
    private fun placeNewOpenRow(items: List<ChecklistItemState>, index: Int, level: Int): Pair<Int, Int> = when {
        !ChecklistNesting.inCheckedSection(items, index) -> index + 1 to level
        separatorShown() -> ChecklistNesting.openRowCount(items) to 0
        else -> ChecklistNesting.groupRange(items, index).last + 1 to 0
    }

    /** Unterpunkte: Zeile einrücken (wird Unterpunkt der Gruppe darüber). */
    fun indentChecklistItem(itemId: String) = changeNesting { ChecklistNesting.indent(it, itemId) }

    /** Unterpunkte: Zeile ausrücken (wird Oberpunkt hinter ihrer bisherigen Gruppe). */
    fun outdentChecklistItem(itemId: String) = changeNesting { ChecklistNesting.outdent(it, itemId) }

    private fun changeNesting(op: (List<ChecklistItem>) -> List<ChecklistItem>) {
        val before = _checklistItems.value
        val changed = before.nested(op).mapIndexed { i, item -> item.copy(order = i, originalOrder = i) }
        if (changed == before) return
        pushUndoSnapshot()
        isDirty = true
        hasUnsavedChecklistEdits = true
        // Ein ausgerückter abgehakter Unterpunkt ist jetzt ein abgehakter Oberpunkt → unter den Trenner
        _checklistItems.value = if (separatorShown()) sortChecklistItems(changed) else changed
        scheduleAutosave()
    }

    // Unterpunkte: laufendes Ziehen (gezogene Zeile + ihre Unterpunkte beim Start), sonst null.
    private var dragDraggedId: String? = null
    private var dragFormerChildIds: List<String> = emptyList()

    /**
     * Unterpunkte: Drag-Start, gerufen vor dem ersten Tausch. Merkt sich die Unterpunkte eines gezogenen
     * Oberpunkts, solange die Liste noch unverändert ist, und legt genau einen Undo-Schritt an: Die
     * Zwischenstände eines Ziehens (Unterpunkte an fremdem Oberpunkt) sind kein Ergebnis zum Zurückholen.
     */
    fun startChecklistDrag(draggedId: String) {
        val items = _checklistItems.value
        val index = items.indexOfFirst { it.id == draggedId }
        if (index < 0) return
        pushUndoSnapshot()
        dragDraggedId = draggedId
        dragFormerChildIds = if (items[index].indentationLevel == 0) {
            ChecklistNesting.groupRange(items, index).drop(1).map { items[it].id }
        } else {
            emptyList()
        }
    }

    /**
     * Unterpunkte: Drag-Ende. Die Unterpunkte eines gezogenen Oberpunkts bleiben beim Ziehen stehen
     * (die DnD-Logik tauscht genau eine Zeile) und rücken jetzt hinter ihn, siehe [ChecklistNesting.finishDrag].
     */
    fun onChecklistDragFinished() {
        val draggedId = dragDraggedId ?: return
        val formerChildIds = dragFormerChildIds
        dragDraggedId = null
        dragFormerChildIds = emptyList()
        val before = _checklistItems.value
        val after = before.nested { ChecklistNesting.finishDrag(it, draggedId, formerChildIds) }
            .mapIndexed { i, item -> item.copy(order = i, originalOrder = i) }
        if (after != before) {
            isDirty = true
            hasUnsavedChecklistEdits = true
            _checklistItems.value = after
        }
        scheduleAutosave()
    }

    fun deleteChecklistItem(itemId: String) {
        pushUndoSnapshot() // 🆕 v1.10.0
        // 🔧 v1.11.0: Prüfe ob gelöschtes Item leer war — kein Autosave nötig wenn nie gespeichert
        val deletedItem = _checklistItems.value.find { it.id == itemId }
        val wasEmpty = deletedItem?.text?.isBlank() != false
        isDirty = true // 🆕 v1.10.0-P2: Deletion is an edit
        hasUnsavedChecklistEdits = true // 🛡️ v1.8.2 (IMPL_17)
        _checklistItems.update { items ->
            // Unterpunkte eines gelöschten Oberpunkts werden Oberpunkte
            val filtered = items.nested { ChecklistNesting.delete(it, itemId) }
            // Ensure at least one item exists
            if (filtered.isEmpty()) {
                listOf(ChecklistItemState.createEmpty(0))
            } else {
                // 🐛 v1.9.0 (F12): Cement originalOrder after deletion to keep baseline consistent
                val renumbered = filtered.mapIndexed { index, item -> item.copy(order = index, originalOrder = index) }
                // Ein abgehakter Unterpunkt, der so zum Oberpunkt wurde, gehört unter den Trenner
                if (separatorShown()) sortChecklistItems(renumbered) else renumbered
            }
        }
        // 🔧 v1.11.0: Autosave nur wenn gelöschtes Item Text hatte (= auf Disk existierte)
        if (!wasEmpty) {
            scheduleAutosave()
        }
    }

    /**
     * 🆕 v2.2.0: Dupliziert ein Checklist-Item direkt unter dem Original.
     * Das Duplikat ist immer unchecked (frischer Eintrag).
     *
     * Wenn das Quell-Item checked ist und ein Separator existiert (MANUAL/UNCHECKED_FIRST etc.),
     * wird das Duplikat VOR dem Separator eingefügt (in die unchecked-Sektion), da es ja
     * unchecked ist.
     */
    fun duplicateChecklistItem(itemId: String): String? {
        val items = _checklistItems.value
        val sourceItem = items.find { it.id == itemId } ?: return null

        pushUndoSnapshot()
        hasUnsavedChecklistEdits = true

        val now = System.currentTimeMillis()
        val newItem = ChecklistItemState(
            id = UUID.randomUUID().toString(),
            text = sourceItem.text,
            isChecked = false,
            order = 0,
            originalOrder = 0,
            createdAt = now
        )

        _checklistItems.update { currentItems ->
            val index = currentItems.indexOfFirst { it.id == itemId }
            if (index >= 0) {
                // Wenn Source im erledigten Abschnitt → neues unchecked Item VOR die checked-Sektion.
                // Unterpunkte: ein Oberpunkt wird hinter seiner Gruppe dupliziert (sonst nähme das
                // Duplikat ihm die Unterpunkte weg), ein Unterpunkt direkt darunter.
                val afterIndex = if (sourceItem.indentationLevel == 0) {
                    ChecklistNesting.groupRange(currentItems, index).last
                } else {
                    index
                }
                val (effectiveIndex, level) = placeNewOpenRow(currentItems, afterIndex, sourceItem.indentationLevel)

                val newList = currentItems.toMutableList()
                newList.add(effectiveIndex, newItem.copy(indentationLevel = level))
                newList.mapIndexed { i, item -> item.copy(order = i, originalOrder = i) }
            } else {
                currentItems
            }
        }

        isDirty = true
        scheduleAutosave()
        return newItem.id
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🆕 v2.2.0: Aktion 3 — In andere Checkliste kopieren
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Liste aller anderen Checklisten-Notizen für den Auswahl-Dialog.
     * null = Dialog nicht sichtbar, emptyList() = keine anderen Checklisten vorhanden.
     */
    private val _otherChecklists = MutableStateFlow<List<Note>?>(null)
    val otherChecklists: StateFlow<List<Note>?> = _otherChecklists.asStateFlow()

    /**
     * Lädt alle Checklisten-Notizen (außer der aktuellen) vom Storage.
     * Setzt _otherChecklists auf die Liste (oder emptyList wenn keine vorhanden).
     * Muss auf IO-Dispatcher laufen weil NotesStorage synchron von Disk liest.
     */
    fun loadOtherChecklists() {
        viewModelScope.launch {
            val currentId = existingNote?.id
            val checklists = withContext(Dispatchers.IO) {
                // 🆕 v2.9.0 (Trash): getrashte Checklisten nicht im Ziel-Picker anbieten.
                storage.loadActiveNotes()
                    .filter { it.noteType == NoteType.CHECKLIST && it.id != currentId }
                    .sortedBy { it.title.lowercase() }
            }
            _otherChecklists.value = checklists
        }
    }

    /**
     * Schließt den Checklisten-Auswahl-Dialog ohne Aktion.
     */
    fun dismissChecklistPicker() {
        _otherChecklists.value = null
    }

    /**
     * Kopiert den Text eines Checklist-Items in eine andere Checklisten-Notiz.
     * Das neue Item wird als unchecked am Ende der Ziel-Checkliste eingefügt.
     *
     * Persistierung: Direkt via NotesStorage — Ziel-Notiz laden, Item anhängen, speichern.
     * SyncStatus der Ziel-Notiz wird auf PENDING gesetzt, damit der nächste Sync sie hochlädt.
     *
     * @param itemId ID des Quell-Items in der aktuellen Checkliste
     * @param targetNoteId ID der Ziel-Checklisten-Notiz
     */
    fun copyItemToChecklist(itemId: String, targetNoteId: String) {
        val itemText = _checklistItems.value.find { it.id == itemId }?.text ?: return

        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                storage.updateNote(targetNoteId) { targetNote ->
                    val existingItems = targetNote.checklistItems.orEmpty()
                    val maxOrder = existingItems.maxOfOrNull { it.order } ?: -1
                    val newItem = ChecklistItem(
                        id = UUID.randomUUID().toString(),
                        text = itemText,
                        isChecked = false,
                        order = maxOrder + 1,
                        originalOrder = maxOrder + 1,
                        createdAt = System.currentTimeMillis()
                    )
                    targetNote.copy(
                        checklistItems = existingItems + newItem,
                        updatedAt = System.currentTimeMillis(),
                        syncStatus = pendingOrLocalOnly(targetNote.folderName)
                    ).withContentTouched(targetNote)
                } != null
            }

            _otherChecklists.value = null // Dialog schließen

            if (success) {
                _events.emit(NoteEditorEvent.ShowToast(ToastMessage.ITEM_COPIED_TO_CHECKLIST))
            }
        }
    }

    fun moveChecklistItem(fromIndex: Int, toIndex: Int) {
        // Während eines Ziehens hat startChecklistDrag den Undo-Schritt schon angelegt
        if (dragDraggedId == null) pushUndoSnapshotDebounced() // IMPL_29: debounce per gesture (was: per-swap snapshot)
        isDirty = true // 🆕 v1.10.0-P2: Moving an item is an edit
        hasUnsavedChecklistEdits = true // 🛡️ v1.8.2 (IMPL_17)
        _checklistItems.update { items ->
            if (fromIndex !in items.indices || toIndex !in items.indices) return@update items
            // 🆕 v1.8.1 IMPL_14: Cross-Boundary Move mit Auto-Toggle. Unterpunkte: maßgeblich ist der
            // Abschnitt (Zustand des Oberpunkts), nicht isChecked der beiden Zeilen.
            items.nested { ChecklistNesting.move(it, fromIndex, toIndex) }
                // 🆕 v1.9.0 (F04): Update originalOrder on manual reorder to cement new position
                .mapIndexed { index, i -> i.copy(order = index, originalOrder = index) }
        }
        // Unterpunkte: Zwischenstände eines Ziehens nicht speichern, onChecklistDragFinished speichert das Ergebnis
        if (dragDraggedId == null) scheduleAutosave() // 🆕 v1.10.0-P2: Trigger autosave on item move
    }

    /**
     * 🔀 v1.8.0 (IMPL_020): Sortiert Checklist-Items nach gewählter Option.
     * Einmalige Aktion (nicht persistiert) — User kann danach per Drag & Drop feinjustieren.
     */
    fun sortChecklistItems(option: ChecklistSortOption) {
        pushUndoSnapshot() // 🆕 v1.10.0
        isDirty = true
        hasUnsavedChecklistEdits = true // 🛡️ v1.8.2 (IMPL_17)
        // Merke die Auswahl für diesen Editor-Session
        _lastChecklistSortOption.value = option

        _checklistItems.update { items ->
            // Bei MANUAL: nur nach Abschnitt trennen (stabil), damit der Separator korrekt platziert wird.
            // Sortiert werden Gruppen nach ihrem Oberpunkt, Unterpunkte bleiben in ihrer Gruppe.
            val effective = if (option == ChecklistSortOption.MANUAL) ChecklistSortOption.UNCHECKED_FIRST else option
            val sorted = sortChecklistStates(items, effective)

            // 🆕 v1.9.0 (F04): Explicit sort resets originalOrder baseline to new positions
            sorted.mapIndexed { index, item -> item.copy(order = index, originalOrder = index) }
        }
        scheduleAutosave()
    }

    /** 🆕 v2.5.0 (Issue #65): Set or clear the note background colour.
     *  Triggers an immediate silent save so Process-Death cannot lose the change. */
    fun setColor(hex: String?) {
        if (hex == _uiState.value.color) return
        _uiState.update { it.copy(color = hex) }
        isDirty = true
        viewModelScope.launch { performSave(silent = true) }
    }

    /**
     * v2.0.0: Holt den aktuellen Inhalt aus dem Compose-`TextFieldState`, den `snapshotFlow`
     * womöglich noch nicht propagiert hat. Muss vor jeder [isDirty]-Prüfung laufen, sonst gelten
     * die letzten Tastendrücke als „nichts geändert“.
     */
    private fun flushLatestContent() {
        if (currentNoteType != NoteType.TEXT) return
        val latest = contentProvider?.invoke() ?: return
        if (latest == _uiState.value.content) return
        _uiState.update { it.copy(content = latest) }
        isDirty = true
    }

    fun saveNote() {
        autosaveJob?.cancel() // 🆕 v1.9.0: manual save supersedes pending autosave
        viewModelScope.launch {
            // 🔧 Issue #124: Ohne echte Änderung nicht neu schreiben — performSave() würde sonst
            // updatedAt und syncStatus anfassen, obwohl der Nutzer nichts geändert hat. Sync und
            // Navigation laufen trotzdem: ein per Autosave geschriebener PENDING-Stand soll auch
            // vom Button noch hochgeladen werden.
            flushLatestContent()
            if (!isDirty && existingNote != null) {
                Logger.d(TAG, "⏭️ saveNote: nothing dirty — skipping write")
            } else if (!performSave()) {
                return@launch
            }

            // 🌟 v1.6.0: Trigger onSave Sync
            triggerOnSaveSync()

            // 🆕 v1.8.0: Betroffene Widgets aktualisieren
            WidgetUpdateHelper.refreshAllWidgets(getApplication())

            _events.emit(NoteEditorEvent.NavigateBack)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🆕 v2.16.0: Konflikt-Auflösung
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * "Meine behalten" — die lokale Fassung wird beim nächsten Sync hochgeladen.
     *
     * Der Editor bleibt offen und zeigt weiter denselben Text; nur das Banner verschwindet.
     */
    fun resolveConflictKeepLocal() {
        val noteId = existingNote?.id ?: return
        viewModelScope.launch {
            if (!conflictResolver.keepLocal(noteId)) {
                emitSnackbar(getApplication<Application>().getString(R.string.conflict_resolve_failed))
                return@launch
            }
            existingNote = existingNote?.copy(syncStatus = SyncStatus.PENDING)
            _uiState.update { it.copy(hasConflict = false, conflictVersions = null) }
            emitSnackbar(getApplication<Application>().getString(R.string.conflict_resolved_keep_local))
            triggerOnSaveSync()
        }
    }

    /**
     * "Server nehmen" — die Server-Fassung ersetzt die lokale und wird sofort in den Editor
     * geladen. Ohne das Neuladen stünde weiter der alte Text im Feld und der nächste
     * Tastendruck machte daraus eine Änderung gegen die gerade übernommene Fassung.
     */
    fun resolveConflictUseServer() {
        val noteId = existingNote?.id ?: return
        viewModelScope.launch {
            if (!conflictResolver.useServer(noteId)) {
                emitSnackbar(getApplication<Application>().getString(R.string.conflict_resolve_failed))
                return@launch
            }
            undoRedoManager.clear() // der übernommene Text ist ein neuer Ausgangspunkt
            loadExistingNote(noteId)
            _uiState.update { it.copy(hasConflict = false, conflictVersions = null) }
            _events.emit(NoteEditorEvent.RestoreContent(_uiState.value.content))
            emitSnackbar(getApplication<Application>().getString(R.string.conflict_resolved_use_server))
        }
    }

    /**
     * "Vergleichen" — holt die Server-Fassung und stellt sie der lokalen gegenüber.
     *
     * Ohne den Dialog fällt die Entscheidung blind: "Server nehmen" ist keine Vorschau,
     * sondern schreibt sofort. Die geholte Fassung lebt nur im UI-State und wird beim
     * Schließen verworfen — kein zweiter Datensatz im Storage, kein Aufräumen.
     *
     * Gegenübergestellt wird die **gespeicherte** lokale Fassung: genau die, die
     * "Meine behalten" hochlädt.
     *
     * Ist der Server nicht erreichbar, öffnet der Dialog gar nicht erst — die beiden
     * Aktionen im Banner bleiben nutzbar, "Meine behalten" braucht kein Netz.
     */
    fun showConflictCompare() {
        val local = existingNote ?: return
        if (_uiState.value.conflictCompareLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(conflictCompareLoading = true) }
            val server = conflictResolver.fetchServerVersion(local.id)
            _uiState.update {
                it.copy(
                    conflictCompareLoading = false,
                    conflictVersions = server?.let { remote -> ConflictVersions(local, remote) }
                )
            }
            if (server == null) {
                emitSnackbar(getApplication<Application>().getString(R.string.conflict_resolve_failed))
            }
        }
    }

    /** Schließt die Vergleichsansicht, ohne zu entscheiden. Der Konflikt bleibt bestehen. */
    fun dismissConflictCompare() {
        _uiState.update { it.copy(conflictVersions = null) }
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
    // Abbau: TECH_DEBT_ROADMAP.md Slice 3
    @Suppress("ReturnCount", "CyclomaticComplexMethod", "LongMethod")
    fun saveOnBack(): Boolean {
        if (isDiscarded) return true // 🆕 Issue #114: verworfene Notiz nicht wieder auf Platte schreiben
        if (!autosaveEnabled) {
            Logger.d(TAG, "⏭️ saveOnBack: autosave disabled — skipping")
            return true
        }

        flushLatestContent()

        if (!isDirty) {
            Logger.d(TAG, "⏭️ saveOnBack: nothing dirty — skipping")
            return true
        }

        autosaveJob?.cancel() // Pending autosave superseded

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
                        color = state.color, // 🆕 v2.5.0
                        updatedAt = System.currentTimeMillis(),
                        syncStatus = pendingOrLocalOnly(existingNote?.folderName)
                    )?.withContentTouched(existingNote) ?: Note(
                        title = title,
                        content = content,
                        noteType = NoteType.TEXT,
                        checklistItems = null,
                        color = state.color, // 🆕 v2.5.0
                        folderName = initialFolderName, // 🆕 v2.7.0 (Folders)
                        deviceId = DeviceIdGenerator.getDeviceId(getApplication()),
                        syncStatus = SyncStatus.LOCAL_ONLY
                    )
                    // runBlocking ist hier akzeptabel: saveOnBack() wird aus onPause() aufgerufen
                    // und MUSS synchron abschließen bevor die Activity zerstört wird.
                    @Suppress("BlockingMethodInNonBlockingContext")
                    runBlocking { persist(note) }
                }
                NoteType.CHECKLIST -> {
                    // Unterpunkte: leere Oberpunkte geben ihre Unterpunkte frei (ChecklistNesting.withoutBlank)
                    val validItems = _checklistItems.value
                        .nested { ChecklistNesting.withoutBlank(it) }
                        .mapIndexed { index, item ->
                            item.toItem(order = index)
                        }
                    if (title.isEmpty() && validItems.isEmpty()) return true

                    // 🔧 v1.11.0: No-Change-Guard — kein Save wenn nur leere Items hinzugefügt
                    // wurden und sich der tatsächliche Inhalt nicht geändert hat.
                    // Verhindert fälschliches PENDING-Flag beim Verlassen der Notiz.
                    if (existingNote != null) {
                        val currentNote = requireNotNull(existingNote)
                        val existingItems = currentNote.checklistItems.orEmpty()
                        val noContentChange = currentNote.title == title &&
                            existingItems.size == validItems.size &&
                            existingItems.zip(validItems).all { (old, new) ->
                                old.id == new.id &&
                                    old.text == new.text &&
                                    old.isChecked == new.isChecked &&
                                    old.order == new.order &&
                                    old.indentationLevel == new.indentationLevel
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
                        color = state.color, // 🆕 v2.5.0
                        updatedAt = System.currentTimeMillis(),
                        syncStatus = pendingOrLocalOnly(existingNote?.folderName)
                    )?.withContentTouched(existingNote) ?: Note(
                        title = title,
                        content = "",
                        noteType = NoteType.CHECKLIST,
                        checklistItems = validItems,
                        checklistSortOption = _lastChecklistSortOption.value.name,
                        color = state.color, // 🆕 v2.5.0
                        folderName = initialFolderName, // 🆕 v2.7.0 (Folders)
                        deviceId = DeviceIdGenerator.getDeviceId(getApplication()),
                        syncStatus = SyncStatus.LOCAL_ONLY
                    )
                    // runBlocking ist hier akzeptabel: saveOnBack() wird aus onPause() aufgerufen
                    // und MUSS synchron abschließen bevor die Activity zerstört wird.
                    @Suppress("BlockingMethodInNonBlockingContext")
                    runBlocking { persist(note) }
                    hasUnsavedChecklistEdits = false
                }
            }
            isDirty = false
            savedSnapshot = currentSnapshot() // 🔧 v1.10.0: Update Referenz-Snapshot nach Save
            Logger.d(TAG, "💾 saveOnBack: saved successfully")
            // Fire-and-forget widget update — saveOnBack must return synchronously
            viewModelScope.launch {
                WidgetUpdateHelper.refreshAllWidgets(getApplication())
            }
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
    private suspend fun performSave(silent: Boolean = false): Boolean = try {
        writeCurrentNote(silent)
    } catch (e: IOException) {
        // Voller Speicher o. ä.: melden statt abstürzen. isDirty bleibt gesetzt, der nächste
        // Autosave oder das Verlassen versucht es erneut, die bisherige Datei ist unversehrt.
        Logger.e(TAG, "Saving the note failed", e)
        emitSnackbar(getApplication<Application>().getString(R.string.note_save_failed))
        false
    }

    // Abbau: TECH_DEBT_ROADMAP.md Slice 3
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private suspend fun writeCurrentNote(silent: Boolean): Boolean {
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
                    color = state.color, // 🆕 v2.5.0
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = pendingOrLocalOnly(existingNote?.folderName)
                )?.withContentTouched(existingNote) ?: Note(
                    title = title,
                    content = content,
                    noteType = NoteType.TEXT,
                    checklistItems = null,
                    color = state.color, // 🆕 v2.5.0
                    folderName = initialFolderName, // 🆕 v2.7.0 (Folders)
                    deviceId = DeviceIdGenerator.getDeviceId(getApplication()),
                    syncStatus = SyncStatus.LOCAL_ONLY
                )

                persist(note)
            }

            NoteType.CHECKLIST -> {
                // 🆕 v1.9.0 (F04): Preserve originalOrder for position restore
                // Unterpunkte: leere Oberpunkte geben ihre Unterpunkte frei (ChecklistNesting.withoutBlank)
                val validItems = _checklistItems.value
                    .nested { ChecklistNesting.withoutBlank(it) }
                    .mapIndexed { index, item ->
                        item.toItem(order = index)
                    }

                if (title.isEmpty() && validItems.isEmpty()) {
                    if (!silent) _events.emit(NoteEditorEvent.ShowToast(ToastMessage.NOTE_IS_EMPTY))
                    return false
                }

                // 🔧 v1.11.0: No-Change-Guard — kein erneutes Speichern wenn sich nichts geändert hat.
                // Verhindert falschen Autosave-Indikator wenn nur leere Items hinzugefügt wurden.
                if (silent && existingNote != null) {
                    val currentNote = requireNotNull(existingNote)
                    val existingItems = currentNote.checklistItems.orEmpty()
                    val existingTitle = currentNote.title
                    val noContentChange = existingTitle == title &&
                        currentNote.color == state.color &&
                        // 🆕 v2.5.x Fix: colour change must not be blocked by guard
                        existingItems.size == validItems.size &&
                        existingItems.zip(validItems).all { (old, new) ->
                            old.id == new.id &&
                                old.text == new.text &&
                                old.isChecked == new.isChecked &&
                                old.order == new.order &&
                                old.indentationLevel == new.indentationLevel
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
                    checklistSortOption = _lastChecklistSortOption.value.name, // 🆕 v1.8.1 (IMPL_03)
                    color = state.color, // 🆕 v2.5.0
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = pendingOrLocalOnly(existingNote?.folderName)
                )?.withContentTouched(existingNote) ?: Note(
                    title = title,
                    content = "",
                    noteType = NoteType.CHECKLIST,
                    checklistItems = validItems,
                    checklistSortOption = _lastChecklistSortOption.value.name, // 🆕 v1.8.1 (IMPL_03)
                    color = state.color, // 🆕 v2.5.0
                    folderName = initialFolderName, // 🆕 v2.7.0 (Folders)
                    deviceId = DeviceIdGenerator.getDeviceId(getApplication()),
                    syncStatus = SyncStatus.LOCAL_ONLY
                )

                persist(note)
                hasUnsavedChecklistEdits = false // 🛡️ v1.8.2 (IMPL_17): Flag zurücksetzen — gespeicherter Stand ist jetzt aktuell
            }
        }
        isDirty = false // 🆕 v1.9.0: note is clean after any successful save
        savedSnapshot = currentSnapshot() // 🔧 v1.10.0: Update Referenz-Snapshot nach Save
        return true
    }

    /**
     * Schreibt [note] und merkt sie als Basis für das nächste Speichern ([existingNote]).
     *
     * Bei einer bestehenden Notiz wird gegen den Stand der Platte geprüft: Hat der Sync inzwischen
     * eine neuere Fassung heruntergeladen (SYNCED, anderer Inhalt als die Basis des Editors), wird
     * die Notiz CONFLICT statt PENDING. Der Download hat den E-Tag schon aufgefrischt, der Upload
     * überschriebe die Server-Fassung sonst ohne Rückfrage. Ein bestehender Konflikt bleibt stehen,
     * bis er im Banner aufgelöst wird, sonst machte ihn der nächste Autosave wieder zu PENDING.
     * Fehlt die Datei inzwischen, wird sie neu geschrieben, damit der Text nicht verloren geht.
     */
    private suspend fun persist(note: Note) = withContext(Dispatchers.IO) {
        val base = existingNote
        if (base == null) {
            storage.saveNote(note)
            logCreateIfNew(note)
            existingNote = note
            return@withContext
        }
        var outdated = false
        val written = storage.updateNote(note.id) { onDisk ->
            outdated = onDisk.syncStatus == SyncStatus.SYNCED && onDisk.contentDiffers(base)
            if (outdated || onDisk.syncStatus == SyncStatus.CONFLICT) note.copy(syncStatus = SyncStatus.CONFLICT) else note
        } ?: note.also { storage.saveNote(it) }
        existingNote = written
        if (written.syncStatus == SyncStatus.CONFLICT) _uiState.update { it.copy(hasConflict = true) }
        if (outdated) {
            Logger.w(TAG, "⚠️ ${note.id} changed on the server while open in the editor, marked CONFLICT")
            ActivityLog.log(
                ActivityLog.Op.CONFLICT,
                ActivityLog.Src.LOCAL,
                id = note.id,
                title = note.title,
                folder = note.folderName,
                why = "editor_outdated_base"
            )
        }
    }

    private fun pendingOrLocalOnly(folderName: String?): SyncStatus =
        if (folderStore.isLocalOnly(folderName)) SyncStatus.LOCAL_ONLY else SyncStatus.PENDING

    /**
     * 🆕 Issue #128 Teil 3: Protokolliert eine Neuanlage (erkennbar daran, dass [existingNote]
     * noch nicht gesetzt ist — der Aufrufer weist erst danach zu). Bearbeitungen werden bewusst
     * NICHT protokolliert: bei Autosave wäre das eine Zeile pro Tastendruck-Pause.
     *
     * Schreibt auf die Platte, gehört also in einen IO-Kontext — [saveOnBack] läuft aus
     * `onPause()` auf dem Main-Thread.
     */
    private fun logCreateIfNew(note: Note) {
        if (existingNote != null) return
        ActivityLog.log(
            ActivityLog.Op.CREATE,
            ActivityLog.Src.LOCAL,
            id = note.id,
            title = note.title,
            folder = note.folderName
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🆕 v1.10.0: Undo/Redo
    // ═══════════════════════════════════════════════════════════════════════

    private fun currentSnapshot(): EditorSnapshot = EditorSnapshot(
        title = _uiState.value.title,
        content = _uiState.value.content,
        checklistItems = _checklistItems.value.toList(),
        noteType = currentNoteType
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
        if (snapshotDebounceJob?.isActive != true) {
            pushUndoSnapshot() // Capture state BEFORE this burst of edits
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
        currentNoteType = snapshot.noteType
        val isNew = _uiState.value.isNewNote
        val restoredToolbarTitle = when {
            snapshot.noteType == NoteType.CHECKLIST && isNew -> ToolbarTitle.NEW_CHECKLIST
            snapshot.noteType == NoteType.CHECKLIST -> ToolbarTitle.EDIT_CHECKLIST
            isNew -> ToolbarTitle.NEW_NOTE
            else -> ToolbarTitle.EDIT_NOTE
        }
        _uiState.update {
            it.copy(
                title = snapshot.title,
                content = snapshot.content,
                noteType = snapshot.noteType,
                toolbarTitle = restoredToolbarTitle
            )
        }
        _checklistItems.value = snapshot.checklistItems

        // 🔧 v1.10.0: isDirty-Reset wenn Undo/Redo den gespeicherten Zustand wiederherstellt
        val isBackToSaved = savedSnapshot?.let { saved ->
            snapshot.title == saved.title &&
                snapshot.content == saved.content &&
                snapshot.checklistItems == saved.checklistItems
        } ?: false

        if (isBackToSaved) {
            isDirty = false
            autosaveJob?.cancel() // Kein Autosave nötig — Zustand = gespeicherter Zustand
        } else {
            isDirty = true
            scheduleAutosave() // Undo/Redo auf Zwischen-Stand → Autosave nötig
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
     * Does NOT trigger sync or navigation — only local disk save + widget update.
     */
    private fun scheduleAutosave() {
        if (isDiscarded) return // 🆕 Issue #114: verworfene Notiz nicht wieder auf Platte schreiben
        if (!autosaveEnabled) return
        if (!isDirty) return // 🆕 v1.9.0: no changes since last save → skip
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            kotlinx.coroutines.delay(Constants.AUTOSAVE_DEBOUNCE_MS)
            val saved = performSave(silent = true)
            if (saved) {
                Logger.d(TAG, "💾 Autosave completed")
                _autosaveIndicatorVisible.value = true
                // Update widgets so they reflect the auto-saved state
                WidgetUpdateHelper.refreshAllWidgets(getApplication())
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
            val (title, content) = exportableText() ?: return@launch
            val strategy = prefs.calendarParsingStrategy()
            val preview = computePreview(content, strategy)
            val calTitle = title.ifBlank { preview.title.ifBlank { content.take(CALENDAR_TITLE_FALLBACK_MAX_LENGTH) } }
            val calDescription = if (strategy == Strategy.RAW) content else preview.description
            _events.emit(
                NoteEditorEvent.OpenCalendar(
                    title = calTitle,
                    description = calDescription,
                    location = preview.location,
                    attendees = preview.attendees
                )
            )
        }
    }

    fun openChecklistItemInCalendar(itemId: String) {
        viewModelScope.launch {
            val item = _checklistItems.value.find { it.id == itemId } ?: return@launch
            if (item.text.isBlank()) return@launch
            val noteTitle = _uiState.value.title.trim()
            val strategy = prefs.calendarParsingStrategy()
            val preview = computePreview(item.text.trim(), strategy)
            val calTitle = preview.title.ifBlank { item.text.trim() }
            val calDescription = if (strategy == Strategy.RAW) {
                if (noteTitle.isNotBlank()) "($noteTitle)" else ""
            } else {
                preview.description
            }
            _events.emit(
                NoteEditorEvent.OpenCalendar(
                    title = calTitle,
                    description = calDescription,
                    location = preview.location,
                    attendees = preview.attendees
                )
            )
        }
    }

    /**
     * 🆕 v1.10.0-Papa: Shares the current note as plain text via ACTION_SEND intent.
     */
    fun shareAsText() {
        viewModelScope.launch {
            val (title, content) = exportableText() ?: return@launch
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
            val (title, content) = exportableText() ?: return@launch
            val pdfTitle = title.ifBlank { content.take(CALENDAR_TITLE_FALLBACK_MAX_LENGTH) }
            _events.emit(NoteEditorEvent.ShareAsPdf(title = pdfTitle))
        }
    }

    fun copyNoteText() {
        viewModelScope.launch {
            val (title, content) = exportableText() ?: return@launch
            val fullText = if (title.isNotBlank()) "$title\n\n$content" else content
            _events.emit(NoteEditorEvent.CopyToClipboard(fullText.trim()))
        }
    }

    /** Titel (getrimmt) und Klartext für Kalender, Teilen und Kopieren; `null` mit Toast, wenn beides leer ist. */
    private suspend fun exportableText(): Pair<String, String>? {
        val state = _uiState.value
        val content = NoteShareHelper.formatAsPlainText(
            noteType = state.noteType,
            textContent = state.content,
            checklistItems = _checklistItems.value
        )
        val title = state.title.trim()
        if (title.isBlank() && content.isBlank()) {
            _events.emit(NoteEditorEvent.ShowToast(ToastMessage.NOTE_IS_EMPTY))
            return null
        }
        return title to content
    }

    /**
     * 🆕 v2.9.0 (Trash): Verschiebt die aktuelle Notiz in den Papierkorb.
     * Die eigentliche Operation übernimmt der MainViewModel (Undo-Snackbar), daher wird hier nur
     * das Event emittiert. Kein Bestätigungs-Dialog mehr — Löschen = Papierkorb.
     */
    fun deleteNote() {
        viewModelScope.launch {
            val note = existingNote
            if (note == null) {
                // 🆕 Issue #114: nie gespeichert → nichts im Papierkorb zu tun, einfach verwerfen.
                isDiscarded = true
                autosaveJob?.cancel()
                isDirty = false
                _events.emit(NoteEditorEvent.NavigateBack)
                return@launch
            }
            _events.emit(NoteEditorEvent.NoteDeleteRequested(note.id))
        }
    }

    /**
     * 🆕 v2.11.0 (Archive): Archiviert/dearchiviert die aktuelle Notiz.
     * Speichert offene Änderungen (silent) und emittiert das Event an die Activity —
     * die Umschaltung + Undo-Snackbar übernimmt der MainViewModel (wie beim Löschen).
     */
    fun toggleArchive() {
        viewModelScope.launch {
            val isUnsaved = existingNote == null
            // 🆕 Issue #114: neue Notiz erst anlegen; silent=false → leere Notiz meldet sich per Toast.
            if (isDirty || isUnsaved) performSave(silent = !isUnsaved)
            val note = existingNote ?: return@launch // leere neue Notiz → performSave hat getoastet
            _events.emit(NoteEditorEvent.NoteArchiveToggleRequested(note.id))
        }
    }

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

        viewModelScope.launch {
            val freshNote = withContext(Dispatchers.IO) { storage.loadNote(noteId) } ?: return@launch

            // Nur Checklist-Items aktualisieren
            if (freshNote.noteType == NoteType.CHECKLIST) {
                val freshItems = freshNote.checklistItems.orEmpty().toEditorStates()
                if (freshItems.isEmpty()) return@launch

                _checklistItems.value = sortChecklistItems(freshItems)
                // existingNote aktualisieren damit beim Speichern der richtige
                // Basis-State verwendet wird
                existingNote = freshNote
            }
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
    // v2.5.0: Logik nach `sync/SyncScheduler` extrahiert.
    private val syncScheduler by lazy { SyncScheduler(getApplication()) }

    private fun triggerOnSaveSync() {
        syncScheduler.triggerOnSaveSync(reason = "onSave")
    }
}
