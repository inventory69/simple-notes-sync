package dev.dettmer.simplenotes.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🆕 v2.14.0: Absicherung für [LogAnonymizer] — was hier durchrutscht, landet in einem
 * öffentlichen GitHub-Issue.
 */
class LogAnonymizerTest {

    private val serverUrl = "https://cloud.example.org/remote.php/dav/files/klausi/notes"
    private val username = "klausi"
    private val titles = listOf("Steuererklärung 2026", "Einkauf", "Urlaub", "Urlaub 2026")

    private fun anonymize(line: String) =
        LogAnonymizer.anonymize(line, serverUrl, username, titles)

    @Test
    fun `replaces host and user in a webdav url`() {
        val result = anonymize(
            "WebDavClient: list(https://cloud.example.org/remote.php/dav/files/klausi/notes/, depth=1)"
        )

        assertFalse("host leaked: $result", result.contains("cloud.example.org"))
        assertFalse("user leaked: $result", result.contains("klausi"))
        assertTrue(result.contains("<server>"))
        assertTrue(result.contains("<user>"))
    }

    @Test
    fun `replaces a bare note title in prose - the dominant log form`() {
        // MarkdownSyncManager loggt Titel überwiegend ohne Dateiendung.
        val result = anonymize("MarkdownSync:    ⏭️ MD skip: Steuererklärung 2026 (content unchanged)")

        assertFalse("title leaked: $result", result.contains("Steuererklärung"))
        assertEquals("MarkdownSync:    ⏭️ MD skip: <note> (content unchanged)", result)
    }

    @Test
    fun `replaces a markdown filename with spaces inside a url`() {
        val result = anonymize("WebDavClient: get(https://cloud.example.org/notes-md/Steuer 2026.md)")

        assertFalse("filename leaked: $result", result.contains("Steuer 2026"))
        assertTrue(result.contains("/<note>.md"))
    }

    @Test
    fun `markdown pattern does not eat surrounding log prose`() {
        // Der Ausdruck ist links durch `/` begrenzt — ohne Pfadtrenner greift er nicht.
        val result = anonymize("MarkdownSync: Skipping unbekannt.md: not ours")

        assertTrue("prose was swallowed: $result", result.startsWith("MarkdownSync: Skipping "))
    }

    @Test
    fun `longer titles win over their own prefixes`() {
        val result = anonymize("Imported new: Urlaub 2026")

        assertEquals("Imported new: <note>", result)
    }

    @Test
    fun `keeps folder names and note uuids`() {
        val result = anonymize(
            "list(https://cloud.example.org/dav/klausi/notes/Arbeit/" +
                "5c851a63-0000-4000-8000-000000000000.json, depth=1)"
        )

        assertTrue("folder name is diagnostic data", result.contains("Arbeit"))
        assertTrue("uuid identifies nobody", result.contains("5c851a63-0000-4000-8000-000000000000"))
    }

    @Test
    fun `handles missing server url, username and titles`() {
        val line = "SyncWorker: sync finished, 3 notes"

        assertEquals(line, LogAnonymizer.anonymize(line, null, null))
        assertEquals(line, LogAnonymizer.anonymize(line, "", "", emptyList()))
    }

    @Test
    fun `does not replace values below the minimum length`() {
        // Sonst würde jedes "jo" im Log zu <user> und die Datei wäre unlesbar.
        val line = "NoteDownloader: jo, 2 notes downloaded"

        assertEquals(line, LogAnonymizer.anonymize(line, null, "jo", listOf("ok")))
    }

    @Test
    fun `is idempotent`() {
        val line = "get(https://cloud.example.org/notes-md/Einkauf.md) for Einkauf"

        val once = anonymize(line)
        val twice = anonymize(once)

        assertEquals(once, twice)
    }

    @Test
    fun `strips host even when the url has a port and the scheme differs`() {
        val result = LogAnonymizer.anonymize(
            "put(http://192.168.0.6:6060/notes/x.json)",
            "http://192.168.0.6:6060/notes",
            null
        )

        assertFalse(result.contains("192.168.0.6"))
    }
}
