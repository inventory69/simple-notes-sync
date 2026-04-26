package dev.dettmer.simplenotes

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import dev.dettmer.simplenotes.sync.NetworkMonitor
import dev.dettmer.simplenotes.sync.SyncStateManager
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.CredentialStore
import dev.dettmer.simplenotes.utils.Logger
import dev.dettmer.simplenotes.utils.NoteCorruptionRepair
import dev.dettmer.simplenotes.utils.NotificationHelper
import dev.dettmer.simplenotes.utils.SyncDebugLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SimpleNotesApplication : Application() {
    companion object {
        private const val TAG = "SimpleNotesApp"
    }

    lateinit var networkMonitor: NetworkMonitor // Public access für SettingsActivity

    // Application-scoped coroutine scope for non-UI background work
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

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

        // 🔐 v2.3.0: Migrate credentials to EncryptedSharedPreferences
        migrateCredentialsToEncryptedPrefs(prefs)

        // File-Logging ZUERST aktivieren (damit alle Logs geschrieben werden!)
        if (prefs.getBoolean("file_logging_enabled", false)) {
            Logger.enableFileLogging(this)
            Logger.d(TAG, "📝 File logging enabled at Application startup")
        }

        // 🆕 v2.2.0: Persistent sync debug logger
        SyncDebugLogger.init(this)

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

        // 🔒 v2.3.0 (FIX-013): Timestamp-based stale sync state cleanup.
        // Replaces unconditional reset with checkAndResetStaleState() which
        // also handles stuck states from configuration changes without process kill.
        SyncStateManager.checkAndResetStaleState()
        Logger.d(TAG, "✅ WorkManager-based auto-sync initialized")

        // 🔧 v2.2.0: Einmalige Reparatur korrupter Checklist-Titel (Bug #07)
        applicationScope.launch {
            try {
                val storage = NotesStorage(this@SimpleNotesApplication)
                NoteCorruptionRepair.repairIfNeeded(storage, prefs)
            } catch (e: Exception) {
                Logger.e(TAG, "⚠️ Corruption repair failed (non-fatal)", e)
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()

        Logger.d(TAG, "🛑 Application onTerminate()")

        // WorkManager läuft weiter auch nach onTerminate!
        // Nur bei deaktiviertem Auto-Sync stoppen wir es
    }

    /**
     * � v2.3.0: Migrate credentials from regular to EncryptedSharedPreferences.
     * One-time migration: removes credentials from unencrypted prefs after copying.
     * If EncryptedSharedPreferences is unavailable (KeyStore issue), credentials
     * remain in regular prefs and migration is retried on next app start.
     *
     * Audit: E-01
     */
    private fun migrateCredentialsToEncryptedPrefs(prefs: android.content.SharedPreferences) {
        val username = prefs.getString(Constants.KEY_USERNAME, null)
        val password = prefs.getString(Constants.KEY_PASSWORD, null)
        if (username != null || password != null) {
            try {
                val securePrefs = CredentialStore.getSecurePrefs(this)
                if (securePrefs == null) {
                    Logger.w(TAG, "⚠️ EncryptedSharedPreferences unavailable — credentials remain in regular prefs (KeyStore issue)")
                    return
                }
                CredentialStore.setCredentials(
                    this,
                    username.orEmpty(),
                    password.orEmpty()
                )
                prefs.edit {
                    remove(Constants.KEY_USERNAME)
                    remove(Constants.KEY_PASSWORD)
                }
                Logger.d(TAG, "✅ Credentials migrated to EncryptedSharedPreferences")
            } catch (e: Exception) {
                Logger.e(TAG, "⚠️ Credential migration failed (non-fatal) — credentials remain in regular prefs", e)
            }
        }
    }

    /**
     * �🔧 Hotfix v1.6.2: Migrate offline mode setting for updates from v1.5.0
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
