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
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.WebDavSyncService
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
@Suppress("TooManyFunctions") // v1.7.0: 35 Funktionen durch viele kleine Setter (setTrigger*, set*)
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    companion object {
        private const val TAG = "SettingsViewModel"
        private const val CONNECTION_TIMEOUT_MS = 3000
        private const val STATUS_CLEAR_DELAY_SUCCESS_MS = 2000L  // 2s for successful operations
        private const val STATUS_CLEAR_DELAY_ERROR_MS = 3000L    // 3s for errors (more important)
        private const val PROGRESS_CLEAR_DELAY_MS = 500L
    }
    
    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    val backupManager = BackupManager(application)
    private val notesStorage = NotesStorage(application) // v1.7.0: For server change detection
    
    // 🔧 v1.7.0 Hotfix: Track last confirmed server URL for change detection
    // This prevents false-positive "server changed" toasts during text input
    private var confirmedServerUrl: String = prefs.getString(Constants.KEY_SERVER_URL, "").orEmpty()
    
    // ═══════════════════════════════════════════════════════════════════════
    // Server Settings State
    // ═══════════════════════════════════════════════════════════════════════
    
    // v1.5.0 Fix: Initialize URL with protocol prefix if empty
    private val storedUrl = prefs.getString(Constants.KEY_SERVER_URL, "").orEmpty()
    
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
    
    private val _username = MutableStateFlow(prefs.getString(Constants.KEY_USERNAME, "").orEmpty())
    val username: StateFlow<String> = _username.asStateFlow()
    
    private val _password = MutableStateFlow(prefs.getString(Constants.KEY_PASSWORD, "").orEmpty())
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

    // 🆕 v1.8.0: Max Parallel Downloads
    private val _maxParallelDownloads = MutableStateFlow(
        prefs.getInt(Constants.KEY_MAX_PARALLEL_DOWNLOADS, Constants.DEFAULT_MAX_PARALLEL_DOWNLOADS)
    )
    val maxParallelDownloads: StateFlow<Int> = _maxParallelDownloads.asStateFlow()

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
    
    // 🎉 v1.7.0: WiFi-Only Sync Toggle
    private val _wifiOnlySync = MutableStateFlow(
        prefs.getBoolean(Constants.KEY_WIFI_ONLY_SYNC, Constants.DEFAULT_WIFI_ONLY_SYNC)
    )
    val wifiOnlySync: StateFlow<Boolean> = _wifiOnlySync.asStateFlow()
    
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
    // 🎨 v1.7.0: Display Settings State
    // ═══════════════════════════════════════════════════════════════════════
    
    private val _displayMode = MutableStateFlow(
        prefs.getString(Constants.KEY_DISPLAY_MODE, Constants.DEFAULT_DISPLAY_MODE) ?: Constants.DEFAULT_DISPLAY_MODE
    )
    val displayMode: StateFlow<String> = _displayMode.asStateFlow()
    
    // ═══════════════════════════════════════════════════════════════════════
    // UI State
    // ═══════════════════════════════════════════════════════════════════════
    
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()
    
    private val _isBackupInProgress = MutableStateFlow(false)
    val isBackupInProgress: StateFlow<Boolean> = _isBackupInProgress.asStateFlow()
    
    // v1.8.0: Descriptive backup status text
    private val _backupStatusText = MutableStateFlow("")
    val backupStatusText: StateFlow<String> = _backupStatusText.asStateFlow()
    
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
     * 🔧 v1.7.0 Hotfix: Removed auto-save to prevent false server-change detection
     * 🔧 v1.7.0 Regression Fix: Restore immediate SharedPrefs write (for WebDavSyncService)
     *    but WITHOUT server-change detection (detection happens only on screen exit)
     */
    fun updateServerHost(host: String) {
        _serverHost.value = host
        
        // ✅ Save immediately for WebDavSyncService, but WITHOUT server-change detection
        val prefix = if (_isHttps.value) "https://" else "http://"
        val fullUrl = if (host.isEmpty()) "" else prefix + host
        prefs.edit().putString(Constants.KEY_SERVER_URL, fullUrl).apply()
    }
    
    fun updateProtocol(useHttps: Boolean) {
        _isHttps.value = useHttps
        // 🌟 v1.6.0: Host stays the same, only prefix changes
        // 🔧 v1.7.0 Hotfix: Removed auto-save to prevent false server-change detection
        // 🔧 v1.7.0 Regression Fix: Restore immediate SharedPrefs write (for WebDavSyncService)
        
        // ✅ Save immediately for WebDavSyncService, but WITHOUT server-change detection
        val prefix = if (useHttps) "https://" else "http://"
        val fullUrl = if (_serverHost.value.isEmpty()) "" else prefix + _serverHost.value
        prefs.edit().putString(Constants.KEY_SERVER_URL, fullUrl).apply()
    }
    
    fun updateUsername(value: String) {
        _username.value = value
        // 🔧 v1.7.0 Regression Fix: Restore immediate SharedPrefs write (for WebDavSyncService)
        prefs.edit().putString(Constants.KEY_USERNAME, value).apply()
    }
    
    fun updatePassword(value: String) {
        _password.value = value
        // 🔧 v1.7.0 Regression Fix: Restore immediate SharedPrefs write (for WebDavSyncService)
        prefs.edit().putString(Constants.KEY_PASSWORD, value).apply()
    }
    
    /**
     * 🔧 v1.7.0 Hotfix: Manual save function - only called when leaving settings screen
     * This prevents false "server changed" detection during text input
     * 🔧 v1.7.0 Regression Fix: Settings are now saved IMMEDIATELY in update functions.
     *    This function now ONLY handles server-change detection and sync reset.
     */
    fun saveServerSettingsManually() {
        // 🌟 v1.6.0: Construct full URL from prefix + host
        val prefix = if (_isHttps.value) "https://" else "http://"
        val fullUrl = if (_serverHost.value.isEmpty()) "" else prefix + _serverHost.value
        
        // 🔄 v1.7.0: Detect server change ONLY against last confirmed URL
        val serverChanged = isServerReallyChanged(confirmedServerUrl, fullUrl)
        
        // ✅ Settings are already saved in updateServerHost/Protocol/Username/Password
        // This function now ONLY handles server-change detection
        
        // Reset sync status if server actually changed
        if (serverChanged) {
            viewModelScope.launch {
                val count = notesStorage.resetAllSyncStatusToPending()
                Logger.d(TAG, "🔄 Server changed from '$confirmedServerUrl' to '$fullUrl': Reset $count notes to PENDING")
                emitToast(getString(R.string.toast_server_changed_sync_reset, count))
            }
            // Update confirmed state after reset
            confirmedServerUrl = fullUrl
        } else {
            Logger.d(TAG, "💾 Server settings check complete (no server change detected)")
        }
    }
    
    /**
     * � v1.7.0 Hotfix: Improved server change detection
     * 
     * Only returns true if the server URL actually changed in a meaningful way.
     * Handles edge cases:
     * - First setup (empty → filled) = NOT a change
     * - Protocol only (http → https) = NOT a change
     * - Server removed (filled → empty) = NOT a change
     * - Trailing slashes, case differences = NOT a change
     * - Different hostname/port/path = IS a change ✓
     */
    private fun isServerReallyChanged(confirmedUrl: String, newUrl: String): Boolean {
        // Empty → Non-empty = First setup, NOT a change
        if (confirmedUrl.isEmpty() && newUrl.isNotEmpty()) {
            Logger.d(TAG, "First server setup detected (no reset needed)")
            return false
        }
        
        // Both empty = No change
        if (confirmedUrl.isEmpty() && newUrl.isEmpty()) {
            return false
        }
        
        // Non-empty → Empty = Server removed (keep notes local, no reset)
        if (confirmedUrl.isNotEmpty() && newUrl.isEmpty()) {
            Logger.d(TAG, "Server removed (notes stay local, no reset needed)")
            return false
        }
        
        // Same URL = No change
        if (confirmedUrl == newUrl) {
            return false
        }
        
        // Normalize URLs for comparison (ignore protocol, trailing slash, case)
        val normalize = { url: String ->
            url.trim()
                .removePrefix("http://")
                .removePrefix("https://")
                .removeSuffix("/")
                .lowercase()
        }
        
        val confirmedNormalized = normalize(confirmedUrl)
        val newNormalized = normalize(newUrl)
        
        // Check if normalized URLs differ
        val changed = confirmedNormalized != newNormalized
        
        if (changed) {
            Logger.d(TAG, "Server URL changed: '$confirmedNormalized' → '$newNormalized'")
        }
        
        return changed
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
                    // 🆕 Issue #21: infoMessage anzeigen wenn vorhanden (z.B. /notes/-Status)
                    result.infoMessage ?: getString(R.string.toast_connection_success)
                } else {
                    getString(R.string.toast_connection_failed, result.errorMessage.orEmpty())
                }
                emitToast(message)
            } catch (e: Exception) {
                _serverStatus.value = ServerStatus.Unreachable(e.message)
                emitToast(getString(R.string.toast_error, e.message.orEmpty()))
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
            val isReachable = withContext(ioDispatcher) {
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
                val syncService = WebDavSyncService(getApplication())
                
                // 🆕 v1.7.0: Zentrale Sync-Gate Prüfung
                val gateResult = syncService.canSync()
                if (!gateResult.canSync) {
                    if (gateResult.isBlockedByWifiOnly) {
                        emitToast(getString(R.string.sync_wifi_only_hint))
                    } else {
                        emitToast(getString(R.string.toast_sync_failed, "Offline mode"))
                    }
                    return@launch
                }
                
                emitToast(getString(R.string.toast_syncing))
                
                if (!syncService.hasUnsyncedChanges()) {
                    emitToast(getString(R.string.toast_already_synced))
                    return@launch
                }
                
                val result = syncService.syncNotes()
                if (result.isSuccess) {
                    emitToast(getString(R.string.toast_sync_success, result.syncedCount))
                } else {
                    emitToast(getString(R.string.toast_sync_failed, result.errorMessage.orEmpty()))
                }
            } catch (e: Exception) {
                emitToast(getString(R.string.toast_error, e.message.orEmpty()))
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

    // 🆕 v1.8.0: Max Parallel Downloads Setter
    fun setMaxParallelDownloads(count: Int) {
        val validCount = count.coerceIn(
            Constants.MIN_PARALLEL_DOWNLOADS,
            Constants.MAX_PARALLEL_DOWNLOADS
        )
        _maxParallelDownloads.value = validCount
        prefs.edit().putInt(Constants.KEY_MAX_PARALLEL_DOWNLOADS, validCount).apply()
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
    
    /**
     * 🎉 v1.7.0: Set WiFi-only sync mode
     * When enabled, sync only happens when connected to WiFi
     */
    fun setWifiOnlySync(enabled: Boolean) {
        _wifiOnlySync.value = enabled
        prefs.edit().putBoolean(Constants.KEY_WIFI_ONLY_SYNC, enabled).apply()
        Logger.d(TAG, "📡 WiFi-only sync: $enabled")
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
                    val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, "").orEmpty()
                    val username = prefs.getString(Constants.KEY_USERNAME, "").orEmpty()
                    val password = prefs.getString(Constants.KEY_PASSWORD, "").orEmpty()
                    
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
                        val exportedCount = withContext(ioDispatcher) {
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
                        kotlinx.coroutines.delay(PROGRESS_CLEAR_DELAY_MS)
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
                    emitToast(getString(R.string.toast_export_failed, e.message.orEmpty()))
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
                emitToast(getString(R.string.toast_error, e.message.orEmpty()))
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════
    // Backup Actions
    // ═══════════════════════════════════════════════════════════════════════
    
    fun createBackup(uri: Uri, password: String? = null) {
        viewModelScope.launch {
            _isBackupInProgress.value = true
            _backupStatusText.value = getString(R.string.backup_progress_creating)
            try {
                val result = backupManager.createBackup(uri, password)
                
                // Phase 2: Show completion status
                _backupStatusText.value = if (result.success) {
                    getString(R.string.backup_progress_complete)
                } else {
                    getString(R.string.backup_progress_failed)
                }
                
                // Phase 3: Clear after delay
                delay(if (result.success) STATUS_CLEAR_DELAY_SUCCESS_MS else STATUS_CLEAR_DELAY_ERROR_MS)
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to create backup", e)
                _backupStatusText.value = getString(R.string.backup_progress_failed)
                delay(STATUS_CLEAR_DELAY_ERROR_MS)
            } finally {
                _isBackupInProgress.value = false
                _backupStatusText.value = ""
            }
        }
    }
    
    fun restoreFromFile(uri: Uri, mode: RestoreMode, password: String? = null) {
        viewModelScope.launch {
            _isBackupInProgress.value = true
            _backupStatusText.value = getString(R.string.backup_progress_restoring)
            try {
                val result = backupManager.restoreBackup(uri, mode, password)
                
                // Phase 2: Show completion status
                _backupStatusText.value = if (result.success) {
                    getString(R.string.restore_progress_complete)
                } else {
                    getString(R.string.restore_progress_failed)
                }
                
                // Phase 3: Clear after delay
                delay(if (result.success) STATUS_CLEAR_DELAY_SUCCESS_MS else STATUS_CLEAR_DELAY_ERROR_MS)
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to restore backup from file", e)
                _backupStatusText.value = getString(R.string.restore_progress_failed)
                delay(STATUS_CLEAR_DELAY_ERROR_MS)
            } finally {
                _isBackupInProgress.value = false
                _backupStatusText.value = ""
            }
        }
    }
    
    /**
     * 🔐 v1.7.0: Check if backup is encrypted and call appropriate callback
     */
    fun checkBackupEncryption(
        uri: Uri,
        onEncrypted: () -> Unit,
        onPlaintext: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val isEncrypted = backupManager.isBackupEncrypted(uri)
                if (isEncrypted) {
                    onEncrypted()
                } else {
                    onPlaintext()
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to check encryption status", e)
                onPlaintext()  // Assume plaintext on error
            }
        }
    }
    
    fun restoreFromServer(mode: RestoreMode) {
        viewModelScope.launch {
            _isBackupInProgress.value = true
            _backupStatusText.value = getString(R.string.backup_progress_restoring_server)
            try {
                val syncService = WebDavSyncService(getApplication())
                val result = withContext(ioDispatcher) {
                    syncService.restoreFromServer(mode)
                }
                
                // Phase 2: Show completion status
                _backupStatusText.value = if (result.isSuccess) {
                    getString(R.string.restore_server_progress_complete)
                } else {
                    getString(R.string.restore_server_progress_failed)
                }
                
                // Phase 3: Clear after delay
                delay(if (result.isSuccess) STATUS_CLEAR_DELAY_SUCCESS_MS else STATUS_CLEAR_DELAY_ERROR_MS)
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to restore from server", e)
                _backupStatusText.value = getString(R.string.restore_server_progress_failed)
                delay(STATUS_CLEAR_DELAY_ERROR_MS)
            } finally {
                _isBackupInProgress.value = false
                _backupStatusText.value = ""
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
                emitToast(getString(R.string.toast_error, e.message.orEmpty()))
            }
        }
    }
    
    fun getLogFile() = Logger.getLogFile(getApplication())
    
    /**
     * v1.8.0: Reset changelog version to force showing the changelog dialog on next start
     * Used for testing the post-update changelog feature
     */
    fun resetChangelogVersion() {
        prefs.edit()
            .putInt(Constants.KEY_LAST_SHOWN_CHANGELOG_VERSION, 0)
            .apply()
    }
    
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
    
    /**
     * 🌍 v1.7.1: Get string resources with correct app locale
     * 
     * AndroidViewModel uses Application context which may not have the correct locale
     * applied when using per-app language settings. We need to get a Context that
     * respects AppCompatDelegate.getApplicationLocales().
     */
    private fun getString(resId: Int): String {
        // Get context with correct locale configuration from AppCompatDelegate
        val appLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        val context = if (!appLocales.isEmpty) {
            // Create configuration with app locale
            val config = android.content.res.Configuration(getApplication<Application>().resources.configuration)
            config.setLocale(appLocales.get(0))
            getApplication<Application>().createConfigurationContext(config)
        } else {
            // Use system locale (default)
            getApplication<Application>()
        }
        return context.getString(resId)
    }
    
    private fun getString(resId: Int, vararg formatArgs: Any): String {
        // Get context with correct locale configuration from AppCompatDelegate
        val appLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        val context = if (!appLocales.isEmpty) {
            // Create configuration with app locale
            val config = android.content.res.Configuration(getApplication<Application>().resources.configuration)
            config.setLocale(appLocales.get(0))
            getApplication<Application>().createConfigurationContext(config)
        } else {
            // Use system locale (default)
            getApplication<Application>()
        }
        return context.getString(resId, *formatArgs)
    }
    
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
    
    // ═══════════════════════════════════════════════════════════════════════
    // 🎨 v1.7.0: Display Mode Functions
    // ═══════════════════════════════════════════════════════════════════════
    
    /**
     * Set display mode (list or grid)
     */
    fun setDisplayMode(mode: String) {
        _displayMode.value = mode
        prefs.edit().putString(Constants.KEY_DISPLAY_MODE, mode).apply()
        Logger.d(TAG, "Display mode changed to: $mode")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🆕 Issue #21: Notes Import Wizard
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Scannt den konfigurierten WebDAV-Ordner nach importierbaren Dateien.
     * Scannt die Base-URL (NICHT /notes/), da dort die externen Dateien liegen.
     */
    suspend fun scanWebDavForImport(): List<dev.dettmer.simplenotes.noteimport.NotesImportWizard.ImportCandidate> =
        withContext(ioDispatcher) {
            try {
                val syncService = WebDavSyncService(getApplication())
                val sardine = syncService.getOrCreateSardine() ?: return@withContext emptyList()
                val serverUrl = syncService.getServerUrl() ?: return@withContext emptyList()

                val wizard = dev.dettmer.simplenotes.noteimport.NotesImportWizard(notesStorage, getApplication())
                wizard.scanWebDavFolder(sardine, serverUrl)
            } catch (e: Exception) {
                Logger.e(TAG, "Import scan failed: ${e.message}")
                emptyList()
            }
        }

    /**
     * Importiert ausgewählte Import-Kandidaten.
     */
    suspend fun importCandidates(
        candidates: List<dev.dettmer.simplenotes.noteimport.NotesImportWizard.ImportCandidate>
    ): dev.dettmer.simplenotes.noteimport.NotesImportWizard.ImportSummary = withContext(ioDispatcher) {
        val wizard = dev.dettmer.simplenotes.noteimport.NotesImportWizard(notesStorage, getApplication())
        wizard.importFiles(candidates)
    }

    /**
     * Importiert lokale Dateien via Android Content-URI.
     */
    suspend fun importLocalFiles(
        uris: List<Uri>
    ): dev.dettmer.simplenotes.noteimport.NotesImportWizard.ImportSummary = withContext(ioDispatcher) {
        val wizard = dev.dettmer.simplenotes.noteimport.NotesImportWizard(notesStorage, getApplication())
        val candidates = uris.mapNotNull { uri ->
            try {
                val name = getFileName(uri) ?: "unknown.txt"
                val size = getFileSize(uri)
                val fileType = wizard.detectFileType(name)
                dev.dettmer.simplenotes.noteimport.NotesImportWizard.ImportCandidate(
                    name = name,
                    source = dev.dettmer.simplenotes.noteimport.NotesImportWizard.ImportSource.LocalFile(uri),
                    size = size,
                    modified = System.currentTimeMillis(),
                    fileType = fileType
                )
            } catch (e: Exception) {
                Logger.w(TAG, "Cannot process URI $uri: ${e.message}")
                null
            }
        }
        wizard.importFiles(candidates)
    }

    private fun getFileName(uri: Uri): String? {
        val cursor = getApplication<android.app.Application>()
            .contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && nameIndex >= 0) it.getString(nameIndex) else null
        }
    }

    private fun getFileSize(uri: Uri): Long {
        val cursor = getApplication<android.app.Application>()
            .contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (it.moveToFirst() && sizeIndex >= 0) it.getLong(sizeIndex) else 0L
        } ?: 0L
    }
}
