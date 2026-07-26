package dev.dettmer.simplenotes.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import com.burgstaller.okhttp.AuthenticationCacheInterceptor
import com.burgstaller.okhttp.CachingAuthenticatorDecorator
import com.burgstaller.okhttp.DispatchingAuthenticator
import com.burgstaller.okhttp.basic.BasicAuthenticator
import com.burgstaller.okhttp.digest.CachingAuthenticator
import com.burgstaller.okhttp.digest.Credentials
import com.burgstaller.okhttp.digest.DigestAuthenticator
import dev.dettmer.simplenotes.BuildConfig
import dev.dettmer.simplenotes.sync.webdav.WebDavClient
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.CredentialStore
import dev.dettmer.simplenotes.utils.Logger
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * 🆕 v2.0.0: Extracted from WebDavSyncService (Commit 18).
 * Manages the WebDAV client lifecycle:
 * - Client creation (credentials, timeout, OkHttp config)
 * - Session caching (one client per sync operation)
 * - Session cleanup (close client + reset caches)
 */
class ConnectionManager(private val context: Context, private val prefs: SharedPreferences) {
    companion object {
        private const val TAG = "ConnectionManager"
        private const val FALLBACK_TIMEOUT_MS = 8000L

        /**
         * Reads the configured connection timeout from SharedPreferences.
         * Converts seconds to milliseconds, clamped to MIN..MAX range.
         * Single source of truth — also used by SyncGateChecker.
         */
        fun getTimeoutMs(prefs: SharedPreferences): Long {
            return try {
                val seconds = prefs.getInt(
                    Constants.KEY_CONNECTION_TIMEOUT_SECONDS,
                    Constants.DEFAULT_CONNECTION_TIMEOUT_SECONDS
                ).coerceIn(
                    Constants.MIN_CONNECTION_TIMEOUT_SECONDS,
                    Constants.MAX_CONNECTION_TIMEOUT_SECONDS
                )
                seconds * 1000L
            } catch (e: Exception) {
                Logger.d(TAG, "Timeout parsing failed, using fallback ${FALLBACK_TIMEOUT_MS}ms: ${e.message}")
                FALLBACK_TIMEOUT_MS
            }
        }

        /**
         * Prozessweiter Auth-Cache. okhttp-digest cached per `scheme:host:port` (ohne
         * Credentials im Key) — deshalb wird der Cache bei Credential-Wechsel komplett
         * verworfen. Stale Einträge heilen sich sonst via 401 → Decorator selbst.
         */
        private val sharedAuthCache = ConcurrentHashMap<String, CachingAuthenticator>()

        @Volatile
        private var sharedAuthCacheKey: String? = null

        /**
         * 🆕 v2.14.0: Baut den OkHttpClient für WebDAV-Requests.
         *
         * Auth läuft über `okhttp-digest`: [DispatchingAuthenticator] wählt anhand der
         * `WWW-Authenticate`-Challenge zwischen Digest und Basic. Der Auth-Cache
         * (Decorator + Interceptor) sorgt dafür, dass nur der erste Request einen
         * 401-Round-Trip kostet — alle Folge-Requests authentifizieren preemptiv.
         * Der Cache ist prozessweit, überlebt also [clearSession] und kostet damit
         * nur einen 401 pro App-Start statt einen pro Sync.
         *
         * Single source of truth für Timeouts: [getTimeoutMs].
         */
        fun buildHttpClient(timeoutMs: Long, username: String, password: String): OkHttpClient {
            val credentials = Credentials(username, password)
            val authenticator = DispatchingAuthenticator.Builder()
                .with("digest", DigestAuthenticator(credentials))
                .with("basic", BasicAuthenticator(credentials))
                .build()
            val authCache = sharedAuthCache.also { cache ->
                val key = credentialFingerprint(username, password)
                synchronized(this) {
                    if (sharedAuthCacheKey != key) {
                        cache.clear()
                        sharedAuthCacheKey = key
                    }
                }
            }

            return OkHttpClient.Builder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .authenticator(CachingAuthenticatorDecorator(authenticator, authCache))
                .addInterceptor(AuthenticationCacheInterceptor(authCache))
                .build()
        }

        /** SHA-256 über `user:pass` — der Klartext darf nirgends gehalten oder geloggt werden. */
        private fun credentialFingerprint(username: String, password: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest("$username:$password".toByteArray())
                .joinToString("") { "%02x".format(it) }

        @VisibleForTesting
        internal fun clearSharedAuthCacheForTest() {
            synchronized(this) {
                sharedAuthCache.clear()
                sharedAuthCacheKey = null
            }
        }
    }

    // ⚡ v1.3.1 Performance: Session-cached WebDAV client
    private var sessionClient: WebDavClient? = null

    /** Tracks whether the notes/ directory has been verified this session. */
    var notesDirEnsured: Boolean = false

    /** Tracks whether the notes-md/ directory has been verified this session. */
    var markdownDirEnsured: Boolean = false

    /** 🆕 Bild-Attachments: Tracks whether the notes-assets/ directory has been verified this session. */
    var assetsDirEnsured: Boolean = false

    /**
     * Returns the cached WebDAV client or creates a new one.
     * Saves ~100ms per call by reusing the existing client.
     * internal for NotesImportWizard access (Issue #21).
     */
    internal fun getOrCreateClient(): WebDavClient? {
        sessionClient?.let {
            Logger.d(TAG, "⚡ Reusing cached WebDAV client")
            return it
        }
        val client = createClient()
        sessionClient = client
        return client
    }

    /**
     * Creates a new WebDavClient with credentials and timeout from SharedPreferences.
     *
     * v1.8.2: readTimeout added — prevents indefinite wait on hanging servers.
     * v1.10.0: Configurable timeout from SharedPreferences.
     * v2.14.0: Eigener Client statt WebDavClient; Basic + Digest via okhttp-digest.
     */
    private fun createClient(): WebDavClient? {
        val username = CredentialStore.getUsername(context) ?: return null
        val password = CredentialStore.getPassword(context) ?: return null

        Logger.d(TAG, "🔧 Creating WebDavClient")

        return WebDavClient(buildHttpClient(getTimeoutMs(), username, password))
    }

    /**
     * Clears the session cache and closes the WebDAV client.
     * Called at the end of each syncNotes() invocation.
     */
    fun clearSession() {
        sessionClient?.let { client ->
            try {
                client.close()
                Logger.d(TAG, "🧹 WebDAV client closed")
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to close WebDAV client: ${e.message}")
            }
        }
        sessionClient = null
        notesDirEnsured = false
        markdownDirEnsured = false
        assetsDirEnsured = false
        if (BuildConfig.DEBUG) {
            Logger.d(TAG, "🧹 Session caches cleared")
        }
    }

    /** Delegates to the Companion implementation — preserves existing call sites. */
    fun getTimeoutMs(): Long = Companion.getTimeoutMs(prefs)
}
