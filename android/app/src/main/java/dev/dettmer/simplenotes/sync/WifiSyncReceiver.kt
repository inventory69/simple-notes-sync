package dev.dettmer.simplenotes.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.dettmer.simplenotes.utils.Constants
import java.util.concurrent.TimeUnit

class WifiSyncReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        // Check if auto-sync is enabled
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val autoSyncEnabled = prefs.getBoolean(Constants.KEY_AUTO_SYNC, false)
        
        if (!autoSyncEnabled) {
            return
        }
        
        // Check if connected to home WiFi
        if (isConnectedToHomeWifi(context)) {
            scheduleSyncWork(context)
        }
    }
    
    private fun isConnectedToHomeWifi(context: Context): Boolean {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val homeSSID = prefs.getString(Constants.KEY_HOME_SSID, null) ?: return false
        
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
            as ConnectivityManager
        
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return false
        }
        
        // Get current SSID
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) 
            as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val currentSSID = wifiInfo.ssid.replace("\"", "")
        
        return currentSSID == homeSSID
    }
    
    private fun scheduleSyncWork(context: Context) {
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(Constants.SYNC_DELAY_SECONDS, TimeUnit.SECONDS)
            .addTag(Constants.SYNC_WORK_TAG)
            .build()
        
        WorkManager.getInstance(context).enqueue(syncRequest)
    }
}
