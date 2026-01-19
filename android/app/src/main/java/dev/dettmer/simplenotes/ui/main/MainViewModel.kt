package dev.dettmer.simplenotes.ui.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.SyncStateManager
import dev.dettmer.simplenotes.sync.WebDavSyncService
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for MainActivity Compose
 * v1.5.0: Jetpack Compose MainActivity Redesign
 * 
 * Manages notes list, sync state, and deletion with undo.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "MainViewModel"
        private const val MIN_AUTO_SYNC_INTERVAL_MS = 60_000L // 1 Minute
        private const val PREF_LAST_AUTO_SYNC_TIME = "last_auto_sync_timestamp"
    }
    
    private val storage = NotesStorage(application)
    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    
    // ═══════════════════════════════════════════════════════════════════════
    // Notes State
    // ═══════════════════════════════════════════════════════════════════════
    
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()
    
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
    // Sync State (derived from SyncStateManager)
    // ═══════════════════════════════════════════════════════════════════════
    
    private val _syncState = MutableStateFlow(SyncStateManager.SyncState.IDLE)
    val syncState: StateFlow<SyncStateManager.SyncState> = _syncState.asStateFlow()
    
    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()
    
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
    
    data class DeleteDialogData(
        val note: Note,
        val originalList: List<Note>
    )
    
    data class SnackbarData(
        val message: String,
        val actionLabel: String,
        val onAction: () -> Unit
    )
    
    // ═══════════════════════════════════════════════════════════════════════
    // Initialization
    // ═══════════════════════════════════════════════════════════════════════
    
    init {
        // v1.5.0 Performance: Load notes asynchronously to avoid blocking UI
        viewModelScope.launch(Dispatchers.IO) {
            loadNotesAsync()
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
                newFirstNoteId != previousFirstNoteId) {
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
        viewModelScope.launch(Dispatchers.IO) {
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
    // Multi-Select Actions (v1.5.0)
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Toggle selection of a note
     */
    fun toggleNoteSelection(noteId: String) {
        _selectedNotes.value = if (noteId in _selectedNotes.value) {
            _selectedNotes.value - noteId
        } else {
            _selectedNotes.value + noteId
        }
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
        
        // Add to pending deletions
        _pendingDeletions.value = _pendingDeletions.value + selectedIds.toSet()
        
        // Delete from storage
        selectedNotes.forEach { note ->
            storage.deleteNote(note.id)
        }
        
        // Clear selection
        clearSelection()
        
        // Reload notes
        loadNotes()
        
        // Show snackbar with undo
        val count = selectedNotes.size
        val message = if (deleteFromServer) {
            getString(R.string.snackbar_notes_deleted_server, count)
        } else {
            getString(R.string.snackbar_notes_deleted_local, count)
        }
        
        viewModelScope.launch {
            _showSnackbar.emit(SnackbarData(
                message = message,
                actionLabel = getString(R.string.snackbar_undo),
                onAction = {
                    undoDeleteMultiple(selectedNotes)
                }
            ))
            
            // If delete from server, actually delete after a short delay
            // (to allow undo action before server deletion)
            if (deleteFromServer) {
                kotlinx.coroutines.delay(3500) // Snackbar shows for ~3s
                // Only delete if not restored (check if still in pending)
                val idsToDelete = selectedIds.filter { it in _pendingDeletions.value }
                if (idsToDelete.isNotEmpty()) {
                    deleteMultipleNotesFromServer(idsToDelete)
                }
            } else {
                // Just finalize local deletion
                selectedIds.forEach { noteId ->
                    finalizeDeletion(noteId)
                }
            }
        }
    }
    
    /**
     * Undo deletion of multiple notes
     */
    private fun undoDeleteMultiple(notes: List<Note>) {
        // Remove from pending deletions
        _pendingDeletions.value = _pendingDeletions.value - notes.map { it.id }.toSet()
        
        // Restore to storage
        notes.forEach { note ->
            storage.saveNote(note)
        }
        
        // Reload notes
        loadNotes()
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
        onNoteLongPressDelete(note)  // Delegate to long-press handler
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
        // Add to pending deletions
        _pendingDeletions.value = _pendingDeletions.value + note.id
        
        // Delete from storage
        storage.deleteNote(note.id)
        
        // Reload notes
        loadNotes()
        
        // Show snackbar with undo
        val message = if (deleteFromServer) {
            getString(R.string.snackbar_note_deleted_server, note.title)
        } else {
            getString(R.string.snackbar_note_deleted_local, note.title)
        }
        
        viewModelScope.launch {
            _showSnackbar.emit(SnackbarData(
                message = message,
                actionLabel = getString(R.string.snackbar_undo),
                onAction = {
                    undoDelete(note)
                }
            ))
            
            // If delete from server, actually delete after snackbar timeout
            if (deleteFromServer) {
                kotlinx.coroutines.delay(3500) // Snackbar shows for ~3s
                // Only delete if not restored (check if still in pending)
                if (note.id in _pendingDeletions.value) {
                    deleteNoteFromServer(note.id)
                }
            } else {
                // Just finalize local deletion
                finalizeDeletion(note.id)
            }
        }
    }
    
    /**
     * Undo note deletion
     */
    fun undoDelete(note: Note) {
        // Remove from pending deletions
        _pendingDeletions.value = _pendingDeletions.value - note.id
        
        // Restore to storage
        storage.saveNote(note)
        
        // Reload notes
        loadNotes()
    }
    
    /**
     * Actually delete note from server after snackbar dismissed
     */
    fun deleteNoteFromServer(noteId: String) {
        viewModelScope.launch {
            try {
                val webdavService = WebDavSyncService(getApplication())
                val success = withContext(Dispatchers.IO) {
                    webdavService.deleteNoteFromServer(noteId)
                }
                
                if (success) {
                    _showToast.emit(getString(R.string.snackbar_deleted_from_server))
                } else {
                    _showToast.emit(getString(R.string.snackbar_server_delete_failed))
                }
            } catch (e: Exception) {
                _showToast.emit(getString(R.string.snackbar_server_error, e.message ?: ""))
            } finally {
                // Remove from pending deletions
                _pendingDeletions.value = _pendingDeletions.value - noteId
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
            
            noteIds.forEach { noteId ->
                try {
                    val success = withContext(Dispatchers.IO) {
                        webdavService.deleteNoteFromServer(noteId)
                    }
                    if (success) successCount++ else failCount++
                } catch (e: Exception) {
                    failCount++
                } finally {
                    _pendingDeletions.value = _pendingDeletions.value - noteId
                }
            }
            
            // Show aggregated toast
            val message = when {
                failCount == 0 -> getString(R.string.snackbar_notes_deleted_from_server, successCount)
                successCount == 0 -> getString(R.string.snackbar_server_delete_failed)
                else -> getString(
                    R.string.snackbar_notes_deleted_from_server_partial,
                    successCount,
                    successCount + failCount
                )
            }
            _showToast.emit(message)
        }
    }
    
    /**
     * Finalize deletion (remove from pending set)
     */
    fun finalizeDeletion(noteId: String) {
        _pendingDeletions.value = _pendingDeletions.value - noteId
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Sync Actions
    // ═══════════════════════════════════════════════════════════════════════
    
    fun updateSyncState(status: SyncStateManager.SyncStatus) {
        _syncState.value = status.state
        _syncMessage.value = status.message
    }
    
    /**
     * Trigger manual sync (from toolbar button or pull-to-refresh)
     */
    fun triggerManualSync(source: String = "manual") {
        // 🌟 v1.6.0: Block sync in offline mode
        if (prefs.getBoolean(Constants.KEY_OFFLINE_MODE, true)) {
            Logger.d(TAG, "⏭️ $source Sync blocked: Offline mode enabled")
            return
        }
        
        if (!SyncStateManager.tryStartSync(source)) {
            return
        }
        
        viewModelScope.launch {
            try {
                val syncService = WebDavSyncService(getApplication())
                
                // Check for unsynced changes
                if (!syncService.hasUnsyncedChanges()) {
                    Logger.d(TAG, "⏭️ $source Sync: No unsynced changes")
                    SyncStateManager.markCompleted("Bereits synchronisiert")
                    loadNotes()
                    return@launch
                }
                
                // Check server reachability
                val isReachable = withContext(Dispatchers.IO) {
                    syncService.isServerReachable()
                }
                
                if (!isReachable) {
                    Logger.d(TAG, "⏭️ $source Sync: Server not reachable")
                    SyncStateManager.markError(getString(R.string.snackbar_server_unreachable))
                    return@launch
                }
                
                // Perform sync
                val result = withContext(Dispatchers.IO) {
                    syncService.syncNotes()
                }
                
                if (result.isSuccess) {
                    val bannerMessage = if (result.syncedCount > 0) {
                        getString(R.string.toast_sync_success, result.syncedCount)
                    } else {
                        getString(R.string.snackbar_nothing_to_sync)
                    }
                    SyncStateManager.markCompleted(bannerMessage)
                    loadNotes()
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
     */
    fun triggerAutoSync(source: String = "auto") {
        // 🌟 v1.6.0: Check if onResume trigger is enabled
        if (!prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_ON_RESUME, Constants.DEFAULT_TRIGGER_ON_RESUME)) {
            Logger.d(TAG, "⏭️ onResume sync disabled - skipping")
            return
        }
        
        // Throttling check
        if (!canTriggerAutoSync()) {
            return
        }
        
        // Check if server is configured
        val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null)
        if (serverUrl.isNullOrEmpty() || serverUrl == "http://" || serverUrl == "https://") {
            Logger.d(TAG, "⏭️ Offline mode - skipping onResume sync")
            return
        }
        
        // v1.5.0: silent=true - kein Banner bei Auto-Sync, aber Fehler werden trotzdem angezeigt
        if (!SyncStateManager.tryStartSync("auto-$source", silent = true)) {
            Logger.d(TAG, "⏭️ Auto-sync ($source): Another sync already in progress")
            return
        }
        
        Logger.d(TAG, "🔄 Auto-sync triggered ($source)")
        
        // Update last sync timestamp
        prefs.edit().putLong(PREF_LAST_AUTO_SYNC_TIME, System.currentTimeMillis()).apply()
        
        viewModelScope.launch {
            try {
                val syncService = WebDavSyncService(getApplication())
                
                // Check for unsynced changes
                if (!syncService.hasUnsyncedChanges()) {
                    Logger.d(TAG, "⏭️ Auto-sync ($source): No unsynced changes - skipping")
                    SyncStateManager.reset()
                    return@launch
                }
                
                // Check server reachability
                val isReachable = withContext(Dispatchers.IO) {
                    syncService.isServerReachable()
                }
                
                if (!isReachable) {
                    Logger.d(TAG, "⏭️ Auto-sync ($source): Server not reachable - skipping silently")
                    SyncStateManager.reset()
                    return@launch
                }
                
                // Perform sync
                val result = withContext(Dispatchers.IO) {
                    syncService.syncNotes()
                }
                
                if (result.isSuccess && result.syncedCount > 0) {
                    Logger.d(TAG, "✅ Auto-sync successful ($source): ${result.syncedCount} notes")
                    SyncStateManager.markCompleted(getString(R.string.toast_sync_success, result.syncedCount))
                    _showToast.emit(getString(R.string.snackbar_synced_count, result.syncedCount))
                    loadNotes()
                } else if (result.isSuccess) {
                    Logger.d(TAG, "ℹ️ Auto-sync ($source): No changes")
                    SyncStateManager.markCompleted(getString(R.string.snackbar_nothing_to_sync))
                } else {
                    Logger.e(TAG, "❌ Auto-sync failed ($source): ${result.errorMessage}")
                    SyncStateManager.markError(result.errorMessage)
                }
            } catch (e: Exception) {
                Logger.e(TAG, "💥 Auto-sync exception ($source): ${e.message}")
                SyncStateManager.markError(e.message)
            }
        }
    }
    
    private fun canTriggerAutoSync(): Boolean {
        val lastSyncTime = prefs.getLong(PREF_LAST_AUTO_SYNC_TIME, 0)
        val now = System.currentTimeMillis()
        val timeSinceLastSync = now - lastSyncTime
        
        if (timeSinceLastSync < MIN_AUTO_SYNC_INTERVAL_MS) {
            val remainingSeconds = (MIN_AUTO_SYNC_INTERVAL_MS - timeSinceLastSync) / 1000
            Logger.d(TAG, "⏳ Auto-sync throttled - wait ${remainingSeconds}s")
            return false
        }
        
        return true
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════
    
    private fun getString(resId: Int): String = getApplication<android.app.Application>().getString(resId)
    
    private fun getString(resId: Int, vararg formatArgs: Any): String = 
        getApplication<android.app.Application>().getString(resId, *formatArgs)
    
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
