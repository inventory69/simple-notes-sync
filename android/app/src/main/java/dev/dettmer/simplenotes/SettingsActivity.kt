package dev.dettmer.simplenotes

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.color.DynamicColors
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.dettmer.simplenotes.sync.WebDavSyncService
import dev.dettmer.simplenotes.sync.NetworkMonitor
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import dev.dettmer.simplenotes.utils.showToast
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

class SettingsActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "SettingsActivity"
        private const val GITHUB_REPO_URL = "https://github.com/inventory69/simple-notes-sync"
        private const val GITHUB_PROFILE_URL = "https://github.com/inventory69"
        private const val LICENSE_URL = "https://github.com/inventory69/simple-notes-sync/blob/main/LICENSE"
    }
    
    private lateinit var editTextServerUrl: EditText
    private lateinit var editTextUsername: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var switchAutoSync: SwitchCompat
    private lateinit var buttonTestConnection: Button
    private lateinit var buttonSyncNow: Button
    private lateinit var buttonRestoreFromServer: Button
    private lateinit var textViewServerStatus: TextView
    private lateinit var chipAutoSaveStatus: Chip
    
    // Sync Interval UI
    private lateinit var radioGroupSyncInterval: RadioGroup
    
    // About Section UI
    private lateinit var textViewAppVersion: TextView
    private lateinit var cardGitHubRepo: MaterialCardView
    private lateinit var cardDeveloperProfile: MaterialCardView
    private lateinit var cardLicense: MaterialCardView
    
    private var autoSaveIndicatorJob: Job? = null
    
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
    }
    
    private fun findViews() {
        editTextServerUrl = findViewById(R.id.editTextServerUrl)
        editTextUsername = findViewById(R.id.editTextUsername)
        editTextPassword = findViewById(R.id.editTextPassword)
        switchAutoSync = findViewById(R.id.switchAutoSync)
        buttonTestConnection = findViewById(R.id.buttonTestConnection)
        buttonSyncNow = findViewById(R.id.buttonSyncNow)
        buttonRestoreFromServer = findViewById(R.id.buttonRestoreFromServer)
        textViewServerStatus = findViewById(R.id.textViewServerStatus)
        chipAutoSaveStatus = findViewById(R.id.chipAutoSaveStatus)
        
        // Sync Interval UI
        radioGroupSyncInterval = findViewById(R.id.radioGroupSyncInterval)
        
        // About Section UI
        textViewAppVersion = findViewById(R.id.textViewAppVersion)
        cardGitHubRepo = findViewById(R.id.cardGitHubRepo)
        cardDeveloperProfile = findViewById(R.id.cardDeveloperProfile)
        cardLicense = findViewById(R.id.cardLicense)
    }
    
    private fun loadSettings() {
        editTextServerUrl.setText(prefs.getString(Constants.KEY_SERVER_URL, ""))
        editTextUsername.setText(prefs.getString(Constants.KEY_USERNAME, ""))
        editTextPassword.setText(prefs.getString(Constants.KEY_PASSWORD, ""))
        switchAutoSync.isChecked = prefs.getBoolean(Constants.KEY_AUTO_SYNC, false)
        
        // Server Status prüfen
        checkServerStatus()
    }
    
    private fun setupListeners() {
        buttonTestConnection.setOnClickListener {
            saveSettings()
            testConnection()
        }
        
        buttonSyncNow.setOnClickListener {
            saveSettings()
            syncNow()
        }
        
        buttonRestoreFromServer.setOnClickListener {
            saveSettings()
            showRestoreConfirmation()
        }
        
        switchAutoSync.setOnCheckedChangeListener { _, isChecked ->
            onAutoSyncToggled(isChecked)
            showAutoSaveIndicator()
        }
        
        // Server Status Check bei Settings-Änderung
        editTextServerUrl.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                checkServerStatus()
                showAutoSaveIndicator()
            }
        }
        
        editTextUsername.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) showAutoSaveIndicator()
        }
        
        editTextPassword.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) showAutoSaveIndicator()
        }
    }
    
    /**
     * Setup sync interval picker with radio buttons
     */
    private fun setupSyncIntervalPicker() {
        // Load current interval from preferences
        val currentInterval = prefs.getLong(Constants.PREF_SYNC_INTERVAL_MINUTES, Constants.DEFAULT_SYNC_INTERVAL_MINUTES)
        
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
                showToast("⏱️ Sync-Intervall auf $intervalText geändert")
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
        // Display app version with build date
        try {
            val versionName = BuildConfig.VERSION_NAME
            val versionCode = BuildConfig.VERSION_CODE
            val buildDate = BuildConfig.BUILD_DATE
            
            textViewAppVersion.text = "Version $versionName ($versionCode)\nErstellt am: $buildDate"
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to load version info", e)
            textViewAppVersion.text = "Version nicht verfügbar"
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
     * Opens URL in browser
     */
    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to open URL: $url", e)
            showToast("❌ Fehler beim Öffnen des Links")
        }
    }
    
    private fun saveSettings() {
        prefs.edit().apply {
            putString(Constants.KEY_SERVER_URL, editTextServerUrl.text.toString().trim())
            putString(Constants.KEY_USERNAME, editTextUsername.text.toString().trim())
            putString(Constants.KEY_PASSWORD, editTextPassword.text.toString().trim())
            putBoolean(Constants.KEY_AUTO_SYNC, switchAutoSync.isChecked)
            apply()
        }
    }
    
    private fun testConnection() {
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
        lifecycleScope.launch {
            try {
                showToast("Synchronisiere...")
                val syncService = WebDavSyncService(this@SettingsActivity)
                val result = syncService.syncNotes()
                
                if (result.isSuccess) {
                    if (result.hasConflicts) {
                        showToast("Sync abgeschlossen. ${result.conflictCount} Konflikte erkannt!")
                    } else {
                        showToast("Erfolgreich! ${result.syncedCount} Notizen synchronisiert")
                    }
                    checkServerStatus() // ✅ Server-Status nach Sync aktualisieren
                } else {
                    showToast("Sync fehlgeschlagen: ${result.errorMessage}")
                    checkServerStatus() // ✅ Auch bei Fehler aktualisieren
                }
            } catch (e: Exception) {
                showToast("Fehler: ${e.message}")
                checkServerStatus() // ✅ Auch bei Exception aktualisieren
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
        
        textViewServerStatus.text = "🔍 Prüfe..."
        textViewServerStatus.setTextColor(getColor(android.R.color.darker_gray))
        
        lifecycleScope.launch {
            val isReachable = withContext(Dispatchers.IO) {
                try {
                    val url = URL(serverUrl)
                    val connection = url.openConnection() as HttpURLConnection
                    connection.connectTimeout = 3000
                    connection.readTimeout = 3000
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
                "Bitte wähle 'Nicht optimieren' für Simple Notes."
            )
            .setPositiveButton("Einstellungen öffnen") { _, _ ->
                openBatteryOptimizationSettings()
            }
            .setNegativeButton("Später") { dialog, _ ->
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
            // Fallback: Open general battery settings
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            } catch (e2: Exception) {
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
    
    private fun showAutoSaveIndicator() {
        // Cancel previous job if still running
        autoSaveIndicatorJob?.cancel()
        
        // Show saving indicator
        chipAutoSaveStatus.apply {
            visibility = android.view.View.VISIBLE
            text = "💾 Speichere..."
            setChipBackgroundColorResource(android.R.color.darker_gray)
        }
        
        // Save settings
        saveSettings()
        
        // Show saved confirmation after short delay
        autoSaveIndicatorJob = lifecycleScope.launch {
            delay(300) // Short delay to show "Speichere..."
            chipAutoSaveStatus.apply {
                text = "✓ Gespeichert"
                setChipBackgroundColorResource(android.R.color.holo_green_light)
            }
            delay(2000) // Show for 2 seconds
            chipAutoSaveStatus.visibility = android.view.View.GONE
        }
    }
    
    private fun showRestoreConfirmation() {
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.restore_confirmation_title)
            .setMessage(R.string.restore_confirmation_message)
            .setPositiveButton(R.string.restore_button) { _, _ ->
                performRestore()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun performRestore() {
        val progressDialog = android.app.ProgressDialog(this).apply {
            setMessage(getString(R.string.restore_progress))
            setCancelable(false)
            show()
        }
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val webdavService = WebDavSyncService(this@SettingsActivity)
                val result = withContext(Dispatchers.IO) {
                    webdavService.restoreFromServer()
                }
                
                progressDialog.dismiss()
                
                if (result.isSuccess) {
                    showToast(getString(R.string.restore_success, result.restoredCount))
                    // Refresh MainActivity's note list
                    setResult(RESULT_OK)
                } else {
                    showToast(getString(R.string.restore_error, result.errorMessage))
                }
                checkServerStatus()
            } catch (e: Exception) {
                progressDialog.dismiss()
                showToast(getString(R.string.restore_error, e.message))
                checkServerStatus()
            }
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
}
