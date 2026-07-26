package dev.dettmer.simplenotes.sync

import android.content.Context
import android.content.SharedPreferences
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.storage.FolderStore
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.webdav.WebDavClient
import dev.dettmer.simplenotes.sync.webdav.WebDavResource
import dev.dettmer.simplenotes.utils.Constants
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

private const val SERVER_URL = "https://host"
private const val NOTES_URL = "https://host/notes/"
private const val WORK_URL = "https://host/notes/Work/"
private const val WORK_NOTE_ID = "11111111-2222-3333-4444-555555555555"

/**
 * Regressionstest am echten Datenverlust-Pfad: sabre/dav klemmt `Depth: infinity` bei
 * ausgeschaltetem `enablePropfindDepthInfinity` still auf Depth 1 und antwortet 207. Die Antwort
 * enthält dann jeden Unterordner, aber keinen seiner Inhalte.
 *
 * Würde [downloadAll] das übernehmen, fehlten alle Notizen der Unterordner in `serverNoteIds`;
 * `detectDeletions` markierte sie als `DELETED_ON_SERVER` und löschte bereits getrashte hart.
 */
class NoteDownloaderDeepPropfindTest {
    private lateinit var tmpDir: File
    private lateinit var storage: NotesStorage
    private lateinit var downloader: NoteDownloader
    private lateinit var webdav: WebDavClient

    @Before fun setUp() {
        tmpDir = Files.createTempDirectory("notedl-deep-propfind-test").toFile()
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getString(Constants.KEY_SYNC_FOLDER_NAME, any()) } returns "notes"
        // Ohne das liefert das relaxed Mock 0 und ParallelDownloader baut ein Semaphore(0).
        every { prefs.getInt(Constants.KEY_MAX_PARALLEL_CONNECTIONS, any()) } returns
            Constants.DEFAULT_MAX_PARALLEL_CONNECTIONS
        val context = mockk<Context> {
            every { filesDir } returns tmpDir
            every { getSharedPreferences(any(), any()) } returns prefs
        }
        storage = NotesStorage(context)
        downloader = NoteDownloader(
            prefs = prefs,
            storage = storage,
            eTagCache = ETagCache(prefs),
            urlBuilder = SyncUrlBuilder(prefs),
            connectionManager = mockk(relaxed = true),
            markdownSyncManager = mockk(relaxed = true),
            ioDispatcher = Dispatchers.Unconfined,
            folderStore = FolderStore(context)
        )
        webdav = mockk(relaxed = true)
    }

    @After fun tearDown() {
        tmpDir.deleteRecursively()
    }

    private fun res(path: String, isDirectory: Boolean = false) = WebDavResource(
        href = URI(path),
        modified = Date(1_000L),
        contentLength = if (isDirectory) -1 else 1,
        isDirectory = isDirectory,
        etag = "\"e\""
    )

    @Test fun `a clamped deep PROPFIND still finds the notes in subfolders`() = runTest {
        val workNote = Note(id = WORK_NOTE_ID, title = "In Work", content = "C", deviceId = "dev")
        // Geklemmt: der Unterordner ist da, sein Inhalt fehlt.
        every { webdav.listDeep(NOTES_URL) } returns listOf(
            res("/notes/", isDirectory = true),
            res("/notes/Work/", isDirectory = true)
        )
        every { webdav.listOrNull(NOTES_URL) } returns listOf(
            res("/notes/", isDirectory = true),
            res("/notes/Work/", isDirectory = true)
        )
        every { webdav.listOrNull(WORK_URL) } returns listOf(res("/notes/Work/$WORK_NOTE_ID.json"))
        every { webdav.get("$WORK_URL$WORK_NOTE_ID.json") } answers {
            workNote.toJson().byteInputStream()
        }

        val result = downloader.downloadAll(webdav, SERVER_URL)

        // Der Fallback ist gelaufen — ohne ihn gäbe es kein Einzel-Listing des Unterordners.
        verify { webdav.listOrNull(match<String> { it.contains("/Work/") }) }
        assertEquals(1, result.downloadedCount)
        val stored = storage.loadNote(WORK_NOTE_ID)
        assertNotNull("subfolder note must be downloaded, not treated as deleted", stored)
        assertEquals("Work", stored!!.folderName)
    }
}
