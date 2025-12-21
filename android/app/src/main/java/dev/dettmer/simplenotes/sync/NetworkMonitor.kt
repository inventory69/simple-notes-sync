package dev.dettmer.simplenotes.sync

import android.content.Context
import android.net.wifi.WifiManager
import androidx.work.*
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import java.util.concurrent.TimeUnit

/**
 * NetworkMonitor: Verwaltet WorkManager-basiertes Auto-Sync
 * WICHTIG: Kein NetworkCallback mehr - WorkManager macht das für uns!
 */
class NetworkMonitor(private val context: Context) {
    
    companion object {
        private const val TAG = "NetworkMonitor"
        private const val AUTO_SYNC_WORK_NAME = "auto_sync_periodic"
    }
    
    private val prefs by lazy {
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Startet WorkManager mit Network Constraints
     * WorkManager kümmert sich automatisch um WiFi-Erkennung!
     */
    fun startMonitoring() {
        val autoSyncEnabled = prefs.getBoolean(Constants.KEY_AUTO_SYNC, false)
        
        if (!autoSyncEnabled) {
            Logger.d(TAG, "Auto-sync disabled - stopping periodic work")
            stopMonitoring()
            return
        }
        
        Logger.d(TAG, "🚀 Starting WorkManager-based auto-sync")
        
        // Constraints: Nur wenn WiFi connected
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)  // WiFi only
            .build()
        
        // Periodic Work Request - prüft alle 30 Minuten (Battery optimized)
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            30, TimeUnit.MINUTES,  // Optimiert: 30 Min statt 15 Min
            10, TimeUnit.MINUTES   // Flex interval
        )
            .setConstraints(constraints)
            .addTag(Constants.SYNC_WORK_TAG)
            .build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AUTO_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,  // UPDATE statt KEEP für immediate trigger
            syncRequest
        )
        
        Logger.d(TAG, "✅ Periodic auto-sync scheduled (every 30min when on WiFi)")
        
        // Trigger sofortigen Sync wenn WiFi bereits connected
        triggerImmediateSync()
    }
    
    /**
     * Stoppt WorkManager Auto-Sync
     */
    fun stopMonitoring() {
        Logger.d(TAG, "🛑 Stopping auto-sync")
        WorkManager.getInstance(context).cancelUniqueWork(AUTO_SYNC_WORK_NAME)
    }
    
    /**
     * Trigger sofortigen Sync (z.B. nach Settings-Änderung)
     */
    private fun triggerImmediateSync() {
        if (!isConnectedToHomeWifi()) {
            Logger.d(TAG, "Not on home WiFi - skipping immediate sync")
            return
        }
        
        Logger.d(TAG, "� Triggering immediate sync...")
        
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(Constants.SYNC_WORK_TAG)
            .build()
        
        WorkManager.getInstance(context).enqueue(syncRequest)
    }
    
    /**
     * Prüft ob connected zu Home WiFi via Gateway IP Check
     */
    private fun isConnectedToHomeWifi(): Boolean {
        val gatewayIP = getGatewayIP() ?: return false
        
        val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null)
        if (serverUrl.isNullOrEmpty()) return false
        
        val serverIP = extractIPFromUrl(serverUrl)
        if (serverIP == null) return false
        
        val sameNetwork = isSameNetwork(gatewayIP, serverIP)
        Logger.d(TAG, "Gateway: $gatewayIP, Server: $serverIP → Same network: $sameNetwork")
        
        return sameNetwork
    }
    
    private fun getGatewayIP(): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) 
                as WifiManager
            val dhcpInfo = wifiManager.dhcpInfo
            val gateway = dhcpInfo.gateway
            
            val ip = String.format(
                "%d.%d.%d.%d",
                gateway and 0xFF,
                (gateway shr 8) and 0xFF,
                (gateway shr 16) and 0xFF,
                (gateway shr 24) and 0xFF
            )
            ip
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to get gateway IP: ${e.message}")
            null
        }
    }
    
    private fun extractIPFromUrl(url: String): String? {
        return try {
            val urlObj = java.net.URL(url)
            val host = urlObj.host
            
            if (host.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                host
            } else {
                val addr = java.net.InetAddress.getByName(host)
                addr.hostAddress
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to extract IP: ${e.message}")
            null
        }
    }
    
    private fun isSameNetwork(ip1: String, ip2: String): Boolean {
        val parts1 = ip1.split(".")
        val parts2 = ip2.split(".")
        
        if (parts1.size != 4 || parts2.size != 4) return false
        
        return parts1[0] == parts2[0] && 
               parts1[1] == parts2[1] && 
               parts1[2] == parts2[2]
    }
}
