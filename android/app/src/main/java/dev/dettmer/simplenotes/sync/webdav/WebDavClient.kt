package dev.dettmer.simplenotes.sync.webdav

import dev.dettmer.simplenotes.utils.Logger
import java.io.Closeable
import java.io.InputStream
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val HTTP_METHOD_NOT_ALLOWED = 405
private const val HTTP_NOT_FOUND = 404
private const val HTTP_FORBIDDEN = 403
private const val HTTP_GONE = 410
private const val HTTP_UNAUTHORIZED = 401

private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()

/**
 * PROPFIND-Body: nur die Properties, die der Sync tatsächlich auswertet.
 * `getcontenttype` dient als zweite Quelle für die Collection-Erkennung.
 */
private val PROPFIND_BODY = """
    <?xml version="1.0" encoding="utf-8"?>
    <d:propfind xmlns:d="DAV:">
      <d:prop>
        <d:getlastmodified/>
        <d:getcontentlength/>
        <d:resourcetype/>
        <d:getetag/>
        <d:getcontenttype/>
      </d:prop>
    </d:propfind>
""".trimIndent()

/**
 * 🆕 v2.14.0: Eigener Mini-WebDAV-Client auf OkHttp — Ersatz für `sardine-android`
 * (seit Jahren unmaintained) und den `SafeSardineWrapper`.
 *
 * Die Statuscode-Matrix aus dem alten Wrapper ist 1:1 erhalten:
 * - [exists]: 2xx → true, 403 → true (Jianguoyun, Issue #44), 404/410 → false,
 *   405 → PROPFIND-Fallback (bewCloud, Issue #50), 401 → [WebDavException]
 * - [createDirectory]: 405 toleriert (existiert bereits), 404 → PROPFIND-Fallback (Issue #55)
 *
 * Authentifizierung (Basic + Digest) hängt am übergebenen [OkHttpClient] —
 * siehe `ConnectionManager.buildHttpClient()`. Der Client setzt keine
 * `Authorization`-Header selbst.
 *
 * Alle Methoden schließen ihren Response-Body (verhindert "connection leaked").
 * Einzige Ausnahme: [get] gibt den Stream heraus — der Aufrufer **muss** ihn
 * über `.use { }` schließen.
 */
class WebDavClient(private val okHttpClient: OkHttpClient) : Closeable {
    companion object {
        private const val TAG = "WebDavClient"
    }

    @Volatile
    private var isClosed = false

    /**
     * PROPFIND. Enthält bei `depth = 1` auch die angefragte Collection selbst
     * als ersten Eintrag (gleiches Verhalten wie zuvor Sardine).
     *
     * @throws WebDavException bei jedem Nicht-2xx-Status (inkl. 404).
     */
    fun list(url: String, depth: Int = 1): List<WebDavResource> = propfind(url, depth.toString())

    /**
     * 🆕 v2.14.0: PROPFIND mit `Depth: infinity` — liefert die komplette Hierarchie in **einem**
     * Request statt 1+N Einzel-Listings.
     *
     * Nicht jeder Server erlaubt das: sabre/dav (Nextcloud/ownCloud) antwortet mit
     * `403 propfind-finite-depth`, wenn `enablePropfindDepthInfinity` aus ist. Aufrufer müssen
     * deshalb auf [list] zurückfallen — siehe [listTreeOrNull].
     */
    fun listDeep(url: String): List<WebDavResource> = propfind(url, "infinity")

