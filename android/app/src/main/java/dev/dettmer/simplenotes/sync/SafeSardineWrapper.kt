package dev.dettmer.simplenotes.sync

import com.thegrizzlylabs.sardineandroid.DavResource
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import dev.dettmer.simplenotes.utils.Logger
import java.io.Closeable
import java.io.InputStream
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val HTTP_METHOD_NOT_ALLOWED = 405
private const val HTTP_NOT_FOUND = 404
private const val HTTP_FORBIDDEN = 403
private const val HTTP_GONE = 410
private const val HTTP_UNAUTHORIZED = 401

/**
 * 🔧 v1.7.1: Wrapper für Sardine der Connection Leaks verhindert
 * 🔧 v1.7.2 (IMPL_003): Implementiert Closeable für explizites Resource-Management
 * 🔧 v2.0.0 (Issue #44): exists() behandelt 403 korrekt (Jianguoyun-Kompatibilität)
 *
 * Hintergrund:
 * - OkHttpSardine.exists() schließt den Response-Body nicht
 * - Dies führt zu "connection leaked" Warnungen im Log
 * - Kann bei vielen Requests zu Socket-Exhaustion führen
 * - Session-Cache hält Referenzen ohne explizites Cleanup
 * - Jianguoyun WebDAV antwortet mit 403 auf HEAD-Requests für Verzeichnisse
 *   → vorher wurde 403 als "nicht gefunden" behandelt → Download komplett übersprungen
 *
 * Lösung:
 * - Eigene exists()-Implementation mit korrektem Response-Cleanup
 * - Preemptive Authentication um 401-Round-Trips zu vermeiden
 * - Closeable Pattern für explizite Resource-Freigabe
 * - 403 wird als "existiert" gewertet (Ressource vorhanden, HEAD nicht erlaubt)
 * - listOrNull() als saubere Alternative zu exists()+list()
 *
 * @see <a href="https://square.github.io/okhttp/4.x/okhttp/okhttp3/-response-body/">OkHttp Response Body Docs</a>
 */
class SafeSardineWrapper private constructor(
    private val delegate: OkHttpSardine,
    private val okHttpClient: OkHttpClient,
    private val authHeader: String
) : Sardine by delegate,
    Closeable {
    companion object {
        private const val TAG = "SafeSardine"

        /**
         * Factory-Methode für SafeSardineWrapper
         */
        fun create(okHttpClient: OkHttpClient, username: String, password: String): SafeSardineWrapper {
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
     * 🔧 v2.0.0 (Issue #44): Jianguoyun-Kompatibilität
     * - 2xx → true (existiert)
     * - 403 → true (existiert, HEAD auf Collection nicht erlaubt — Jianguoyun-Verhalten)
     * - 404 → false (existiert nicht)
     * - 405 → list() fallback (bewCloud erlaubt kein HEAD — PROPFIND als Alternative)
     * - 410 → false (wurde gelöscht)
     * - 401 → IOException (Auth-Fehler, soll propagiert werden)
     * - Sonstiges → IOException (unerwarteter Fehler)
     *
     * Im Gegensatz zu OkHttpSardine.exists() wird hier:
     * 1. Preemptive Auth-Header gesendet (kein 401 Round-Trip)
     * 2. Response.use{} für garantiertes Cleanup verwendet
     * 3. 403 korrekt als "existiert" behandelt (statt false)
     */
    override fun exists(url: String): Boolean {
        val request = Request.Builder()
            .url(url)
            .head()
            .header("Authorization", authHeader)
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            val code = response.code
            Logger.d(TAG, "exists($url) → ${response.isSuccessful} ($code)")
            when {
                response.isSuccessful -> true
                code == HTTP_NOT_FOUND -> false
                code == HTTP_FORBIDDEN -> {
                    // Jianguoyun returns 403 for HEAD on collections.
                    // Log as warning so users can diagnose false positives on other servers.
                    Logger.w(TAG, "exists($url) received 403 — assuming resource exists (Jianguoyun workaround)")
                    true
                }
                code == HTTP_GONE -> false
                code == HTTP_UNAUTHORIZED -> throw java.io.IOException(
                    "Authentication failed ($code) for $url"
                )
                // 🔧 v2.2.1 (Issue #50): bewCloud returns 405 for HEAD requests.
                // Fallback: use PROPFIND (list) to check existence.
                code == HTTP_METHOD_NOT_ALLOWED -> {
                    Logger.d(
                        TAG,
                        "exists($url) → false ($code), trying list() fallback"
                    )
                    try {
                        val resources = delegate.list(url)
                        val exists = resources.isNotEmpty()
                        Logger.d(
                            TAG,
                            "list() fallback → exists=$exists" +
                                " (found ${resources.size} items)"
                        )
                        exists
                    } catch (e: Exception) {
                        Logger.d(TAG, "list() fallback failed: ${e.message}")
                        false
                    }
                }
                else -> throw java.io.IOException(
                    "Unexpected HTTP $code for exists($url): ${response.message}"
                )
            }
        }
    }

    /**
     * 🆕 v2.0.0 (Issue #44): Listet Ressourcen oder gibt null zurück wenn 404.
     *
     * Ersetzt das Pattern `if (exists(url)) { list(url) }` durch eine einzelne Operation.
     * Funktioniert auf allen WebDAV-Servern da PROPFIND universell unterstützt wird,
     * auch auf Servern die HEAD auf Collections ablehnen (z.B. Jianguoyun).
     *
     * @return Liste der Ressourcen, oder null wenn URL nicht existiert (404)
     * @throws IOException für andere Fehler (Netzwerk, Auth, 5xx)
     */
    fun listOrNull(url: String): List<DavResource>? {
        return try {
            list(url)
        } catch (e: com.thegrizzlylabs.sardineandroid.impl.SardineException) {
            if (e.statusCode == HTTP_NOT_FOUND) null else throw e
        } catch (e: java.io.IOException) {
            if (e.message?.contains("404") == true) null else throw e
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
            if (!response.isSuccessful && response.code != HTTP_METHOD_NOT_ALLOWED) { // 405 = already exists
                // 🔧 v2.3.0 (Issue #55): MKCOL 404 means the parent collection doesn't exist
                // or the URL is not a valid WebDAV endpoint.
                // Try list() fallback to check if the directory already exists
                // (some servers reject MKCOL but support PROPFIND).
                if (response.code == HTTP_NOT_FOUND) {
                    Logger.d(TAG, "createDirectory($url) → 404, trying list() fallback")
                    val alreadyExists = runCatching { delegate.list(url) }
                        .getOrNull()
                        ?.isNotEmpty() == true
                    if (alreadyExists) {
                        Logger.d(TAG, "list() fallback → directory already exists")
                        return
                    }
                    Logger.d(TAG, "list() fallback also failed or returned empty")
                    throw java.io.IOException(
                        "MKCOL failed: 404 – the server path does not exist. " +
                            "Please verify the WebDAV URL (e.g. /remote.php/dav/files/USERNAME/)"
                    )
                }
                if (response.code == HTTP_UNAUTHORIZED) {
                    throw com.thegrizzlylabs.sardineandroid.impl.SardineException(
                        "Authentication failed during MKCOL for $url",
                        response.code,
                        null
                    )
                }
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
