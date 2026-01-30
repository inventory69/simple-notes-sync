@file:Suppress("DEPRECATION") // Legacy code using ProgressDialog & LocalBroadcastManager, will be removed in v2.0.0

package dev.dettmer.simplenotes

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import dev.dettmer.simplenotes.backup.BackupManager
import dev.dettmer.simplenotes.backup.RestoreMode
import dev.dettmer.simplenotes.utils.UrlValidator
import kotlinx.coroutines.withContext
import dev.dettmer.simplenotes.sync.WebDavSyncService
import dev.dettmer.simplenotes.sync.SyncStateManager
import dev.dettmer.simplenotes.sync.NetworkMonitor
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import dev.dettmer.simplenotes.utils.showToast
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

@Suppress("LargeClass", "DEPRECATION") // Legacy code using ProgressDialog & LocalBroadcastManager, will be removed in v2.0.0
class SettingsActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "SettingsActivity"
        private const val GITHUB_REPO_URL = "https://github.com/inventory69/simple-notes-sync"
        private const val GITHUB_PROFILE_URL = "https://github.com/inventory69"
        private const val LICENSE_URL = "https://github.com/inventory69/simple-notes-sync/blob/main/LICENSE"
        private const val CONNECTION_TIMEOUT_MS = 3000
    }
    
    private lateinit var textInputLayoutServerUrl: com.google.android.material.textfield.TextInputLayout
    private lateinit var editTextServerUrl: EditText
    private lateinit var editTextUsername: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var switchAutoSync: SwitchCompat
    private lateinit var switchMarkdownAutoSync: SwitchCompat
    private lateinit var buttonTestConnection: Button
    private lateinit var buttonSyncNow: Button
    private lateinit var buttonCreateBackup: Button
    private lateinit var buttonRestoreFromFile: Button
    private lateinit var buttonRestoreFromServer: Button
    private lateinit var buttonManualMarkdownSync: Button
    private lateinit var textViewServerStatus: TextView
    private lateinit var textViewManualSyncInfo: TextView
    
    // Protocol Selection UI
    private lateinit var protocolRadioGroup: RadioGroup
    private lateinit var radioHttp: RadioButton
    private lateinit var radioHttps: RadioButton
    private lateinit var protocolHintText: TextView
    
    // Sync Interval UI
    private lateinit var radioGroupSyncInterval: RadioGroup
    
    // About Section UI
    private lateinit var textViewAppVersion: TextView
    private lateinit var cardGitHubRepo: MaterialCardView
    private lateinit var cardDeveloperProfile: MaterialCardView
    private lateinit var cardLicense: MaterialCardView
    
    // Debug Section UI
    private lateinit var switchFileLogging: com.google.android.material.materialswitch.MaterialSwitch
    private lateinit var buttonExportLogs: Button
    private lateinit var buttonClearLogs: Button
    
    // Backup Manager
    private val backupManager by lazy { BackupManager(this) }
    
    // Activity Result Launchers
    private val createBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { createBackup(it) }
    }
    
    private val restoreBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { showRestoreDialog(RestoreSource.LOCAL_FILE, it) }
    }
    
    private val prefs by lazy {
        getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Apply Dynamic Colors for Android 12+ (Material You)
        DynamicColors.applyToActivityIfAvailable(this)
        
        setContentView(R.layout.activity_settings)
        
        // Setup toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Einstellungen"
        }
        
        findViews()
        loadSettings()
        setupListeners()
        setupSyncIntervalPicker()
        setupAboutSection()
        setupDebugSection()
    }
    
    private fun findViews() {
        textInputLayoutServerUrl = findViewById(R.id.textInputLayoutServerUrl)
        editTextServerUrl = findViewById(R.id.editTextServerUrl)
        editTextUsername = findViewById(R.id.editTextUsername)
        editTextPassword = findViewById(R.id.editTextPassword)
        switchAutoSync = findViewById(R.id.switchAutoSync)
        switchMarkdownAutoSync = findViewById(R.id.switchMarkdownAutoSync)
        buttonTestConnection = findViewById(R.id.buttonTestConnection)
        buttonSyncNow = findViewById(R.id.buttonSyncNow)
        buttonCreateBackup = findViewById(R.id.buttonCreateBackup)
        buttonRestoreFromFile = findViewById(R.id.buttonRestoreFromFile)
        buttonRestoreFromServer = findViewById(R.id.buttonRestoreFromServer)
        buttonManualMarkdownSync = findViewById(R.id.buttonManualMarkdownSync)
        textViewServerStatus = findViewById(R.id.textViewServerStatus)
        textViewManualSyncInfo = findViewById(R.id.textViewManualSyncInfo)
        
        // Protocol Selection UI
        protocolRadioGroup = findViewById(R.id.protocolRadioGroup)
        radioHttp = findViewById(R.id.radioHttp)
        radioHttps = findViewById(R.id.radioHttps)
        protocolHintText = findViewById(R.id.protocolHintText)
        
        // Sync Interval UI
        radioGroupSyncInterval = findViewById(R.id.radioGroupSyncInterval)
        
        // About Section UI
        textViewAppVersion = findViewById(R.id.textViewAppVersion)
        cardGitHubRepo = findViewById(R.id.cardGitHubRepo)
        cardDeveloperProfile = findViewById(R.id.cardDeveloperProfile)
        cardLicense = findViewById(R.id.cardLicense)
        
        // Debug Section UI
        switchFileLogging = findViewById(R.id.switchFileLogging)
        buttonExportLogs = findViewById(R.id.buttonExportLogs)
        buttonClearLogs = findViewById(R.id.buttonClearLogs)
    }
    
    private fun loadSettings() {
        val savedUrl = prefs.getString(Constants.KEY_SERVER_URL, "") ?: ""
        
        // Parse existing URL to extract protocol and host/path
        if (savedUrl.isNotEmpty()) {
            val (protocol, hostPath) = parseUrl(savedUrl)
            
            // Set protocol radio button
            when (protocol) {
                "http" -> radioHttp.isChecked = true
                "https" -> radioHttps.isChecked = true
                else -> radioHttp.isChecked = true // Default to HTTP (most users have local servers)
            }
            
            // Set URL with protocol prefix in the text field
            editTextServerUrl.setText("$protocol://$hostPath")
        } else {
            // Default: HTTP selected (lokale Server sind häufiger), empty URL with prefix
            radioHttp.isChecked = true
            editTextServerUrl.setText("http://")
        }
        
        editTextUsername.setText(prefs.getString(Constants.KEY_USERNAME, ""))
        editTextPassword.setText(prefs.getString(Constants.KEY_PASSWORD, ""))
        switchAutoSync.isChecked = prefs.getBoolean(Constants.KEY_AUTO_SYNC, false)
        
        // Load Markdown Auto-Sync (backward compatible)
        val markdownExport = prefs.getBoolean(Constants.KEY_MARKDOWN_EXPORT, false)
        val markdownAutoImport = prefs.getBoolean(Constants.KEY_MARKDOWN_AUTO_IMPORT, false)
        val markdownAutoSync = markdownExport && markdownAutoImport
        switchMarkdownAutoSync.isChecked = markdownAutoSync
        
        updateMarkdownButtonVisibility()
        
        // Update hint text based on selected protocol
        updateProtocolHint()
        
        // Server Status prüfen
        checkServerStatus()
    }
    
    /**
     * Parse URL into protocol and host/path components
     * @param url Full URL like "https://example.com:8080/webdav"
     * @return Pair of (protocol, hostPath) like ("https", "example.com:8080/webdav")
     */
    private fun parseUrl(url: String): Pair<String, String> {
        return when {
            url.startsWith("https://") -> "https" to url.removePrefix("https://")
            url.startsWith("http://") -> "http" to url.removePrefix("http://")
            else -> "http" to url // Default to HTTP if no protocol specified
        }
    }
    
    /**
     * Update the hint text below protocol selection based on selected protocol
     */
    private fun updateProtocolHint() {
        protocolHintText.text = if (radioHttp.isChecked) {
            getString(R.string.server_connection_http_hint)
        } else {
            getString(R.string.server_connection_https_hint)
        }
    }
    
    /**
     * Update protocol prefix in URL field when radio button changes
     * Keeps the host/path part, only changes http:// <-> https://
     */
    private fun updateProtocolInUrl() {
        val currentText = editTextServerUrl.text.toString()
        val newProtocol = if (radioHttp.isChecked) "http" else "https"
        
        // Extract host/path without protocol
        val hostPath = when {
            currentText.startsWith("https://") -> currentText.removePrefix("https://")
            currentText.startsWith("http://") -> currentText.removePrefix("http://")
            else -> currentText
        }
        
        // Set new URL with correct protocol
        editTextServerUrl.setText("$newProtocol://$hostPath")
        
        // Move cursor to end
        editTextServerUrl.setSelection(editTextServerUrl.text?.length ?: 0)
    }
    
    private fun setupListeners() {
        // Protocol selection listener - update URL prefix when radio changes
        protocolRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            updateProtocolInUrl()
            updateProtocolHint()
        }
        
        buttonTestConnection.setOnClickListener {
            saveSettings()
            testConnection()
        }
        
        buttonSyncNow.setOnClickListener {
            saveSettings()
            syncNow()
        }
        
        buttonCreateBackup.setOnClickListener {
            // Dateiname mit Timestamp
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)
                .format(java.util.Date())
            val filename = "simplenotes_backup_$timestamp.json"
            createBackupLauncher.launch(filename)
        }
        
        buttonRestoreFromFile.setOnClickListener {
            restoreBackupLauncher.launch(arrayOf("application/json"))
        }
        
        buttonRestoreFromServer.setOnClickListener {
            saveSettings()
            showRestoreDialog(RestoreSource.WEBDAV_SERVER, null)
        }
        
        buttonManualMarkdownSync.setOnClickListener {
            performManualMarkdownSync()
        }
        
        switchAutoSync.setOnCheckedChangeListener { _, isChecked ->
            onAutoSyncToggled(isChecked)
        }
        
        switchMarkdownAutoSync.setOnCheckedChangeListener { _, isChecked ->
            onMarkdownAutoSyncToggled(isChecked)
        }
        
        // Clear error when user starts typing again
        editTextServerUrl.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                textInputLayoutServerUrl.error = null
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        
        // Server Status Check bei Settings-Änderung
        editTextServerUrl.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                checkServerStatus()
            }
        }
    }
    
    /**
     * Setup sync interval picker with radio buttons
     */
    private fun setupSyncIntervalPicker() {
        // Load current interval from preferences
        val currentInterval = prefs.getLong(
            Constants.PREF_SYNC_INTERVAL_MINUTES,
            Constants.DEFAULT_SYNC_INTERVAL_MINUTES
        )
        
        // Set checked radio button based on current interval
        val checkedId = when (currentInterval) {
            15L -> R.id.radioInterval15
            30L -> R.id.radioInterval30
            60L -> R.id.radioInterval60
            else -> R.id.radioInterval30 // Default
        }
        radioGroupSyncInterval.check(checkedId)
        
        // Listen for interval changes
        radioGroupSyncInterval.setOnCheckedChangeListener { _, checkedId ->
            val newInterval = when (checkedId) {
                R.id.radioInterval15 -> 15L
                R.id.radioInterval60 -> 60L
                else -> 30L // R.id.radioInterval30 or fallback
            }
            
            // Save new interval to preferences
            prefs.edit().putLong(Constants.PREF_SYNC_INTERVAL_MINUTES, newInterval).apply()
            
            // Restart periodic sync with new interval (only if auto-sync is enabled)
            if (prefs.getBoolean(Constants.KEY_AUTO_SYNC, false)) {
                val networkMonitor = NetworkMonitor(this)
                networkMonitor.startMonitoring()
                
                val intervalText = when (newInterval) {
                    15L -> "15 Minuten"
                    30L -> "30 Minuten"
                    60L -> "60 Minuten"
                    else -> "$newInterval Minuten"
                }
                showToast(getString(R.string.toast_sync_interval_changed, intervalText))
                Logger.i(TAG, "Sync interval changed to $newInterval minutes, restarted periodic sync")
            } else {
                showToast("⏱️ Sync-Intervall gespeichert (Auto-Sync ist deaktiviert)")
            }
        }
    }
    
    /**
     * Setup about section with version info and clickable cards
     */
    private fun setupAboutSection() {
        // Display app version
        try {
            val versionName = BuildConfig.VERSION_NAME
            val versionCode = BuildConfig.VERSION_CODE
            
            textViewAppVersion.text = "Version $versionName ($versionCode)"
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to load version info", e)
            textViewAppVersion.text = getString(R.string.version_not_available)
        }
        
        // GitHub Repository Card
        cardGitHubRepo.setOnClickListener {
            openUrl(GITHUB_REPO_URL)
        }
        
        // Developer Profile Card
        cardDeveloperProfile.setOnClickListener {
            openUrl(GITHUB_PROFILE_URL)
        }
        
        // License Card
        cardLicense.setOnClickListener {
            openUrl(LICENSE_URL)
        }
    }
    
    /**
     * Setup Debug section with file logging toggle and export functionality
     */
    private fun setupDebugSection() {
        // Load current file logging state
        val fileLoggingEnabled = prefs.getBoolean(Constants.KEY_FILE_LOGGING_ENABLED, false)
        switchFileLogging.isChecked = fileLoggingEnabled
        
        // Update Logger state
        Logger.setFileLoggingEnabled(fileLoggingEnabled)
        
        // Toggle file logging
        switchFileLogging.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(Constants.KEY_FILE_LOGGING_ENABLED, isChecked).apply()
            Logger.setFileLoggingEnabled(isChecked)
            
            if (isChecked) {
                showToast("📝 Datei-Logging aktiviert")
                Logger.i(TAG, "File logging enabled by user")
            } else {
                showToast("📝 Datei-Logging deaktiviert")
            }
        }
        
        // Export logs button
        buttonExportLogs.setOnClickListener {
            exportAndShareLogs()
        }
        
        // Clear logs button
        buttonClearLogs.setOnClickListener {
            showClearLogsConfirmation()
        }
    }
    
    /**
     * Export logs and share via system share sheet
     */
    private fun exportAndShareLogs() {
        lifecycleScope.launch {
            try {
                val logFile = Logger.getLogFile(this@SettingsActivity)
                
                if (logFile == null || !logFile.exists() || logFile.length() == 0L) {
                    showToast("📭 Keine Logs vorhanden")
                    return@launch
                }
                
                // Create share intent using FileProvider
                val logUri = FileProvider.getUriForFile(
                    this@SettingsActivity,
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                    logFile
                )
                
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, logUri)
                    putExtra(Intent.EXTRA_SUBJECT, "SimpleNotes Sync Logs")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                startActivity(Intent.createChooser(shareIntent, "Logs teilen via..."))
                Logger.i(TAG, "Logs exported and shared")
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to export logs", e)
                showToast("❌ Fehler beim Exportieren: ${e.message}")
            }
        }
    }
    
    /**
     * Show confirmation dialog before clearing logs
     */
    private fun showClearLogsConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.debug_delete_logs_title))
            .setMessage(getString(R.string.debug_delete_logs_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                clearLogs()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    /**
     * Clear all log files
     */
    private fun clearLogs() {
        try {
            val cleared = Logger.clearLogFile(this)
            if (cleared) {
                showToast(getString(R.string.toast_logs_deleted))
            } else {
                showToast(getString(R.string.toast_no_logs_to_delete))
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to clear logs", e)
            showToast(getString(R.string.toast_logs_delete_error, e.message ?: ""))
        }
    }
    
    /**
     * Opens URL in browser
     */
    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to open URL: $url", e)
            showToast(getString(R.string.toast_link_error))
        }
    }
    
    private fun saveSettings() {
        // URL is already complete with protocol in the text field (http:// or https://)
        val fullUrl = editTextServerUrl.text.toString().trim()
        
        // Clear previous error
        textInputLayoutServerUrl.error = null
        textInputLayoutServerUrl.isErrorEnabled = false
        
        // 🔥 v1.1.2: Validate HTTP URL (only allow for local networks)
        if (fullUrl.isNotEmpty()) {
            val (isValid, errorMessage) = UrlValidator.validateHttpUrl(this, fullUrl)
            if (!isValid) {
                // Only show error in TextField (no Toast)
                textInputLayoutServerUrl.isErrorEnabled = true
                textInputLayoutServerUrl.error = errorMessage
                return
            }
        }
        
        prefs.edit().apply {
            putString(Constants.KEY_SERVER_URL, fullUrl)
            putString(Constants.KEY_USERNAME, editTextUsername.text.toString().trim())
            putString(Constants.KEY_PASSWORD, editTextPassword.text.toString().trim())
            putBoolean(Constants.KEY_AUTO_SYNC, switchAutoSync.isChecked)
            apply()
        }
    }
    
    private fun testConnection() {
        // URL is already complete with protocol in the text field (http:// or https://)
        val fullUrl = editTextServerUrl.text.toString().trim()
        
        // Clear previous error
        textInputLayoutServerUrl.error = null
        textInputLayoutServerUrl.isErrorEnabled = false
        
        // 🔥 v1.1.2: Validate before testing
        if (fullUrl.isNotEmpty()) {
            val (isValid, errorMessage) = UrlValidator.validateHttpUrl(this, fullUrl)
            if (!isValid) {
                // Only show error in TextField (no Toast)
                textInputLayoutServerUrl.isErrorEnabled = true
                textInputLayoutServerUrl.error = errorMessage
                return
            }
        }
        
        lifecycleScope.launch {
            try {
                showToast("Teste Verbindung...")
                val syncService = WebDavSyncService(this@SettingsActivity)
                val result = syncService.testConnection()
                
                if (result.isSuccess) {
                    showToast("Verbindung erfolgreich!")
                    checkServerStatus() // ✅ Server-Status sofort aktualisieren
                } else {
                    showToast("Verbindung fehlgeschlagen: ${result.errorMessage}")
                    checkServerStatus() // ✅ Auch bei Fehler aktualisieren
                }
            } catch (e: Exception) {
                showToast("Fehler: ${e.message}")
                checkServerStatus() // ✅ Auch bei Exception aktualisieren
            }
        }
    }
    
    private fun syncNow() {
        // 🔄 v1.3.1: Check if sync already running (Button wird deaktiviert)
        if (!SyncStateManager.tryStartSync("settings")) {
            return
        }
        
        // Disable button during sync
        buttonSyncNow.isEnabled = false
        
        lifecycleScope.launch {
            try {
                val syncService = WebDavSyncService(this@SettingsActivity)
                
                // 🔥 v1.1.2: Check if there are unsynced changes first (performance optimization)
                if (!syncService.hasUnsyncedChanges()) {
                    showToast("✅ Bereits synchronisiert")
                    SyncStateManager.markCompleted()
                    return@launch
                }
                
                showToast("🔄 Synchronisiere...")
                
                // ⭐ WICHTIG: Server-Erreichbarkeits-Check VOR Sync (wie in anderen Triggern)
                if (!syncService.isServerReachable()) {
                    showToast("⚠️ ${getString(R.string.snackbar_server_unreachable)}")
                    SyncStateManager.markError(getString(R.string.snackbar_server_unreachable))
                    checkServerStatus() // Server-Status aktualisieren
                    return@launch
                }
                
                val result = syncService.syncNotes()
                
                if (result.isSuccess) {
                    if (result.hasConflicts) {
                        showToast("✅ Sync abgeschlossen. ${result.conflictCount} Konflikte erkannt!")
                    } else {
                        showToast("✅ Erfolgreich! ${result.syncedCount} Notizen synchronisiert")
                    }
                    SyncStateManager.markCompleted("${result.syncedCount} Notizen")
                    checkServerStatus() // ✅ Server-Status nach Sync aktualisieren
                } else {
                    showToast("❌ Sync fehlgeschlagen: ${result.errorMessage}")
                    SyncStateManager.markError(result.errorMessage)
                    checkServerStatus() // ✅ Auch bei Fehler aktualisieren
                }
            } catch (e: Exception) {
                showToast("❌ Fehler: ${e.message}")
                SyncStateManager.markError(e.message)
                checkServerStatus() // ✅ Auch bei Exception aktualisieren
            } finally {
                // Re-enable button
                buttonSyncNow.isEnabled = true
            }
        }
    }
    
    private fun checkServerStatus() {
        val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null)
        
        if (serverUrl.isNullOrEmpty()) {
            textViewServerStatus.text = "❌ Nicht konfiguriert"
            textViewServerStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            return
        }
        
        textViewServerStatus.text = getString(R.string.status_checking)
        textViewServerStatus.setTextColor(getColor(android.R.color.darker_gray))
        
        lifecycleScope.launch {
            val isReachable = withContext(Dispatchers.IO) {
                try {
                    val url = URL(serverUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = CONNECTION_TIMEOUT_MS
                    connection.readTimeout = CONNECTION_TIMEOUT_MS
                    val code = connection.responseCode
                    connection.disconnect()
                    code in 200..299 || code == 401  // 401 = Server da, Auth fehlt
                } catch (e: Exception) {
                    Log.e(TAG, "Server check failed: ${e.message}")
                    false
                }
            }
            
            if (isReachable) {
                textViewServerStatus.text = "✅ Erreichbar"
                textViewServerStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            } else {
                textViewServerStatus.text = "❌ Nicht erreichbar"
                textViewServerStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            }
        }
    }
    
    private fun onAutoSyncToggled(enabled: Boolean) {
        prefs.edit().putBoolean(Constants.KEY_AUTO_SYNC, enabled).apply()
        
        if (enabled) {
            showToast("Auto-Sync aktiviert")
            checkBatteryOptimization()
            restartNetworkMonitor()
        } else {
            showToast("Auto-Sync deaktiviert")
            restartNetworkMonitor()
        }
    }
    
    private fun onMarkdownAutoSyncToggled(enabled: Boolean) {
        if (enabled) {
            // Initial-Export wenn Feature aktiviert wird
            lifecycleScope.launch {
                try {
                    val noteStorage = dev.dettmer.simplenotes.storage.NotesStorage(this@SettingsActivity)
                    val currentNoteCount = noteStorage.loadAllNotes().size
                    
                    if (currentNoteCount > 0) {
                        // Zeige Progress-Dialog
                        val progressDialog = ProgressDialog(this@SettingsActivity).apply {
                            setTitle("Markdown Auto-Sync")
                            setMessage("Exportiere Notizen nach Markdown...")
                            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
                            max = currentNoteCount
                            progress = 0
                            setCancelable(false)
                            show()
                        }
                        
                        try {
                            // Hole Server-Daten
                            val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, "") ?: ""
                            val username = prefs.getString(Constants.KEY_USERNAME, "") ?: ""
                            val password = prefs.getString(Constants.KEY_PASSWORD, "") ?: ""
                            
                            if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
                                progressDialog.dismiss()
                                showToast("⚠️ Bitte zuerst WebDAV-Server konfigurieren")
                                switchMarkdownAutoSync.isChecked = false
                                return@launch
                            }
                            
                            // Führe Initial-Export aus
                            val syncService = WebDavSyncService(this@SettingsActivity)
                            val exportedCount = syncService.exportAllNotesToMarkdown(
                                serverUrl = serverUrl,
                                username = username,
                                password = password,
                                onProgress = { current, total ->
                                    runOnUiThread {
                                        progressDialog.progress = current
                                        progressDialog.setMessage("Exportiere $current/$total Notizen...")
                                    }
                                }
                            )
                            
                            progressDialog.dismiss()
                            
                            // Speichere beide Einstellungen
                            prefs.edit()
                                .putBoolean(Constants.KEY_MARKDOWN_EXPORT, enabled)
                                .putBoolean(Constants.KEY_MARKDOWN_AUTO_IMPORT, enabled)
                                .apply()
                            
                            updateMarkdownButtonVisibility()
                            
                            // Erfolgs-Nachricht
                            showToast("✅ $exportedCount Notizen nach Markdown exportiert")
                            
                        } catch (e: Exception) {
                            progressDialog.dismiss()
                            showToast("❌ Export fehlgeschlagen: ${e.message}")
                            
                            // Deaktiviere Toggle bei Fehler
                            switchMarkdownAutoSync.isChecked = false
                            return@launch
                        }
                    } else {
                        // Keine Notizen vorhanden - speichere Einstellungen direkt
                        prefs.edit()
                            .putBoolean(Constants.KEY_MARKDOWN_EXPORT, enabled)
                            .putBoolean(Constants.KEY_MARKDOWN_AUTO_IMPORT, enabled)
                            .apply()
                        
                        updateMarkdownButtonVisibility()
                        showToast(
                            "Markdown Auto-Sync aktiviert - " +
                                "Notizen werden als .md-Dateien exportiert und importiert"
                        )
                    }
                    
                } catch (e: Exception) {
                    Logger.e(TAG, "Error toggling markdown auto-sync: ${e.message}")
                    showToast("Fehler: ${e.message}")
                    switchMarkdownAutoSync.isChecked = false
                }
            }
        } else {
            // Deaktivieren - Settings speichern
            prefs.edit()
                .putBoolean(Constants.KEY_MARKDOWN_EXPORT, enabled)
                .putBoolean(Constants.KEY_MARKDOWN_AUTO_IMPORT, enabled)
                .apply()
            
            updateMarkdownButtonVisibility()
            showToast("Markdown Auto-Sync deaktiviert - nur JSON-Sync aktiv")
        }
    }
    
    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = packageName
        
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            showBatteryOptimizationDialog()
        }
    }
    
    private fun showBatteryOptimizationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Hintergrund-Synchronisation")
            .setMessage(
                "Damit die App im Hintergrund synchronisieren kann, " +
                "muss die Akku-Optimierung deaktiviert werden.\n\n" +
                getString(R.string.battery_optimization_dialog_message)
            )
            .setPositiveButton(getString(R.string.battery_optimization_open_settings)) { _, _ ->
                openBatteryOptimizationSettings()
            }
            .setNegativeButton(getString(R.string.battery_optimization_later)) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun openBatteryOptimizationSettings() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to open battery optimization settings: ${e.message}")
            // Fallback: Open general battery settings
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            } catch (e2: Exception) {
                Logger.w(TAG, "Failed to open fallback battery settings: ${e2.message}")
                showToast("Bitte Akku-Optimierung manuell deaktivieren")
            }
        }
    }
    
    private fun restartNetworkMonitor() {
        try {
            val app = application as SimpleNotesApplication
            Log.d(TAG, "🔄 Restarting NetworkMonitor with new settings")
            app.networkMonitor.stopMonitoring()
            app.networkMonitor.startMonitoring()
            Log.d(TAG, "✅ NetworkMonitor restarted successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to restart NetworkMonitor", e)
            showToast("Fehler beim Neustart des NetworkMonitors")
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                saveSettings()
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onPause() {
        super.onPause()
        saveSettings()
    }
    
    // ========================================
    // BACKUP & RESTORE FUNCTIONS (v1.2.0)
    // ========================================
    
    /**
     * Restore-Quelle (Lokale Datei oder WebDAV Server)
     */
    private enum class RestoreSource {
        LOCAL_FILE,
        WEBDAV_SERVER
    }
    
    /**
     * Erstellt Backup (Task #1.2.0-04)
     */
    private fun createBackup(uri: Uri) {
        lifecycleScope.launch {
            try {
                Logger.d(TAG, "📦 Creating backup...")
                val result = backupManager.createBackup(uri)
                
                if (result.success) {
                    showToast("✅ ${result.message}")
                } else {
                    showErrorDialog("Backup fehlgeschlagen", result.error ?: "Unbekannter Fehler")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to create backup", e)
                showErrorDialog("Backup fehlgeschlagen", e.message ?: "Unbekannter Fehler")
            }
        }
    }
    
    /**
     * Universeller Restore-Dialog für beide Quellen (Task #1.2.0-05 + #1.2.0-05b)
     * 
     * @param source Lokale Datei oder WebDAV Server
     * @param fileUri URI der lokalen Datei (nur für LOCAL_FILE)
     */
    private fun showRestoreDialog(source: RestoreSource, fileUri: Uri?) {
        val sourceText = when (source) {
            RestoreSource.LOCAL_FILE -> "Lokale Datei"
            RestoreSource.WEBDAV_SERVER -> "WebDAV Server"
        }
        
        // Custom View mit Radio Buttons
        val radioGroup = android.widget.RadioGroup(this).apply {
            orientation = android.widget.RadioGroup.VERTICAL
            setPadding(50, 20, 50, 20)
        }
        
        // Radio Buttons erstellen
        val radioMerge = android.widget.RadioButton(this).apply {
            text = getString(R.string.backup_mode_merge_full)
            id = android.view.View.generateViewId()
            isChecked = true
            setPadding(10, 10, 10, 10)
        }
        
        val radioReplace = android.widget.RadioButton(this).apply {
            text = getString(R.string.backup_mode_replace_full)
            id = android.view.View.generateViewId()
            setPadding(10, 10, 10, 10)
        }
        
        val radioOverwrite = android.widget.RadioButton(this).apply {
            text = getString(R.string.backup_mode_overwrite_full)
            id = android.view.View.generateViewId()
            setPadding(10, 10, 10, 10)
        }
        
        radioGroup.addView(radioMerge)
        radioGroup.addView(radioReplace)
        radioGroup.addView(radioOverwrite)
        
        // Hauptlayout
        val mainLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 30, 50, 30)
        }
        
        // Info Text
        val infoText = android.widget.TextView(this).apply {
            text = "Quelle: $sourceText\n\nWiederherstellungs-Modus:"
            textSize = 16f
            setPadding(0, 0, 0, 20)
        }
        
        // Hinweis Text
        val hintText = android.widget.TextView(this).apply {
            text = "\nℹ️ Ein Sicherheits-Backup wird vor dem Wiederherstellen automatisch erstellt."
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.ITALIC)
            setPadding(0, 20, 0, 0)
        }
        
        mainLayout.addView(infoText)
        mainLayout.addView(radioGroup)
        mainLayout.addView(hintText)
        
        // Dialog erstellen
        AlertDialog.Builder(this)
            .setTitle("⚠️ Backup wiederherstellen?")
            .setView(mainLayout)
            .setPositiveButton("Wiederherstellen") { _, _ ->
                val selectedMode = when (radioGroup.checkedRadioButtonId) {
                    radioReplace.id -> RestoreMode.REPLACE
                    radioOverwrite.id -> RestoreMode.OVERWRITE_DUPLICATES
                    else -> RestoreMode.MERGE
                }
                
                when (source) {
                    RestoreSource.LOCAL_FILE -> fileUri?.let { performRestoreFromFile(it, selectedMode) }
                    RestoreSource.WEBDAV_SERVER -> performRestoreFromServer(selectedMode)
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    
    /**
     * Führt Restore aus lokaler Datei durch (Task #1.2.0-05)
     */
    private fun performRestoreFromFile(uri: Uri, mode: RestoreMode) {
        lifecycleScope.launch {
            val progressDialog = android.app.ProgressDialog(this@SettingsActivity).apply {
                setMessage("Wiederherstellen...")
                setCancelable(false)
                show()
            }
            
            try {
                Logger.d(TAG, "📥 Restoring from file: $uri (mode: $mode)")
                val result = backupManager.restoreBackup(uri, mode)
                
                progressDialog.dismiss()
                
                if (result.success) {
                    val message = result.message ?: "Wiederhergestellt: ${result.importedNotes} Notizen"
                    showToast("✅ $message")
                    
                    // Refresh MainActivity's note list
                    setResult(RESULT_OK)
                    broadcastNotesChanged(result.importedNotes)
                } else {
                    showErrorDialog("Wiederherstellung fehlgeschlagen", result.error ?: "Unbekannter Fehler")
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                Logger.e(TAG, "Failed to restore from file", e)
                showErrorDialog("Wiederherstellung fehlgeschlagen", e.message ?: "Unbekannter Fehler")
            }
        }
    }
    
    /**
     * Server-Restore mit Restore-Modi (v1.3.0)
     */
    private fun performRestoreFromServer(mode: RestoreMode) {
        lifecycleScope.launch {
            val progressDialog = android.app.ProgressDialog(this@SettingsActivity).apply {
                setMessage("Wiederherstellen vom Server...")
                setCancelable(false)
                show()
            }
            
            try {
                Logger.d(TAG, "📥 Restoring from server (mode: $mode)")
                
                // Auto-Backup erstellen (Sicherheitsnetz)
                val autoBackupUri = backupManager.createAutoBackup()
                if (autoBackupUri == null) {
                    Logger.w(TAG, "⚠️ Auto-backup failed, but continuing with restore")
                }
                
                // Server-Restore durchführen
                val webdavService = WebDavSyncService(this@SettingsActivity)
                val result = withContext(Dispatchers.IO) {
                    webdavService.restoreFromServer(mode)  // ✅ Pass mode parameter
                }
                
                progressDialog.dismiss()
                
                if (result.isSuccess) {
                    showToast("✅ Wiederhergestellt: ${result.restoredCount} Notizen")
                    setResult(RESULT_OK)
                    broadcastNotesChanged(result.restoredCount)
                } else {
                    showErrorDialog("Wiederherstellung fehlgeschlagen", result.errorMessage ?: "Unbekannter Fehler")
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                Logger.e(TAG, "Failed to restore from server", e)
                showErrorDialog("Wiederherstellung fehlgeschlagen", e.message ?: "Unbekannter Fehler")
            }
        }
    }
    
    /**
     * Sendet Broadcast dass Notizen geändert wurden
     */
    private fun broadcastNotesChanged(count: Int = 0) {
        val intent = Intent(dev.dettmer.simplenotes.sync.SyncWorker.ACTION_SYNC_COMPLETED)
        intent.putExtra("success", true)
        intent.putExtra("syncedCount", count)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
    
    /**
     * Updates visibility of manual sync button based on Auto-Sync toggle state
     */
    private fun updateMarkdownButtonVisibility() {
        val autoSyncEnabled = switchMarkdownAutoSync.isChecked
        val visibility = if (autoSyncEnabled) View.GONE else View.VISIBLE
        
        textViewManualSyncInfo.visibility = visibility
        buttonManualMarkdownSync.visibility = visibility
    }
    
    /**
     * Performs manual Markdown sync (Export + Import)
     * Called when manual sync button is clicked
     */
    private fun performManualMarkdownSync() {
        lifecycleScope.launch {
            var progressDialog: ProgressDialog? = null
            try {
                // Validierung
                val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, "")
                val username = prefs.getString(Constants.KEY_USERNAME, "")
                val password = prefs.getString(Constants.KEY_PASSWORD, "")
                
                if (serverUrl.isNullOrBlank() || username.isNullOrBlank() || password.isNullOrBlank()) {
                    showToast("⚠️ Bitte zuerst WebDAV-Server konfigurieren")
                    return@launch
                }
                
                // Progress-Dialog
                progressDialog = ProgressDialog(this@SettingsActivity).apply {
                    setTitle("Markdown-Sync")
                    setMessage("Synchronisiere Markdown-Dateien...")
                    setCancelable(false)
                    show()
                }
                
                // Sync ausführen
                val syncService = dev.dettmer.simplenotes.sync.WebDavSyncService(this@SettingsActivity)
                val result = syncService.manualMarkdownSync()
                
                progressDialog.dismiss()
                
                // Erfolgs-Nachricht
                val message = "✅ Sync abgeschlossen\n" +
                    "📤 ${result.exportedCount} exportiert\n" +
                    "📥 ${result.importedCount} importiert"
                showToast(message)
                
                Logger.d(
                    "SettingsActivity",
                    "Manual markdown sync: exported=${result.exportedCount}, " +
                        "imported=${result.importedCount}"
                )
                
            } catch (e: Exception) {
                progressDialog?.dismiss()
                showToast("❌ Sync fehlgeschlagen: ${e.message}")
                Logger.e("SettingsActivity", "Manual markdown sync failed", e)
            }
        }
    }
    
    /**
     * Zeigt Error-Dialog an
     */
    private fun showErrorDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
