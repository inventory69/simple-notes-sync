package dev.dettmer.simplenotes.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.*
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import java.util.concurrent.TimeUnit

/**
 * NetworkMonitor: Verwaltet Auto-Sync
 * - Periodic WorkManager für Auto-Sync alle 30min
 * - NetworkCallback für WiFi-Connect Detection → WorkManager OneTime Sync
 */
class NetworkMonitor(private val context: Context) {
    
    companion object {
        private const val TAG = "NetworkMonitor"
        private const val AUTO_SYNC_WORK_NAME = "auto_sync_periodic"
    }
    
    private val prefs by lazy {
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    
    // 🔥 Track last connected network ID to detect network changes (SSID wechsel, WiFi an/aus)
    // null = kein Netzwerk, sonst Network.toString() als eindeutiger Identifier
    private var lastConnectedNetworkId: String? = null
    
    /**
     * NetworkCallback: Erkennt WiFi-Verbindung und triggert WorkManager
     * WorkManager funktioniert auch wenn App geschlossen ist!
     */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            
            Logger.d(TAG, "🌐 NetworkCallback.onAvailable() triggered")
            
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            Logger.d(TAG, "    Network capabilities: $capabilities")
            
            val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            Logger.d(TAG, "    Is WiFi: $isWifi")
            
            if (isWifi) {
                val currentNetworkId = network.toString()
                Logger.d(TAG, "📶 WiFi network connected: $currentNetworkId")
                
                // 🔥 Trigger bei:
                // 1. WiFi aus -> WiFi an (lastConnectedNetworkId == null)
                // 2. SSID-Wechsel (lastConnectedNetworkId != currentNetworkId)
                // NICHT triggern bei: App-Restart mit gleichem WiFi
                
                if (lastConnectedNetworkId != currentNetworkId) {
                    if (lastConnectedNetworkId == null) {
                        Logger.d(TAG, "    🎯 WiFi state changed: OFF -> ON (network: $currentNetworkId)")
                    } else {
                        Logger.d(TAG, "    🎯 WiFi network changed: $lastConnectedNetworkId -> $currentNetworkId")
                    }
                    
                    lastConnectedNetworkId = currentNetworkId
                    
                    // Auto-Sync check
                    val autoSyncEnabled = prefs.getBoolean(Constants.KEY_AUTO_SYNC, false)
                    Logger.d(TAG, "    Auto-Sync enabled: $autoSyncEnabled")
                    
                    if (autoSyncEnabled) {
                        Logger.d(TAG, "    ✅ Triggering WorkManager...")
                        triggerWifiConnectSync()
                    } else {
                        Logger.d(TAG, "    ❌ Auto-sync disabled - not triggering")
                    }
                } else {
                    Logger.d(TAG, "    ⚠️ Same WiFi network as before - ignoring (no network change)")
                }
            } else {
                Logger.d(TAG, "    ⚠️ Not WiFi - ignoring")
            }
        }
        
