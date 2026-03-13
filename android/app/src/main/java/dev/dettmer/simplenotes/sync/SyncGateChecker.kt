package dev.dettmer.simplenotes.sync

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

/**
 * 🆕 v2.0.0: Extracted from WebDavSyncService.
 * Checks all preconditions before a sync is started.
 */
class SyncGateChecker(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val TAG = "SyncGateChecker"
        private const val FALLBACK_TIMEOUT_MS = 8000L
    }

    /**
     * Prüft ob WebDAV-Server erreichbar ist (ohne Sync zu starten)
     * Verwendet Socket-Check für schnelle Erreichbarkeitsprüfung
     */
    suspend fun isServerReachable(): Boolean = withContext(ioDispatcher) {
        return@withContext try {
            val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null)
            if (serverUrl == null) {
                Logger.d(TAG, "❌ Server URL not configured")
                return@withContext false
            }

            val url = URL(serverUrl)
            val host = url.host
            val port = if (url.port > 0) url.port else url.defaultPort

            Logger.d(TAG, "🔍 Checking server reachability: $host:$port")

            val socketTimeoutMs = getTimeoutMs().toInt()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), socketTimeoutMs)
            }

            Logger.d(TAG, "✅ Server is reachable")
            true
        } catch (e: Exception) {
            Logger.d(TAG, "❌ Server not reachable: ${e.message}")
            false
        }
    }

    /**
     * Prüft ob Gerät aktuell im WLAN ist.
     * Für schnellen Pre-Check VOR dem langsamen Socket-Check.
     */
    fun isOnWiFi(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager ?: return false
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to check WiFi state", e)
            false
        }
    }

    /**
     * Zentrale Sync-Gate Prüfung.
     * Prüft ALLE Voraussetzungen bevor ein Sync gestartet wird.
     *
     * @return SyncGateResult mit canSync flag und optionalem Blockierungsgrund
     */
    fun canSync(): SyncGateResult {
        // 1. Offline Mode Check
        if (prefs.getBoolean(Constants.KEY_OFFLINE_MODE, true)) {
            return SyncGateResult(canSync = false, blockReason = null) // Silent skip
        }

        // 2. Server configured?
        val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null)
        if (serverUrl.isNullOrEmpty() || serverUrl == "http://" || serverUrl == "https://") {
            return SyncGateResult(canSync = false, blockReason = null) // Silent skip
        }

        // 3. WiFi-Only Check
        val wifiOnlySync = prefs.getBoolean(Constants.KEY_WIFI_ONLY_SYNC, Constants.DEFAULT_WIFI_ONLY_SYNC)
        if (wifiOnlySync && !isOnWiFi()) {
            return SyncGateResult(canSync = false, blockReason = "wifi_only")
        }

        return SyncGateResult(canSync = true, blockReason = null)
    }

    private fun getTimeoutMs(): Long {
        return try {
            val seconds = prefs.getInt(
                Constants.KEY_CONNECTION_TIMEOUT_SECONDS,
                Constants.DEFAULT_CONNECTION_TIMEOUT_SECONDS
            ).coerceIn(
                Constants.MIN_CONNECTION_TIMEOUT_SECONDS,
                Constants.MAX_CONNECTION_TIMEOUT_SECONDS
            )
            seconds * 1000L
        } catch (_: Exception) {
            FALLBACK_TIMEOUT_MS
        }
    }
}

/**
 * Result of a canSync() check.
 */
data class SyncGateResult(
    val canSync: Boolean,
    val blockReason: String? = null
) {
    val isBlockedByWifiOnly: Boolean get() = blockReason == "wifi_only"
}
