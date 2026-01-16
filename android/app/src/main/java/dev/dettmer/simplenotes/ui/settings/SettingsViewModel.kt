package dev.dettmer.simplenotes.ui.settings

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.backup.BackupManager
import dev.dettmer.simplenotes.backup.RestoreMode
import dev.dettmer.simplenotes.sync.WebDavSyncService
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * ViewModel for Settings screens
 * v1.5.0: Jetpack Compose Settings Redesign
 * 
 * Manages all settings state and actions across the Settings navigation graph.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "SettingsViewModel"
        private const val CONNECTION_TIMEOUT_MS = 3000
    }
    
    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    val backupManager = BackupManager(application)
    
    // ═══════════════════════════════════════════════════════════════════════
    // Server Settings State
    // ═══════════════════════════════════════════════════════════════════════
    
    // v1.5.0 Fix: Initialize URL with protocol prefix if empty
    private val storedUrl = prefs.getString(Constants.KEY_SERVER_URL, "") ?: ""
    private val initialUrl = if (storedUrl.isEmpty()) "http://" else storedUrl
    
    private val _serverUrl = MutableStateFlow(initialUrl)
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()
    
    private val _username = MutableStateFlow(prefs.getString(Constants.KEY_USERNAME, "") ?: "")
    val username: StateFlow<String> = _username.asStateFlow()
    
    private val _password = MutableStateFlow(prefs.getString(Constants.KEY_PASSWORD, "") ?: "")
    val password: StateFlow<String> = _password.asStateFlow()
    
    // v1.5.0 Fix: isHttps based on stored URL (false = HTTP if empty)
    private val _isHttps = MutableStateFlow(storedUrl.startsWith("https://"))
    val isHttps: StateFlow<Boolean> = _isHttps.asStateFlow()
    
    private val _serverStatus = MutableStateFlow<ServerStatus>(ServerStatus.Unknown)
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()
    
    // ═══════════════════════════════════════════════════════════════════════
    // Events (for Activity-level actions like dialogs, intents)
    // ═══════════════════════════════════════════════════════════════════════
    
    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()
    
    // ═══════════════════════════════════════════════════════════════════════
    // Markdown Export Progress State
    // ═══════════════════════════════════════════════════════════════════════
    
    private val _markdownExportProgress = MutableStateFlow<MarkdownExportProgress?>(null)
    val markdownExportProgress: StateFlow<MarkdownExportProgress?> = _markdownExportProgress.asStateFlow()
    
    // ═══════════════════════════════════════════════════════════════════════
    // Sync Settings State
    // ═══════════════════════════════════════════════════════════════════════
    
    private val _autoSyncEnabled = MutableStateFlow(prefs.getBoolean(Constants.KEY_AUTO_SYNC, false))
    val autoSyncEnabled: StateFlow<Boolean> = _autoSyncEnabled.asStateFlow()
    
    private val _syncInterval = MutableStateFlow(
        prefs.getLong(Constants.PREF_SYNC_INTERVAL_MINUTES, Constants.DEFAULT_SYNC_INTERVAL_MINUTES)
    )
    val syncInterval: StateFlow<Long> = _syncInterval.asStateFlow()
    
    // ═══════════════════════════════════════════════════════════════════════
    // Markdown Settings State
    // ═══════════════════════════════════════════════════════════════════════
    
    private val _markdownAutoSync = MutableStateFlow(
        prefs.getBoolean(Constants.KEY_MARKDOWN_EXPORT, false) &&
        prefs.getBoolean(Constants.KEY_MARKDOWN_AUTO_IMPORT, false)
    )
    val markdownAutoSync: StateFlow<Boolean> = _markdownAutoSync.asStateFlow()
    
    // ═══════════════════════════════════════════════════════════════════════
    // Debug Settings State
    // ═══════════════════════════════════════════════════════════════════════
    
    private val _fileLoggingEnabled = MutableStateFlow(
        prefs.getBoolean(Constants.KEY_FILE_LOGGING_ENABLED, false)
    )
    val fileLoggingEnabled: StateFlow<Boolean> = _fileLoggingEnabled.asStateFlow()
    
    // ═══════════════════════════════════════════════════════════════════════
    // UI State
    // ═══════════════════════════════════════════════════════════════════════
    
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
    
    private val _isBackupInProgress = MutableStateFlow(false)
    val isBackupInProgress: StateFlow<Boolean> = _isBackupInProgress.asStateFlow()
    
    private val _showToast = MutableSharedFlow<String>()
    val showToast: SharedFlow<String> = _showToast.asSharedFlow()
    
    // ═══════════════════════════════════════════════════════════════════════
    // Server Settings Actions
    // ═══════════════════════════════════════════════════════════════════════
    
    fun updateServerUrl(url: String) {
        _serverUrl.value = url
        saveServerSettings()
    }
    
    fun updateProtocol(useHttps: Boolean) {
        _isHttps.value = useHttps
        val currentUrl = _serverUrl.value
        
        // v1.5.0 Fix: Automatisch Prefix setzen, auch bei leerem Feld
        val newUrl = if (useHttps) {
            when {
                currentUrl.isEmpty() || currentUrl == "http://" -> "https://"
                currentUrl.startsWith("http://") -> currentUrl.replace("http://", "https://")
                !currentUrl.startsWith("https://") -> "https://$currentUrl"
                else -> currentUrl
            }
        } else {
            when {
                currentUrl.isEmpty() || currentUrl == "https://" -> "http://"
                currentUrl.startsWith("https://") -> currentUrl.replace("https://", "http://")
                !currentUrl.startsWith("http://") -> "http://$currentUrl"
                else -> currentUrl
            }
        }
        _serverUrl.value = newUrl
        saveServerSettings()
    }
    
    fun updateUsername(value: String) {
        _username.value = value
        saveServerSettings()
    }
    
    fun updatePassword(value: String) {
        _password.value = value
        saveServerSettings()
    }
    
    private fun saveServerSettings() {
        prefs.edit().apply {
            putString(Constants.KEY_SERVER_URL, _serverUrl.value)
            putString(Constants.KEY_USERNAME, _username.value)
            putString(Constants.KEY_PASSWORD, _password.value)
            apply()
        }
    }
    
    fun testConnection() {
        viewModelScope.launch {
            _serverStatus.value = ServerStatus.Checking
            try {
                val syncService = WebDavSyncService(getApplication())
                val result = syncService.testConnection()
                _serverStatus.value = if (result.isSuccess) {
                    ServerStatus.Reachable
                } else {
                    ServerStatus.Unreachable(result.errorMessage)
                }
                emitToast(if (result.isSuccess) getString(R.string.toast_connection_success) else getString(R.string.toast_connection_failed, result.errorMessage ?: ""))
            } catch (e: Exception) {
                _serverStatus.value = ServerStatus.Unreachable(e.message)
                emitToast(getString(R.string.toast_error, e.message ?: ""))
            }
        }
    }
    
    fun checkServerStatus() {
        val serverUrl = _serverUrl.value
        // v1.5.0 Fix: URL mit nur Prefix gilt als "nicht konfiguriert"
        if (serverUrl.isEmpty() || serverUrl == "http://" || serverUrl == "https://") {
            _serverStatus.value = ServerStatus.NotConfigured
            return
        }
        
        viewModelScope.launch {
            _serverStatus.value = ServerStatus.Checking
            val isReachable = withContext(Dispatchers.IO) {
                try {
                    val url = URL(serverUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = CONNECTION_TIMEOUT_MS
                    connection.readTimeout = CONNECTION_TIMEOUT_MS
                    val code = connection.responseCode
                    connection.disconnect()
                    code in 200..299 || code == 401
                } catch (e: Exception) {
                    Log.e(TAG, "Server check failed: ${e.message}")
                    false
                }
            }
            _serverStatus.value = if (isReachable) ServerStatus.Reachable else ServerStatus.Unreachable(null)
        }
    }
    
    fun syncNow() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                emitToast(getString(R.string.toast_syncing))
                val syncService = WebDavSyncService(getApplication())
                
                if (!syncService.hasUnsyncedChanges()) {
                    emitToast(getString(R.string.toast_already_synced))
                    return@launch
                }
                
                val result = syncService.syncNotes()
                if (result.isSuccess) {
                    emitToast(getString(R.string.toast_sync_success, result.syncedCount))
                } else {
                    emitToast(getString(R.string.toast_sync_failed, result.errorMessage ?: ""))
                }
            } catch (e: Exception) {
                emitToast(getString(R.string.toast_error, e.message ?: ""))
            } finally {
                _isSyncing.value = false
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Sync Settings Actions
    // ═══════════════════════════════════════════════════════════════════════
    
    fun setAutoSync(enabled: Boolean) {
        _autoSyncEnabled.value = enabled
        prefs.edit().putBoolean(Constants.KEY_AUTO_SYNC, enabled).apply()
        
        viewModelScope.launch {
            if (enabled) {
                // v1.5.0 Fix: Trigger battery optimization check and network monitor restart
                _events.emit(SettingsEvent.RequestBatteryOptimization)
                _events.emit(SettingsEvent.RestartNetworkMonitor)
                emitToast(getString(R.string.toast_auto_sync_enabled))
            } else {
                _events.emit(SettingsEvent.RestartNetworkMonitor)
                emitToast(getString(R.string.toast_auto_sync_disabled))
            }
        }
    }
    
    fun setSyncInterval(minutes: Long) {
        _syncInterval.value = minutes
        prefs.edit().putLong(Constants.PREF_SYNC_INTERVAL_MINUTES, minutes).apply()
        viewModelScope.launch {
            val text = when (minutes) {
                15L -> getString(R.string.toast_sync_interval_15min)
                60L -> getString(R.string.toast_sync_interval_60min)
                else -> getString(R.string.toast_sync_interval_30min)
            }
            emitToast(getString(R.string.toast_sync_interval, text))
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Markdown Settings Actions
    // ═══════════════════════════════════════════════════════════════════════
    
    fun setMarkdownAutoSync(enabled: Boolean) {
        if (enabled) {
            // v1.5.0 Fix: Perform initial export when enabling (like old SettingsActivity)
            viewModelScope.launch {
                try {
                    // Check server configuration first
                    val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, "") ?: ""
                    val username = prefs.getString(Constants.KEY_USERNAME, "") ?: ""
                    val password = prefs.getString(Constants.KEY_PASSWORD, "") ?: ""
                    
                    if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
                        emitToast(getString(R.string.toast_configure_server_first))
                        // Don't enable - revert state
                        return@launch
                    }
                    
                    // Check if there are notes to export
                    val noteStorage = dev.dettmer.simplenotes.storage.NotesStorage(getApplication())
                    val noteCount = noteStorage.loadAllNotes().size
                    
                    if (noteCount > 0) {
                        // Show progress and perform initial export
                        _markdownExportProgress.value = MarkdownExportProgress(0, noteCount)
                        
                        val syncService = WebDavSyncService(getApplication())
                        val exportedCount = withContext(Dispatchers.IO) {
                            syncService.exportAllNotesToMarkdown(
                                serverUrl = serverUrl,
                                username = username,
                                password = password,
                                onProgress = { current, total ->
                                    _markdownExportProgress.value = MarkdownExportProgress(current, total)
                                }
                            )
                        }
                        
                        // Export successful - save settings
                        _markdownAutoSync.value = true
                        prefs.edit()
                            .putBoolean(Constants.KEY_MARKDOWN_EXPORT, true)
                            .putBoolean(Constants.KEY_MARKDOWN_AUTO_IMPORT, true)
                            .apply()
                        
                        _markdownExportProgress.value = MarkdownExportProgress(noteCount, noteCount, isComplete = true)
                        emitToast(getString(R.string.toast_markdown_exported, exportedCount))
                        
                        // Clear progress after short delay
                        kotlinx.coroutines.delay(500)
                        _markdownExportProgress.value = null
                        
                    } else {
                        // No notes - just enable the feature
                        _markdownAutoSync.value = true
                        prefs.edit()
                            .putBoolean(Constants.KEY_MARKDOWN_EXPORT, true)
                            .putBoolean(Constants.KEY_MARKDOWN_AUTO_IMPORT, true)
                            .apply()
                        emitToast(getString(R.string.toast_markdown_enabled))
                    }
                    
                } catch (e: Exception) {
                    _markdownExportProgress.value = null
                    emitToast(getString(R.string.toast_export_failed, e.message ?: ""))
                    // Don't enable on error
                }
            }
        } else {
            // Disable - simple
            _markdownAutoSync.value = false
            prefs.edit()
                .putBoolean(Constants.KEY_MARKDOWN_EXPORT, false)
                .putBoolean(Constants.KEY_MARKDOWN_AUTO_IMPORT, false)
                .apply()
            viewModelScope.launch {
                emitToast(getString(R.string.toast_markdown_disabled))
            }
        }
    }
    
    fun performManualMarkdownSync() {
        viewModelScope.launch {
            try {
                emitToast(getString(R.string.toast_markdown_syncing))
                val syncService = WebDavSyncService(getApplication())
                val result = syncService.manualMarkdownSync()
                emitToast(getString(R.string.toast_markdown_result, result.exportedCount, result.importedCount))
            } catch (e: Exception) {
                emitToast(getString(R.string.toast_error, e.message ?: ""))
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Backup Actions
    // ═══════════════════════════════════════════════════════════════════════
    
    fun createBackup(uri: Uri) {
        viewModelScope.launch {
            _isBackupInProgress.value = true
            try {
                val result = backupManager.createBackup(uri)
                emitToast(if (result.success) getString(R.string.toast_backup_success, result.message ?: "") else getString(R.string.toast_backup_failed, result.error ?: ""))
            } catch (e: Exception) {
                emitToast(getString(R.string.toast_backup_failed, e.message ?: ""))
            } finally {
                _isBackupInProgress.value = false
            }
        }
    }
    
    fun restoreFromFile(uri: Uri, mode: RestoreMode) {
        viewModelScope.launch {
            _isBackupInProgress.value = true
            try {
                val result = backupManager.restoreBackup(uri, mode)
                emitToast(if (result.success) getString(R.string.toast_restore_success, result.importedNotes) else getString(R.string.toast_restore_failed, result.error ?: ""))
            } catch (e: Exception) {
                emitToast(getString(R.string.toast_restore_failed, e.message ?: ""))
            } finally {
                _isBackupInProgress.value = false
            }
        }
    }
    
    fun restoreFromServer(mode: RestoreMode) {
        viewModelScope.launch {
            _isBackupInProgress.value = true
            try {
                emitToast(getString(R.string.restore_progress))
                val syncService = WebDavSyncService(getApplication())
                val result = withContext(Dispatchers.IO) {
                    syncService.restoreFromServer(mode)
                }
                emitToast(if (result.isSuccess) getString(R.string.toast_restore_success, result.restoredCount) else getString(R.string.toast_restore_failed, result.errorMessage ?: ""))
            } catch (e: Exception) {
                emitToast(getString(R.string.toast_error, e.message ?: ""))
            } finally {
                _isBackupInProgress.value = false
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Debug Settings Actions
    // ═══════════════════════════════════════════════════════════════════════
    
    fun setFileLogging(enabled: Boolean) {
        _fileLoggingEnabled.value = enabled
        prefs.edit().putBoolean(Constants.KEY_FILE_LOGGING_ENABLED, enabled).apply()
        Logger.setFileLoggingEnabled(enabled)
        viewModelScope.launch {
            emitToast(if (enabled) getString(R.string.toast_file_logging_enabled) else getString(R.string.toast_file_logging_disabled))
        }
    }
    
    fun clearLogs() {
        viewModelScope.launch {
            try {
                val cleared = Logger.clearLogFile(getApplication())
                emitToast(if (cleared) getString(R.string.toast_logs_deleted) else getString(R.string.toast_logs_deleted))
            } catch (e: Exception) {
                emitToast(getString(R.string.toast_error, e.message ?: ""))
            }
        }
    }
    
    fun getLogFile() = Logger.getLogFile(getApplication())
    
    // ═══════════════════════════════════════════════════════════════════════
    // Helper
    // ═══════════════════════════════════════════════════════════════════════
    
    private fun getString(resId: Int): String = getApplication<android.app.Application>().getString(resId)
    
    private fun getString(resId: Int, vararg formatArgs: Any): String = 
        getApplication<android.app.Application>().getString(resId, *formatArgs)
    
    private suspend fun emitToast(message: String) {
        _showToast.emit(message)
    }
    
    /**
     * Server status states
     */
    sealed class ServerStatus {
        data object Unknown : ServerStatus()
        data object NotConfigured : ServerStatus()
        data object Checking : ServerStatus()
        data object Reachable : ServerStatus()
        data class Unreachable(val error: String?) : ServerStatus()
    }
    
    /**
     * Events for Activity-level actions (dialogs, intents, etc.)
     * v1.5.0: Ported from old SettingsActivity
     */
    sealed class SettingsEvent {
        data object RequestBatteryOptimization : SettingsEvent()
        data object RestartNetworkMonitor : SettingsEvent()
    }
    
    /**
     * Progress state for Markdown export
     * v1.5.0: For initial export progress dialog
     */
    data class MarkdownExportProgress(
        val current: Int,
        val total: Int,
        val isComplete: Boolean = false
    )
}