        override fun onLost(network: Network) {
            super.onLost(network)
            
            val lostNetworkId = network.toString()
            Logger.d(TAG, "🔴 NetworkCallback.onLost() - Network disconnected: $lostNetworkId")
            
            if (lastConnectedNetworkId == lostNetworkId) {
                Logger.d(TAG, "    Last WiFi network lost - resetting state")
                lastConnectedNetworkId = null
            }
        }
    }
    
    /**
     * Triggert WiFi-Connect Sync via WorkManager
     * WorkManager wacht App auf (funktioniert auch wenn App geschlossen!)
     * v1.6.0: Configurable trigger - checks KEY_SYNC_TRIGGER_WIFI_CONNECT
     */
    private fun triggerWifiConnectSync() {
        // 🌟 v1.6.0: Check if WiFi-Connect trigger is enabled
        if (!prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_WIFI_CONNECT, Constants.DEFAULT_TRIGGER_WIFI_CONNECT)) {
            Logger.d(TAG, "⏭️ WiFi-Connect sync disabled - skipping")
            return
        }
        
        // Check if server is configured
        val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null)
        if (serverUrl.isNullOrEmpty() || serverUrl == "http://" || serverUrl == "https://") {
            Logger.d(TAG, "⏭️ Offline mode - skipping WiFi-Connect sync")
            return
        }
        
        Logger.d(TAG, "📡 Scheduling WiFi-Connect sync via WorkManager")
        
        // 🔥 WICHTIG: NetworkType.UNMETERED constraint!
        // Ohne Constraint könnte WorkManager den Job auf Cellular ausführen
        // (z.B. wenn WiFi disconnected bevor Job startet)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)  // WiFi only!
            .build()
        
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(constraints)  // 🔥 Constraints hinzugefügt
            .addTag(Constants.SYNC_WORK_TAG)
            .addTag("wifi-connect")
            .build()
        
        WorkManager.getInstance(context).enqueue(syncRequest)
        Logger.d(TAG, "✅ WiFi-Connect sync scheduled (WIFI ONLY, WorkManager will wake app if needed)")
    }
    
    /**
     * Startet WorkManager mit Network Constraints + NetworkCallback
     */
    fun startMonitoring() {
        val autoSyncEnabled = prefs.getBoolean(Constants.KEY_AUTO_SYNC, false)
        
        if (!autoSyncEnabled) {
            Logger.d(TAG, "Auto-sync disabled - stopping all monitoring")
            stopMonitoring()
            return
        }
        
        Logger.d(TAG, "🚀 Starting NetworkMonitor (WorkManager + WiFi Callback)")
        
        // 1. WorkManager für periodic sync
        startPeriodicSync()
        
        // 2. NetworkCallback für WiFi-Connect Detection
        startWifiMonitoring()
    }
    
    /**
     * Startet WorkManager periodic sync
     * 🔥 Interval aus SharedPrefs konfigurierbar (15/30/60 min)
     * v1.6.0: Configurable trigger - checks KEY_SYNC_TRIGGER_PERIODIC
     */
    private fun startPeriodicSync() {
        // 🌟 v1.6.0: Check if Periodic trigger is enabled
        if (!prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_PERIODIC, Constants.DEFAULT_TRIGGER_PERIODIC)) {
            Logger.d(TAG, "⏭️ Periodic sync disabled - skipping")
            // Cancel existing periodic work if disabled
            WorkManager.getInstance(context).cancelUniqueWork(AUTO_SYNC_WORK_NAME)
            return
        }
        
        // Check if server is configured
        val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null)
        if (serverUrl.isNullOrEmpty() || serverUrl == "http://" || serverUrl == "https://") {
            Logger.d(TAG, "⏭️ Offline mode - skipping Periodic sync")
            WorkManager.getInstance(context).cancelUniqueWork(AUTO_SYNC_WORK_NAME)
            return
        }
        
        // 🔥 Interval aus SharedPrefs lesen
        val intervalMinutes = prefs.getLong(
            Constants.PREF_SYNC_INTERVAL_MINUTES,
            Constants.DEFAULT_SYNC_INTERVAL_MINUTES
        )
        
        Logger.d(TAG, "📅 Configuring periodic sync: ${intervalMinutes}min interval")
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)  // WiFi only
            .build()
        
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalMinutes, TimeUnit.MINUTES,  // 🔥 Dynamisch!
            5, TimeUnit.MINUTES  // Flex interval
        )
            .setConstraints(constraints)
            .addTag(Constants.SYNC_WORK_TAG)
            .build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AUTO_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,  // 🔥 Update bei Interval-Änderung
            syncRequest
        )
        
        Logger.d(TAG, "✅ Periodic sync scheduled (every ${intervalMinutes}min)")
    }
    
    /**
     * Startet NetworkCallback für WiFi-Connect Detection
     */
    private fun startWifiMonitoring() {
        try {
            Logger.d(TAG, "🚀 Starting WiFi monitoring...")
            
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            Logger.d(TAG, "    NetworkRequest built: WIFI + INTERNET capability")
            
            connectivityManager.registerNetworkCallback(request, networkCallback)
            Logger.d(TAG, "✅✅✅ WiFi NetworkCallback registered successfully")
            Logger.d(TAG, "    Callback will trigger on WiFi connect/disconnect")
            
            // 🔥 FIX: Initialisiere wasWifiConnected State beim Start
            // onAvailable() wird nur bei NEUEN Verbindungen getriggert!
            initializeWifiState()
            
        } catch (e: Exception) {
            Logger.e(TAG, "❌❌❌ Failed to register NetworkCallback", e)
        }
    }
    
    /**
     * Initialisiert lastConnectedNetworkId beim App-Start
     * Wichtig damit wir echte Netzwerk-Wechsel von App-Restarts unterscheiden können
     */
    private fun initializeWifiState() {
        try {
            Logger.d(TAG, "🔍 Initializing WiFi state...")
            
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork == null) {
                Logger.d(TAG, "    ❌ No active network - lastConnectedNetworkId = null")
                lastConnectedNetworkId = null
                return
            }
            
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            
            if (isWifi) {
                lastConnectedNetworkId = activeNetwork.toString()
                Logger.d(TAG, "    ✅ Initial WiFi network: $lastConnectedNetworkId")
                Logger.d(
                    TAG,
                    "    📡 WiFi already connected at startup - " +
                        "onAvailable() will only trigger on network change"
                )
            } else {
                lastConnectedNetworkId = null
                Logger.d(TAG, "    ⚠️ Not on WiFi at startup")
            }
            
        } catch (e: Exception) {
            Logger.e(TAG, "❌ Error initializing WiFi state", e)
            lastConnectedNetworkId = null
        }
    }
    
    /**
     * Prüft ob WiFi aktuell verbunden ist
     * @return true wenn WiFi verbunden, false sonst (Cellular, offline, etc.)
     */
    fun isWiFiConnected(): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            Logger.e(TAG, "Error checking WiFi status", e)
            false
        }
    }
    
    /**
     * Stoppt WorkManager Auto-Sync + NetworkCallback
     */
    fun stopMonitoring() {
        Logger.d(TAG, "🛑 Stopping auto-sync")
        
        // Stop WorkManager
        WorkManager.getInstance(context).cancelUniqueWork(AUTO_SYNC_WORK_NAME)
        
        // Unregister NetworkCallback
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            Logger.d(TAG, "✅ WiFi monitoring stopped")
        } catch (e: Exception) {
            Logger.w(TAG, "NetworkCallback already unregistered: ${e.message}")
        }
    }
}
