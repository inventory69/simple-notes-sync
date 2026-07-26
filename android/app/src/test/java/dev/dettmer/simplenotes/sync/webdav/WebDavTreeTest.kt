package dev.dettmer.simplenotes.sync.webdav

import dev.dettmer.simplenotes.sync.ConnectionManager
import io.mockk.every
import io.mockk.mockk
import java.net.URI
import java.util.Date
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🆕 v2.14.0 (Variante A): Ein `Depth: infinity`-PROPFIND ersetzt das 1+N-Listing.
 * Geprüft werden die Gruppierung und — sicherheitsrelevant — der Fallback auf das alte
 * Verhalten, wenn der Server die Tiefe ablehnt.
 */
class WebDavTreeTest {
    private val baseUrl = "https://host/dav/notes/"

    private fun res(path: String, isDirectory: Boolean = false) = WebDavResource(
        href = URI(path),
        modified = Date(0),
        contentLength = if (isDirectory) -1 else 1,
        isDirectory = isDirectory,
        etag = "\"e\""
    )

    private val listing = listOf(
        res("/dav/notes/", isDirectory = true), // Self-Eintrag
        res("/dav/notes/a.json"),
        res("/dav/notes/Work/", isDirectory = true),
        res("/dav/notes/Work/b.json"),
        res("/dav/notes/Empty/", isDirectory = true),
        res("/dav/notes/Work/Deeper/", isDirectory = true),
        res("/dav/notes/Work/Deeper/c.json")
    )

    @Test fun `the base group holds direct children without the self entry`() {
        val grouped = listing.groupByFolder(baseUrl)

        assertEquals(listOf("a.json", "Work", "Empty"), grouped.getValue(null).map { it.name })
    }

    @Test fun `a subfolder's files land under its name`() {
        val grouped = listing.groupByFolder(baseUrl)

        assertEquals(listOf("b.json"), grouped.getValue("Work").map { it.name })
    }

    /** Kritisch: ohne Key würde der Aufrufer den Ordner erneut einzeln listen. */
    @Test fun `an empty subfolder is a key with an empty list`() {
        val grouped = listing.groupByFolder(baseUrl)

        assertTrue(grouped.containsKey("Empty"))
        assertEquals(emptyList<WebDavResource>(), grouped.getValue("Empty"))
    }

    @Test fun `entries below the first folder level are dropped`() {
        val grouped = listing.groupByFolder(baseUrl)

        assertFalse(grouped.containsKey("Deeper"))
        assertFalse(grouped.getValue("Work").any { it.name == "c.json" })
    }

    @Test fun `a refused depth reports back and yields null`() {
        var refused = false
        val webdav = mockk<WebDavClient> {
            every { listDeep(any()) } throws WebDavException("propfind-finite-depth", 403)
        }

        assertNull(webdav.listTreeOrNull(baseUrl) { refused = true })
        assertTrue(refused)
    }

    /** 404 heißt „Collection fehlt", nicht „Server kann kein infinity" — Flag darf nicht kippen. */
    @Test fun `a missing collection does not mark the server as refusing`() {
        var refused = false
        val webdav = mockk<WebDavClient> {
            every { listDeep(any()) } throws WebDavException("not found", 404)
        }

        assertNull(webdav.listTreeOrNull(baseUrl) { refused = true })
        assertFalse(refused)
    }

    /**
     * Ohne diesen Guard hielte der Sync den Server für leer und würde alle Notizen als dort
     * gelöscht behandeln — eine existierende Collection liefert immer ihren Self-Eintrag.
     */
    @Test fun `an empty deep listing falls back instead of reporting an empty server`() {
        var refused = false
        val webdav = mockk<WebDavClient> {
            every { listDeep(any()) } returns emptyList()
        }

        assertNull(webdav.listTreeOrNull(baseUrl) { refused = true })
        assertFalse(refused)
    }

    @Test fun `a successful deep listing is grouped`() {
        val webdav = mockk<WebDavClient> {
            every { listDeep(baseUrl) } returns listing
        }

        val grouped = webdav.listTreeOrNull(baseUrl) { }

        assertNotNull(grouped)
        assertEquals(listOf("b.json"), grouped!!.getValue("Work").map { it.name })
    }

    // ═══════════════════════════════════════════════
    // Geklemmtes Depth: infinity (sabre/dav)
    // ═══════════════════════════════════════════════

