package dev.dettmer.simplenotes.sync

import android.content.Context
import android.content.SharedPreferences
import dev.dettmer.simplenotes.storage.FolderStore
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.webdav.WebDavClient
import dev.dettmer.simplenotes.utils.Constants
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * 🆕 v2.14.0: Ein Move (Notiz in Ordner verschieben / Ordner umbenennen) räumt nur den alten
 * Serverpfad auf. Der E-Tag-Cache zeigt zu dem Zeitpunkt schon auf den frisch hochgeladenen neuen
 * Pfad — ihn zu löschen liess denselben Sync die Notiz sofort wieder herunterladen
 * (Beta-Log: "📥 Downloading <id>: Modified + no cached E-Tag", 200 ms nach dem PUT).
 */
class NoteDownloaderMoveEtagTest {
    private lateinit var tmpDir: File
    private lateinit var prefs: SharedPreferences
    private lateinit var webdav: WebDavClient
    private lateinit var eTagCache: ETagCache

    @After fun tearDown() {
        tmpDir.deleteRecursively()
    }

    @Before fun setUp() {
        tmpDir = Files.createTempDirectory("notedl-move-test").toFile()
        prefs = mockk(relaxed = true)
        every { prefs.getString(Constants.KEY_SYNC_FOLDER_NAME, any()) } returns "notes"
        every { prefs.getString(Constants.KEY_SERVER_URL, any()) } returns "http://server:8080"
        every { prefs.getBoolean(Constants.KEY_MARKDOWN_EXPORT, any()) } returns false
        every { prefs.getBoolean(Constants.KEY_MARKDOWN_AUTO_IMPORT, any()) } returns false
        webdav = mockk(relaxed = true)
        eTagCache = mockk(relaxed = true)
    }

    private fun downloader(): NoteDownloader {
        val context = mockk<Context> {
            every { filesDir } returns tmpDir
            every { getSharedPreferences(any(), any()) } returns prefs
        }
        return NoteDownloader(
            prefs = prefs,
            storage = NotesStorage(context),
            eTagCache = eTagCache,
            urlBuilder = SyncUrlBuilder(prefs),
            connectionManager = mockk(relaxed = true) { every { getOrCreateClient() } returns webdav },
            markdownSyncManager = mockk(relaxed = true),
            ioDispatcher = Dispatchers.Unconfined,
            folderStore = FolderStore(context)
        )
    }

    @Test fun `move keeps the cached E-Tag of the new path`() = runTest {
        downloader().deleteFromServer("moved", folderName = "Work", isMove = true)

        verify(exactly = 0) { eTagCache.clearForNote(any()) }
    }

    @Test fun `real deletion still invalidates the E-Tag`() = runTest {
        downloader().deleteFromServer("gone", folderName = "Work", isMove = false)

        verify(exactly = 1) { eTagCache.clearForNote("gone") }
    }
}
