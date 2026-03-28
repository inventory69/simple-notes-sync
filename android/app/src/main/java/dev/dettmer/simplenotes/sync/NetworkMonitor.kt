package dev.dettmer.simplenotes.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.*
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import java.util.concurrent.TimeUnit

/**
 * NetworkMonitor: Verwaltet Auto-Sync
 * - Periodic WorkManager für Auto-Sync alle 30min
 * - NetworkCallback für WiFi-Connect Detection → WorkManager OneTime Sync
 */
class NetworkMonitor(context: Context) {
    private val context: Context = context.applicationContext

    companion object {
        private const val TAG = "NetworkMonitor"
        private const val AUTO_SYNC_WORK_NAME = "auto_sync_periodic"

        // 🛡️ Kaltstart-Guard: Verhindert Sync-Trigger direkt nach Package-Update/Prozess-Neustart
        private const val COLD_START_GUARD_MS = 5_000L
    }

    private val prefs by lazy {
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    // 🔥 Track last connected network ID to detect network changes (SSID wechsel, WiFi an/aus)
    // null = kein Netzwerk, sonst Network.toString() als eindeutiger Identifier
    // @Volatile: NetworkCallback läuft auf ConnectivityThread, initializeWifiState() auf Main
    @Volatile
    private var lastConnectedNetworkId: String? = null

    // 🛡️ Kaltstart-Guard: Zeitpunkt des Monitoring-Starts
    private var monitoringStartTime: Long = 0L

    /**
     * NetworkCallback: Erkennt WiFi-Verbindung und triggert WorkManager
     * WorkManager funktioniert auch wenn App geschlossen ist!
     */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)

            Logger.d(TAG, "🌐 NetworkCallback.onAvailable() triggered")

            val capabilities = connectivityManager.getNetworkCapabilities(network)
            Logger.d(TAG, "    Network capabilities: $capabilities")

            val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            Logger.d(TAG, "    Is WiFi: $isWifi")

