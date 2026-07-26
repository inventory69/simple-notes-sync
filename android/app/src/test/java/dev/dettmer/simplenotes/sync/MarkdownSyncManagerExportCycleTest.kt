package dev.dettmer.simplenotes.sync

import android.content.SharedPreferences
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.webdav.WebDavClient
import dev.dettmer.simplenotes.sync.webdav.WebDavResource
import dev.dettmer.simplenotes.utils.Constants
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.net.URI
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * 🆕 v2.14.0: Eine im selben Sync-Zyklus exportierte MD-Datei wird beim Import übersprungen,
 * **bevor** sie heruntergeladen wird. Vorher lud der Import sie erst und verwarf sie dann
 * anhand der ID aus dem YAML-Header — ein GET pro exportierter Notiz.
 */
class MarkdownSyncManagerExportCycleTest {
    private lateinit var manager: MarkdownSyncManager

    @Before fun setUp() {
        val prefs = mockk<SharedPreferences>(relaxed = true) {
            every { getString(Constants.KEY_SYNC_FOLDER_NAME, any()) } returns "notes"
        }
        manager = MarkdownSyncManager(
            prefs = prefs,
            storage = mockk<NotesStorage>(relaxed = true),
            eTagCache = mockk(relaxed = true),
            urlBuilder = mockk(relaxed = true) {
                every { getMarkdownUrl(any()) } returns MD_URL
                every { getMarkdownFolderUrl(any(), null) } returns MD_URL
            },
            connectionManager = mockk(relaxed = true),
            timestampManager = mockk(relaxed = true),
            ioDispatcher = Dispatchers.Unconfined,
            folderStore = mockk(relaxed = true)
        )
    }

    /** Server meldet die soeben exportierte Datei als frisch geändert (kein mtime-Skip). */
    private fun webdavWithExportedFile() = mockk<WebDavClient>(relaxed = true).also { webdav ->
        every { webdav.exists(MD_URL) } returns true
        // Echter Stream statt relaxed Mock — ein gemockter InputStream liefert 0 und der
        // Reader darüber liefe endlos.
        every { webdav.get(any()) } answers { note().toMarkdown().byteInputStream() }
        every { webdav.list(MD_URL) } returns listOf(
            WebDavResource(
                href = URI("/notes-md/Title.md"),
                modified = Date(),
                contentLength = 1,
                isDirectory = false,
                etag = "\"e1\""
            )
        )
    }

    private fun note() = Note(id = "note-1", title = "Title", content = "body", deviceId = "dev")

    @Test fun `a file exported in this cycle is not downloaded again`() = runTest {
        val webdav = webdavWithExportedFile()

        manager.beginSyncCycle()
        manager.exportSingle(webdav, SERVER, note())
        manager.importAll(webdav, SERVER)

        verify(exactly = 0) { webdav.get(match { it.endsWith(".md") }) }
    }

    @Test fun `beginSyncCycle drops the previous cycle's exports`() = runTest {
        val webdav = webdavWithExportedFile()

        manager.beginSyncCycle()
        manager.exportSingle(webdav, SERVER, note())
        manager.beginSyncCycle()
        manager.importAll(webdav, SERVER)

        verify { webdav.get(MD_URL + "Title.md") }
    }

    companion object {
        private const val SERVER = "http://server"
        private const val MD_URL = "http://server/notes-md/"
    }
}
