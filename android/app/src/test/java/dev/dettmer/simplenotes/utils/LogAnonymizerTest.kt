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
    private val folders = listOf("Arbeit", "Privat")

    private fun anonymize(line: String) =
        LogAnonymizer.anonymize(line, serverUrl, username, titles, folders)

    @Test
    fun `replaces host and user in a webdav url`() {
        val result = anonymize(
            "WebDavClient: list(https://cloud.example.org/remote.php/dav/files/klausi/notes/, depth=1)"
        )

        assertFalse("host leaked: $result", result.contains("cloud.example.org"))
        assertFalse("user leaked: $result", result.contains("klausi"))
        assertTrue(result.contains("<server>"))
        // Kein `<user>`: bei Nextcloud steht der Benutzername **im** Sync-Pfad, den `<path>`
        // komplett ersetzt. `<user>` greift weiter dort, wo der Name außerhalb der URL steht.
        assertTrue(result.contains("<path>"))
    }

    @Test
    fun `replaces the username outside of the url too`() {
        val result = anonymize("ConnectionManager: auth cache miss for klausi")

        assertEquals("ConnectionManager: auth cache miss for <user>", result)
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
    fun `replaces folder names but keeps note uuids`() {
        val result = anonymize(
            "list(https://cloud.example.org/dav/klausi/notes/Arbeit/" +
                "5c851a63-0000-4000-8000-000000000000.json, depth=1)"
        )

        assertFalse("folder leaked: $result", result.contains("Arbeit"))
        assertTrue(result.contains("/<folder>/"))
        assertTrue("uuid identifies nobody", result.contains("5c851a63-0000-4000-8000-000000000000"))
    }

    @Test
    fun `replaces a folder name logged bare, without a path`() {
        // NoteUploader: "📁 Ensured folder dir: Privat"
        assertEquals("NoteUploader: 📁 Ensured folder dir: <folder>", anonymize("NoteUploader: 📁 Ensured folder dir: Privat"))
    }

    @Test
    fun `redacts a markdown filename that no local title matches`() {
        // Regression aus den Beta-Logs: verwaiste MD-Mirrors gelöschter Notizen liegen weiter auf
        // dem Server. Ihr Name steht in keiner Titelliste — nur der Pfad-Fall fängt sie ab, und
        // nur deshalb loggt MarkdownSyncManager `resource.path` statt `resource.name`.
        val result = anonymize(
            "MarkdownSyncManager:    ⏭️ Skipping /notes-md/Privat/015152953036 Galinsky.md: " +
                "not modified since last sync"
        )

        assertFalse("orphan filename leaked: $result", result.contains("Galinsky"))
        assertFalse("phone number leaked: $result", result.contains("015152953036"))
        assertEquals(
            "MarkdownSyncManager:    ⏭️ Skipping /notes-md/<folder>/<note>.md: not modified since last sync",
            result
        )
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

    /**
     * Regression: OX App Suite legt den Userstore unter dem **Klarnamen** an. Der steckt in
     * keiner Titel-, Ordner- oder Benutzernamen-Liste (der Login ist eine Mailadresse) und stand
     * deshalb im exportierten Log — ein Beta-Tester musste die Datei von Hand nachbearbeiten.
     */
    @Test
    fun `strips a legal name that only appears in the url path`() {
        val ox = "https://webmail.example.org/servlet/webdav.infostore/Userstore/Max Mustermann/"

        val result = LogAnonymizer.anonymize(
            "WebDavClient: list(https://webmail.example.org/servlet/webdav.infostore/" +
                "Userstore/Max%20Mustermann/notes/, depth=infinity)",
            ox,
            "max.mustermann@example.org"
        )

        assertFalse("legal name leaked: $result", result.contains("Mustermann", ignoreCase = true))
        assertEquals("WebDavClient: list(https://<server><path>/notes/, depth=infinity)", result)
    }

    /** Dieselbe URL roh — so steht sie in Logzeilen, die vor der URL-Kanonisierung entstanden. */
    @Test
    fun `strips the raw path variant too`() {
        val ox = "https://webmail.example.org/servlet/webdav.infostore/Userstore/Max Mustermann/"

        val result = LogAnonymizer.anonymize(
            "📡 Server URL: https://webmail.example.org/servlet/webdav.infostore/" +
                "Userstore/Max Mustermann/",
            ox,
            null
        )

        assertFalse("legal name leaked: $result", result.contains("Mustermann"))
        assertEquals("📡 Server URL: https://<server><path>/", result)
    }

    /** Der Pfad darf den Nextcloud-Fall nicht schlechter machen: Rest der URL bleibt lesbar. */
    @Test
    fun `keeps the part below the sync base readable`() {
        val result = anonymize(
            "get(https://cloud.example.org/remote.php/dav/files/klausi/notes/deletions.json)"
        )

        assertTrue("diagnosability lost: $result", result.endsWith("/deletions.json)"))
    }
}
