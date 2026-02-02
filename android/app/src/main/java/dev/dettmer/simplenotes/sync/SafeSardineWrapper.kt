package dev.dettmer.simplenotes.sync

import com.thegrizzlylabs.sardineandroid.DavResource
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import dev.dettmer.simplenotes.utils.Logger
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream

/**
 * 🔧 v1.7.1: Wrapper für Sardine der Connection Leaks verhindert
 *
 * Hintergrund:
 * - OkHttpSardine.exists() schließt den Response-Body nicht
 * - Dies führt zu "connection leaked" Warnungen im Log
 * - Kann bei vielen Requests zu Socket-Exhaustion führen
 *
 * Lösung:
 * - Eigene exists()-Implementation mit korrektem Response-Cleanup
 * - Preemptive Authentication um 401-Round-Trips zu vermeiden
 *
 * @see <a href="https://square.github.io/okhttp/4.x/okhttp/okhttp3/-response-body/">OkHttp Response Body Docs</a>
 */
class SafeSardineWrapper private constructor(
    private val delegate: OkHttpSardine,
    private val okHttpClient: OkHttpClient,
    private val authHeader: String
) : Sardine by delegate {

    companion object {
        private const val TAG = "SafeSardine"

        /**
         * Factory-Methode für SafeSardineWrapper
         */
        fun create(
            okHttpClient: OkHttpClient,
            username: String,
            password: String
        ): SafeSardineWrapper {
            val delegate = OkHttpSardine(okHttpClient).apply {
                setCredentials(username, password)
            }
            val authHeader = Credentials.basic(username, password)
            return SafeSardineWrapper(delegate, okHttpClient, authHeader)
        }
    }

    /**
     * ✅ Sichere exists()-Implementation mit Response Cleanup
     *
     * Im Gegensatz zu OkHttpSardine.exists() wird hier:
     * 1. Preemptive Auth-Header gesendet (kein 401 Round-Trip)
     * 2. Response.use{} für garantiertes Cleanup verwendet
     */
    override fun exists(url: String): Boolean {
        val request = Request.Builder()
            .url(url)
            .head()
            .header("Authorization", authHeader)
            .build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                val isSuccess = response.isSuccessful
                Logger.d(TAG, "exists($url) → $isSuccess (${response.code})")
                isSuccess
            }
        } catch (e: Exception) {
            Logger.d(TAG, "exists($url) failed: ${e.message}")
            false
        }
    }

    /**
     * ✅ Wrapper um get() mit Logging
     *
     * WICHTIG: Der zurückgegebene InputStream MUSS vom Caller geschlossen werden!
     * Empfohlen: inputStream.bufferedReader().use { it.readText() }
     */
    override fun get(url: String): InputStream {
        Logger.d(TAG, "get($url)")
        return delegate.get(url)
    }

    /**
     * ✅ Wrapper um list() mit Logging
     */
    override fun list(url: String): List<DavResource> {
        Logger.d(TAG, "list($url)")
        return delegate.list(url)
    }

    /**
     * ✅ Wrapper um list(url, depth) mit Logging
     */
    override fun list(url: String, depth: Int): List<DavResource> {
        Logger.d(TAG, "list($url, depth=$depth)")
        return delegate.list(url, depth)
    }

    // Alle anderen Methoden werden automatisch durch 'by delegate' weitergeleitet
}
