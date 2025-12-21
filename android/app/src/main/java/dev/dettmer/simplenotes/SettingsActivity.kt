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
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import dev.dettmer.simplenotes.sync.WebDavSyncService
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class SettingsActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "SettingsActivity"
    }
    
    private lateinit var editTextServerUrl: EditText
    private lateinit var editTextUsername: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var switchAutoSync: SwitchCompat
    private lateinit var buttonTestConnection: Button
    private lateinit var buttonSyncNow: Button
    private lateinit var textViewServerStatus: TextView
    
    private val prefs by lazy {
        getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    }
    
    private fun findViews() {
        editTextServerUrl = findViewById(R.id.editTextServerUrl)
        editTextUsername = findViewById(R.id.editTextUsername)
        editTextPassword = findViewById(R.id.editTextPassword)
        switchAutoSync = findViewById(R.id.switchAutoSync)
        buttonTestConnection = findViewById(R.id.buttonTestConnection)
        buttonSyncNow = findViewById(R.id.buttonSyncNow)
        textViewServerStatus = findViewById(R.id.textViewServerStatus)
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
        
        switchAutoSync.setOnCheckedChangeListener { _, isChecked ->
            onAutoSyncToggled(isChecked)
        }
        
        // Server Status Check bei Settings-Änderung
        editTextServerUrl.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                checkServerStatus()
            }
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
                } else {
                    showToast("Verbindung fehlgeschlagen: ${result.errorMessage}")
                }
            } catch (e: Exception) {
                showToast("Fehler: ${e.message}")
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
                } else {
                    showToast("Sync fehlgeschlagen: ${result.errorMessage}")
                }
            } catch (e: Exception) {
                showToast("Fehler: ${e.message}")
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