    /**
     * sabre/dav klemmt `Depth: infinity` bei ausgeschaltetem `enablePropfindDepthInfinity` still
     * auf Depth 1 und antwortet 207 — kein Statuscode, an dem der Fallback hängen könnte. Würde
     * das Ergebnis übernommen, fehlten alle Notizen der Unterordner in `serverNoteIds` und
     * `detectDeletions` löschte sie lokal (bei bereits getrashten Notizen hart).
     */
    @Test fun `a clamped depth-1 answer is not taken at face value`() {
        var refused = false
        val clamped = listOf(
            res("/dav/notes/", isDirectory = true), // Self-Eintrag
            res("/dav/notes/a.json"),
            res("/dav/notes/Work/", isDirectory = true),
            res("/dav/notes/Empty/", isDirectory = true)
        )
        val webdav = mockk<WebDavClient> {
            every { listDeep(baseUrl) } returns clamped
        }

        assertNull(webdav.listTreeOrNull(baseUrl) { refused = true })
        // Geklemmt und „wirklich leer" sind nicht unterscheidbar — ein Flag würde die Optimierung
        // für einen Baum aus lauter leeren Ordnern dauerhaft abschalten.
        assertFalse("clamping must not disable deep PROPFIND permanently", refused)
    }

    /** Ohne Unterordner sind Depth 1 und Depth infinity identisch — die Erkennung darf nicht anspringen. */
    @Test fun `a listing without any subfolder is still accepted`() {
        val webdav = mockk<WebDavClient> {
            every { listDeep(baseUrl) } returns listOf(
                res("/dav/notes/", isDirectory = true),
                res("/dav/notes/a.json")
            )
        }

        val grouped = webdav.listTreeOrNull(baseUrl) { }

        assertNotNull(grouped)
        assertEquals(listOf("a.json"), grouped!!.getValue(null).map { it.name })
    }

    /** Ein einziger befüllter Unterordner beweist, dass der Server wirklich rekursiv geantwortet hat. */
    @Test fun `one non-empty subfolder is enough to trust the answer`() {
        val webdav = mockk<WebDavClient> {
            every { listDeep(baseUrl) } returns listOf(
                res("/dav/notes/", isDirectory = true),
                res("/dav/notes/Work/", isDirectory = true),
                res("/dav/notes/Work/b.json"),
                res("/dav/notes/Empty/", isDirectory = true)
            )
        }

        val grouped = webdav.listTreeOrNull(baseUrl) { }

        assertNotNull(grouped)
        assertEquals(listOf("b.json"), grouped!!.getValue("Work").map { it.name })
        assertEquals(emptyList<WebDavResource>(), grouped.getValue("Empty"))
    }

    /**
     * Ende-zu-Ende gegen einen echten 207 in Depth-1-Form: beweist die Kette
     * [PropfindParser] → [groupByFolder] → Erkennung, nicht nur die Gruppierungs-Logik.
     */
    @Test fun `a clamped 207 from a real server falls back`() {
        // Der Auth-Cache ist prozessweit — ohne Reset leaken 401-Zählungen zwischen Tests.
        ConnectionManager.clearSharedAuthCacheForTest()
        val server = MockWebServer()
        server.start()
        val client = WebDavClient(ConnectionManager.buildHttpClient(5_000L, "user", "pw"))
        try {
            server.enqueue(MockResponse().setResponseCode(207).setBody(CLAMPED_MULTISTATUS))

            val grouped = client.listTreeOrNull(server.url("/notes/").toString()) { }

            assertNull(grouped)
            assertEquals("infinity", server.takeRequest().getHeader("Depth"))
        } finally {
            client.close()
            server.shutdown()
        }
    }
}

/** Antwort eines sabre/dav mit `enablePropfindDepthInfinity = false`: Unterordner ohne Inhalt. */
private const val CLAMPED_MULTISTATUS = """<?xml version="1.0"?>
<d:multistatus xmlns:d="DAV:">
  <d:response>
    <d:href>/notes/</d:href>
    <d:propstat>
      <d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>
      <d:status>HTTP/1.1 200 OK</d:status>
    </d:propstat>
  </d:response>
  <d:response>
    <d:href>/notes/a.json</d:href>
    <d:propstat>
      <d:prop><d:getcontentlength>7</d:getcontentlength><d:getetag>"e1"</d:getetag></d:prop>
      <d:status>HTTP/1.1 200 OK</d:status>
    </d:propstat>
  </d:response>
  <d:response>
    <d:href>/notes/Work/</d:href>
    <d:propstat>
      <d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>
      <d:status>HTTP/1.1 200 OK</d:status>
    </d:propstat>
  </d:response>
  <d:response>
    <d:href>/notes/Private/</d:href>
    <d:propstat>
      <d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>
      <d:status>HTTP/1.1 200 OK</d:status>
    </d:propstat>
  </d:response>
</d:multistatus>"""
