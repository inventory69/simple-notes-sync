package dev.dettmer.simplenotes.ui.settings

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.backup.BackupManager
import dev.dettmer.simplenotes.backup.RestoreMode
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.WebDavSyncService
import dev.dettmer.simplenotes.ui.theme.ColorTheme
import dev.dettmer.simplenotes.ui.theme.ThemeMode
import dev.dettmer.simplenotes.ui.theme.ThemePreferences
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.CredentialStore
import dev.dettmer.simplenotes.utils.Logger
import android.os.PowerManager
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
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
import kotlinx.coroutines.withTimeout

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
        private const val STATUS_CLEAR_DELAY_SUCCESS_MS = 2000L // 2s for successful operations
        private const val STATUS_CLEAR_DELAY_ERROR_MS = 3000L // 3s for errors (more important)
        private const val PROGRESS_CLEAR_DELAY_MS = 500L

        // 🆕 v1.10.0: Overhead-Timeout für Markdown-Export (Ordner-Erstellung, Listing etc.)
        private const val EXPORT_OVERHEAD_TIMEOUT_MS = 10_000L
    }

    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    val backupManager = BackupManager(application)
    private val notesStorage = NotesStorage(application) // v1.7.0: For server change detection

    // 🔧 v1.7.0 Hotfix: Track last confirmed server URL for change detection
    // This prevents false-positive "server changed" toasts during text input
    private var confirmedServerUrl: String = prefs.getString(Constants.KEY_SERVER_URL, "").orEmpty()

    // 🆕 v1.9.0: Track last confirmed sync folder name for change detection
    private var confirmedSyncFolderName: String =
        prefs.getString(Constants.KEY_SYNC_FOLDER_NAME, Constants.DEFAULT_SYNC_FOLDER_NAME)
            ?: Constants.DEFAULT_SYNC_FOLDER_NAME

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

    private val _username = MutableStateFlow(CredentialStore.getUsername(getApplication()).orEmpty())
    val username: StateFlow<String> = _username.asStateFlow()

    private val _password = MutableStateFlow(CredentialStore.getPassword(getApplication()).orEmpty())
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

    private val _showBatteryOptimizationDialog = MutableStateFlow(false)
    val showBatteryOptimizationDialog: StateFlow<Boolean> = _showBatteryOptimizationDialog.asStateFlow()

    fun dismissBatteryOptimizationDialog() {
        _showBatteryOptimizationDialog.value = false
    }

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

    // 🔧 v1.9.0: Unified parallel connections setting (downloads + uploads)
    private val _maxParallelConnections = MutableStateFlow(
        prefs.getInt(Constants.KEY_MAX_PARALLEL_CONNECTIONS, Constants.DEFAULT_MAX_PARALLEL_CONNECTIONS)
            .coerceIn(Constants.MIN_PARALLEL_CONNECTIONS, Constants.MAX_PARALLEL_CONNECTIONS)
    )
    val maxParallelConnections: StateFlow<Int> = _maxParallelConnections.asStateFlow()

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
    // 🆕 v1.11.0: Notification Settings State
    // ═══════════════════════════════════════════════════════════════════════

    private val _notificationsEnabled = MutableStateFlow(
        prefs.getBoolean(Constants.KEY_NOTIFICATIONS_ENABLED, Constants.DEFAULT_NOTIFICATIONS_ENABLED)
    )
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _notificationsErrorsOnly = MutableStateFlow(
        prefs.getBoolean(Constants.KEY_NOTIFICATIONS_ERRORS_ONLY, Constants.DEFAULT_NOTIFICATIONS_ERRORS_ONLY)
    )
    val notificationsErrorsOnly: StateFlow<Boolean> = _notificationsErrorsOnly.asStateFlow()

    private val _notificationsServerWarning = MutableStateFlow(
        prefs.getBoolean(Constants.KEY_NOTIFICATIONS_SERVER_WARNING, Constants.DEFAULT_NOTIFICATIONS_SERVER_WARNING)
    )
    val notificationsServerWarning: StateFlow<Boolean> = _notificationsServerWarning.asStateFlow()

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

    // 🔧 v1.11.0: Developer Options (Easter-Egg) — session-only, nicht persistiert
    private val _developerOptionsUnlocked = MutableStateFlow(false)
    val developerOptionsUnlocked: StateFlow<Boolean> = _developerOptionsUnlocked.asStateFlow()

    /**
     * Schaltet Entwickleroptionen frei (5× Tippen auf den Banner im Über-Screen).
     * Nur In-Memory — wird bei Prozess-Tod zurückgesetzt.
     */
    fun unlockDeveloperOptions() {
        _developerOptionsUnlocked.value = true
    }

    // ═══════════════════════════════════════════════════════════════════════
    // v2.0.0: Theme Settings State
    // ═══════════════════════════════════════════════════════════════════════

    private val _themeMode = MutableStateFlow(ThemePreferences.getThemeMode(prefs))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _colorTheme = MutableStateFlow(ThemePreferences.getColorTheme(prefs))
    val colorTheme: StateFlow<ColorTheme> = _colorTheme.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        ThemePreferences.setThemeMode(prefs, mode)
    }

    fun setColorTheme(theme: ColorTheme) {
        _colorTheme.value = theme
        ThemePreferences.setColorTheme(prefs, theme)
    }

    // 🎨 v1.7.0: Display Settings State
    // ═══════════════════════════════════════════════════════════════════════

    private val _displayMode = MutableStateFlow(
        prefs.getString(Constants.KEY_DISPLAY_MODE, Constants.DEFAULT_DISPLAY_MODE) ?: Constants.DEFAULT_DISPLAY_MODE
    )
    val displayMode: StateFlow<String> = _displayMode.asStateFlow()

    // 🆕 v2.1.0 (F46): Grid column control
    private val _gridAdaptiveScaling = MutableStateFlow(
        prefs.getBoolean(Constants.KEY_GRID_ADAPTIVE_SCALING, Constants.DEFAULT_GRID_ADAPTIVE_SCALING)
    )
    val gridAdaptiveScaling: StateFlow<Boolean> = _gridAdaptiveScaling.asStateFlow()

    private val _gridManualColumns = MutableStateFlow(
        prefs.getInt(Constants.KEY_GRID_MANUAL_COLUMNS, Constants.DEFAULT_GRID_MANUAL_COLUMNS)
    )
    val gridManualColumns: StateFlow<Int> = _gridManualColumns.asStateFlow()

    // 🆕 v1.9.0 (F05): Custom App Title
    private val _customAppTitle = MutableStateFlow(
        prefs.getString(Constants.KEY_CUSTOM_APP_TITLE, Constants.DEFAULT_CUSTOM_APP_TITLE)
            ?: Constants.DEFAULT_CUSTOM_APP_TITLE
    )
    val customAppTitle: StateFlow<String> = _customAppTitle.asStateFlow()

    // 🆕 v1.9.0: Configurable WebDAV Sync Folder
    private val _syncFolderName = MutableStateFlow(
        prefs.getString(Constants.KEY_SYNC_FOLDER_NAME, Constants.DEFAULT_SYNC_FOLDER_NAME)
            ?: Constants.DEFAULT_SYNC_FOLDER_NAME
    )
    val syncFolderName: StateFlow<String> = _syncFolderName.asStateFlow()

    // 🆕 v1.9.0: Autosave
    private val _autosaveEnabled = MutableStateFlow(
        prefs.getBoolean(Constants.KEY_AUTOSAVE_ENABLED, Constants.DEFAULT_AUTOSAVE_ENABLED)
    )
    val autosaveEnabled: StateFlow<Boolean> = _autosaveEnabled.asStateFlow()

    // 🆕 v1.10.0: Configurable connection timeout
    private val _connectionTimeoutSeconds = MutableStateFlow(
        prefs.getInt(
            Constants.KEY_CONNECTION_TIMEOUT_SECONDS,
            Constants.DEFAULT_CONNECTION_TIMEOUT_SECONDS
        ).coerceIn(
            Constants.MIN_CONNECTION_TIMEOUT_SECONDS,
            Constants.MAX_CONNECTION_TIMEOUT_SECONDS
        )
    )
    val connectionTimeoutSeconds: StateFlow<Int> = _connectionTimeoutSeconds.asStateFlow()

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

    private val _showSnackbar = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val showSnackbar: SharedFlow<String> = _showSnackbar.asSharedFlow()

    // ═══════════════════════════════════════════════════════════════════════
    // Server Settings Actions
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * v1.6.0: Set offline mode on/off
     * When enabled, all network features are disabled
     */
    fun setOfflineMode(enabled: Boolean) {
        _offlineMode.value = enabled
        prefs.edit { putBoolean(Constants.KEY_OFFLINE_MODE, enabled) }

        if (enabled) {
            _serverStatus.value = ServerStatus.OfflineMode
        } else {
            // Re-check server status when disabling offline mode
            checkServerStatus()

            // 🆕 v2.3.0: Prompt battery optimization when leaving offline mode.
            // Background sync (especially WiFi-Connect Trigger) requires the app to be
            // exempt from battery optimization, otherwise WorkManager jobs are heavily
            // throttled or delayed.
            checkAndPromptBatteryOptimization()
        }
    }

    /**
     * 🆕 v2.3.0: Checks if the app is exempt from battery optimization.
     * If not, triggers the battery optimization dialog.
     * Does NOT check offline mode — caller must ensure sync is relevant.
     */
    private fun checkAndPromptBatteryOptimization() {
        val context = getApplication<Application>()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
            _showBatteryOptimizationDialog.value = true
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
        prefs.edit { putString(Constants.KEY_SERVER_URL, fullUrl) }
    }

    fun updateProtocol(useHttps: Boolean) {
        _isHttps.value = useHttps
        // 🌟 v1.6.0: Host stays the same, only prefix changes
        // 🔧 v1.7.0 Hotfix: Removed auto-save to prevent false server-change detection
        // 🔧 v1.7.0 Regression Fix: Restore immediate SharedPrefs write (for WebDavSyncService)

        // ✅ Save immediately for WebDavSyncService, but WITHOUT server-change detection
        val prefix = if (useHttps) "https://" else "http://"
        val fullUrl = if (_serverHost.value.isEmpty()) "" else prefix + _serverHost.value
        prefs.edit { putString(Constants.KEY_SERVER_URL, fullUrl) }
    }

    fun updateUsername(value: String) {
        _username.value = value
        // 🔧 v1.7.0 Regression Fix: Restore immediate SharedPrefs write (for WebDavSyncService)
        CredentialStore.setCredentials(getApplication(), value, _password.value)
    }

    fun updatePassword(value: String) {
        _password.value = value
        // 🔧 v1.7.0 Regression Fix: Restore immediate SharedPrefs write (for WebDavSyncService)
        CredentialStore.setCredentials(getApplication(), _username.value, value)
    }

    // 🆕 v1.9.0: Update configurable sync folder name
    fun updateSyncFolderName(name: String) {
        val sanitized = name
            .replace(Regex("[^a-zA-Z0-9_-]"), "")
            .take(Constants.MAX_SYNC_FOLDER_NAME_LENGTH)
        _syncFolderName.value = sanitized
        prefs.edit {
            putString(Constants.KEY_SYNC_FOLDER_NAME, sanitized.ifEmpty { Constants.DEFAULT_SYNC_FOLDER_NAME })
        }
    }

    /**
     * 🆕 v1.9.0: Toggle autosave.
     * Saves immediately to SharedPreferences. NoteEditorViewModel reads
     * the preference at init time.
     */
    fun setAutosaveEnabled(enabled: Boolean) {
        _autosaveEnabled.value = enabled
        prefs.edit { putBoolean(Constants.KEY_AUTOSAVE_ENABLED, enabled) }
    }

    /**
     * 🆕 v1.10.0: Set connection timeout in seconds.
     * Clamped to MIN_CONNECTION_TIMEOUT_SECONDS..MAX_CONNECTION_TIMEOUT_SECONDS range.
     * WebDavSyncService reads this at each sync start via SharedPreferences.
     */
    fun setConnectionTimeoutSeconds(seconds: Int) {
        val validSeconds = seconds.coerceIn(
            Constants.MIN_CONNECTION_TIMEOUT_SECONDS,
            Constants.MAX_CONNECTION_TIMEOUT_SECONDS
        )
        _connectionTimeoutSeconds.value = validSeconds
        prefs.edit { putInt(Constants.KEY_CONNECTION_TIMEOUT_SECONDS, validSeconds) }
        Logger.d(TAG, "Connection timeout set to: ${validSeconds}s")
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

        // 🆕 v1.9.0: Folder change counts as server change (different data location)
        val currentFolder = _syncFolderName.value.ifEmpty { Constants.DEFAULT_SYNC_FOLDER_NAME }
        val folderChanged = currentFolder != confirmedSyncFolderName

        // 🔄 v1.7.0: Detect server change ONLY against last confirmed URL
        val serverChanged = isServerReallyChanged(confirmedServerUrl, fullUrl) || folderChanged

        // ✅ Settings are already saved in updateServerHost/Protocol/Username/Password
        // This function now ONLY handles server-change detection

        // Reset sync status if server actually changed
        if (serverChanged) {
            viewModelScope.launch {
                // 🔧 v1.9.0: E-Tag/Content-Hash-Caches und Sync-Timestamp löschen
                // Verhindert Upload-Skip durch veraltete Cache-Einträge des alten Servers
                clearServerCaches()
                val count = notesStorage.resetAllSyncStatusToPending()
                Logger.d(
                    TAG,
                    "🔄 Server changed from '$confirmedServerUrl' to '$fullUrl': Reset $count notes to PENDING"
                )
                emitToast(getString(R.string.toast_server_changed_sync_reset, count))
            }
            // Update confirmed state after reset
            confirmedServerUrl = fullUrl
            confirmedSyncFolderName = currentFolder
        } else {
            Logger.d(TAG, "💾 Server settings check complete (no server change detected)")
        }
    }

    /**
     * 🔧 v1.9.0: Löscht alle server-spezifischen Caches beim Server-Wechsel.
     *
     * Ohne diesen Clear greift die Content-Hash-Skip-Logik in uploadSingleNoteParallel():
     * Hash matcht (Inhalt gleich) + E-Tag vom alten Server noch vorhanden → Upload übersprungen,
     * Note auf SYNCED gesetzt ohne je auf neuen Server hochgeladen zu werden.
     *
     * Gelöscht werden:
     * - etag_json_*       (JSON-Datei E-Tags)
     * - etag_md_*         (Markdown-Datei E-Tags)
     * - content_hash_*    (JSON-Content-Hashes)
     * - content_hash_md_* (Markdown-Content-Hashes)
     * - lastSyncTimestamp (damit hasUnsyncedChanges() korrekt funktioniert)
     * - DeletionTracker   (alte Lösch-Historie ist für neuen Server irrelevant)
     */
    private fun clearServerCaches() {
        prefs.edit {
            prefs.all.keys.filter {
                it.startsWith("etag_json_") ||
                    it.startsWith("etag_md_") ||
                    it.startsWith("content_hash_") ||
                    it.startsWith("content_hash_md_")
            }.forEach { key -> remove(key) }
            remove(Constants.KEY_LAST_SYNC)
            remove(Constants.KEY_LAST_SUCCESSFUL_SYNC)
        }
        notesStorage.clearDeletionTracker()
        Logger.d(TAG, "🧹 Cleared server caches (E-Tags, content hashes, sync timestamp, deletion tracker)")
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
                    try {
                        connection.connectTimeout = CONNECTION_TIMEOUT_MS
                        connection.readTimeout = CONNECTION_TIMEOUT_MS
                        val code = connection.responseCode
                        code in 200..299 || code == 401
                    } finally {
                        connection.disconnect()
                    }
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

    fun showBatteryOptimizationDialogRequest() {
        _showBatteryOptimizationDialog.value = true
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Sync Settings Actions
    // ═══════════════════════════════════════════════════════════════════════

    fun setAutoSync(enabled: Boolean) {
        _autoSyncEnabled.value = enabled
        prefs.edit { putBoolean(Constants.KEY_AUTO_SYNC, enabled) }

        viewModelScope.launch {
            if (enabled) {
                // v2.0.0: Battery optimization dialog now state-driven via showBatteryOptimizationDialog
                _showBatteryOptimizationDialog.value = true
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
        prefs.edit { putLong(Constants.PREF_SYNC_INTERVAL_MINUTES, minutes) }
        viewModelScope.launch {
            val text = when (minutes) {
                15L -> getString(R.string.toast_sync_interval_15min)
                60L -> getString(R.string.toast_sync_interval_60min)
                else -> getString(R.string.toast_sync_interval_30min)
            }
            emitToast(getString(R.string.toast_sync_interval, text))
        }
    }

    // 🔧 v1.9.0: Unified parallel connections setter
    fun setMaxParallelConnections(count: Int) {
        val validCount = count.coerceIn(
            Constants.MIN_PARALLEL_CONNECTIONS,
            Constants.MAX_PARALLEL_CONNECTIONS
        )
        _maxParallelConnections.value = validCount
        prefs.edit { putInt(Constants.KEY_MAX_PARALLEL_CONNECTIONS, validCount) }
    }

    // 🌟 v1.6.0: Configurable Sync Triggers Setters

    fun setTriggerOnSave(enabled: Boolean) {
        _triggerOnSave.value = enabled
        prefs.edit { putBoolean(Constants.KEY_SYNC_TRIGGER_ON_SAVE, enabled) }
        Logger.d(TAG, "Trigger onSave: $enabled")
    }

    fun setTriggerOnResume(enabled: Boolean) {
        _triggerOnResume.value = enabled
        prefs.edit { putBoolean(Constants.KEY_SYNC_TRIGGER_ON_RESUME, enabled) }
        Logger.d(TAG, "Trigger onResume: $enabled")
    }

    fun setTriggerWifiConnect(enabled: Boolean) {
        _triggerWifiConnect.value = enabled
        prefs.edit { putBoolean(Constants.KEY_SYNC_TRIGGER_WIFI_CONNECT, enabled) }
        viewModelScope.launch {
            _events.emit(SettingsEvent.RestartNetworkMonitor)
        }
        Logger.d(TAG, "Trigger WiFi-Connect: $enabled")
    }

    fun setTriggerPeriodic(enabled: Boolean) {
        _triggerPeriodic.value = enabled
        prefs.edit { putBoolean(Constants.KEY_SYNC_TRIGGER_PERIODIC, enabled) }
        viewModelScope.launch {
            _events.emit(SettingsEvent.RestartNetworkMonitor)
        }
        Logger.d(TAG, "Trigger Periodic: $enabled")
    }

    fun setTriggerBoot(enabled: Boolean) {
        _triggerBoot.value = enabled
        prefs.edit { putBoolean(Constants.KEY_SYNC_TRIGGER_BOOT, enabled) }
        Logger.d(TAG, "Trigger Boot: $enabled")
    }

    /**
     * 🎉 v1.7.0: Set WiFi-only sync mode
     * When enabled, sync only happens when connected to WiFi
     */
    fun setWifiOnlySync(enabled: Boolean) {
        _wifiOnlySync.value = enabled
        prefs.edit { putBoolean(Constants.KEY_WIFI_ONLY_SYNC, enabled) }
        Logger.d(TAG, "📡 WiFi-only sync: $enabled")
    }

    // 🆕 v1.11.0: Notification Settings Setters

    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationsEnabled.value = enabled
        prefs.edit { putBoolean(Constants.KEY_NOTIFICATIONS_ENABLED, enabled) }
        Logger.d(TAG, "🔔 Notifications enabled: $enabled")
    }

    fun setNotificationsErrorsOnly(enabled: Boolean) {
        _notificationsErrorsOnly.value = enabled
        prefs.edit { putBoolean(Constants.KEY_NOTIFICATIONS_ERRORS_ONLY, enabled) }
        Logger.d(TAG, "🔔 Notifications errors-only: $enabled")
    }

    fun setNotificationsServerWarning(enabled: Boolean) {
        _notificationsServerWarning.value = enabled
        prefs.edit { putBoolean(Constants.KEY_NOTIFICATIONS_SERVER_WARNING, enabled) }
        Logger.d(TAG, "🔔 Notifications server warning: $enabled")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Markdown Settings Actions
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * 🔧 v1.10.0: Timeout-Absicherung + konsistente Fehlermeldung.
     * - withTimeout() verhindert endloses Hängen bei Server-Problemen
     * - mapSyncExceptionToMessage() für user-freundliche Fehlertexte
     * - Toggle wird bei Fehler/Timeout automatisch zurückgesetzt
     */
    fun setMarkdownAutoSync(enabled: Boolean) {
        if (enabled) {
            // 🆕 v1.10.0: Optimistic Update — Toggle springt sofort auf ON,
            // Dialog öffnet sich direkt mit "Verbindung wird geprüft...".
            // Bei Fehler werden beide wieder zurückgesetzt.
            _markdownAutoSync.value = true
            _markdownExportProgress.value = MarkdownExportProgress(0, 0, isChecking = true)

            // v1.5.0 Fix: Perform initial export when enabling (like old SettingsActivity)
            viewModelScope.launch {
                try {
                    // Check server configuration first
                    val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, "").orEmpty()
                    val username = CredentialStore.getUsername(getApplication()).orEmpty()
                    val password = CredentialStore.getPassword(getApplication()).orEmpty()

                    if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
                        _markdownAutoSync.value = false
                        _markdownExportProgress.value = null
                        emitToast(getString(R.string.toast_configure_server_first))
                        return@launch
                    }

                    // 🔧 v2.2.1 (Issue #50): Prefs SOFORT setzen nach Server-Config-Validierung.
                    // KEY_MARKDOWN_EXPORT wird in NoteUploader beim on-save Sync gelesen.
                    // Wenn der Initial-Export fehlschlägt (z.B. bewCloud 405, Timeout,
                    // Netzwerkfehler oder ViewModel-Cancellation), muss der reguläre Sync
                    // trotzdem bei jedem Save die .md-Datei schreiben können.
                    // Der Initial-Export ist "best effort" für Bestandsnotizen.
                    prefs.edit {
                        putBoolean(Constants.KEY_MARKDOWN_EXPORT, true)
                        putBoolean(Constants.KEY_MARKDOWN_AUTO_IMPORT, true)
                    }

                    // Check if there are notes to export
                    val noteStorage = dev.dettmer.simplenotes.storage.NotesStorage(getApplication())
                    val noteCount = withContext(ioDispatcher) { noteStorage.loadAllNotes().size }

                    if (noteCount > 0) {
                        val syncService = WebDavSyncService(getApplication())

                        // 🔧 v1.10.0 Fix: Schnell-Fail — Server-Erreichbarkeit VOR dem Export prüfen.
                        // Verhindert N×timeout (z.B. 21×3s=63s) wenn der Server schlicht nicht
                        // erreichbar ist. Ein einzelner Socket-Check reicht (1×timeout).
                        val reachable = withContext(ioDispatcher) { syncService.isServerReachable() }
                        if (!reachable) {
                            // 🔧 v2.2.1: Prefs bleiben auf true — on-save Export funktioniert
                            // beim nächsten erfolgreichen Sync. Nur Toast als Warnung.
                            _markdownExportProgress.value = null
                            emitToast(getString(R.string.snackbar_server_unreachable))
                            return@launch
                        }

                        // Server erreichbar — Dialog wechselt zur echten Export-Progress-Anzeige
                        _markdownExportProgress.value = MarkdownExportProgress(0, noteCount)

                        // 🆕 v1.10.0: Gesamt-Timeout für den Export-Vorgang.
                        // Pro Note rechnen wir mit max. 2× dem konfigurierten Timeout
                        // (1× connect + 1× upload), plus 10s Overhead für Ordner-Erstellung etc.
                        val perNoteTimeoutMs = prefs.getInt(
                            Constants.KEY_CONNECTION_TIMEOUT_SECONDS,
                            Constants.DEFAULT_CONNECTION_TIMEOUT_SECONDS
                        ).coerceIn(
                            Constants.MIN_CONNECTION_TIMEOUT_SECONDS,
                            Constants.MAX_CONNECTION_TIMEOUT_SECONDS
                        ) * 2 * 1000L
                        val totalTimeoutMs = (noteCount * perNoteTimeoutMs) + EXPORT_OVERHEAD_TIMEOUT_MS

                        try {
                            val exportedCount = withTimeout(totalTimeoutMs) {
                                withContext(ioDispatcher) {
                                    syncService.exportAllNotesToMarkdown(
                                        serverUrl = serverUrl,
                                        username = username,
                                        password = password,
                                        onProgress = { current, total ->
                                            _markdownExportProgress.value = MarkdownExportProgress(current, total)
                                        }
                                    )
                                }
                            }

                            _markdownExportProgress.value = MarkdownExportProgress(noteCount, noteCount, isComplete = true)
                            if (exportedCount > 0) {
                                emitToast(getString(R.string.toast_markdown_exported, exportedCount))
                            } else {
                                // 🔧 v2.2.1: exportedCount==0 → Warnung, Feature bleibt aktiv.
                                // Bestandsnotizen werden beim nächsten Save/Sync exportiert.
                                emitToast(getString(R.string.toast_markdown_enabled))
                            }
                        } catch (e: TimeoutCancellationException) {
                            // 🔧 v2.2.1: Timeout → Feature bleibt aktiv (Prefs bereits gesetzt).
                            Logger.w(TAG, "Markdown initial export timed out: ${e.message}")
                            _markdownExportProgress.value = null
                            emitToast(getString(R.string.toast_export_timeout))
                        }

                        // Clear progress after short delay
                        kotlinx.coroutines.delay(PROGRESS_CLEAR_DELAY_MS)
                        _markdownExportProgress.value = null
                    } else {
                        // No notes — feature sofort aktivieren, kein Export nötig
                        _markdownExportProgress.value = null
                        emitToast(getString(R.string.toast_markdown_enabled))
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // 🔧 v2.2.1 (Issue #50): viewModelScope-Cancellation (Activity destroyed).
                    // Prefs sind bereits gesetzt — Feature funktioniert unabhängig vom Initial-Export.
                    throw e // CancellationException muss weiter propagiert werden
                } catch (e: Exception) {
                    // 🔧 v2.2.1: Bei echtem Fehler (z.B. Auth-Problem) → Feature deaktivieren + Prefs zurücksetzen.
                    _markdownAutoSync.value = false
                    _markdownExportProgress.value = null
                    prefs.edit {
                        putBoolean(Constants.KEY_MARKDOWN_EXPORT, false)
                        putBoolean(Constants.KEY_MARKDOWN_AUTO_IMPORT, false)
                    }
                    val syncService = WebDavSyncService(getApplication())
                    val userMessage = syncService.mapSyncExceptionToMessage(e)
                    emitToast(getString(R.string.toast_export_failed, userMessage))
                }
            }
        } else {
            // Disable - simple
            _markdownAutoSync.value = false
            prefs.edit {
                putBoolean(Constants.KEY_MARKDOWN_EXPORT, false)
                putBoolean(Constants.KEY_MARKDOWN_AUTO_IMPORT, false)
            }
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

    fun createBackup(uri: Uri, password: String? = null, includeServerSettings: Boolean = false) {
        viewModelScope.launch {
            _isBackupInProgress.value = true
            _backupStatusText.value = getString(R.string.backup_progress_creating)
            try {
                val result = backupManager.createBackup(uri, password, includeServerSettings)

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

    fun restoreFromFile(uri: Uri, mode: RestoreMode, password: String? = null, restoreServerSettings: Boolean = false) {
        viewModelScope.launch {
            _isBackupInProgress.value = true
            _backupStatusText.value = getString(R.string.backup_progress_restoring)
            try {
                val result = backupManager.restoreBackup(uri, mode, password, restoreServerSettings)

                // v1.9.0: Reload server settings StateFlows if they were restored
                if (restoreServerSettings && result.success) {
                    reloadServerSettingsFromPrefs()
                }

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
     * v1.9.0: Reloads all app settings from SharedPreferences into ViewModel StateFlows.
     * Called after a backup restore that includes app settings.
     */
    private fun reloadServerSettingsFromPrefs() {
        val url = prefs.getString(Constants.KEY_SERVER_URL, "").orEmpty()
        _isHttps.value = url.startsWith("https://")
        _serverHost.value = extractHostFromUrl(url)
        _username.value = CredentialStore.getUsername(getApplication()).orEmpty()
        _password.value = CredentialStore.getPassword(getApplication()).orEmpty()
        confirmedServerUrl = url
        confirmedSyncFolderName = prefs.getString(
            Constants.KEY_SYNC_FOLDER_NAME,
            Constants.DEFAULT_SYNC_FOLDER_NAME
        ) ?: Constants.DEFAULT_SYNC_FOLDER_NAME
        _syncFolderName.value = confirmedSyncFolderName
        _connectionTimeoutSeconds.value = prefs.getInt(
            Constants.KEY_CONNECTION_TIMEOUT_SECONDS,
            Constants.DEFAULT_CONNECTION_TIMEOUT_SECONDS
        ).coerceIn(Constants.MIN_CONNECTION_TIMEOUT_SECONDS, Constants.MAX_CONNECTION_TIMEOUT_SECONDS)
        _maxParallelConnections.value = prefs.getInt(
            Constants.KEY_MAX_PARALLEL_CONNECTIONS,
            Constants.DEFAULT_MAX_PARALLEL_CONNECTIONS
        ).coerceIn(Constants.MIN_PARALLEL_CONNECTIONS, Constants.MAX_PARALLEL_CONNECTIONS)
        _offlineMode.value = prefs.getBoolean(Constants.KEY_OFFLINE_MODE, true)
        _autoSyncEnabled.value = prefs.getBoolean(Constants.KEY_AUTO_SYNC, false)
        _wifiOnlySync.value = prefs.getBoolean(Constants.KEY_WIFI_ONLY_SYNC, Constants.DEFAULT_WIFI_ONLY_SYNC)
        _markdownAutoSync.value = prefs.getBoolean(Constants.KEY_MARKDOWN_EXPORT, false) &&
            prefs.getBoolean(Constants.KEY_MARKDOWN_AUTO_IMPORT, false)
        _triggerOnSave.value = prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_ON_SAVE, Constants.DEFAULT_TRIGGER_ON_SAVE)
        _triggerOnResume.value =
            prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_ON_RESUME, Constants.DEFAULT_TRIGGER_ON_RESUME)
        _triggerWifiConnect.value =
            prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_WIFI_CONNECT, Constants.DEFAULT_TRIGGER_WIFI_CONNECT)
        _triggerPeriodic.value =
            prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_PERIODIC, Constants.DEFAULT_TRIGGER_PERIODIC)
        _triggerBoot.value = prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_BOOT, Constants.DEFAULT_TRIGGER_BOOT)
        _syncInterval.value =
            prefs.getLong(Constants.PREF_SYNC_INTERVAL_MINUTES, Constants.DEFAULT_SYNC_INTERVAL_MINUTES)
        _displayMode.value =
            prefs.getString(Constants.KEY_DISPLAY_MODE, Constants.DEFAULT_DISPLAY_MODE)
                ?: Constants.DEFAULT_DISPLAY_MODE
        _gridAdaptiveScaling.value = prefs.getBoolean(
            Constants.KEY_GRID_ADAPTIVE_SCALING,
            Constants.DEFAULT_GRID_ADAPTIVE_SCALING
        )
        _gridManualColumns.value = prefs.getInt(
            Constants.KEY_GRID_MANUAL_COLUMNS,
            Constants.DEFAULT_GRID_MANUAL_COLUMNS
        )
        _themeMode.value = ThemePreferences.getThemeMode(prefs)
        _colorTheme.value = ThemePreferences.getColorTheme(prefs)
        _customAppTitle.value =
            prefs.getString(Constants.KEY_CUSTOM_APP_TITLE, Constants.DEFAULT_CUSTOM_APP_TITLE)
                ?: Constants.DEFAULT_CUSTOM_APP_TITLE
        _autosaveEnabled.value = prefs.getBoolean(Constants.KEY_AUTOSAVE_ENABLED, Constants.DEFAULT_AUTOSAVE_ENABLED)
        _notificationsEnabled.value =
            prefs.getBoolean(Constants.KEY_NOTIFICATIONS_ENABLED, Constants.DEFAULT_NOTIFICATIONS_ENABLED)
        _notificationsErrorsOnly.value =
            prefs.getBoolean(Constants.KEY_NOTIFICATIONS_ERRORS_ONLY, Constants.DEFAULT_NOTIFICATIONS_ERRORS_ONLY)
        _notificationsServerWarning.value =
            prefs.getBoolean(Constants.KEY_NOTIFICATIONS_SERVER_WARNING, Constants.DEFAULT_NOTIFICATIONS_SERVER_WARNING)
        Logger.d(TAG, "🔄 App settings reloaded from prefs after backup restore")
    }

    fun checkBackupContainsAppSettings(uri: Uri, password: String? = null, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = backupManager.backupContainsAppSettings(uri, password)
            onResult(result)
        }
    }

    /**
     * 🔐 v1.7.0: Check if backup is encrypted and call appropriate callback
     */
    fun checkBackupEncryption(uri: Uri, onEncrypted: () -> Unit, onPlaintext: () -> Unit) {
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
                onPlaintext() // Assume plaintext on error
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
        prefs.edit { putBoolean(Constants.KEY_FILE_LOGGING_ENABLED, enabled) }
        Logger.setFileLoggingEnabled(enabled)
        viewModelScope.launch {
            emitToast(
                if (enabled) {
                    getString(
                        R.string.toast_file_logging_enabled
                    )
                } else {
                    getString(R.string.toast_file_logging_disabled)
                }
            )
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            try {
                val cleared = Logger.clearLogFile(getApplication())
                emitToast(
                    if (cleared) getString(R.string.toast_logs_deleted) else getString(R.string.toast_logs_deleted)
                )
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
        prefs.edit { putInt(Constants.KEY_LAST_SHOWN_CHANGELOG_VERSION, 0) }
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

    /**
     * Zeigt eine Snackbar über den SettingsNavHost-Collector an.
     * Aufrufbar aus synchronen Click-Handlern (kein suspend).
     */
    fun showSnackbar(message: String) {
        _showSnackbar.tryEmit(message)
    }

    private suspend fun emitToast(message: String) {
        _showSnackbar.emit(message)
    }

    /**
     * Server status states
     * v1.6.0: Added OfflineMode state
     */
    sealed class ServerStatus {
        data object Unknown : ServerStatus()

        data object OfflineMode : ServerStatus() // 🌟 v1.6.0

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

        data object ShowBatteryOptimizationDialog : SettingsEvent()
    }

    /**
     * Progress state for Markdown export
     * v1.5.0: For initial export progress dialog
     */
    data class MarkdownExportProgress(
        val current: Int,
        val total: Int,
        val isComplete: Boolean = false,
        val isChecking: Boolean = false // 🆕 v1.10.0: Server-Check-Phase (indeterminate)
    )

    // ═══════════════════════════════════════════════════════════════════════
    // 🎨 v1.7.0: Display Mode Functions
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Set display mode (list or grid)
     */
    fun setDisplayMode(mode: String) {
        _displayMode.value = mode
        prefs.edit { putString(Constants.KEY_DISPLAY_MODE, mode) }
        Logger.d(TAG, "Display mode changed to: $mode")
    }

    // 🆕 v2.1.0 (F46): Grid column control setters
    fun setGridAdaptiveScaling(enabled: Boolean) {
        _gridAdaptiveScaling.value = enabled
        prefs.edit { putBoolean(Constants.KEY_GRID_ADAPTIVE_SCALING, enabled) }
        Logger.d(TAG, "Grid adaptive scaling: $enabled")
    }

    fun setGridManualColumns(columns: Int) {
        val clamped = columns.coerceIn(Constants.GRID_MIN_COLUMNS, Constants.GRID_MAX_COLUMNS)
        _gridManualColumns.value = clamped
        prefs.edit { putInt(Constants.KEY_GRID_MANUAL_COLUMNS, clamped) }
        Logger.d(TAG, "Grid manual columns: $clamped")
    }

    /**
     * 🆕 v1.9.0 (F05): Set custom app title.
     * Enforces max length limit.
     */
    fun setCustomAppTitle(title: String) {
        val sanitized = title.take(Constants.MAX_CUSTOM_APP_TITLE_LENGTH)
        _customAppTitle.value = sanitized
        prefs.edit { putString(Constants.KEY_CUSTOM_APP_TITLE, sanitized) }
        Logger.d(TAG, "Custom app title changed to: '$sanitized'")
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
    suspend fun importLocalFiles(uris: List<Uri>): dev.dettmer.simplenotes.noteimport.NotesImportWizard.ImportSummary =
        withContext(ioDispatcher) {
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
