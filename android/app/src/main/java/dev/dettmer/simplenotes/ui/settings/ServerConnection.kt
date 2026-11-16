package dev.dettmer.simplenotes.ui.settings

import androidx.core.content.edit
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.sync.SyncExceptionMapper
import dev.dettmer.simplenotes.sync.TrustedCertificate
import dev.dettmer.simplenotes.sync.WebDavSyncService
import dev.dettmer.simplenotes.ui.settings.SettingsViewModel.CertificatePrompt
import dev.dettmer.simplenotes.ui.settings.SettingsViewModel.ServerStatus
import dev.dettmer.simplenotes.utils.ActivityLog
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Verbindung zum konfigurierten Server: Status-Anzeige, Verbindungstest samt Zertifikats-Pin,
 * „Jetzt synchronisieren" und der Verbindungs-Timeout.
 *
 * Adresse und Offline-Modus gehören [ServerSettings]; diese Klasse liest sie über [serverUrl]
 * und [isOffline] im Moment des Aufrufs.
 */
class ServerConnection(
    private val ctx: SettingsContext,
    private val serverUrl: () -> String,
    private val isOffline: () -> Boolean
) {
    private val prefs = ctx.prefs
    private val ioDispatcher = Dispatchers.IO

    private val _serverStatus = MutableStateFlow<ServerStatus>(ServerStatus.Unknown)
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

    private val _certificatePrompt = MutableStateFlow<CertificatePrompt?>(null)
    val certificatePrompt: StateFlow<CertificatePrompt?> = _certificatePrompt.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // 🆕 v1.10.0
    private val _connectionTimeoutSeconds = MutableStateFlow(readConnectionTimeout())
    val connectionTimeoutSeconds: StateFlow<Int> = _connectionTimeoutSeconds.asStateFlow()

    /** WebDavSyncService liest den Wert bei jedem Sync-Start aus den Prefs. */
    fun setConnectionTimeoutSeconds(seconds: Int) {
        val validSeconds = seconds.coerceIn(Constants.MIN_CONNECTION_TIMEOUT_SECONDS, Constants.MAX_CONNECTION_TIMEOUT_SECONDS)
        prefs.edit { putInt(Constants.KEY_CONNECTION_TIMEOUT_SECONDS, validSeconds) }
        _connectionTimeoutSeconds.value = validSeconds
        Logger.d(TAG, "Connection timeout set to: ${validSeconds}s")
    }

    fun testConnection() {
        ctx.scope.launch {
            _serverStatus.value = ServerStatus.Checking
            try {
                val result = WebDavSyncService(ctx.app).testConnection()
                _serverStatus.value = when {
                    result.isSuccess -> ServerStatus.Reachable
                    result.untrustedCertificate != null -> ServerStatus.CertificateProblem
                    else -> ServerStatus.Unreachable(result.errorMessage)
                }
                // 🆕 v2.21.0: Der Dialog ersetzt den Fehler-Toast
                result.untrustedCertificate?.let { certificate ->
                    val replacesTrusted = TrustedCertificate.pin(prefs, certificate.url) != null // nur gleicher Host
                    _certificatePrompt.value = CertificatePrompt(certificate, replacesTrusted)
                    return@launch
                }
                val message = if (result.isSuccess) {
                    // 🆕 Issue #21: infoMessage anzeigen wenn vorhanden (z.B. /notes/-Status)
                    result.infoMessage ?: ctx.string(R.string.toast_connection_success)
                } else {
                    ctx.string(R.string.toast_connection_failed, result.errorMessage.orEmpty())
                }
                ctx.toast(message)
            } catch (e: Exception) {
                _serverStatus.value = ServerStatus.Unreachable(e.message)
                ctx.toast(ctx.string(R.string.toast_error, e.message.orEmpty()))
            }
        }
    }

    /** 🆕 v2.21.0: Pin für den getesteten Host speichern und gleich erneut testen. */
    fun trustCertificate() {
        val prompt = _certificatePrompt.value ?: return
        TrustedCertificate.trust(prefs, prompt.certificate.url, prompt.certificate.sha256)
        Logger.i(TAG, "Trusted server certificate updated")
        _certificatePrompt.value = null
        testConnection()
    }

    fun dismissCertificatePrompt() {
        _certificatePrompt.value = null
    }

    fun checkServerStatus() {
        if (isOffline()) {
            _serverStatus.value = ServerStatus.OfflineMode
            return
        }
        val url = serverUrl()
        if (url.isEmpty()) {
            _serverStatus.value = ServerStatus.NotConfigured
            return
        }
        ctx.scope.launch {
            _serverStatus.value = ServerStatus.Checking
            _serverStatus.value = withContext(ioDispatcher) {
                try {
                    // 🔧 v2.21.0: OkHttp statt HttpURLConnection, mit Pin, Timeout aus den Einstellungen
                    // und Connection: close (HEAD, v2.20.2). 405 = HEAD verboten (bewCloud), Server antwortet.
                    val request = Request.Builder().url(url).head().build()
                    val code = TrustedCertificate.probeClient(prefs, url).newCall(request).execute().use { it.code }
                    val reachable = code in 200..299 || code == HTTP_UNAUTHORIZED || code == HTTP_METHOD_NOT_ALLOWED
                    if (reachable) ServerStatus.Reachable else ServerStatus.Unreachable(null)
                } catch (e: Exception) {
                    Logger.e(TAG, "Server check failed: ${e.message}")
                    // 🆕 v2.21.0: Server antwortet, das Zertifikat scheitert. „Nicht erreichbar" wäre falsch.
                    if (SyncExceptionMapper.sslKind(e) != null) ServerStatus.CertificateProblem else ServerStatus.Unreachable(null)
                }
            }
        }
    }

    fun syncNow() {
        if (_isSyncing.value) return
        ctx.scope.launch {
            _isSyncing.value = true
            try {
                runSyncNow()
            } catch (e: Exception) {
                ctx.toast(ctx.string(R.string.toast_error, e.message.orEmpty()))
            } finally {
                _isSyncing.value = false
            }
        }
    }

    private suspend fun runSyncNow() {
        val syncService = WebDavSyncService(ctx.app)
        val gateResult = syncService.canSync()
        if (!gateResult.canSync) {
            val message = if (gateResult.isBlockedByWifiOnly) {
                ctx.string(R.string.sync_wifi_only_hint)
            } else {
                ctx.string(R.string.toast_sync_failed, "Offline mode")
            }
            ctx.toast(message)
            return
        }
        ctx.toast(ctx.string(R.string.toast_syncing))
        if (!syncService.hasUnsyncedChanges()) {
            ctx.toast(ctx.string(R.string.toast_already_synced))
            return
        }
        // 🆕 FIX-015: HTTP reachability check before sync
        if (!withContext(ioDispatcher) { syncService.isServerReachable(userAction = true) }) {
            ctx.toast(ctx.string(R.string.snackbar_server_unreachable))
            return
        }
        val result = syncService.syncNotes(ActivityLog.Trigger.SETTINGS)
        val message = when {
            result.isSuccess -> ctx.string(R.string.toast_sync_success, result.syncedCount)
            result.e2eeBlocked -> result.errorMessage.orEmpty() // 🆕 v2.20.0: Sperre, kein Fehler
            else -> ctx.string(R.string.toast_sync_failed, result.errorMessage.orEmpty())
        }
        ctx.toast(message)
    }

    fun reloadFromPrefs() {
        _connectionTimeoutSeconds.value = readConnectionTimeout()
    }

    private fun readConnectionTimeout(): Int =
        prefs.getInt(Constants.KEY_CONNECTION_TIMEOUT_SECONDS, Constants.DEFAULT_CONNECTION_TIMEOUT_SECONDS)
            .coerceIn(Constants.MIN_CONNECTION_TIMEOUT_SECONDS, Constants.MAX_CONNECTION_TIMEOUT_SECONDS)

    private companion object {
        const val TAG = "ServerConnection"
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_METHOD_NOT_ALLOWED = 405
    }
}
