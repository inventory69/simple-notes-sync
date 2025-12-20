package dev.dettmer.simplenotes

import android.app.Application
import android.util.Log
import dev.dettmer.simplenotes.sync.NetworkMonitor
import dev.dettmer.simplenotes.utils.NotificationHelper

class SimpleNotesApplication : Application() {
    
    companion object {
        private const val TAG = "SimpleNotesApp"
    }
    
    private lateinit var networkMonitor: NetworkMonitor
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "🚀 Application onCreate()")
        
        // Initialize notification channel
        NotificationHelper.createNotificationChannel(this)
        Log.d(TAG, "✅ Notification channel created")
        
        // Initialize and start NetworkMonitor at application level
        // CRITICAL: Use applicationContext, not 'this'!
        networkMonitor = NetworkMonitor(applicationContext)
        networkMonitor.startMonitoring()
        
        Log.d(TAG, "✅ NetworkMonitor initialized and started")
    }
    
    override fun onTerminate() {
        super.onTerminate()
        
        Log.d(TAG, "🛑 Application onTerminate()")
        
        // Clean up NetworkMonitor when app is terminated
        networkMonitor.stopMonitoring()
    }
}
