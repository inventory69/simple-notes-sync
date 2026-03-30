package dev.dettmer.simplenotes

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import dev.dettmer.simplenotes.sync.NetworkMonitor
import dev.dettmer.simplenotes.sync.SyncStateManager
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import dev.dettmer.simplenotes.utils.NoteCorruptionRepair
import dev.dettmer.simplenotes.utils.NotificationHelper

class SimpleNotesApplication : Application() {
    companion object {
        private const val TAG = "SimpleNotesApp"
    }

    lateinit var networkMonitor: NetworkMonitor // Public access für SettingsActivity

    /**
     * 🌍 v1.7.1: Apply app locale to Application Context
     *
     * This ensures ViewModels and other components using Application Context
     * get the correct locale-specific strings.
     */
    override fun attachBaseContext(base: Context) {
        // Apply the app locale before calling super
        // This is handled by AppCompatDelegate which reads from system storage
        super.attachBaseContext(base)
    }

    override fun onCreate() {
        super.onCreate()

        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

        // 🔧 Hotfix v1.6.2: Migrate offline mode setting BEFORE any ViewModel initialization
        // This prevents the offline mode bug where users updating from v1.5.0 incorrectly
        // appear as offline even though they have a configured server
        migrateOfflineModeSetting(prefs)

        // File-Logging ZUERST aktivieren (damit alle Logs geschrieben werden!)
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

        // 🆕 v1.8.2: Stale Sync-State cleanup beim App-Kaltstart
        // Nach einem Prozess-Neustart kann kein Sync mehr aktiv sein.
        // SyncStateManager ist ein Kotlin object — bei Activity-Recreate ohne
        // Prozess-Kill kann ein verwaister SYNCING-State dauerhaft blockieren.
        if (SyncStateManager.isSyncing) {
            Logger.e(TAG, "⚠️ Stale sync state detected on cold start - resetting")
            SyncStateManager.reset()
        }
        Logger.d(TAG, "✅ WorkManager-based auto-sync initialized")

        // 🔧 v2.2.0: Einmalige Reparatur korrupter Checklist-Titel (Bug #07)
        try {
            val storage = NotesStorage(this)
            NoteCorruptionRepair.repairIfNeeded(storage, prefs)
        } catch (e: Exception) {
            Logger.e(TAG, "⚠️ Corruption repair failed (non-fatal)", e)
        }
    }

    override fun onTerminate() {
        super.onTerminate()

        Logger.d(TAG, "🛑 Application onTerminate()")

        // WorkManager läuft weiter auch nach onTerminate!
        // Nur bei deaktiviertem Auto-Sync stoppen wir es
    }

    /**
     * 🔧 Hotfix v1.6.2: Migrate offline mode setting for updates from v1.5.0
     *
     * Problem: KEY_OFFLINE_MODE didn't exist in v1.5.0, but MainViewModel
     * and NoteEditorViewModel use `true` as default, causing existing users
     * with configured servers to appear in offline mode after update.
     *
     * Fix: Set the key BEFORE any ViewModel is initialized based on whether
     * a server was already configured.
     */
    private fun migrateOfflineModeSetting(prefs: android.content.SharedPreferences) {
        if (!prefs.contains(Constants.KEY_OFFLINE_MODE)) {
            val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null)
            val hasServerConfig = !serverUrl.isNullOrEmpty() &&
                serverUrl != "http://" &&
                serverUrl != "https://"

            // If server was configured → offlineMode = false (continue syncing)
            // If no server → offlineMode = true (new users / offline users)
            val offlineModeValue = !hasServerConfig
            prefs.edit { putBoolean(Constants.KEY_OFFLINE_MODE, offlineModeValue) }

            Logger.i(
                TAG,
                "🔄 Migrated offline_mode_enabled: hasServer=$hasServerConfig → offlineMode=$offlineModeValue"
            )
        }
    }
}
