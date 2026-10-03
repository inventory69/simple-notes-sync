package dev.dettmer.simplenotes.sync

import dev.dettmer.simplenotes.sync.E2eeGate.Probe
import dev.dettmer.simplenotes.sync.webdav.WebDavClient
import dev.dettmer.simplenotes.sync.webdav.WebDavException
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.FakeSharedPreferences
import io.mockk.every
import io.mockk.mockk
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Testvektoren aus `project-docs/simple-notes-sync/e2ee/slice-1.md`, Zeile für Zeile.
 * Desktop setzt dieselbe Tabelle in `classify_e2ee_probe` um.
 */
class E2eeGateTest {
    private val k = "\"simple-notes-e2ee\""

    private fun probe(code: Int, body: String) = E2eeGate.classify(code, E2eeGate.readPrefix(body.byteInputStream()))

    @Test fun `contract vectors`() {
        val vectors = listOf(
            Triple(1, 200, """{"format":"simple-notes-e2ee","version":1}""") to Probe.ACTIVE,
            Triple(2, 200, """{"format":"simple-notes-e2ee",""") to Probe.ACTIVE,
            Triple(3, 207, """{"format":"simple-notes-e2ee"}""") to Probe.ACTIVE,
            Triple(4, 200, " ".repeat(65_517) + k) to Probe.ACTIVE,
            Triple(5, 200, " ".repeat(65_518) + k) to Probe.INACTIVE,
            Triple(6, 200, "") to Probe.INACTIVE,
            Triple(7, 200, "<html><body>Login</body></html>") to Probe.INACTIVE,
            Triple(8, 200, "<html>Not found: /simple-notes-e2ee/e2ee.json</html>") to Probe.INACTIVE,
            Triple(9, 200, """{"format":"simple-notes"}""") to Probe.INACTIVE
        ) + listOf(404, 410).map { Triple(10, it, "") to Probe.INACTIVE } +
            listOf(400, 403, 405, 409, 418).map { Triple(11, it, k) to Probe.INACTIVE } +
            listOf(401, 407, 408, 425, 429).map { Triple(12, it, k) to Probe.ERROR } +
            listOf(500, 502, 503).map { Triple(13, it, "") to Probe.ERROR } +
            listOf(301, 302, 307).map { Triple(14, it, "") to Probe.ERROR }

        vectors.forEach { (v, expected) ->
            val (row, code, body) = v
            assertEquals("vector $row ($code)", expected, probe(code, body))
        }
    }

    @Test fun `vector 15 - no response rethrows`() {
        val webdav = mockk<WebDavClient>()
        every { webdav.get(any()) } throws SocketTimeoutException("timeout")
        assertThrows(SocketTimeoutException::class.java) { E2eeGate.isActive(webdav, "http://s/notes-e2ee/e2ee.json") }
    }

    @Test fun `isActive maps the status of a failed GET`() {
        fun failingWith(code: Int) = mockk<WebDavClient>().also {
            every { it.get(any()) } throws WebDavException("GET failed: $code", code)
        }
        assertFalse(E2eeGate.isActive(failingWith(404), "u"))
        assertFalse(E2eeGate.isActive(failingWith(403), "u"))
        // FEHLER: die Original-Exception, damit 401 als Auth-Fehler gemeldet wird
        val e = assertThrows(WebDavException::class.java) { E2eeGate.isActive(failingWith(401), "u") }
        assertEquals(401, e.statusCode)
    }

    @Test fun `isActive reads the body of a successful GET`() {
        val webdav = mockk<WebDavClient>()
        every { webdav.get(any()) } answers { """{"format":"simple-notes-e2ee"}""".byteInputStream() }
        assertTrue(E2eeGate.isActive(webdav, "u"))
    }

    @Test fun `isBlocked only for the marker url of the current config`() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString(Constants.KEY_SERVER_URL, "http://s/").putString(Constants.KEY_SYNC_FOLDER_NAME, "notes").apply()
        val urls = SyncUrlBuilder(prefs)
        assertFalse(E2eeGate.isBlocked(prefs, urls))

        E2eeGate.record(prefs, "http://s/notes-e2ee/e2ee.json", active = true)
        assertTrue(E2eeGate.isBlocked(prefs, urls))

        // Ordnerwechsel hebt die Anzeige auf, ohne Reset-Hook
        prefs.edit().putString(Constants.KEY_SYNC_FOLDER_NAME, "other").apply()
        assertFalse(E2eeGate.isBlocked(prefs, urls))
    }
}
