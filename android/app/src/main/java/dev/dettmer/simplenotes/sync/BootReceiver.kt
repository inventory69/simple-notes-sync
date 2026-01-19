package dev.dettmer.simplenotes.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger

/**
 * BootReceiver: Startet WorkManager nach Device Reboot
 * CRITICAL: Ohne diesen Receiver funktioniert Auto-Sync nach Reboot NICHT!
 * v1.6.0: Configurable trigger - checks KEY_SYNC_TRIGGER_BOOT
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            Logger.w(TAG, "Received unexpected intent: ${intent.action}")
            return
        }
        
        Logger.d(TAG, "📱 BOOT_COMPLETED received")
        
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        
        // 🌟 v1.6.0: Check if Boot trigger is enabled
        if (!prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_BOOT, Constants.DEFAULT_TRIGGER_BOOT)) {
            Logger.d(TAG, "⏭️ Boot sync disabled - not starting WorkManager")
            return
        }
        
        // Check if server is configured
        val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null)
        if (serverUrl.isNullOrEmpty() || serverUrl == "http://" || serverUrl == "https://") {
            Logger.d(TAG, "⏭️ Offline mode - not starting WorkManager")
            return
        }
        
        Logger.d(TAG, "🚀 Boot sync enabled - starting WorkManager")
        
        // WorkManager neu starten
        val networkMonitor = NetworkMonitor(context.applicationContext)
        networkMonitor.startMonitoring()
        
        Logger.d(TAG, "✅ WorkManager started after boot")
    }
}
