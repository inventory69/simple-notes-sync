package dev.dettmer.simplenotes.sync

import android.content.Context
import android.content.SharedPreferences
import dev.dettmer.simplenotes.storage.FolderStore
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.webdav.WebDavClient
import dev.dettmer.simplenotes.utils.Constants
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 🆕 v2.14.0: [NoteDownloader.deleteFromServer] fasst den MD-Spiegel nur an, wenn ein
 * Markdown-Feature aktiv ist — sonst kostet jede Löschung einen PROPFIND + DELETE umsonst.
 */
class NoteDownloaderMarkdownGateTest {
    private lateinit var tmpDir: File
    private lateinit var prefs: SharedPreferences
    private lateinit var webdav: WebDavClient
    private lateinit var markdownSyncManager: MarkdownSyncManager

    @After fun tearDown() {
        tmpDir.deleteRecursively()
    }

    @Before fun setUp() {
        tmpDir = Files.createTempDirectory("notedl-mdgate-test").toFile()
        prefs = mockk(relaxed = true)
        every { prefs.getString(Constants.KEY_SYNC_FOLDER_NAME, any()) } returns "notes"
        every { prefs.getString(Constants.KEY_SERVER_URL, any()) } returns "http://server:8080"
        webdav = mockk(relaxed = true)
        markdownSyncManager = mockk(relaxed = true)
    }

    private fun downloader(mdExport: Boolean, mdAutoImport: Boolean): NoteDownloader {
        every { prefs.getBoolean(Constants.KEY_MARKDOWN_EXPORT, any()) } returns mdExport
        every { prefs.getBoolean(Constants.KEY_MARKDOWN_AUTO_IMPORT, any()) } returns mdAutoImport
        val context = mockk<Context> {
            every { filesDir } returns tmpDir
            every { getSharedPreferences(any(), any()) } returns prefs
        }
        return NoteDownloader(
            prefs = prefs,
            storage = NotesStorage(context),
            eTagCache = ETagCache(prefs),
            urlBuilder = SyncUrlBuilder(prefs),
            connectionManager = mockk(relaxed = true) { every { getOrCreateClient() } returns webdav },
            markdownSyncManager = markdownSyncManager,
            ioDispatcher = Dispatchers.Unconfined,
            folderStore = FolderStore(context)
        )
    }

    @Test fun `markdown disabled skips the MD lookup and the MD delete`() = runTest {
        assertTrue(downloader(mdExport = false, mdAutoImport = false).deleteFromServer("gone"))

        coVerify(exactly = 0) { markdownSyncManager.findAllByNoteId(any(), any(), any()) }
        verify(exactly = 0) { webdav.delete(match { it.endsWith(".md") }) }
        verify(exactly = 1) { webdav.delete(match { it.endsWith("gone.json") }) }
    }

    @Test fun `markdown export enabled still deletes the MD mirror`() = runTest {
        coEvery { markdownSyncManager.findAllByNoteId(any(), any(), any()) } returns listOf("Gone.md")

        downloader(mdExport = true, mdAutoImport = false).deleteFromServer("gone")

        verify(exactly = 1) { webdav.delete(match { it.endsWith("Gone.md") }) }
    }

    /** Auto-Import allein reicht — MD-Dateien können auch ohne Export auf dem Server liegen. */
    @Test fun `auto import alone also deletes the MD mirror`() = runTest {
        coEvery { markdownSyncManager.findAllByNoteId(any(), any(), any()) } returns listOf("Gone.md")

        downloader(mdExport = false, mdAutoImport = true).deleteFromServer("gone")

        verify(exactly = 1) { webdav.delete(match { it.endsWith("Gone.md") }) }
    }

    /** 🆕 v2.19.0: Der Export in den neuen Ordner hat den Pfad schon umgesetzt und die alte Datei gelöscht. */
    @Test fun `move after export sends no MD delete`() = runTest {
        every { prefs.getString("etag_md_path_a1", any()) } returns "http://server:8080/notes-md/Home/New.md"

        downloader(mdExport = true, mdAutoImport = false).deleteFromServer("a1", folderName = "Work", isMove = true)

        verify(exactly = 0) { webdav.delete(match { it.endsWith(".md") }) }
        coVerify(exactly = 0) { markdownSyncManager.findAllByNoteId(any(), any(), any()) }
    }

    /** 🆕 v2.19.0: Altbestand ohne gemerkten Pfad: alle Zwillinge mit der ID gehen weg. */
    @Test fun `legacy note without a path deletes every file with its id`() = runTest {
        coEvery { markdownSyncManager.findAllByNoteId(any(), any(), any()) } returns listOf("A.md", "B.md")

        downloader(mdExport = true, mdAutoImport = false).deleteFromServer("a1")

        verify(exactly = 1) { webdav.delete(match { it.endsWith("/A.md") }) }
        verify(exactly = 1) { webdav.delete(match { it.endsWith("/B.md") }) }
    }
}
