package dev.dettmer.simplenotes.sync.webdav

import dev.dettmer.simplenotes.sync.ConnectionManager
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val MULTISTATUS = """<?xml version="1.0"?>
<d:multistatus xmlns:d="DAV:">
  <d:response>
    <d:href>/notes/a.json</d:href>
    <d:propstat>
      <d:prop><d:getcontentlength>7</d:getcontentlength></d:prop>
      <d:status>HTTP/1.1 200 OK</d:status>
    </d:propstat>
  </d:response>
</d:multistatus>"""

private const val EMPTY_MULTISTATUS = """<?xml version="1.0"?><d:multistatus xmlns:d="DAV:"/>"""

/**
 * Integrationstests für [WebDavClient] gegen einen echten HTTP-Server (MockWebServer).
 *
 * Schwerpunkt: die Statuscode-Matrix aus dem alten `SafeSardineWrapper` (Server-Workarounds
 * für Jianguoyun/bewCloud) und der Auth-Roundtrip über `okhttp-digest`.
 */
class WebDavClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: WebDavClient

    @Before fun setUp() {
        // Der Auth-Cache ist prozessweit — ohne Reset leaken 401-Zählungen zwischen Tests.
        ConnectionManager.clearSharedAuthCacheForTest()
        server = MockWebServer()
        server.start()
        client = WebDavClient(ConnectionManager.buildHttpClient(5_000L, "user", "pw"))
    }

    @After fun tearDown() {
        client.close()
        server.shutdown()
    }

    private fun url(path: String = "/notes/") = server.url(path).toString()

    private fun multistatus(body: String = MULTISTATUS) =
        MockResponse().setResponseCode(207).setBody(body)

    // ═══════════════════════════════════════════════
    // exists() — Statuscode-Matrix
    // ═══════════════════════════════════════════════

    @Test fun `exists returns true on 2xx`() {
        server.enqueue(MockResponse().setResponseCode(200))
        assertTrue(client.exists(url()))
        assertEquals("HEAD", server.takeRequest().method)
    }

    @Test fun `exists returns false on 404`() {
        server.enqueue(MockResponse().setResponseCode(404))
        assertFalse(client.exists(url()))
    }

    @Test fun `exists returns false on 410 gone`() {
        server.enqueue(MockResponse().setResponseCode(410))
        assertFalse(client.exists(url()))
    }

    /** Issue #44 — Jianguoyun antwortet auf HEAD einer Collection mit 403. */
    @Test fun `exists returns true on 403 (Jianguoyun workaround)`() {
        server.enqueue(MockResponse().setResponseCode(403))
        assertTrue(client.exists(url()))
    }

    @Test fun `exists throws WebDavException with status 401`() {
        server.enqueue(MockResponse().setResponseCode(401))
        val e = runCatching { client.exists(url()) }.exceptionOrNull()
        assertTrue(e is WebDavException)
        assertEquals(401, (e as WebDavException).statusCode)
    }

    @Test fun `exists throws WebDavException on unexpected status`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val e = runCatching { client.exists(url()) }.exceptionOrNull()
        assertEquals(500, (e as WebDavException).statusCode)
    }

    /** Issue #50 — bewCloud lehnt HEAD mit 405 ab, PROPFIND funktioniert. */
    @Test fun `exists falls back to PROPFIND on 405`() {
        server.enqueue(MockResponse().setResponseCode(405))
        server.enqueue(multistatus())

        assertTrue(client.exists(url()))

        assertEquals("HEAD", server.takeRequest().method)
        assertEquals("PROPFIND", server.takeRequest().method)
    }

    @Test fun `exists is false when the 405 PROPFIND fallback finds nothing`() {
        server.enqueue(MockResponse().setResponseCode(405))
        server.enqueue(multistatus(EMPTY_MULTISTATUS))

        assertFalse(client.exists(url()))
    }

    @Test fun `exists is false when the 405 PROPFIND fallback also fails`() {
        server.enqueue(MockResponse().setResponseCode(405))
        server.enqueue(MockResponse().setResponseCode(404))

        assertFalse(client.exists(url()))
    }

    // ═══════════════════════════════════════════════
    // list() / listOrNull()
    // ═══════════════════════════════════════════════

    @Test fun `list sends PROPFIND with a Depth header and a prop body`() {
        server.enqueue(multistatus())

        val resources = client.list(url(), depth = 1)

        assertEquals(1, resources.size)
        assertEquals(7L, resources[0].contentLength)
        val request = server.takeRequest()
        assertEquals("PROPFIND", request.method)
        assertEquals("1", request.getHeader("Depth"))
        assertTrue(request.body.readUtf8().contains("getlastmodified"))
    }

    @Test fun `list forwards depth 0`() {
        server.enqueue(multistatus())
        client.list(url(), depth = 0)
        assertEquals("0", server.takeRequest().getHeader("Depth"))
    }

    @Test fun `list throws WebDavException with the server status`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val e = runCatching { client.list(url()) }.exceptionOrNull()
        assertEquals(404, (e as WebDavException).statusCode)
    }

    @Test fun `listOrNull returns null on 404`() {
        server.enqueue(MockResponse().setResponseCode(404))
        assertNull(client.listOrNull(url()))
    }

    @Test fun `listOrNull rethrows non-404 errors`() {
        server.enqueue(MockResponse().setResponseCode(500))
        val e = runCatching { client.listOrNull(url()) }.exceptionOrNull()
        assertEquals(500, (e as WebDavException).statusCode)
    }

    // ═══════════════════════════════════════════════
    // get / put / delete
    // ═══════════════════════════════════════════════

    @Test fun `get returns the response body stream`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"id\":\"x\"}"))
        val content = client.get(url("/notes/x.json")).use { it.bufferedReader().readText() }
        assertEquals("{\"id\":\"x\"}", content)
    }

    @Test fun `get throws WebDavException on 404`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val e = runCatching { client.get(url("/notes/x.json")) }.exceptionOrNull()
        assertEquals(404, (e as WebDavException).statusCode)
    }

    @Test fun `put sends the body and content type`() {
        server.enqueue(MockResponse().setResponseCode(201))

        client.put(url("/notes/x.json"), "payload".toByteArray(), "application/json")

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("payload", request.body.readUtf8())
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("application/json"))
    }

    @Test fun `put returns the ETag header`() {
        server.enqueue(MockResponse().setResponseCode(201).setHeader("ETag", "\"abc123\""))
        assertEquals("\"abc123\"", client.put(url("/notes/x.json"), ByteArray(0), null))
    }

    @Test fun `put returns null when the server sends no ETag`() {
        server.enqueue(MockResponse().setResponseCode(201))
        assertNull(client.put(url("/notes/x.json"), ByteArray(0), null))
    }

    @Test fun `put throws WebDavException with the server status`() {
        server.enqueue(MockResponse().setResponseCode(507))
        val e = runCatching { client.put(url("/notes/x.json"), ByteArray(0), null) }.exceptionOrNull()
        assertEquals(507, (e as WebDavException).statusCode)
    }

    @Test fun `delete succeeds on 204`() {
        server.enqueue(MockResponse().setResponseCode(204))
        client.delete(url("/notes/x.json"))
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test fun `delete throws WebDavException on 404 and isWebDavNotFound detects it`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val e = runCatching { client.delete(url("/notes/x.json")) }.exceptionOrNull()
        assertEquals(404, (e as WebDavException).statusCode)
        assertTrue(e.isWebDavNotFound())
    }

    // ═══════════════════════════════════════════════
    // createDirectory (MKCOL)
    // ═══════════════════════════════════════════════

    @Test fun `createDirectory sends MKCOL`() {
        server.enqueue(MockResponse().setResponseCode(201))
        client.createDirectory(url())
        assertEquals("MKCOL", server.takeRequest().method)
    }

    /** 405 = Collection existiert bereits — kein Fehler. */
    @Test fun `createDirectory tolerates 405`() {
        server.enqueue(MockResponse().setResponseCode(405))
        client.createDirectory(url())
    }

    /** Issue #55 — Server lehnt MKCOL mit 404 ab, der Ordner existiert aber. */
    @Test fun `createDirectory accepts a 404 when the PROPFIND fallback finds the directory`() {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(multistatus())

        client.createDirectory(url())

        assertEquals("MKCOL", server.takeRequest().method)
        assertEquals("PROPFIND", server.takeRequest().method)
    }

    @Test fun `createDirectory throws a path hint when MKCOL and fallback both 404`() {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(404))

        val e = runCatching { client.createDirectory(url()) }.exceptionOrNull()

        assertEquals(404, (e as WebDavException).statusCode)
        assertTrue(e.message.orEmpty().contains("MKCOL failed"))
    }

    @Test fun `createDirectory maps 401 to WebDavException`() {
        server.enqueue(MockResponse().setResponseCode(401))
        val e = runCatching { client.createDirectory(url()) }.exceptionOrNull()
        assertEquals(401, (e as WebDavException).statusCode)
    }

    // ═══════════════════════════════════════════════
    // Authentifizierung
    // ═══════════════════════════════════════════════

    @Test fun `basic challenge is answered with an Authorization header`() {
        server.enqueue(
            MockResponse().setResponseCode(401).setHeader("WWW-Authenticate", "Basic realm=\"notes\"")
        )
        server.enqueue(MockResponse().setResponseCode(200))

        assertTrue(client.exists(url()))

        assertNull("first request is unauthenticated", server.takeRequest().getHeader("Authorization"))
        val retry = server.takeRequest().getHeader("Authorization")
        assertTrue("retry must carry Basic auth", retry.orEmpty().startsWith("Basic "))
    }

    /** Nextcloud schickt `Basic realm="Nextcloud", charset="UTF-8"` — der Parameter darf nicht stören. */
    @Test fun `basic challenge with a charset parameter is answered`() {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setHeader("WWW-Authenticate", "Basic realm=\"Nextcloud\", charset=\"UTF-8\"")
        )
        server.enqueue(MockResponse().setResponseCode(200))

        assertTrue(client.exists(url()))

        assertNull("first request is unauthenticated", server.takeRequest().getHeader("Authorization"))
        assertTrue(server.takeRequest().getHeader("Authorization").orEmpty().startsWith("Basic "))
    }

    @Test fun `digest challenge is answered with a digest response`() {
        server.enqueue(
            MockResponse().setResponseCode(401).setHeader(
                "WWW-Authenticate",
                "Digest realm=\"notes\", nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", qop=\"auth\", algorithm=MD5"
            )
        )
        server.enqueue(MockResponse().setResponseCode(200))

        assertTrue(client.exists(url()))

        server.takeRequest() // unauthenticated first attempt
        val retry = server.takeRequest().getHeader("Authorization").orEmpty()
        assertTrue("retry must carry Digest auth: $retry", retry.startsWith("Digest "))
        assertTrue(retry.contains("username=\"user\""))
        assertTrue(retry.contains("response="))
    }

    /** Nach der ersten Challenge authentifiziert der Cache preemptiv — kein zweiter 401-Roundtrip. */
    @Test fun `follow-up requests authenticate preemptively`() {
        server.enqueue(
            MockResponse().setResponseCode(401).setHeader("WWW-Authenticate", "Basic realm=\"notes\"")
        )
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))

        client.exists(url())
        client.exists(url())

        server.takeRequest() // 401
        server.takeRequest() // authenticated retry
        val second = server.takeRequest()
        assertTrue(
            "second exists() must send auth without a 401 first",
            second.getHeader("Authorization").orEmpty().startsWith("Basic ")
        )
        assertEquals(3, server.requestCount)
    }

    /** Der Auth-Cache ist prozessweit — ein neuer Client nach `close()` zahlt keinen 401 mehr. */
    @Test fun `a new client reuses the shared auth cache`() {
        server.enqueue(
            MockResponse().setResponseCode(401).setHeader("WWW-Authenticate", "Basic realm=\"notes\"")
        )
        server.enqueue(MockResponse().setResponseCode(200))
        server.enqueue(MockResponse().setResponseCode(200))

        client.exists(url())
        client.close()

        val second = WebDavClient(ConnectionManager.buildHttpClient(5_000L, "user", "pw"))
        second.exists(url())
        second.close()

        assertEquals("second session must not pay another 401", 3, server.requestCount)
    }

    @Test fun `changing credentials invalidates the shared auth cache`() {
        repeat(2) {
            server.enqueue(
                MockResponse().setResponseCode(401).setHeader("WWW-Authenticate", "Basic realm=\"notes\"")
            )
            server.enqueue(MockResponse().setResponseCode(200))
        }

        client.exists(url())
        client.close()

        val other = WebDavClient(ConnectionManager.buildHttpClient(5_000L, "user", "other-pw"))
        other.exists(url())
        other.close()

        assertEquals("new credentials must trigger exactly one more 401", 4, server.requestCount)
    }

    // ═══════════════════════════════════════════════
    // Timeout
    // ═══════════════════════════════════════════════

    @Test fun `read timeout surfaces as SocketTimeoutException`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val shortTimeout = WebDavClient(
            OkHttpClient.Builder()
                .connectTimeout(300, TimeUnit.MILLISECONDS)
                .readTimeout(300, TimeUnit.MILLISECONDS)
                .build()
        )

        val e = runCatching { shortTimeout.exists(url()) }.exceptionOrNull()

        assertTrue("expected a timeout, got $e", e is SocketTimeoutException)
        shortTimeout.close()
    }
}
