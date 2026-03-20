package dev.dettmer.simplenotes.sync

import android.content.Context
import dev.dettmer.simplenotes.R

/**
 * Maps sync exceptions to user-friendly error messages.
 *
 * Extracted from WebDavSyncService in v2.0.0 (Commit 16).
 * Centralizes all Sardine/IO/SSL exception handling in one place.
 */
class SyncExceptionMapper(private val context: Context) {
    companion object {
        // HTTP Status codes for SardineException mapping
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_INTERNAL_SERVER_ERROR = 500
    }

    /**
     * Maps an exception to a user-friendly error message.
     *
     * @param e The exception that occurred during sync
     * @return A localised, user-readable error message
     */
    fun mapToUserMessage(e: Exception): String {
        return when (e) {
            is java.net.ConnectException ->
                context.getString(R.string.snackbar_server_unreachable)
            is java.net.UnknownHostException ->
                context.getString(R.string.snackbar_server_unreachable)
            is java.net.SocketTimeoutException ->
                context.getString(R.string.snackbar_connection_timeout)
            is java.net.NoRouteToHostException ->
                context.getString(R.string.snackbar_server_unreachable)
            is java.io.IOException -> {
                // IOException kann vieles sein — prüfe ob es ein Timeout-artiger Fehler ist
                val msg = e.message?.lowercase().orEmpty()
                when {
                    msg.contains("timeout") -> context.getString(R.string.snackbar_connection_timeout)
                    msg.contains("refused") -> context.getString(R.string.snackbar_server_unreachable)
                    msg.contains("unreachable") -> context.getString(R.string.snackbar_server_unreachable)
                    else -> "${context.getString(R.string.sync_error_unknown)}: ${e.message}"
                }
            }
            is javax.net.ssl.SSLException ->
                context.getString(R.string.sync_error_ssl)
            is com.thegrizzlylabs.sardineandroid.impl.SardineException -> {
                when (e.statusCode) {
                    HTTP_UNAUTHORIZED -> context.getString(R.string.sync_error_auth_failed)
                    HTTP_FORBIDDEN -> context.getString(R.string.sync_error_access_denied)
                    HTTP_NOT_FOUND -> context.getString(R.string.sync_error_path_not_found)
                    HTTP_INTERNAL_SERVER_ERROR -> context.getString(R.string.sync_error_server)
                    else -> context.getString(R.string.sync_error_http, e.statusCode)
                }
            }
            else -> e.message ?: context.getString(R.string.sync_error_unknown)
        }
    }
}
