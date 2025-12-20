package dev.dettmer.simplenotes

import android.net.wifi.WifiManager
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import dev.dettmer.simplenotes.sync.WebDavSyncService
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.showToast
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var editTextServerUrl: EditText
    private lateinit var editTextUsername: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var editTextHomeSSID: EditText
    private lateinit var switchAutoSync: SwitchCompat
    private lateinit var buttonTestConnection: Button
    private lateinit var buttonSyncNow: Button
    private lateinit var buttonDetectSSID: Button
    
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
        editTextHomeSSID = findViewById(R.id.editTextHomeSSID)
        switchAutoSync = findViewById(R.id.switchAutoSync)
        buttonTestConnection = findViewById(R.id.buttonTestConnection)
        buttonSyncNow = findViewById(R.id.buttonSyncNow)
        buttonDetectSSID = findViewById(R.id.buttonDetectSSID)
    }
    
    private fun loadSettings() {
        editTextServerUrl.setText(prefs.getString(Constants.KEY_SERVER_URL, ""))
        editTextUsername.setText(prefs.getString(Constants.KEY_USERNAME, ""))
        editTextPassword.setText(prefs.getString(Constants.KEY_PASSWORD, ""))
        editTextHomeSSID.setText(prefs.getString(Constants.KEY_HOME_SSID, ""))
        switchAutoSync.isChecked = prefs.getBoolean(Constants.KEY_AUTO_SYNC, false)
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
        
        buttonDetectSSID.setOnClickListener {
            detectCurrentSSID()
        }
    }
    
    private fun saveSettings() {
        prefs.edit().apply {
            putString(Constants.KEY_SERVER_URL, editTextServerUrl.text.toString().trim())
            putString(Constants.KEY_USERNAME, editTextUsername.text.toString().trim())
            putString(Constants.KEY_PASSWORD, editTextPassword.text.toString().trim())
            putString(Constants.KEY_HOME_SSID, editTextHomeSSID.text.toString().trim())
            putBoolean(Constants.KEY_AUTO_SYNC, switchAutoSync.isChecked)
            apply()
        }
    }
    
    private fun testConnection() {
        lifecycleScope.launch {
            try {
                showToast("Teste Verbindung...")
                val syncService = WebDavSyncService(this@SettingsActivity)
                val result = syncService.syncNotes()
                
                if (result.isSuccess) {
                    showToast("Verbindung erfolgreich! ${result.syncedCount} Notizen synchronisiert")
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
    
    private fun detectCurrentSSID() {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val ssid = wifiInfo.ssid.replace("\"", "")
        
        if (ssid.isNotEmpty() && ssid != "<unknown ssid>") {
            editTextHomeSSID.setText(ssid)
            showToast("SSID erkannt: $ssid")
        } else {
            showToast("Nicht mit WLAN verbunden")
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
