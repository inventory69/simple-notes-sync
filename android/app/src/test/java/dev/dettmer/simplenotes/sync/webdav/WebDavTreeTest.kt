package dev.dettmer.simplenotes.sync.webdav

import io.mockk.every
import io.mockk.mockk
import java.net.URI
import java.util.Date
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
}
