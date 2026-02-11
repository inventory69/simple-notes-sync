package dev.dettmer.simplenotes.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.dettmer.simplenotes.utils.Constants
import java.util.concurrent.TimeUnit

/**
 * WiFi-Sync BroadcastReceiver
 * 
 * Triggert Sync wenn WiFi verbunden wird (jedes WiFi, keine SSID-Prüfung mehr)
 * Die eigentliche Server-Erreichbarkeitsprüfung erfolgt im SyncWorker.
 */
class WifiSyncReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        // Check if auto-sync is enabled
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val autoSyncEnabled = prefs.getBoolean(Constants.KEY_AUTO_SYNC, false)
        
        if (!autoSyncEnabled) {
            return
        }
        
        // 🆕 v1.8.1 (IMPL_08): Globaler Cooldown (verhindert Doppel-Trigger mit NetworkMonitor)
        if (!SyncStateManager.canSyncGlobally(prefs)) {
            return
        }
        
        // 🆕 v1.8.1 (IMPL_08): Auch KEY_SYNC_TRIGGER_WIFI_CONNECT prüfen (Konsistenz mit NetworkMonitor)
        if (!prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_WIFI_CONNECT, Constants.DEFAULT_TRIGGER_WIFI_CONNECT)) {
            return
        }
        
        // Check if connected to any WiFi (SSID-Prüfung entfernt in v1.4.0)
        if (isConnectedToWifi(context)) {
            scheduleSyncWork(context)
        }
    }
    
    /**
     * Prüft ob ein WiFi-Netzwerk verbunden ist (beliebiges WiFi)
     * Die Server-Erreichbarkeitsprüfung erfolgt erst im SyncWorker.
     */
    private fun isConnectedToWifi(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
            as ConnectivityManager
        
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
    
    private fun scheduleSyncWork(context: Context) {
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInitialDelay(Constants.SYNC_DELAY_SECONDS, TimeUnit.SECONDS)
            .addTag(Constants.SYNC_WORK_TAG)
            .build()
        
        WorkManager.getInstance(context).enqueue(syncRequest)
    }
}
