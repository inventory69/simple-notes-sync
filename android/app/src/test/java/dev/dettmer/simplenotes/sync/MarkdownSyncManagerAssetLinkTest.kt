package dev.dettmer.simplenotes.sync

import android.content.SharedPreferences
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.webdav.WebDavClient
import dev.dettmer.simplenotes.utils.Constants
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 🆕 Bild-Attachments: verifiziert die relative Pfad-Umschreibung von `.assets/`-Links
 * beim MD-Mirror-Export (Modul 5). Content-Referenzen (JSON) bleiben unangetastet —
 * nur der exportierte MD-Spiegel bekommt den relativen Pfad zum Geschwister-Ordner.
 */
class MarkdownSyncManagerAssetLinkTest {
    private lateinit var prefs: SharedPreferences
    private lateinit var manager: MarkdownSyncManager

    @Before fun setUp() {
        prefs = mockk<SharedPreferences>(relaxed = true) {
            every { getString(Constants.KEY_SYNC_FOLDER_NAME, any()) } returns "notes"
        }
        manager = MarkdownSyncManager(
            prefs = prefs,
            storage = mockk<NotesStorage>(relaxed = true),
            eTagCache = mockk(relaxed = true),
            urlBuilder = mockk(relaxed = true),
            connectionManager = mockk(relaxed = true),
            timestampManager = mockk(relaxed = true),
            ioDispatcher = Dispatchers.Unconfined,
            folderStore = mockk(relaxed = true)
        )
    }

    private fun note(content: String, folderName: String? = null) = Note(
        id = "note-1",
        title = "Title",
        content = content,
        deviceId = "dev",
        folderName = folderName
    )

    @Test fun `root note gets one level up to the assets sibling folder`() {
        val webdav = mockk<WebDavClient>(relaxed = true)
        val bytes = slot<ByteArray>()
        every { webdav.put(any(), capture(bytes), any()) } returns null

        manager.exportSingle(webdav, "http://server", note("![](.assets/abc123.webp)"))

        assertTrue(String(bytes.captured).contains("![](../notes-assets/abc123.webp)"))
    }

    @Test fun `note inside a folder gets two levels up`() {
        val webdav = mockk<WebDavClient>(relaxed = true)
        val bytes = slot<ByteArray>()
        every { webdav.put(any(), capture(bytes), any()) } returns null

        manager.exportSingle(webdav, "http://server", note("![](.assets/abc123.webp)", folderName = "Rezepte"))

        assertTrue(String(bytes.captured).contains("![](../../notes-assets/abc123.webp)"))
    }

    @Test fun `content without image links is unaffected`() {
        val webdav = mockk<WebDavClient>(relaxed = true)
        val bytes = slot<ByteArray>()
        every { webdav.put(any(), capture(bytes), any()) } returns null

        manager.exportSingle(webdav, "http://server", note("just plain text"))

        assertEquals(true, String(bytes.captured).contains("just plain text"))
    }
}
