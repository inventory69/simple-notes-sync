package dev.dettmer.simplenotes

import android.app.Application
import android.content.Context
import dev.dettmer.simplenotes.utils.Logger
import dev.dettmer.simplenotes.sync.NetworkMonitor
import dev.dettmer.simplenotes.utils.NotificationHelper
import dev.dettmer.simplenotes.utils.Constants

class SimpleNotesApplication : Application() {
    
    companion object {
        private const val TAG = "SimpleNotesApp"
    }
    
    lateinit var networkMonitor: NetworkMonitor  // Public access für SettingsActivity
    
    override fun onCreate() {
        super.onCreate()
        
        // File-Logging ZUERST aktivieren (damit alle Logs geschrieben werden!)
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean("file_logging_enabled", false)) {
            Logger.enableFileLogging(this)
            Logger.d(TAG, "📝 File logging enabled at Application startup")
        }
        
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
