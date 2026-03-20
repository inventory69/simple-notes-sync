package dev.dettmer.simplenotes.sync

import android.content.SharedPreferences
import com.thegrizzlylabs.sardineandroid.Sardine
import dev.dettmer.simplenotes.BuildConfig
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * 🆕 v2.0.0: Extracted from WebDavSyncService (Commit 18).
 * Manages the WebDAV/Sardine client lifecycle:
 * - Client creation (credentials, timeout, OkHttp config)
 * - Session caching (one client per sync operation)
 * - Session cleanup (close client + reset caches)
 */
class ConnectionManager(private val prefs: SharedPreferences) {
    companion object {
        private const val TAG = "ConnectionManager"
        private const val FALLBACK_TIMEOUT_MS = 8000L
    }

    // ⚡ v1.3.1 Performance: Session-cached Sardine client
    private var sessionSardine: SafeSardineWrapper? = null

    /** Tracks whether the notes/ directory has been verified this session. */
    var notesDirEnsured: Boolean = false

    /** Tracks whether the notes-md/ directory has been verified this session. */
    var markdownDirEnsured: Boolean = false

    /**
     * Returns the cached Sardine client or creates a new one.
     * Saves ~100ms per call by reusing the existing client.
     * internal for NotesImportWizard access (Issue #21).
     */
    internal fun getOrCreateClient(): Sardine? {
        sessionSardine?.let {
            Logger.d(TAG, "⚡ Reusing cached Sardine client")
            return it
        }
        val sardine = createClient()
        sessionSardine = sardine
        return sardine
    }

    /**
     * Creates a new SafeSardineWrapper with credentials and timeout from SharedPreferences.
     *
     * v1.7.2: Intelligent routing based on target address —
     *   local servers use WiFi binding, external servers use default routing.
     * v1.7.1: Uses SafeSardineWrapper (prevents connection leaks, preemptive auth).
     */
    private fun createClient(): SafeSardineWrapper? {
        val username = prefs.getString(Constants.KEY_USERNAME, null) ?: return null
        val password = prefs.getString(Constants.KEY_PASSWORD, null) ?: return null

        Logger.d(TAG, "🔧 Creating SafeSardineWrapper")

        // v1.8.2: readTimeout added — prevents indefinite wait on hanging servers
        // v1.10.0: Configurable timeout from SharedPreferences
        val timeoutMs = getTimeoutMs()
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()

        return SafeSardineWrapper.create(okHttpClient, username, password)
    }

    /**
     * Clears the session cache and closes the Sardine client.
     * Called at the end of each syncNotes() invocation.
     */
    fun clearSession() {
        sessionSardine?.let { sardine ->
            try {
                sardine.close()
                Logger.d(TAG, "🧹 Sardine client closed")
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to close Sardine client: ${e.message}")
            }
        }
        sessionSardine = null
        notesDirEnsured = false
        markdownDirEnsured = false
        if (BuildConfig.DEBUG) {
            Logger.d(TAG, "🧹 Session caches cleared")
        }
    }

    /**
     * Reads the configured connection timeout from SharedPreferences.
     * Converts seconds to milliseconds, clamped to MIN..MAX range.
     */
    fun getTimeoutMs(): Long {
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