            if (isWifi) {
                val currentNetworkId = network.toString()
                Logger.d(TAG, "📶 WiFi network connected: $currentNetworkId")

                // 🔥 Trigger bei:
                // 1. WiFi aus -> WiFi an (lastConnectedNetworkId == null)
                // 2. SSID-Wechsel (lastConnectedNetworkId != currentNetworkId)
                // NICHT triggern bei: App-Restart mit gleichem WiFi

                if (lastConnectedNetworkId != currentNetworkId) {
                    if (lastConnectedNetworkId == null) {
                        Logger.d(TAG, "    🎯 WiFi state changed: OFF -> ON (network: $currentNetworkId)")
                    } else {
                        Logger.d(TAG, "    🎯 WiFi network changed: $lastConnectedNetworkId -> $currentNetworkId")
                    }

                    lastConnectedNetworkId = currentNetworkId

                    // 🛡️ Kaltstart-Guard: Nach Package-Update/Prozess-Neustart
                    // feuert onAvailable() sofort für das bestehende WiFi.
                    // In den ersten 5s keinen Sync auslösen.
                    val msSinceStart = System.currentTimeMillis() - monitoringStartTime
                    if (msSinceStart < COLD_START_GUARD_MS) {
                        Logger.d(
                            TAG,
                            "    ⏭️ Cold-start guard active (${msSinceStart}ms < ${COLD_START_GUARD_MS}ms) - ignoring"
                        )
                    } else {
                        // WiFi-Connect Trigger prüfen - NICHT KEY_AUTO_SYNC!
                        // Der Callback ist registriert WEIL KEY_SYNC_TRIGGER_WIFI_CONNECT = true
                        // Aber defensive Prüfung für den Fall, dass Settings sich geändert haben
                        val wifiConnectEnabled = prefs.getBoolean(
                            Constants.KEY_SYNC_TRIGGER_WIFI_CONNECT,
                            Constants.DEFAULT_TRIGGER_WIFI_CONNECT
                        )
                        Logger.d(TAG, "    WiFi-Connect trigger enabled: $wifiConnectEnabled")

                        if (wifiConnectEnabled) {
                            Logger.d(TAG, "    ✅ Triggering WiFi-Connect sync...")
                            triggerWifiConnectSync()
                        } else {
                            Logger.d(TAG, "    ⏭️ WiFi-Connect trigger disabled in settings")
                        }
                    }
                } else {
                    Logger.d(TAG, "    ⚠️ Same WiFi network as before - ignoring (no network change)")
                }
            } else {
                Logger.d(TAG, "    ⚠️ Not WiFi - ignoring")
            }
        }

        override fun onLost(network: Network) {
            super.onLost(network)

            val lostNetworkId = network.toString()
            Logger.d(TAG, "🔴 NetworkCallback.onLost() - Network disconnected: $lostNetworkId")

            if (lastConnectedNetworkId == lostNetworkId) {
                Logger.d(TAG, "    Last WiFi network lost - resetting state")
                lastConnectedNetworkId = null
            }
        }
    }

    /**
     * Triggert WiFi-Connect Sync via WorkManager
     * WorkManager wacht App auf (funktioniert auch wenn App geschlossen!)
     * v1.6.0: Configurable trigger - checks KEY_SYNC_TRIGGER_WIFI_CONNECT
     */
    private fun triggerWifiConnectSync() {
        // 🌟 v1.6.0: Check if WiFi-Connect trigger is enabled
        if (!prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_WIFI_CONNECT, Constants.DEFAULT_TRIGGER_WIFI_CONNECT)) {
            Logger.d(TAG, "⏭️ WiFi-Connect sync disabled - skipping")
            return
        }

        // Check if server is configured
        val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null)
        if (serverUrl.isNullOrEmpty() || serverUrl == "http://" || serverUrl == "https://") {
            Logger.d(TAG, "⏭️ Offline mode - skipping WiFi-Connect sync")
            return
        }

        Logger.d(TAG, "📡 Scheduling WiFi-Connect sync via WorkManager")

        // 🔥 WICHTIG: NetworkType.UNMETERED constraint!
        // Ohne Constraint könnte WorkManager den Job auf Cellular ausführen
        // (z.B. wenn WiFi disconnected bevor Job startet)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED) // WiFi only!
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(constraints) // 🔥 Constraints hinzugefügt
            .addTag(Constants.SYNC_WORK_TAG)
            .addTag("wifi-connect")
            .build()

        WorkManager.getInstance(context).enqueue(syncRequest)
        Logger.d(TAG, "✅ WiFi-Connect sync scheduled (WIFI ONLY, WorkManager will wake app if needed)")
    }

    /**
     * 🆕 v2.2.0: Registriert einen periodischen WorkManager-Job als Fallback für den
     * WiFi-Connect-Trigger nach Prozess-Tod.
     *
     * Hintergrund: ConnectivityManager.NetworkCallback ist prozessgebunden und geht
     * verloren, wenn Android den Prozess im Standby killt. Dieser WorkManager-Job
     * überlebt Prozess-Tod und prüft periodisch (alle 6h) ob ein WiFi verbunden ist.
     *
     * Da der Job eine UNMETERED-Constraint hat, wird er NUR ausgeführt wenn WiFi
     * verfügbar ist — also genau dann, wenn der NetworkCallback es verpasst hätte.
     *
     * Der Cooldown in SyncWorker.doWork() verhindert doppelte Syncs wenn der
     * NetworkCallback noch aktiv ist und bereits getriggert hat.
     */
    private fun startWifiFallbackWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()

        val fallbackRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            Constants.WIFI_FALLBACK_INTERVAL_HOURS, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .addTag(Constants.SYNC_WORK_TAG)
            .addTag("wifi-fallback")
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            Constants.WIFI_FALLBACK_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            fallbackRequest
        )

        Logger.d(TAG, "✅ WiFi-Fallback worker registered (every ${Constants.WIFI_FALLBACK_INTERVAL_HOURS}h, UNMETERED only)")
    }

    /**
     * 🆕 v2.2.0: Stoppt den WiFi-Fallback-Worker.
     */
    private fun stopWifiFallbackWorker() {
        WorkManager.getInstance(context).cancelUniqueWork(Constants.WIFI_FALLBACK_WORK_NAME)
        Logger.d(TAG, "🛑 WiFi-Fallback worker cancelled")
    }

    /**
     * Startet WorkManager mit Network Constraints + NetworkCallback
     *
     * 🆕 v1.7.0: Überarbeitete Logik - WiFi-Connect Trigger funktioniert UNABHÄNGIG von KEY_AUTO_SYNC
     * - KEY_AUTO_SYNC + KEY_SYNC_TRIGGER_PERIODIC → Periodic Sync
     * - KEY_SYNC_TRIGGER_WIFI_CONNECT → WiFi-Connect Trigger (unabhängig!)
     */
    fun startMonitoring() {
        Logger.d(TAG, "🚀 NetworkMonitor.startMonitoring() called")

        val autoSyncEnabled = prefs.getBoolean(Constants.KEY_AUTO_SYNC, false)
        val periodicEnabled = prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_PERIODIC, Constants.DEFAULT_TRIGGER_PERIODIC)
        val wifiConnectEnabled = prefs.getBoolean(
            Constants.KEY_SYNC_TRIGGER_WIFI_CONNECT,
            Constants.DEFAULT_TRIGGER_WIFI_CONNECT
        )

        Logger.d(
            TAG,
            "    Settings: autoSync=$autoSyncEnabled, periodic=$periodicEnabled, wifiConnect=$wifiConnectEnabled"
        )

        // 1. Periodic Sync (nur wenn KEY_AUTO_SYNC UND KEY_SYNC_TRIGGER_PERIODIC aktiv)
        if (autoSyncEnabled && periodicEnabled) {
            Logger.d(TAG, "📅 Starting periodic sync...")
            startPeriodicSync()
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(AUTO_SYNC_WORK_NAME)
            Logger.d(TAG, "⏭️ Periodic sync disabled (autoSync=$autoSyncEnabled, periodic=$periodicEnabled)")
        }

        // 2. WiFi-Connect Trigger (🆕 UNABHÄNGIG von KEY_AUTO_SYNC!)
        if (wifiConnectEnabled) {
            Logger.d(TAG, "📶 Starting WiFi monitoring...")
            startWifiMonitoring()
        } else {
            stopWifiMonitoring()
            Logger.d(TAG, "⏭️ WiFi-Connect trigger disabled")
        }

        // 3. 🆕 v2.2.0: WiFi-Fallback Worker (überlebt Prozess-Tod)
        // Registriert parallel zum NetworkCallback — WorkManager als Sicherheitsnetz
        if (wifiConnectEnabled) {
            startWifiFallbackWorker()
        } else {
            stopWifiFallbackWorker()
        }

        // 4. Logging für Debug
        if (!autoSyncEnabled && !wifiConnectEnabled) {
            Logger.d(TAG, "🛑 No background triggers active")
        }
    }

    /**
     * 🆕 v1.7.0: Stoppt nur WiFi-Monitoring, nicht den gesamten NetworkMonitor
     */
    @Suppress("SwallowedException")
    private fun stopWifiMonitoring() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            Logger.d(TAG, "🛑 WiFi NetworkCallback unregistered")
        } catch (e: Exception) {
            // Already unregistered - das ist OK
            Logger.d(TAG, "    WiFi callback already unregistered")
        }
    }

    /**
     * Startet WorkManager periodic sync
     * 🔥 Interval aus SharedPrefs konfigurierbar (15/30/60 min)
     * v1.6.0: Configurable trigger - checks KEY_SYNC_TRIGGER_PERIODIC
     */
    private fun startPeriodicSync() {
        // 🌟 v1.6.0: Check if Periodic trigger is enabled
        if (!prefs.getBoolean(Constants.KEY_SYNC_TRIGGER_PERIODIC, Constants.DEFAULT_TRIGGER_PERIODIC)) {
            Logger.d(TAG, "⏭️ Periodic sync disabled - skipping")
            // Cancel existing periodic work if disabled
            WorkManager.getInstance(context).cancelUniqueWork(AUTO_SYNC_WORK_NAME)
            return
        }

        // Check if server is configured
        val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null)
        if (serverUrl.isNullOrEmpty() || serverUrl == "http://" || serverUrl == "https://") {
            Logger.d(TAG, "⏭️ Offline mode - skipping Periodic sync")
            WorkManager.getInstance(context).cancelUniqueWork(AUTO_SYNC_WORK_NAME)
            return
        }

        // 🔥 Interval aus SharedPrefs lesen
        val intervalMinutes = prefs.getLong(
            Constants.PREF_SYNC_INTERVAL_MINUTES,
            Constants.DEFAULT_SYNC_INTERVAL_MINUTES
        )

        Logger.d(TAG, "📅 Configuring periodic sync: ${intervalMinutes}min interval")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED) // WiFi only
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalMinutes,
            TimeUnit.MINUTES, // 🔥 Dynamisch!
            5,
            TimeUnit.MINUTES // Flex interval
        )
            .setConstraints(constraints)
            .addTag(Constants.SYNC_WORK_TAG)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AUTO_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE, // 🔥 Update bei Interval-Änderung
            syncRequest
        )

        Logger.d(TAG, "✅ Periodic sync scheduled (every ${intervalMinutes}min)")
    }

    /**
     * Startet NetworkCallback für WiFi-Connect Detection
     */
    private fun startWifiMonitoring() {
        try {
            Logger.d(TAG, "🚀 Starting WiFi monitoring...")

            // 🛡️ Kaltstart-Guard Zeitpunkt setzen + WiFi-State initialisieren
            // WICHTIG: VOR registerNetworkCallback() — sonst Race-Condition,
            // weil onAvailable() asynchron auf ConnectivityThread feuert
            monitoringStartTime = System.currentTimeMillis()
            initializeWifiState()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            Logger.d(TAG, "    NetworkRequest built: WIFI + INTERNET capability")

            connectivityManager.registerNetworkCallback(request, networkCallback)
            Logger.d(TAG, "✅✅✅ WiFi NetworkCallback registered successfully")
            Logger.d(TAG, "    Callback will trigger on WiFi connect/disconnect")
        } catch (e: Exception) {
            Logger.e(TAG, "❌❌❌ Failed to register NetworkCallback", e)
        }
    }

    /**
     * Initialisiert lastConnectedNetworkId beim App-Start
     * Wichtig damit wir echte Netzwerk-Wechsel von App-Restarts unterscheiden können
     */
    private fun initializeWifiState() {
        try {
            Logger.d(TAG, "🔍 Initializing WiFi state...")

            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork == null) {
                Logger.d(TAG, "    ❌ No active network - lastConnectedNetworkId = null")
                lastConnectedNetworkId = null
                return
            }

            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

            if (isWifi) {
                lastConnectedNetworkId = activeNetwork.toString()
                Logger.d(TAG, "    ✅ Initial WiFi network: $lastConnectedNetworkId")
                Logger.d(
                    TAG,
                    "    📡 WiFi already connected at startup - " +
                        "onAvailable() will only trigger on network change"
                )
            } else {
                lastConnectedNetworkId = null
                Logger.d(TAG, "    ⚠️ Not on WiFi at startup")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "❌ Error initializing WiFi state", e)
            lastConnectedNetworkId = null
        }
    }

    /**
     * Prüft ob WiFi aktuell verbunden ist
     * @return true wenn WiFi verbunden, false sonst (Cellular, offline, etc.)
     */
    fun isWiFiConnected(): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            Logger.e(TAG, "Error checking WiFi status", e)
            false
        }
    }

    /**
     * Stoppt WorkManager Auto-Sync + NetworkCallback
     */
    fun stopMonitoring() {
        Logger.d(TAG, "🛑 Stopping auto-sync")

        // Stop WorkManager
        WorkManager.getInstance(context).cancelUniqueWork(AUTO_SYNC_WORK_NAME)

        // Unregister NetworkCallback
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            Logger.d(TAG, "✅ WiFi monitoring stopped")
        } catch (e: Exception) {
            Logger.w(TAG, "NetworkCallback already unregistered: ${e.message}")
        }
    }
}
