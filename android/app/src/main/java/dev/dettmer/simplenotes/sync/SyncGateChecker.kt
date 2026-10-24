package dev.dettmer.simplenotes.sync

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

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
    }

    /**
     * Prüft ob WebDAV-Server erreichbar ist (ohne Sync zu starten)
     * Verwendet Socket-Check für schnelle Erreichbarkeitsprüfung
     */
    suspend fun checkReachability(): Reachability = withContext(ioDispatcher) {
        return@withContext try {
            val serverUrl = prefs.getString(Constants.KEY_SERVER_URL, null)
            if (serverUrl == null) {
                Logger.d(TAG, "❌ Server URL not configured")
                return@withContext Reachability.Unreachable
            }

            val url = URL(serverUrl)
            val host = url.host
            val port = if (url.port > 0) url.port else url.defaultPort

            Logger.d(TAG, "🔍 Checking server reachability: $host:$port")

            val timeoutMs = ConnectionManager.getTimeoutMs(prefs)
            val socketTimeoutMs = timeoutMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

            // Phase 1: Quick TCP check
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), socketTimeoutMs)
            }

            // Phase 2: HTTP HEAD check via OkHttp to verify the server speaks HTTP.
            // 🆕 v2.21.0: probeClient trägt Pin und Connection: close, wie Statuskarte und Zertifikatsabruf.
            val request = Request.Builder().url(serverUrl).head().build()
            val code = TrustedCertificate.probeClient(prefs, serverUrl).newCall(request).execute().use { it.code }
            // Any HTTP response code (incl. 401, 403) proves HTTP capability
            if (code > 0) {
                Logger.d(TAG, "✅ Server is reachable (HTTP $code)")
                Reachability.Reachable
            } else {
                Logger.d(TAG, "❌ Server TCP reachable but HTTP check returned $code")
                Reachability.Unreachable
            }
        } catch (e: Exception) {
            val kind = SyncExceptionMapper.sslKind(e)
            if (kind != null) {
                Logger.d(TAG, "⚠️ Server answered, TLS failed ($kind): ${e.message}")
                Reachability.TlsError(kind)
            } else {
                Logger.d(TAG, "❌ Server not reachable: ${e.message}")
                Reachability.Unreachable
            }
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
        if (prefs.getBoolean(Constants.KEY_OFFLINE_MODE, Constants.DEFAULT_OFFLINE_MODE)) {
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
}

/**
 * 🆕 v2.21.0: [TlsError] = Server antwortet, aber das Zertifikat (oder TLS) scheitert. Nutzeraktionen
 * zählen das als erreichbar und zeigen den echten Fehler. Der Hintergrund-Sync überspringt (in
 * fremdem WLAN oder hinter einem Captive Portal scheitert TLS genauso), hält die Meldung aber im
 * Sync-Status fest, damit ein erneuertes Zertifikat nicht unbemerkt den Sync stoppt.
 */
sealed interface Reachability {
    data object Reachable : Reachability

    data object Unreachable : Reachability

    data class TlsError(val kind: SslKind) : Reachability
}

/**
 * Result of a canSync() check.
 */
data class SyncGateResult(val canSync: Boolean, val blockReason: String? = null) {
    val isBlockedByWifiOnly: Boolean get() = blockReason == "wifi_only"
}
