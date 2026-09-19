package dev.dettmer.simplenotes.sync

import android.content.SharedPreferences
import dev.dettmer.simplenotes.utils.Constants
import io.mockk.every
import io.mockk.mockk
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SyncUrlBuilderTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var builder: SyncUrlBuilder

    @Before fun setUp() {
        prefs = mockk {
            every { getString(Constants.KEY_SYNC_FOLDER_NAME, Constants.DEFAULT_SYNC_FOLDER_NAME) } returns "notes"
        }
        builder = SyncUrlBuilder(prefs)
    }

    @Test fun `folderUrl null equals notesUrl`() {
        val base = "http://s:8080/"
        assertEquals(builder.getNotesUrl(base), builder.getNotesFolderUrl(base, null))
    }

    @Test fun `folderUrl appends segment`() {
        assertEquals("http://s:8080/notes/Rezepte/", builder.getNotesFolderUrl("http://s:8080/", "Rezepte"))
    }

    @Test fun `folderUrl encodes spaces`() {
        assertEquals("http://s:8080/notes/Reise%202024/", builder.getNotesFolderUrl("http://s:8080/", "Reise 2024"))
    }

    @Test fun `markdownFolderUrl is nested and not corrupted by folder named notes`() {
        assertEquals("http://s:8080/notes-md/notes/", builder.getMarkdownFolderUrl("http://s:8080/", "notes"))
    }

    /**
     * Regression (#150): `replace("/notes", ...)` traf **jedes** Vorkommen — auch das `/notes` in
     * `https://notes.example.com`. Der MD-Sync lief damit gegen eine erfundene Subdomain
     * `notes-md.example.com`, die der Nutzer nie konfiguriert hat: TLS-Fehler, MD-Export still
     * kaputt, und Basic-Auth-Credentials an einen fremden Host.
     */
    @Test fun `markdownUrl keeps host that starts with the folder name`() {
        assertEquals("https://notes.example.com/notes-md/", builder.getMarkdownUrl("https://notes.example.com/"))
    }

    @Test fun `assetsUrl keeps host that starts with the folder name`() {
        assertEquals("https://notes.example.com/notes-assets/", builder.getAssetsUrl("https://notes.example.com/"))
    }

    /** Gleiche Wurzel: ein Basispfad, der den Ordnernamen enthält, wurde mitersetzt. */
    @Test fun `markdownUrl keeps base path that contains the folder name`() {
        assertEquals("http://s:8080/notes-archive/notes-md/", builder.getMarkdownUrl("http://s:8080/notes-archive/"))
    }

    @Test fun `markdownUrl is sibling of notesUrl for a plain base`() {
        assertEquals("http://s:8080/notes-md/", builder.getMarkdownUrl("http://s:8080/"))
        assertEquals("http://s:8080/notes-md/", builder.getMarkdownUrl("http://s:8080/notes/"))
    }

    /**
     * Regression: eine OX-App-Suite-URL mit Klarnamen im Pfad enthält ein rohes Leerzeichen.
     * Ungekodiert warf `URI(baseUrl)` in `groupByFolder` und riss den ganzen Sync mit
     * ("Illegal character in path at index 63").
     */
    @Test fun `serverUrl encodes raw spaces so java-net-URI parses it`() {
        every { prefs.getString(Constants.KEY_SERVER_URL, null) } returns
            "https://s/servlet/webdav.infostore/Userstore/Max Mustermann/"

        val url = builder.getServerUrl()!!

        assertEquals("https://s/servlet/webdav.infostore/Userstore/Max%20Mustermann/", url)
        // Der dekodierte Pfad muss das Leerzeichen zurückgeben — so vergleicht groupByFolder
        // gegen die (ebenfalls dekodierten) hrefs aus der PROPFIND-Antwort.
        assertEquals("/servlet/webdav.infostore/Userstore/Max Mustermann/", URI(url).path)
    }

    @Test fun `serverUrl passes unparsable values through untouched`() {
        every { prefs.getString(Constants.KEY_SERVER_URL, null) } returns "https://"

        assertEquals("https://", builder.getServerUrl())
    }
}