    private fun propfind(url: String, depth: String): List<WebDavResource> {
        Logger.d(TAG, "list($url, depth=$depth)")
        val request = Request.Builder()
            .url(url)
            .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML_MEDIA_TYPE))
            .header("Depth", depth)
            .build()

        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw WebDavException(
                    "PROPFIND failed: ${response.code} ${response.message} for $url",
                    response.code
                )
            }
            val body = response.body
                ?: throw WebDavException("PROPFIND returned an empty body for $url", response.code)
            PropfindParser.parse(body.byteStream())
        }
    }

    /**
     * Listet Ressourcen oder gibt `null` zurück wenn die URL nicht existiert (404).
     *
     * Ersetzt das Pattern `if (exists(url)) { list(url) }` durch eine einzelne Operation.
     * Funktioniert auf allen Servern, da PROPFIND universell unterstützt wird — auch auf
     * Servern die HEAD auf Collections ablehnen (Jianguoyun, Issue #44).
     */
    fun listOrNull(url: String): List<WebDavResource>? = try {
        list(url)
    } catch (e: WebDavException) {
        if (e.statusCode == HTTP_NOT_FOUND) null else throw e
    }

    /**
     * HEAD-basierter Existenzcheck mit der historisch gewachsenen Statuscode-Matrix.
     * Siehe Klassen-KDoc — die Sonderfälle sind Server-Workarounds, nicht ändern.
     */
    fun exists(url: String): Boolean {
        val request = Request.Builder().url(url).head().build()

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
                code == HTTP_UNAUTHORIZED -> throw WebDavException(
                    "Authentication failed ($code) for $url",
                    code
                )
                // 🔧 v2.2.1 (Issue #50): bewCloud returns 405 for HEAD requests.
                // Fallback: use PROPFIND (list) to check existence.
                code == HTTP_METHOD_NOT_ALLOWED -> existsViaPropfind(url, code)
                else -> throw WebDavException(
                    "Unexpected HTTP $code for exists($url): ${response.message}",
                    code
                )
            }
        }
    }

    private fun existsViaPropfind(url: String, code: Int): Boolean {
        Logger.d(TAG, "exists($url) → false ($code), trying list() fallback")
        return try {
            val resources = list(url)
            val exists = resources.isNotEmpty()
            Logger.d(TAG, "list() fallback → exists=$exists (found ${resources.size} items)")
            exists
        } catch (e: java.io.IOException) {
            Logger.d(TAG, "list() fallback failed: ${e.message}")
            false
        }
    }

    /**
     * GET. **Der zurückgegebene Stream muss vom Aufrufer geschlossen werden**
     * (empfohlen: `get(url).use { it.bufferedReader().readText() }`).
     */
    fun get(url: String): InputStream {
        Logger.d(TAG, "get($url)")
        val request = Request.Builder().url(url).get().build()
        val response = okHttpClient.newCall(request).execute()
        val body = response.body
        if (!response.isSuccessful || body == null) {
            response.close()
            throw WebDavException("GET failed: ${response.code} ${response.message} for $url", response.code)
        }
        return body.byteStream()
    }

    /**
     * PUT.
     *
     * @return den `ETag`-Header der Antwort, oder `null` wenn der Server keinen schickt.
     * Spart dem Aufrufer einen PROPFIND — Format-Unterschiede zum PROPFIND-`getetag`
     * (W/-Präfix, Quotes) gleicht [etagsMatch] aus.
     *
     * 🆕 v2.14.0: Nextcloud schickt bei `201` (neue Datei) **keinen** `ETag`, wohl aber
     * `OC-ETag` mit exakt dem Wert, den ein PROPFIND später als `getetag` liefert. Ohne
     * diesen Fallback kostete jede neu angelegte Datei einen zusätzlichen PROPFIND.
     */
    fun put(url: String, data: ByteArray, contentType: String?): String? {
        val body = data.toRequestBody(contentType?.toMediaTypeOrNull())
        val request = Request.Builder().url(url).put(body).build()

        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw WebDavException("PUT failed: ${response.code} ${response.message}", response.code)
            }
            Logger.d(TAG, "put($url) → ${response.code}")
            response.header("ETag") ?: response.header("OC-ETag")
        }
    }

    fun delete(url: String) {
        val request = Request.Builder().url(url).delete().build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw WebDavException("DELETE failed: ${response.code} ${response.message}", response.code)
            }
            Logger.d(TAG, "delete($url) → ${response.code}")
        }
    }

    /**
     * MKCOL. 405 wird toleriert — der Ordner existiert dann bereits.
     */
    fun createDirectory(url: String) {
        val request = Request.Builder().url(url).method("MKCOL", null).build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != HTTP_METHOD_NOT_ALLOWED) {
                handleMkcolFailure(url, response.code, response.message)
            }
            Logger.d(TAG, "createDirectory($url) → ${response.code}")
        }
    }

    private fun handleMkcolFailure(url: String, code: Int, message: String) {
        // 🔧 v2.3.0 (Issue #55): MKCOL 404 means the parent collection doesn't exist
        // or the URL is not a valid WebDAV endpoint.
        // Try list() fallback to check if the directory already exists
        // (some servers reject MKCOL but support PROPFIND).
        if (code == HTTP_NOT_FOUND) {
            Logger.d(TAG, "createDirectory($url) → 404, trying list() fallback")
            val alreadyExists = runCatching { list(url) }.getOrNull()?.isNotEmpty() == true
            if (alreadyExists) {
                Logger.d(TAG, "list() fallback → directory already exists")
                return
            }
            Logger.d(TAG, "list() fallback also failed or returned empty")
        }
        throw WebDavException(mkcolErrorMessage(url, code, message), code)
    }

    private fun mkcolErrorMessage(url: String, code: Int, message: String): String = when (code) {
        HTTP_NOT_FOUND ->
            "MKCOL failed: 404 – the server path does not exist. " +
                "Please verify the WebDAV URL (e.g. /remote.php/dav/files/USERNAME/)"
        HTTP_UNAUTHORIZED -> "Authentication failed during MKCOL for $url"
        else -> "MKCOL failed: $code $message"
    }

    /**
     * Schließt alle offenen Verbindungen. Nach `close()` darf der Client
     * nicht mehr verwendet werden.
     */
    override fun close() {
        if (isClosed) {
            Logger.d(TAG, "Already closed, skipping")
            return
        }

        try {
            okHttpClient.connectionPool.evictAll()
            okHttpClient.dispatcher.cancelAll()
            isClosed = true
            Logger.d(TAG, "✅ Closed successfully (connections evicted)")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to close", e)
        }
    }
}
