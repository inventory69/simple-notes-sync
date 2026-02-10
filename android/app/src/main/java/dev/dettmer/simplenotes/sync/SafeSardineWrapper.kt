package dev.dettmer.simplenotes.sync

import com.thegrizzlylabs.sardineandroid.DavResource
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import dev.dettmer.simplenotes.utils.Logger
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.Closeable
import java.io.InputStream

/**
 * 🔧 v1.7.1: Wrapper für Sardine der Connection Leaks verhindert
 * 🔧 v1.7.2 (IMPL_003): Implementiert Closeable für explizites Resource-Management
 *
 * Hintergrund:
 * - OkHttpSardine.exists() schließt den Response-Body nicht
 * - Dies führt zu "connection leaked" Warnungen im Log
 * - Kann bei vielen Requests zu Socket-Exhaustion führen
 * - Session-Cache hält Referenzen ohne explizites Cleanup
 *
 * Lösung:
 * - Eigene exists()-Implementation mit korrektem Response-Cleanup
 * - Preemptive Authentication um 401-Round-Trips zu vermeiden
 * - Closeable Pattern für explizite Resource-Freigabe
 *
 * @see <a href="https://square.github.io/okhttp/4.x/okhttp/okhttp3/-response-body/">OkHttp Response Body Docs</a>
 */
class SafeSardineWrapper private constructor(
    private val delegate: OkHttpSardine,
    private val okHttpClient: OkHttpClient,
    private val authHeader: String
) : Sardine by delegate, Closeable {

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
    
    // 🆕 v1.7.2 (IMPL_003): Track ob bereits geschlossen
    @Volatile
    private var isClosed = false

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

    /**
     * ✅ Sichere put()-Implementation mit Response Cleanup
     *
     * Im Gegensatz zu OkHttpSardine.put() wird hier der Response-Body garantiert geschlossen.
     * Verhindert "connection leaked" Warnungen.
     */
    override fun put(url: String, data: ByteArray, contentType: String?) {
        val mediaType = contentType?.toMediaTypeOrNull()
        val body = data.toRequestBody(mediaType)
        
        val request = Request.Builder()
            .url(url)
            .put(body)
            .header("Authorization", authHeader)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("PUT failed: ${response.code} ${response.message}")
            }
            Logger.d(TAG, "put($url) → ${response.code}")
        }
    }

    /**
     * ✅ Sichere delete()-Implementation mit Response Cleanup
     *
     * Im Gegensatz zu OkHttpSardine.delete() wird hier der Response-Body garantiert geschlossen.
     * Verhindert "connection leaked" Warnungen.
     */
    override fun delete(url: String) {
        val request = Request.Builder()
            .url(url)
            .delete()
            .header("Authorization", authHeader)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("DELETE failed: ${response.code} ${response.message}")
            }
            Logger.d(TAG, "delete($url) → ${response.code}")
        }
    }

    /**
     * ✅ Sichere createDirectory()-Implementation mit Response Cleanup
     *
     * Im Gegensatz zu OkHttpSardine.createDirectory() wird hier der Response-Body garantiert geschlossen.
     * Verhindert "connection leaked" Warnungen.
     * 405 (Method Not Allowed) wird toleriert da dies bedeutet, dass der Ordner bereits existiert.
     */
    override fun createDirectory(url: String) {
        val request = Request.Builder()
            .url(url)
            .method("MKCOL", null)
            .header("Authorization", authHeader)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 405) { // 405 = already exists
                throw java.io.IOException("MKCOL failed: ${response.code} ${response.message}")
            }
            Logger.d(TAG, "createDirectory($url) → ${response.code}")
        }
    }
    
    /**
     * 🆕 v1.7.2 (IMPL_003): Schließt alle offenen Verbindungen
     * 
     * Wichtig: Nach close() darf der Client nicht mehr verwendet werden!
     * Eviction von Connection Pool Einträgen und Cleanup von internen Ressourcen.
     */
    override fun close() {
        if (isClosed) {
            Logger.d(TAG, "Already closed, skipping")
            return
        }
        
        try {
            // OkHttpClient Connection Pool räumen
            okHttpClient.connectionPool.evictAll()
            
            // Dispatcher shutdown (beendet laufende Calls)
            okHttpClient.dispatcher.cancelAll()
            
            isClosed = true
            Logger.d(TAG, "✅ Closed successfully (connections evicted)")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to close", e)
        }
    }

    // Alle anderen Methoden werden automatisch durch 'by delegate' weitergeleitet
}
