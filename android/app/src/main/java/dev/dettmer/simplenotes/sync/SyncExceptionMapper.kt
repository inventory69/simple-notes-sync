package dev.dettmer.simplenotes.sync

import android.content.Context
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.sync.webdav.WebDavException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/** 🆕 v2.21.0: Art des TLS-Fehlers, unabhängig vom (übersetzten) Meldungstext. */
enum class SslKind { HOSTNAME, EXPIRED, UNTRUSTED, OTHER }

/**
 * Maps sync exceptions to user-friendly error messages.
 *
 * Extracted from WebDavSyncService in v2.0.0 (Commit 16).
 * Centralizes all WebDavClient/IO/SSL exception handling in one place.
 */
class SyncExceptionMapper(private val context: Context) {
    companion object {
        // HTTP Status codes for WebDavException mapping
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_CONFLICT = 409
        private const val HTTP_PAYLOAD_TOO_LARGE = 413
        private const val HTTP_INTERNAL_SERVER_ERROR = 500
        private const val HTTP_INSUFFICIENT_STORAGE = 507
        private const val MAX_CAUSE_DEPTH = 10

        /**
         * 🆕 v2.21.0: Sucht die ganze Ursachenkette ab, OkHttp und Aufrufer hüllen die
         * `SSLException` mitunter in eine `IOException`. Auch `suppressed`: OkHttp 5 probiert IPv6
         * und IPv4 parallel, scheitert eine Adresse am Connect, steckt der TLS-Fehler der anderen
         * dort. `null` = kein TLS-Fehler, auch bei blanker `SSLException` (Verbindungsabbruch,
         * Captive Portal mitten im Transfer): Das ist ein Netzfehler, kein Zertifikatsproblem.
         */
        fun sslKind(e: Throwable): SslKind? {
            val chain = related(e, 0).toList()
            val certificate = chain.any { it is CertificateException || it is CertPathValidatorException }
            return when {
                chain.any { it is SSLPeerUnverifiedException } -> SslKind.HOSTNAME
                chain.any { it is CertificateExpiredException || it is CertificateNotYetValidException } ->
                    SslKind.EXPIRED
                certificate -> SslKind.UNTRUSTED
                chain.any { it is SSLHandshakeException } -> SslKind.OTHER // Protokoll/Cipher
                else -> null
            }
        }

        fun sslMessage(kind: SslKind): Int = when (kind) {
            SslKind.HOSTNAME -> R.string.sync_error_ssl_hostname
            SslKind.EXPIRED -> R.string.sync_error_ssl_expired
            SslKind.UNTRUSTED -> R.string.sync_error_ssl_untrusted
            SslKind.OTHER -> R.string.sync_error_ssl
        }

        private fun related(e: Throwable, depth: Int): Sequence<Throwable> = sequence {
            yield(e)
            if (depth >= MAX_CAUSE_DEPTH) return@sequence
            e.cause?.let { yieldAll(related(it, depth + 1)) }
            e.suppressed.forEach { yieldAll(related(it, depth + 1)) }
        }
    }

    /**
     * Maps an exception to a user-friendly error message.
     *
     * @param e The exception that occurred during sync
     * @return A localised, user-readable error message
     */
    // 🔧 v2.21.0: TLS vor allem anderen, SSLException erbt von IOException (der alte Zweig war tot).
    fun mapToUserMessage(e: Exception): String =
        sslKind(e)?.let { context.getString(sslMessage(it)) } ?: mapNonSsl(e)

    private fun mapNonSsl(e: Exception): String {
        return when (e) {
            is java.net.ConnectException ->
                context.getString(R.string.snackbar_server_unreachable)
            is java.net.UnknownHostException ->
                context.getString(R.string.snackbar_server_unreachable)
            is java.net.SocketTimeoutException ->
                context.getString(R.string.snackbar_connection_timeout)
            is java.net.NoRouteToHostException ->
                context.getString(R.string.snackbar_server_unreachable)
            // ⚠️ Muss VOR dem IOException-Zweig stehen — WebDavException erbt davon.
            is WebDavException -> mapWebDavException(e)
            is java.io.IOException -> {
                // IOException kann vieles sein — prüfe ob es ein Timeout-artiger Fehler ist
                val msg = e.message?.lowercase().orEmpty()
                when {
                    // 🔧 v2.3.0: Auth errors must be detected before MKCOL check —
                    // MKCOL can fail with 401, which is an auth problem, not a path problem.
                    // Also catches IOException("Authentication failed (401)...") from exists().
                    msg.contains("401") && (msg.contains("unauthorized") || msg.contains("authentication failed"))
                    -> context.getString(R.string.sync_error_auth_failed)
                    msg.contains("mkcol failed") -> context.getString(R.string.sync_error_mkcol_failed)
                    msg.contains("timeout") -> context.getString(R.string.snackbar_connection_timeout)
                    msg.contains("refused") -> context.getString(R.string.snackbar_server_unreachable)
                    msg.contains("unreachable") -> context.getString(R.string.snackbar_server_unreachable)
                    else -> "${context.getString(R.string.sync_error_unknown)}: ${e.message}"
                }
            }
            else -> e.message ?: context.getString(R.string.sync_error_unknown)
        }
    }

    /**
     * MKCOL-404 behält seine eigene Meldung ("Server-Pfad prüfen") — der generische
     * 404-Text ("Pfad nicht gefunden") wäre beim Erstsetup irreführend.
     */
    private fun mapWebDavException(e: WebDavException): String {
        if (e.message?.lowercase()?.contains("mkcol failed") == true && e.statusCode == HTTP_NOT_FOUND) {
            return context.getString(R.string.sync_error_mkcol_failed)
        }
        return when (e.statusCode) {
            HTTP_UNAUTHORIZED -> context.getString(R.string.sync_error_auth_failed)
            HTTP_FORBIDDEN -> context.getString(R.string.sync_error_access_denied)
            HTTP_NOT_FOUND -> context.getString(R.string.sync_error_path_not_found)
            // 🆕 v2.19.0: die drei häufigsten Gründe, warum eine MD-Kopie oder ein Bild nicht hochgeht
            HTTP_CONFLICT -> context.getString(R.string.sync_error_conflict_path)
            HTTP_PAYLOAD_TOO_LARGE -> context.getString(R.string.sync_error_too_large)
            HTTP_INTERNAL_SERVER_ERROR -> context.getString(R.string.sync_error_server)
            HTTP_INSUFFICIENT_STORAGE -> context.getString(R.string.sync_error_storage_full)
            else -> context.getString(R.string.sync_error_http, e.statusCode)
        }
    }
}
