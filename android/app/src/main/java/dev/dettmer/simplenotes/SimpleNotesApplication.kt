package dev.dettmer.simplenotes

import android.app.Application
import dev.dettmer.simplenotes.utils.Logger
import dev.dettmer.simplenotes.sync.NetworkMonitor
import dev.dettmer.simplenotes.utils.NotificationHelper

class SimpleNotesApplication : Application() {
    
    companion object {
        private const val TAG = "SimpleNotesApp"
    }
    
    lateinit var networkMonitor: NetworkMonitor  // Public access für SettingsActivity
    
    override fun onCreate() {
        super.onCreate()
        
        Logger.d(TAG, "🚀 Application onCreate()")
        
        // Initialize notification channel
        NotificationHelper.createNotificationChannel(this)
        Logger.d(TAG, "✅ Notification channel created")
        
        // Initialize NetworkMonitor (WorkManager-based)
        // VORTEIL: WorkManager läuft auch ohne aktive App!
        networkMonitor = NetworkMonitor(applicationContext)
        
        // Start WorkManager periodic sync
        // Dies läuft im Hintergrund auch wenn App geschlossen ist
        networkMonitor.startMonitoring()
        
        Logger.d(TAG, "✅ WorkManager-based auto-sync initialized")
    }
    
    override fun onTerminate() {
        super.onTerminate()
        
        Logger.d(TAG, "🛑 Application onTerminate()")
        
        // WorkManager läuft weiter auch nach onTerminate!
        // Nur bei deaktiviertem Auto-Sync stoppen wir es
    }
}
