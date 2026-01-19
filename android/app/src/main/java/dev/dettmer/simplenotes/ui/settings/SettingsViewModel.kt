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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    
    // 🌟 v1.6.0: Separate host from prefix for better UX
    // isHttps determines the prefix, serverHost is the editable part
    private val _isHttps = MutableStateFlow(storedUrl.startsWith("https://"))
    val isHttps: StateFlow<Boolean> = _isHttps.asStateFlow()
    
    // Extract host part (everything after http:// or https://)
    private fun extractHostFromUrl(url: String): String {
        return when {
            url.startsWith("https://") -> url.removePrefix("https://")
            url.startsWith("http://") -> url.removePrefix("http://")
            else -> url
        }
    }
    
    // 🌟 v1.6.0: Only the host part is editable (without protocol prefix)
    private val _serverHost = MutableStateFlow(extractHostFromUrl(storedUrl))
    val serverHost: StateFlow<String> = _serverHost.asStateFlow()
    
    // 🌟 v1.6.0: Full URL for display purposes (computed from prefix + host)
    val serverUrl: StateFlow<String> = combine(_isHttps, _serverHost) { https, host ->
        val prefix = if (https) "https://" else "http://"
        if (host.isEmpty()) "" else prefix + host
    }.stateIn(viewModelScope, SharingStarted.Eagerly, storedUrl)
    
    private val _username = MutableStateFlow(prefs.getString(Constants.KEY_USERNAME, "") ?: "")
    val username: StateFlow<String> = _username.asStateFlow()
    
    private val _password = MutableStateFlow(prefs.getString(Constants.KEY_PASSWORD, "") ?: "")
    val password: StateFlow<String> = _password.asStateFlow()
    
    private val _serverStatus = MutableStateFlow<ServerStatus>(ServerStatus.Unknown)
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()
    
    // 🌟 v1.6.0: Offline Mode Toggle
    // Default: true for new users (no server), false for existing users (has server config)
    private val _offlineMode = MutableStateFlow(
        if (prefs.contains(Constants.KEY_OFFLINE_MODE)) {
            prefs.getBoolean(Constants.KEY_OFFLINE_MODE, true)
        } else {
            // Migration: auto-detect based on existing server config
            !hasExistingServerConfig()
        }
    )
    val offlineMode: StateFlow<Boolean> = _offlineMode.asStateFlow()
    
    private fun hasExistingServerConfig(): Boolean {
        val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null)
        return !serverUrl.isNullOrEmpty() && 
               serverUrl != "http://" && 
               serverUrl != "https://"
    }
    
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
    
    // 🌟 v1.6.0: Configurable Sync Triggers
    private val _triggerOnSave = MutableStateFlow(
        prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_ON_SAVE, Constants.DEFAULT_TRIGGER_ON_SAVE)
    )
    val triggerOnSave: StateFlow<Boolean> = _triggerOnSave.asStateFlow()
    
    private val _triggerOnResume = MutableStateFlow(
        prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_ON_RESUME, Constants.DEFAULT_TRIGGER_ON_RESUME)
    )
    val triggerOnResume: StateFlow<Boolean> = _triggerOnResume.asStateFlow()
    
    private val _triggerWifiConnect = MutableStateFlow(
        prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_WIFI_CONNECT, Constants.DEFAULT_TRIGGER_WIFI_CONNECT)
    )
    val triggerWifiConnect: StateFlow<Boolean> = _triggerWifiConnect.asStateFlow()
    
    private val _triggerPeriodic = MutableStateFlow(
        prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_PERIODIC, Constants.DEFAULT_TRIGGER_PERIODIC)
    )
    val triggerPeriodic: StateFlow<Boolean> = _triggerPeriodic.asStateFlow()
    
    private val _triggerBoot = MutableStateFlow(
        prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_BOOT, Constants.DEFAULT_TRIGGER_BOOT)
    )
    val triggerBoot: StateFlow<Boolean> = _triggerBoot.asStateFlow()
    
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
    
    /**
     * v1.6.0: Set offline mode on/off
     * When enabled, all network features are disabled
     */
    fun setOfflineMode(enabled: Boolean) {
        _offlineMode.value = enabled
        prefs.edit().putBoolean(Constants.KEY_OFFLINE_MODE, enabled).apply()
        
        if (enabled) {
            _serverStatus.value = ServerStatus.OfflineMode
        } else {
            // Re-check server status when disabling offline mode
            checkServerStatus()
        }
    }
    
    fun updateServerUrl(url: String) {
        // 🌟 v1.6.0: Deprecated - use updateServerHost instead
        // This function is kept for compatibility but now delegates to updateServerHost
        val host = extractHostFromUrl(url)
        updateServerHost(host)
    }
    
    /**
     * 🌟 v1.6.0: Update only the host part of the server URL
     * The protocol prefix is handled separately by updateProtocol()
     */
    fun updateServerHost(host: String) {
        _serverHost.value = host
        saveServerSettings()
    }
    
    fun updateProtocol(useHttps: Boolean) {
        _isHttps.value = useHttps
        // 🌟 v1.6.0: Host stays the same, only prefix changes
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
        // 🌟 v1.6.0: Construct full URL from prefix + host
        val prefix = if (_isHttps.value) "https://" else "http://"
        val fullUrl = if (_serverHost.value.isEmpty()) "" else prefix + _serverHost.value
        
        prefs.edit().apply {
            putString(Constants.KEY_SERVER_URL, fullUrl)
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
                val message = if (result.isSuccess) {
                    getString(R.string.toast_connection_success)
                } else {
                    getString(R.string.toast_connection_failed, result.errorMessage ?: "")
                }
                emitToast(message)
            } catch (e: Exception) {
                _serverStatus.value = ServerStatus.Unreachable(e.message)
                emitToast(getString(R.string.toast_error, e.message ?: ""))
            }
        }
    }
    
    fun checkServerStatus() {
        // 🌟 v1.6.0: Respect offline mode first
        if (_offlineMode.value) {
            _serverStatus.value = ServerStatus.OfflineMode
            return
        }
        
        // 🌟 v1.6.0: Check if host is configured
        val serverHost = _serverHost.value
        if (serverHost.isEmpty()) {
            _serverStatus.value = ServerStatus.NotConfigured
            return
        }
        
        // Construct full URL
        val prefix = if (_isHttps.value) "https://" else "http://"
        val serverUrl = prefix + serverHost
        
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
    
    // 🌟 v1.6.0: Configurable Sync Triggers Setters
    
    fun setTriggerOnSave(enabled: Boolean) {
        _triggerOnSave.value = enabled
        prefs.edit().putBoolean(Constants.KEY_SYNC_TRIGGER_ON_SAVE, enabled).apply()
        Logger.d(TAG, "Trigger onSave: $enabled")
    }
    
    fun setTriggerOnResume(enabled: Boolean) {
        _triggerOnResume.value = enabled
        prefs.edit().putBoolean(Constants.KEY_SYNC_TRIGGER_ON_RESUME, enabled).apply()
        Logger.d(TAG, "Trigger onResume: $enabled")
    }
    
    fun setTriggerWifiConnect(enabled: Boolean) {
        _triggerWifiConnect.value = enabled
        prefs.edit().putBoolean(Constants.KEY_SYNC_TRIGGER_WIFI_CONNECT, enabled).apply()
        viewModelScope.launch {
            _events.emit(SettingsEvent.RestartNetworkMonitor)
        }
        Logger.d(TAG, "Trigger WiFi-Connect: $enabled")
    }
    
    fun setTriggerPeriodic(enabled: Boolean) {
        _triggerPeriodic.value = enabled
        prefs.edit().putBoolean(Constants.KEY_SYNC_TRIGGER_PERIODIC, enabled).apply()
        viewModelScope.launch {
            _events.emit(SettingsEvent.RestartNetworkMonitor)
        }
        Logger.d(TAG, "Trigger Periodic: $enabled")
    }
    
    fun setTriggerBoot(enabled: Boolean) {
        _triggerBoot.value = enabled
        prefs.edit().putBoolean(Constants.KEY_SYNC_TRIGGER_BOOT, enabled).apply()
        Logger.d(TAG, "Trigger Boot: $enabled")
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
        // 🌟 v1.6.0: Block in offline mode
        if (_offlineMode.value) {
            Logger.d(TAG, "⏭️ Manual Markdown sync blocked: Offline mode enabled")
            return
        }
        
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
                val message = if (result.success) {
                    getString(R.string.toast_backup_success, result.message ?: "")
                } else {
                    getString(R.string.toast_backup_failed, result.error ?: "")
                }
                emitToast(message)
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
                val message = if (result.success) {
                    getString(R.string.toast_restore_success, result.importedNotes)
                } else {
                    getString(R.string.toast_restore_failed, result.error ?: "")
                }
                emitToast(message)
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
                val message = if (result.isSuccess) {
                    getString(R.string.toast_restore_success, result.restoredCount)
                } else {
                    getString(R.string.toast_restore_failed, result.errorMessage ?: "")
                }
                emitToast(message)
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
    
    /**
     * Check if server is configured AND not in offline mode
     * v1.6.0: Returns false if offline mode is enabled
     */
    fun isServerConfigured(): Boolean {
        // Offline mode takes priority
        if (_offlineMode.value) return false
        
        val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null)
        return !serverUrl.isNullOrEmpty() && 
               serverUrl != "http://" && 
               serverUrl != "https://"
    }
    
    private fun getString(resId: Int): String = getApplication<android.app.Application>().getString(resId)
    
    private fun getString(resId: Int, vararg formatArgs: Any): String = 
        getApplication<android.app.Application>().getString(resId, *formatArgs)
    
    private suspend fun emitToast(message: String) {
        _showToast.emit(message)
    }
    
    /**
     * Server status states
     * v1.6.0: Added OfflineMode state
     */
    sealed class ServerStatus {
        data object Unknown : ServerStatus()
        data object OfflineMode : ServerStatus()  // 🌟 v1.6.0
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
