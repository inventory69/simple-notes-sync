package dev.dettmer.simplenotes.ui.main

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteFilter
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.models.SortDirection
import dev.dettmer.simplenotes.models.SortOption
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.PendingServerDeletions
import dev.dettmer.simplenotes.sync.SyncPhase
import dev.dettmer.simplenotes.sync.SyncProgress
import dev.dettmer.simplenotes.sync.SyncStateManager
import dev.dettmer.simplenotes.sync.WebDavSyncService
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for MainActivity Compose
 * v1.5.0: Jetpack Compose MainActivity Redesign
 *
 * Manages notes list, sync state, and deletion with undo.
 */
@Suppress(
    "TooManyFunctions", // 🔧 v1.10.0: Detekt compliance — class has many features
    "LargeClass" // 🔧 v2.1.0 (F46): Extended with grid column control state
)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    companion object {
        private const val TAG = "MainViewModel"
        private const val MIN_AUTO_SYNC_INTERVAL_MS = 60_000L // 1 Minute
        private const val SNACKBAR_UNDO_DELAY_MS = 3500L
    }

    private val storage = NotesStorage(application)
    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val pendingServerDeletions = PendingServerDeletions(application)

    // ═══════════════════════════════════════════════════════════════════════
    // Notes State
    // ═══════════════════════════════════════════════════════════════════════

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _pendingDeletions = MutableStateFlow<Set<String>>(emptySet())
    val pendingDeletions: StateFlow<Set<String>> = _pendingDeletions.asStateFlow()

    // ═══════════════════════════════════════════════════════════════════════
    // Multi-Select State (v1.5.0)
    // ═══════════════════════════════════════════════════════════════════════

    private val _selectedNotes = MutableStateFlow<Set<String>>(emptySet())
    val selectedNotes: StateFlow<Set<String>> = _selectedNotes.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedNotes
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ═══════════════════════════════════════════════════════════════════════
    // 🌟 v1.6.0: Offline Mode State (reactive)
    // ═══════════════════════════════════════════════════════════════════════

    private val _isOfflineMode = MutableStateFlow(
        prefs.getBoolean(Constants.KEY_OFFLINE_MODE, true)
    )
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    /**
     * Refresh offline mode state from SharedPreferences
     * Called when returning from Settings screen (in onResume)
     */
    fun refreshOfflineModeState() {
        val oldValue = _isOfflineMode.value
        val newValue = prefs.getBoolean(Constants.KEY_OFFLINE_MODE, true)
        _isOfflineMode.value = newValue
        Logger.d(TAG, "🔄 refreshOfflineModeState: offlineMode=$oldValue → $newValue")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🎨 v1.7.0: Display Mode State
    // ═══════════════════════════════════════════════════════════════════════

    private val _displayMode = MutableStateFlow(
        prefs.getString(Constants.KEY_DISPLAY_MODE, Constants.DEFAULT_DISPLAY_MODE) ?: Constants.DEFAULT_DISPLAY_MODE
    )
    val displayMode: StateFlow<String> = _displayMode.asStateFlow()

    /**
     * Refresh display mode from SharedPreferences
     * Called when returning from Settings screen
     */
    fun refreshDisplayMode() {
        val newValue =
            prefs.getString(Constants.KEY_DISPLAY_MODE, Constants.DEFAULT_DISPLAY_MODE)
                ?: Constants.DEFAULT_DISPLAY_MODE
        _displayMode.value = newValue
        Logger.d(TAG, "🔄 refreshDisplayMode: displayMode=${_displayMode.value} → $newValue")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🆕 v2.1.0 (F46): Grid Column Control State
    // ═══════════════════════════════════════════════════════════════════════

    private val _gridAdaptiveScaling = MutableStateFlow(
        prefs.getBoolean(Constants.KEY_GRID_ADAPTIVE_SCALING, Constants.DEFAULT_GRID_ADAPTIVE_SCALING)
    )
    val gridAdaptiveScaling: StateFlow<Boolean> = _gridAdaptiveScaling.asStateFlow()

    private val _gridManualColumns = MutableStateFlow(
        prefs.getInt(Constants.KEY_GRID_MANUAL_COLUMNS, Constants.DEFAULT_GRID_MANUAL_COLUMNS)
    )
    val gridManualColumns: StateFlow<Int> = _gridManualColumns.asStateFlow()

    /**
     * Refresh grid settings from SharedPreferences.
     * Called when returning from Settings screen.
     */
    fun refreshGridSettings() {
        _gridAdaptiveScaling.value = prefs.getBoolean(
            Constants.KEY_GRID_ADAPTIVE_SCALING,
            Constants.DEFAULT_GRID_ADAPTIVE_SCALING
        )
        _gridManualColumns.value = prefs.getInt(
            Constants.KEY_GRID_MANUAL_COLUMNS,
            Constants.DEFAULT_GRID_MANUAL_COLUMNS
        )
        Logger.d(
            TAG,
            "🔄 refreshGridSettings: adaptive=${_gridAdaptiveScaling.value}, columns=${_gridManualColumns.value}"
        )
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🆕 v1.9.0 (F05): Custom App Title State
    // ═══════════════════════════════════════════════════════════════════════

    private val _customAppTitle = MutableStateFlow(
        prefs.getString(Constants.KEY_CUSTOM_APP_TITLE, Constants.DEFAULT_CUSTOM_APP_TITLE)
            ?: Constants.DEFAULT_CUSTOM_APP_TITLE
    )
    val customAppTitle: StateFlow<String> = _customAppTitle.asStateFlow()

    /**
     * Refresh custom app title from SharedPreferences.
     * Called when returning from Settings screen (same pattern as refreshDisplayMode).
     */
    fun refreshCustomAppTitle() {
        val newValue = prefs.getString(Constants.KEY_CUSTOM_APP_TITLE, Constants.DEFAULT_CUSTOM_APP_TITLE)
            ?: Constants.DEFAULT_CUSTOM_APP_TITLE
        _customAppTitle.value = newValue
        Logger.d(TAG, "🔄 refreshCustomAppTitle: '$newValue'")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔀 v1.8.0: Sort State
    // ═══════════════════════════════════════════════════════════════════════

    private val _sortOption = MutableStateFlow(
        SortOption.fromPrefsValue(
            prefs.getString(Constants.KEY_SORT_OPTION, Constants.DEFAULT_SORT_OPTION) ?: Constants.DEFAULT_SORT_OPTION
        )
    )
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _sortDirection = MutableStateFlow(
        SortDirection.fromPrefsValue(
            prefs.getString(Constants.KEY_SORT_DIRECTION, Constants.DEFAULT_SORT_DIRECTION)
                ?: Constants.DEFAULT_SORT_DIRECTION
        )
    )
    val sortDirection: StateFlow<SortDirection> = _sortDirection.asStateFlow()

    // 🆕 v1.9.0 (F06): Note Filter State
    private val _noteFilter = MutableStateFlow(
        NoteFilter.fromPrefsValue(
            prefs.getString(Constants.KEY_NOTE_FILTER, Constants.DEFAULT_NOTE_FILTER)
                ?: Constants.DEFAULT_NOTE_FILTER
        )
    )
    val noteFilter: StateFlow<NoteFilter> = _noteFilter.asStateFlow()

    // 🆕 v1.9.0 (F10): Search Query State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * 🔀 v1.8.0: Sortierte Notizen — kombiniert aus Notes + SortOption + SortDirection.
     * 🆕 v1.9.0 (F06): + Filter nach NoteType
     * 🆕 v1.9.0 (F10): + Volltextsuche über Titel und Inhalt
     */
    val sortedNotes: StateFlow<List<Note>> = combine(
        _notes,
        _sortOption,
        _sortDirection,
        _noteFilter,
        _searchQuery
    ) { notes, option, direction, filter, query ->
        val filtered = filterNotes(notes, filter)
        val searched = searchNotes(filtered, query)
        sortNotes(searched, option, direction)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    // ═══════════════════════════════════════════════════════════════════════
    // Sync State
    // ═══════════════════════════════════════════════════════════════════════

    // 🆕 v1.8.0 / 🔧 v1.10.0: Banner-System — min. Anzeigedauer pro Phase
    val syncProgress: StateFlow<SyncProgress> = SyncStateManager.syncProgress
        .withMinPhaseDuration(Constants.BANNER_PHASE_MIN_MS)
        .stateIn(viewModelScope, SharingStarted.Eagerly, SyncProgress.IDLE)

    /**
     * 🔧 v1.10.0: Ensures each active sync phase (PREPARING/UPLOADING/DOWNLOADING/
     * IMPORTING_MARKDOWN) is displayed for at least [minMs] milliseconds.
     * Phase transitions IDLE→active and active→IDLE are not delayed, so the
     * banner still appears and disappears quickly.
     */
    @Suppress("NestedBlockDepth") // inherently nested: flow → collect → if
    private fun Flow<SyncProgress>.withMinPhaseDuration(minMs: Long): Flow<SyncProgress> = flow {
        var lastEmitTime = 0L
        var lastPhase = dev.dettmer.simplenotes.sync.SyncPhase.IDLE
        collect { value ->
            if (
                value.phase != dev.dettmer.simplenotes.sync.SyncPhase.IDLE &&
                lastPhase != dev.dettmer.simplenotes.sync.SyncPhase.IDLE &&
                value.phase != lastPhase
            ) {
                val elapsed = System.currentTimeMillis() - lastEmitTime
                val remaining = minMs - elapsed
                if (remaining > 0) delay(remaining)
            }
            emit(value)
            lastEmitTime = System.currentTimeMillis()
            lastPhase = value.phase
        }
    }

    // Intern: SyncState für PullToRefresh-Indikator
    private val _syncState = MutableStateFlow(SyncStateManager.SyncState.IDLE)
    val syncState: StateFlow<SyncStateManager.SyncState> = _syncState.asStateFlow()

    // ═══════════════════════════════════════════════════════════════════════
    // UI Events
    // ═══════════════════════════════════════════════════════════════════════

    private val _showToast = MutableSharedFlow<String>()
    val showToast: SharedFlow<String> = _showToast.asSharedFlow()

    private val _showDeleteDialog = MutableSharedFlow<DeleteDialogData>()
    val showDeleteDialog: SharedFlow<DeleteDialogData> = _showDeleteDialog.asSharedFlow()

    private val _showSnackbar = MutableSharedFlow<SnackbarData>()
    val showSnackbar: SharedFlow<SnackbarData> = _showSnackbar.asSharedFlow()

    // Phase 3: Scroll-to-top when new note is created
    private val _scrollToTop = MutableStateFlow(false)
    val scrollToTop: StateFlow<Boolean> = _scrollToTop.asStateFlow()

    // Track first note ID to detect new notes
    private var previousFirstNoteId: String? = null

    // ═══════════════════════════════════════════════════════════════════════
    // Data Classes
    // ═══════════════════════════════════════════════════════════════════════

    data class DeleteDialogData(val note: Note, val originalList: List<Note>)

    data class SnackbarData(val message: String, val actionLabel: String, val onAction: () -> Unit)

    // ═══════════════════════════════════════════════════════════════════════
    // Initialization
    // ═══════════════════════════════════════════════════════════════════════

    init {
        // v1.5.0 Performance: Load notes asynchronously to avoid blocking UI
        viewModelScope.launch(ioDispatcher) {
            loadNotesAsync()
            _isReady.value = true
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Notes Actions
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Load notes asynchronously on IO dispatcher
     * This prevents UI blocking during app startup
     */
    private suspend fun loadNotesAsync() {
        val allNotes = storage.loadAllNotes()
        val pendingIds = _pendingDeletions.value
        val filteredNotes = allNotes.filter { it.id !in pendingIds }

        withContext(Dispatchers.Main) {
            // Phase 3: Detect if a new note was added at the top
            val newFirstNoteId = filteredNotes.firstOrNull()?.id
            if (newFirstNoteId != null &&
                previousFirstNoteId != null &&
                newFirstNoteId != previousFirstNoteId
            ) {
                // New note at top → trigger scroll
                _scrollToTop.value = true
                Logger.d(TAG, "📜 New note detected at top, triggering scroll-to-top")
            }
            previousFirstNoteId = newFirstNoteId

            _notes.value = filteredNotes
        }
    }

    /**
     * Public loadNotes - delegates to async version
     */
    fun loadNotes() {
        viewModelScope.launch(ioDispatcher) {
            loadNotesAsync()
        }
    }

    /**
     * Reset scroll-to-top flag after scroll completed
     */
    fun resetScrollToTop() {
        _scrollToTop.value = false
    }

    /**
     * Force scroll to top (e.g., after returning from editor)
     */
    fun scrollToTop() {
        _scrollToTop.value = true
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🆕 v1.9.0 (F13): Scroll-to-top after manual sync completion
    // ═══════════════════════════════════════════════════════════════════════

    private val _syncCompletedScrollToTop = MutableStateFlow(false)
    val syncCompletedScrollToTop: StateFlow<Boolean> = _syncCompletedScrollToTop.asStateFlow()

    /**
     * 🆕 v1.9.0 (F13): Reset the sync-scroll flag after the UI has scrolled.
     */
    fun resetSyncCompletedScrollToTop() {
        _syncCompletedScrollToTop.value = false
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Multi-Select Actions (v1.5.0)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Toggle selection of a note
     */
    fun toggleNoteSelection(noteId: String) {
        _selectedNotes.update { if (noteId in it) it - noteId else it + noteId }
    }

    /**
     * Start selection mode with initial note
     */
    fun startSelectionMode(noteId: String) {
        _selectedNotes.value = setOf(noteId)
    }

    /**
     * Select all notes
     */
    fun selectAllNotes() {
        _selectedNotes.value = _notes.value.map { it.id }.toSet()
    }

    /**
     * Clear selection and exit selection mode
     */
    fun clearSelection() {
        _selectedNotes.value = emptySet()
    }

    /**
     * Get count of selected notes
     */
    fun getSelectedCount(): Int = _selectedNotes.value.size

    /**
     * Delete all selected notes
     */
    fun deleteSelectedNotes(deleteFromServer: Boolean) {
        val selectedIds = _selectedNotes.value.toList()
        val selectedNotes = _notes.value.filter { it.id in selectedIds }

        if (selectedNotes.isEmpty()) return

        _pendingDeletions.update { it + selectedIds.toSet() }

        val count = selectedNotes.size
        val message = if (deleteFromServer) {
            getQuantityString(R.plurals.snackbar_notes_deleted_server, count, count)
        } else {
            getQuantityString(R.plurals.snackbar_notes_deleted_local, count, count)
        }

        viewModelScope.launch {
            withContext(ioDispatcher) {
                selectedNotes.forEach { note -> storage.deleteNote(note.id) }
            }
            clearSelection()
            loadNotes()

            _showSnackbar.emit(
                SnackbarData(
                    message = message,
                    actionLabel = getString(R.string.snackbar_undo),
                    onAction = { undoDeleteMultiple(selectedNotes) }
                )
            )

            if (deleteFromServer) {
                kotlinx.coroutines.delay(SNACKBAR_UNDO_DELAY_MS)
                val idsToDelete = selectedIds.filter { it in _pendingDeletions.value }
                if (idsToDelete.isNotEmpty()) {
                    attemptServerDeletion(idsToDelete)
                }
            } else {
                selectedIds.forEach { noteId -> finalizeDeletion(noteId) }
            }
        }
    }

    /**
     * Undo deletion of multiple notes
     */
    private fun undoDeleteMultiple(notes: List<Note>) {
        _pendingDeletions.update { it - notes.map { note -> note.id }.toSet() }

        viewModelScope.launch {
            withContext(ioDispatcher) {
                notes.forEach { note -> storage.saveNote(note) }
            }
            loadNotes()
        }
    }

    /**
     * Called when user long-presses a note to delete
     * Shows dialog for delete confirmation (replaces swipe-to-delete for performance)
     */
    fun onNoteLongPressDelete(note: Note) {
        val alwaysDeleteFromServer = prefs.getBoolean(Constants.KEY_ALWAYS_DELETE_FROM_SERVER, false)

        // Store original list for potential restore
        val originalList = _notes.value.toList()

        if (alwaysDeleteFromServer) {
            // Auto-delete without dialog
            deleteNoteConfirmed(note, deleteFromServer = true)
        } else {
            // Show dialog - don't remove from UI yet (user can cancel)
            viewModelScope.launch {
                _showDeleteDialog.emit(DeleteDialogData(note, originalList))
            }
        }
    }

    /**
     * Called when user swipes to delete a note (legacy - kept for compatibility)
     * Shows dialog if "always delete from server" is not enabled
     */
    fun onNoteSwipedToDelete(note: Note) {
        onNoteLongPressDelete(note) // Delegate to long-press handler
    }

    /**
     * Restore note after swipe (user cancelled dialog)
     */
    fun restoreNoteAfterSwipe(originalList: List<Note>) {
        _notes.value = originalList
    }

    /**
     * Confirm note deletion (from dialog or auto-delete)
     */
    fun deleteNoteConfirmed(note: Note, deleteFromServer: Boolean) {
        _pendingDeletions.update { it + note.id }

        val message = if (deleteFromServer) {
            getString(R.string.snackbar_note_deleted_server, note.title)
        } else {
            getString(R.string.snackbar_note_deleted_local, note.title)
        }

        viewModelScope.launch {
            withContext(ioDispatcher) {
                storage.deleteNote(note.id)
            }
            loadNotes()

            _showSnackbar.emit(
                SnackbarData(
                    message = message,
                    actionLabel = getString(R.string.snackbar_undo),
                    onAction = { undoDelete(note) }
                )
            )

            if (deleteFromServer) {
                kotlinx.coroutines.delay(SNACKBAR_UNDO_DELAY_MS)
                if (note.id in _pendingDeletions.value) {
                    attemptServerDeletion(listOf(note.id))
                }
            } else {
                finalizeDeletion(note.id)
            }
        }
    }

    /**
     * 🆕 v1.10.0-P2: Called when a note was deleted from the editor.
     * Loads the note from storage (not yet deleted) then delegates to
     * [deleteNoteConfirmed] which shows the undo snackbar.
     */
    fun deleteNoteFromEditor(noteId: String, deleteFromServer: Boolean) {
        viewModelScope.launch {
            val note = withContext(ioDispatcher) { storage.loadNote(noteId) } ?: return@launch
            deleteNoteConfirmed(note, deleteFromServer)
        }
    }

    /**
     * Undo note deletion
     */
    fun undoDelete(note: Note) {
        _pendingDeletions.update { it - note.id }

        viewModelScope.launch {
            withContext(ioDispatcher) {
                storage.saveNote(note)
            }
            loadNotes()
        }
    }

    /**
     * Attempts to delete notes from the server.
     * If the server is not reachable, queues the deletions for the next sync.
     */
    private suspend fun attemptServerDeletion(noteIds: List<String>) {
        val webdavService = WebDavSyncService(getApplication())
        val isReachable = try {
            withContext(ioDispatcher) { webdavService.isServerReachable() }
        } catch (_: Exception) {
            false
        }
        if (!isReachable) {
            // Queue for next sync — server not reachable right now
            pendingServerDeletions.add(noteIds)
            noteIds.forEach { finalizeDeletion(it) }
            SyncStateManager.showInfo(getString(R.string.snackbar_delete_queued_for_sync))
            return
        }
        // Server reachable → delete immediately
        if (noteIds.size == 1) {
            deleteNoteFromServer(noteIds[0])
        } else {
            deleteMultipleNotesFromServer(noteIds)
        }
    }

    /**
     * Actually delete note from server after snackbar dismissed
     */
    fun deleteNoteFromServer(noteId: String) {
        viewModelScope.launch {
            try {
                val webdavService = WebDavSyncService(getApplication())
                val success = withContext(ioDispatcher) {
                    webdavService.deleteNoteFromServer(noteId)
                }

                if (success) {
                    // 🆕 v1.8.1 (IMPL_12): Toast → Banner INFO
                    SyncStateManager.showInfo(getString(R.string.snackbar_deleted_from_server))
                } else {
                    SyncStateManager.showError(getString(R.string.snackbar_server_delete_failed))
                }
            } catch (e: Exception) {
                SyncStateManager.showError(getString(R.string.snackbar_server_error, e.message.orEmpty()))
            } finally {
                // Remove from pending deletions
                _pendingDeletions.update { it - noteId }
            }
        }
    }

    /**
     * Delete multiple notes from server with aggregated toast
     * Shows single toast at the end instead of one per note
     */
    private fun deleteMultipleNotesFromServer(noteIds: List<String>) {
        viewModelScope.launch {
            val webdavService = WebDavSyncService(getApplication())
            var successCount = 0
            var failCount = 0
            val total = noteIds.size

            // 🆕 v1.10.0-P2: Show progress banner for server deletion (only for 2+)
            if (total > 1) {
                SyncStateManager.updateProgress(
                    phase = SyncPhase.DELETING,
                    current = 0,
                    total = total,
                    currentFileName = null
                )
            }

            noteIds.forEach { noteId ->
                try {
                    // 🆕 v1.10.0-P2: Increment progress counter before each deletion
                    if (total > 1) {
                        SyncStateManager.incrementProgress(currentFileName = null)
                    }

                    val success = withContext(ioDispatcher) {
                        webdavService.deleteNoteFromServer(noteId)
                    }
                    if (success) successCount++ else failCount++
                } catch (e: Exception) {
                    Logger.w(TAG, "Failed to delete note $noteId from server: ${e.message}")
                    failCount++
                } finally {
                    _pendingDeletions.update { it - noteId }
                }
            }

            // 🆕 v1.8.1 (IMPL_12): Toast → Banner INFO/ERROR
            val message = when {
                failCount == 0 -> getQuantityString(
                    R.plurals.snackbar_notes_deleted_from_server,
                    successCount,
                    successCount
                )
                successCount == 0 -> getString(R.string.snackbar_server_delete_failed)
                else -> getString(
                    R.string.snackbar_notes_deleted_from_server_partial,
                    successCount,
                    successCount + failCount
                )
            }
            if (failCount > 0) {
                SyncStateManager.showError(message)
            } else {
                SyncStateManager.showInfo(message)
            }
        }
    }

    /**
     * Finalize deletion (remove from pending set)
     */
    fun finalizeDeletion(noteId: String) {
        _pendingDeletions.update { it - noteId }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Sync Actions
    // ═══════════════════════════════════════════════════════════════════════

    fun updateSyncState(status: SyncStateManager.SyncStatus) {
        _syncState.value = status.state
    }

    /**
     * Trigger manual sync (from toolbar button or pull-to-refresh)
     * v1.7.0: Uses central canSync() gate for WiFi-only check
     * v1.8.0: Banner erscheint sofort beim Klick (PREPARING-Phase)
     */
    fun triggerManualSync(source: String = "manual") {
        // 🆕 v1.7.0: Zentrale Sync-Gate Prüfung (inkl. WiFi-Only, Offline Mode, Server Config)
        val syncService = WebDavSyncService(getApplication())
        val gateResult = syncService.canSync()
        if (!gateResult.canSync) {
            if (gateResult.isBlockedByWifiOnly) {
                Logger.d(TAG, "⏭️ $source Sync blocked: WiFi-only mode, not on WiFi")
                SyncStateManager.markError(getString(R.string.sync_wifi_only_error))
            } else {
                Logger.d(TAG, "⏭️ $source Sync blocked: ${gateResult.blockReason ?: "offline/no server"}")
            }
            return
        }

        // 🆕 v1.8.1 (IMPL_08): Globalen Cooldown markieren (verhindert Auto-Sync direkt danach)
        // Manueller Sync prüft NICHT den globalen Cooldown (User will explizit synchronisieren)
        val prefs = getApplication<android.app.Application>().getSharedPreferences(
            Constants.PREFS_NAME,
            android.content.Context.MODE_PRIVATE
        )

        // 🆕 v1.7.0: Feedback wenn Sync bereits läuft
        // 🆕 v1.8.0: tryStartSync setzt sofort PREPARING → Banner erscheint instant
        if (!SyncStateManager.tryStartSync(source)) {
            if (SyncStateManager.isSyncing) {
                // 🛡️ v1.8.2 (IMPL_24): Wenn ein Silent-Sync läuft, einfach sichtbar machen
                // statt den User mit "already in progress" abzulehnen
                if (SyncStateManager.promoteToVisible()) {
                    Logger.d(TAG, "📢 $source: Promoted silent sync to visible")
                    // Kein return — User sieht jetzt das Banner des laufenden Syncs
                } else {
                    Logger.d(TAG, "⏭️ $source Sync blocked: Another sync in progress")
                    viewModelScope.launch {
                        _showSnackbar.emit(
                            SnackbarData(
                                message = getString(R.string.sync_already_running),
                                actionLabel = "",
                                onAction = {}
                            )
                        )
                    }
                }
            }
            return
        }

        // 🆕 v1.8.1 (IMPL_08): Globalen Cooldown markieren (nach tryStartSync, vor Launch)
        SyncStateManager.markGlobalSyncStarted(prefs)

        viewModelScope.launch {
            try {
                // Check for unsynced changes (Banner zeigt bereits PREPARING)
                if (!syncService.hasUnsyncedChanges()) {
                    Logger.d(TAG, "⏭️ $source Sync: No unsynced changes")
                    SyncStateManager.markCompleted(getString(R.string.toast_already_synced))
                    loadNotes()
                    // 🆕 v1.9.0 (F13): Scroll to top even for "already synced" on manual trigger
                    _syncCompletedScrollToTop.value = true
                    return@launch
                }

                // Check server reachability
                val isReachable = withContext(ioDispatcher) {
                    syncService.isServerReachable()
                }

                if (!isReachable) {
                    Logger.d(TAG, "⏭️ $source Sync: Server not reachable")
                    SyncStateManager.markError(getString(R.string.snackbar_server_unreachable))
                    return@launch
                }

                // Perform sync
                val result = withContext(ioDispatcher) {
                    syncService.syncNotes()
                }

                if (result.isSuccess) {
                    // 🆕 v1.8.0 (IMPL_022): Erweiterte Banner-Nachricht mit Löschungen
                    val bannerMessage = buildString {
                        if (result.syncedCount > 0) {
                            append(getString(R.string.toast_sync_success, result.syncedCount))
                        }
                        if (result.deletedOnServerCount > 0) {
                            if (isNotEmpty()) append(" · ")
                            append(getString(R.string.sync_deleted_on_server_count, result.deletedOnServerCount))
                        }
                        if (isEmpty()) {
                            append(getString(R.string.snackbar_nothing_to_sync))
                        }
                    }
                    SyncStateManager.markCompleted(bannerMessage)
                    loadNotes()
                    // 🆕 v1.9.0 (F13): Scroll to top after manual sync with changes
                    if (result.syncedCount > 0 || result.deletedOnServerCount > 0) {
                        _syncCompletedScrollToTop.value = true
                    }
                } else {
                    SyncStateManager.markError(result.errorMessage)
                }
            } catch (e: Exception) {
                SyncStateManager.markError(e.message)
            }
        }
    }

    /**
     * Trigger auto-sync (onResume)
     * Only runs if server is configured and interval has passed
     * v1.5.0: Silent-Sync - kein Banner während des Syncs, Fehler werden trotzdem angezeigt
     * v1.6.0: Configurable trigger - checks KEY_SYNC_TRIGGER_ON_RESUME
     * v1.7.0: Uses central canSync() gate for WiFi-only check
     */
    fun triggerAutoSync(source: String = "auto") {
        // 🌟 v1.6.0: Check if onResume trigger is enabled
        if (!prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_ON_RESUME, Constants.DEFAULT_TRIGGER_ON_RESUME)) {
            Logger.d(TAG, "⏭️ onResume sync disabled - skipping")
            return
        }

        // 🆕 v1.8.1 (IMPL_08): Globaler Sync-Cooldown (alle Trigger teilen sich diesen)
        if (!SyncStateManager.canSyncGlobally(prefs)) {
            return
        }

        // Throttling check (eigener 60s-Cooldown für onResume)
        if (!canTriggerAutoSync()) {
            return
        }

        // 🆕 v1.7.0: Zentrale Sync-Gate Prüfung (inkl. WiFi-Only, Offline Mode, Server Config)
        val syncService = WebDavSyncService(getApplication())
        val gateResult = syncService.canSync()
        if (!gateResult.canSync) {
            if (gateResult.isBlockedByWifiOnly) {
                Logger.d(TAG, "⏭️ Auto-sync ($source) blocked: WiFi-only mode, not on WiFi")
            } else {
                Logger.d(TAG, "⏭️ Auto-sync ($source) blocked: ${gateResult.blockReason ?: "offline/no server"}")
            }
            return
        }

        // v1.5.0: silent=true → kein Banner bei Auto-Sync
        // 🆕 v1.8.0: tryStartSync mit silent=true → SyncProgress.silent=true → Banner unsichtbar
        if (!SyncStateManager.tryStartSync("auto-$source", silent = true)) {
            Logger.d(TAG, "⏭️ Auto-sync ($source): Another sync already in progress")
            return
        }

        Logger.d(TAG, "🔄 Auto-sync triggered ($source)")

        // Update last sync timestamp (in-memory only — resets on process restart
        // so first onResume after cold start always triggers)
        lastAutoSyncTime = System.currentTimeMillis()

        // 🆕 v1.8.1 (IMPL_08): Globalen Sync-Cooldown markieren
        SyncStateManager.markGlobalSyncStarted(prefs)

        viewModelScope.launch {
            try {
                // Check for unsynced changes
                if (!syncService.hasUnsyncedChanges()) {
                    Logger.d(TAG, "⏭️ Auto-sync ($source): No unsynced changes - skipping")
                    SyncStateManager.reset() // Silent → geht direkt auf IDLE
                    return@launch
                }

                // Check server reachability
                val isReachable = withContext(ioDispatcher) {
                    syncService.isServerReachable()
                }

                if (!isReachable) {
                    Logger.d(TAG, "⏭️ Auto-sync ($source): Server not reachable - skipping silently")
                    SyncStateManager.reset() // Silent → kein Error-Banner
                    return@launch
                }

                // Perform sync
                val result = withContext(ioDispatcher) {
                    syncService.syncNotes()
                }

                if (result.isSuccess && result.syncedCount > 0) {
                    Logger.d(TAG, "✅ Auto-sync successful ($source): ${result.syncedCount} notes")
                    // 🆕 v1.8.1 (IMPL_11): Kein Toast bei Silent-Sync
                    // Das Banner-System respektiert silent=true korrekt (markCompleted → IDLE)
                    // Toast wurde fälschlicherweise trotzdem angezeigt
                    SyncStateManager.markCompleted(getString(R.string.toast_sync_success, result.syncedCount))
                    loadNotes()
                } else if (result.isSuccess) {
                    Logger.d(TAG, "ℹ️ Auto-sync ($source): No changes")
                    SyncStateManager.markCompleted() // Silent → geht direkt auf IDLE
                } else {
                    Logger.e(TAG, "❌ Auto-sync failed ($source): ${result.errorMessage}")
                    // Fehler werden IMMER angezeigt (auch bei Silent-Sync)
                    SyncStateManager.markError(result.errorMessage)
                }
            } catch (e: Exception) {
                Logger.e(TAG, "💥 Auto-sync exception ($source): ${e.message}")
                SyncStateManager.markError(e.message)
            }
        }
    }

    // In-memory throttle for onResume sync (not persisted — cold start always syncs)
    private var lastAutoSyncTime: Long = 0L

    private fun canTriggerAutoSync(): Boolean {
        val now = System.currentTimeMillis()
        val timeSinceLastSync = now - lastAutoSyncTime

        if (timeSinceLastSync < MIN_AUTO_SYNC_INTERVAL_MS) {
            val remainingSeconds = (MIN_AUTO_SYNC_INTERVAL_MS - timeSinceLastSync) / 1000
            Logger.d(TAG, "⏳ Auto-sync throttled - wait ${remainingSeconds}s")
            return false
        }

        return true
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔀 v1.8.0: Sortierung
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * 🆕 v1.9.0 (F06): Filtert Notizen nach NoteType.
     */
    private fun filterNotes(notes: List<Note>, filter: NoteFilter): List<Note> {
        return when (filter) {
            NoteFilter.ALL -> notes
            NoteFilter.TEXT_ONLY -> notes.filter { it.noteType == NoteType.TEXT }
            NoteFilter.CHECKLIST_ONLY -> notes.filter { it.noteType == NoteType.CHECKLIST }
        }
    }

    /**
     * 🆕 v1.9.0 (F10): Filters notes by search query across title and content.
     * Empty query returns all notes unchanged.
     * Checklist notes are searched by joining all item texts.
     */
    private fun searchNotes(notes: List<Note>, query: String): List<Note> {
        if (query.isBlank()) return notes
        val lowerQuery = query.trim().lowercase()
        return notes.filter { note ->
            note.title.lowercase().contains(lowerQuery) ||
                note.content.lowercase().contains(lowerQuery) ||
                note.checklistItems?.any { item ->
                    item.text.lowercase().contains(lowerQuery)
                } == true
        }
    }

    /**
     * 🔀 v1.8.0: Sortiert Notizen nach gewählter Option und Richtung.
     */
    private fun sortNotes(notes: List<Note>, option: SortOption, direction: SortDirection): List<Note> {
        val comparator: Comparator<Note> = when (option) {
            SortOption.UPDATED_AT -> compareBy { it.updatedAt }
            SortOption.CREATED_AT -> compareBy { it.createdAt }
            SortOption.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            SortOption.NOTE_TYPE -> compareBy<Note> { it.noteType.ordinal }
                .thenByDescending { it.updatedAt } // Sekundär: Datum innerhalb gleicher Typen
        }

        return when (direction) {
            SortDirection.ASCENDING -> notes.sortedWith(comparator)
            SortDirection.DESCENDING -> notes.sortedWith(comparator.reversed())
        }
    }

    /**
     * 🔀 v1.8.0: Setzt die Sortieroption und speichert in SharedPreferences.
     */
    fun setSortOption(option: SortOption) {
        _sortOption.value = option
        prefs.edit { putString(Constants.KEY_SORT_OPTION, option.prefsValue) }
        Logger.d(TAG, "🔀 Sort option changed to: ${option.prefsValue}")
    }

    /**
     * 🔀 v1.8.0: Setzt die Sortierrichtung und speichert in SharedPreferences.
     */
    fun setSortDirection(direction: SortDirection) {
        _sortDirection.value = direction
        prefs.edit { putString(Constants.KEY_SORT_DIRECTION, direction.prefsValue) }
        Logger.d(TAG, "🔀 Sort direction changed to: ${direction.prefsValue}")
    }

    /**
     * 🆕 v1.9.0 (F06): Setzt den Notiz-Filter und speichert in SharedPreferences.
     */
    fun setNoteFilter(filter: NoteFilter) {
        _noteFilter.value = filter
        prefs.edit { putString(Constants.KEY_NOTE_FILTER, filter.prefsValue) }
        Logger.d(TAG, "🔍 Note filter changed to: ${filter.prefsValue}")
    }

    /**
     * 🆕 v1.9.0 (F10): Setzt den Suchbegriff (session-only, nicht persistent).
     */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        Logger.d(TAG, "🔎 Search query changed to: \"$query\"")
    }

    /**
     * 🔀 v1.8.0: Toggelt die Sortierrichtung.
     */
    fun toggleSortDirection() {
        val newDirection = _sortDirection.value.toggle()
        setSortDirection(newDirection)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private fun getString(resId: Int): String = getApplication<android.app.Application>().getString(resId)

    private fun getString(resId: Int, vararg formatArgs: Any): String =
        getApplication<android.app.Application>().getString(resId, *formatArgs)

    private fun getQuantityString(resId: Int, quantity: Int, vararg formatArgs: Any): String =
        getApplication<android.app.Application>().resources.getQuantityString(resId, quantity, *formatArgs)

    fun isServerConfigured(): Boolean {
        // 🌟 v1.6.0: Use reactive offline mode state
        if (_isOfflineMode.value) {
            return false
        }
        val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null)
        return !serverUrl.isNullOrEmpty() && serverUrl != "http://" && serverUrl != "https://"
    }

    /**
     * 🌟 v1.6.0: Check if server has a configured URL (ignores offline mode)
     * Used for determining if sync would be available when offline mode is disabled
     */
    fun hasServerConfig(): Boolean {
        val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null)
        return !serverUrl.isNullOrEmpty() && serverUrl != "http://" && serverUrl != "https://"
    }
}
