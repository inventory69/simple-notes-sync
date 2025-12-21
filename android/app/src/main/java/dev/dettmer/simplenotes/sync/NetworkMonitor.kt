package dev.dettmer.simplenotes.sync

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.*
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import java.util.concurrent.TimeUnit

/**
 * NetworkMonitor: Verwaltet Auto-Sync
 * - Periodic WorkManager für Auto-Sync alle 30min (funktioniert zuverlässig)
 * - NetworkCallback für WiFi-Connect Detection → Broadcast an MainActivity (direkter Sync!)
 */
class NetworkMonitor(private val context: Context) {
    
    companion object {
        private const val TAG = "NetworkMonitor"
        private const val AUTO_SYNC_WORK_NAME = "auto_sync_periodic"
        const val ACTION_WIFI_CONNECTED = "dev.dettmer.simplenotes.WIFI_CONNECTED"
    }
    
    private val prefs by lazy {
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    
    /**
     * NetworkCallback: Erkennt WiFi-Verbindung und sendet Broadcast an MainActivity
     * (KEIN WorkManager - der nutzt VPN IP statt WiFi!)
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
                Logger.d(TAG, "📶 WiFi connected detected!")
                
                // Auto-Sync check
                val autoSyncEnabled = prefs.getBoolean(Constants.KEY_AUTO_SYNC, false)
                Logger.d(TAG, "    Auto-Sync enabled: $autoSyncEnabled")
                
                if (autoSyncEnabled) {
                    Logger.d(TAG, "    ✅ Triggering broadcast...")
                    broadcastWifiConnected()
                } else {
                    Logger.d(TAG, "    ❌ Auto-sync disabled - not broadcasting")
                }
            } else {
                Logger.d(TAG, "    ⚠️ Not WiFi - ignoring")
            }
        }
    }
    
    /**
     * Sendet Broadcast an MainActivity für direkten Sync
     * (Direkter Sync nutzt richtiges WiFi-Interface, WorkManager nutzt VPN!)
     */
    private fun broadcastWifiConnected() {
        Logger.d(TAG, "📡📡📡 Broadcasting WiFi-Connect to MainActivity")
        Logger.d(TAG, "    Action: $ACTION_WIFI_CONNECTED")
        Logger.d(TAG, "    Package: ${context.packageName}")
        
        val intent = Intent(ACTION_WIFI_CONNECTED)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        
        Logger.d(TAG, "✅✅✅ WiFi-Connect broadcast SENT via LocalBroadcastManager")
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
     */
    private fun startPeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)  // WiFi only
            .build()
        
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            30, TimeUnit.MINUTES,
            10, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(Constants.SYNC_WORK_TAG)
            .build()
        
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AUTO_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            syncRequest
        )
        
        Logger.d(TAG, "✅ Periodic sync scheduled (every 30min)")
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
            
        } catch (e: Exception) {
            Logger.e(TAG, "❌❌❌ Failed to register NetworkCallback", e)
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
            // Already unregistered
        }
    }
}
