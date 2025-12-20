package dev.dettmer.simplenotes.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import dev.dettmer.simplenotes.utils.Constants
import java.util.concurrent.TimeUnit

class NetworkMonitor(private val context: Context) {
    
    companion object {
        private const val TAG = "NetworkMonitor"
    }
    
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
        as ConnectivityManager
    
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            
            Log.d(TAG, "📶 Network available: $network")
            
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                Log.d(TAG, "✅ WiFi detected")
                checkAndTriggerSync()
            } else {
                Log.d(TAG, "❌ Not WiFi: ${capabilities?.toString()}")
            }
        }
        
        override fun onCapabilitiesChanged(
            network: Network, 
            capabilities: NetworkCapabilities
        ) {
            super.onCapabilitiesChanged(network, capabilities)
            
            Log.d(TAG, "🔄 Capabilities changed: $network")
            
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                Log.d(TAG, "✅ WiFi capabilities")
                checkAndTriggerSync()
            }
        }
        
        override fun onLost(network: Network) {
            super.onLost(network)
            Log.d(TAG, "❌ Network lost: $network")
        }
    }
    
    fun startMonitoring() {
        Log.d(TAG, "🚀 Starting NetworkMonitor")
        Log.d(TAG, "Context type: ${context.javaClass.simpleName}")
        
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
            Log.d(TAG, "✅ NetworkCallback registered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to register NetworkCallback: ${e.message}", e)
        }
    }
    
    fun stopMonitoring() {
        Log.d(TAG, "🛑 Stopping NetworkMonitor")
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            Log.d(TAG, "✅ NetworkCallback unregistered")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ NetworkCallback already unregistered: ${e.message}")
        }
    }
    
    private fun checkAndTriggerSync() {
        Log.d(TAG, "🔍 Checking auto-sync conditions...")
        
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val autoSyncEnabled = prefs.getBoolean(Constants.KEY_AUTO_SYNC, false)
        
        Log.d(TAG, "Auto-sync enabled: $autoSyncEnabled")
        
        if (!autoSyncEnabled) {
            Log.d(TAG, "❌ Auto-sync disabled, skipping")
            return
        }
        
        val homeSSID = prefs.getString(Constants.KEY_HOME_SSID, null)
        Log.d(TAG, "Home SSID configured: $homeSSID")
        
        if (isConnectedToHomeWifi()) {
            Log.d(TAG, "✅ Connected to home WiFi, scheduling sync!")
            scheduleSyncWork()
        } else {
            Log.d(TAG, "❌ Not connected to home WiFi")
        }
    }
    
    private fun isConnectedToHomeWifi(): Boolean {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val homeSSID = prefs.getString(Constants.KEY_HOME_SSID, null) ?: return false
        
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) 
            as WifiManager
        
        val currentSSID = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+: Use WifiInfo from NetworkCapabilities
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val wifiInfo = capabilities.transportInfo as? WifiInfo
                wifiInfo?.ssid?.replace("\"", "") ?: ""
            } else {
                wifiManager.connectionInfo.ssid.replace("\"", "")
            }
        } else {
            wifiManager.connectionInfo.ssid.replace("\"", "")
        }
        
        Log.d(TAG, "Current SSID: '$currentSSID', Home SSID: '$homeSSID'")
        
        val isHome = currentSSID == homeSSID
        Log.d(TAG, "Is home WiFi: $isHome")
        
        return isHome
    }
    
    private fun scheduleSyncWork() {
        Log.d(TAG, "📅 Scheduling sync work...")
        
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(Constants.SYNC_WORK_TAG)
            .build()
        
        WorkManager.getInstance(context).enqueue(syncRequest)
        
        Log.d(TAG, "✅ Sync work scheduled with WorkManager")
    }
}
