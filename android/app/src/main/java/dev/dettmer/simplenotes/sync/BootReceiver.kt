package dev.dettmer.simplenotes.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger

/**
 * BootReceiver: Startet WorkManager nach Device Reboot
 * CRITICAL: Ohne diesen Receiver funktioniert Auto-Sync nach Reboot NICHT!
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
        
        // Prüfe ob Auto-Sync aktiviert ist
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val autoSyncEnabled = prefs.getBoolean(Constants.KEY_AUTO_SYNC, false)
        
        if (!autoSyncEnabled) {
            Logger.d(TAG, "❌ Auto-sync disabled - not starting WorkManager")
            return
        }
        
        Logger.d(TAG, "🚀 Auto-sync enabled - starting WorkManager")
        
        // WorkManager neu starten
        val networkMonitor = NetworkMonitor(context.applicationContext)
        networkMonitor.startMonitoring()
        
        Logger.d(TAG, "✅ WorkManager started after boot")
    }
}
