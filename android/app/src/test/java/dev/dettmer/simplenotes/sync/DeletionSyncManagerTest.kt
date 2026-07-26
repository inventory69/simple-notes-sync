package dev.dettmer.simplenotes.sync

import dev.dettmer.simplenotes.models.DeletionTracker
import dev.dettmer.simplenotes.sync.webdav.WebDavClient
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Request-Budget des Lösch-Ledgers: downloadRemote macht genau einen GET (kein HEAD davor),
 * appendAllAndUpload genau einen GET + einen PUT für beliebig viele IDs.
 */
class DeletionSyncManagerTest {
    private lateinit var server: MockWebServer
    private lateinit var webdav: WebDavClient
    private lateinit var manager: DeletionSyncManager

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        webdav = WebDavClient(OkHttpClient())
        val urlBuilder = mockk<SyncUrlBuilder>()
        every { urlBuilder.getNotesUrl(any()) } returns "https://s/notes/"
        manager = DeletionSyncManager(urlBuilder)
    }

    @After fun tearDown() {
        webdav.close()
        server.shutdown()
    }

    private fun url() = server.url("/notes/deletions.json").toString()

    /** deletedAt = jetzt, sonst prunet [DeletionTracker.pruneOlderThan] die Einträge weg. */
    private fun ledgerJson(vararg ids: String): String {
        val now = System.currentTimeMillis()
        val notes = ids.joinToString(",") { """{"id":"$it","deletedAt":$now,"deviceId":"dev"}""" }
        return """{"version":1,"deletedNotes":[$notes]}"""
    }

    @Test fun `downloadRemote issues a single GET without an exists probe`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(ledgerJson("n1")))

        val tracker = manager.downloadRemote(webdav, url())

        assertTrue(tracker.isDeleted("n1"))
        assertEquals(1, server.requestCount)
        assertEquals("GET", server.takeRequest().method)
    }

    @Test fun `downloadRemote returns an empty tracker on 404`() {
        server.enqueue(MockResponse().setResponseCode(404))

        assertTrue(manager.downloadRemote(webdav, url()).deletedNotes.isEmpty())
        assertEquals(1, server.requestCount)
    }

    @Test fun `appendAllAndUpload writes every id with one GET and one PUT`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(ledgerJson("existing")))
        server.enqueue(MockResponse().setResponseCode(204))

        val tracker = manager.appendAllAndUpload(webdav, url(), listOf("a", "b", "c"), "dev-1")

        assertEquals(2, server.requestCount)
        assertEquals("GET", server.takeRequest().method)
        val put = server.takeRequest()
        assertEquals("PUT", put.method)

        val uploadedIds = JSONObject(put.body.readUtf8()).getJSONArray("deletedNotes")
            .let { array -> (0 until array.length()).map { array.getJSONObject(it).getString("id") } }
        assertEquals(setOf("existing", "a", "b", "c"), uploadedIds.toSet())
        assertTrue(listOf("a", "b", "c").all { tracker.isDeleted(it) })
    }

    /** PUT-Fehler sind non-fatal — der gemergte Tracker wird trotzdem zurückgegeben. */
    @Test fun `appendAllAndUpload returns the merged tracker when the PUT fails`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(ledgerJson()))
        server.enqueue(MockResponse().setResponseCode(507))

        val tracker = manager.appendAllAndUpload(webdav, url(), listOf("a"), "dev-1")

        assertTrue(tracker.isDeleted("a"))
    }
}
