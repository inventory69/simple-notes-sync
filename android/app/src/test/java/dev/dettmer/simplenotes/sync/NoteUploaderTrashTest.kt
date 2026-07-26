package dev.dettmer.simplenotes.sync

import android.content.Context
import android.content.SharedPreferences
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.FolderStore
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.webdav.WebDavClient
import dev.dettmer.simplenotes.sync.webdav.WebDavException
import dev.dettmer.simplenotes.utils.Constants
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 🆕 v2.9.0 (Trash): Stellt sicher, dass beim Upload einer getrashten Notiz der Server-MD-Spiegel
 * gelöscht (statt exportiert) wird, die JSON aber trotzdem hochgeht.
 */
class NoteUploaderTrashTest {
    private lateinit var tmpDir: File
    private lateinit var prefs: SharedPreferences
    private lateinit var storage: NotesStorage
    private val serverUrl = "http://server:8080"

    @Before fun setUp() {
        tmpDir = Files.createTempDirectory("uploader-trash-test").toFile()
        prefs = mockk(relaxed = true)
        every { prefs.getString(Constants.KEY_SYNC_FOLDER_NAME, any()) } returns "notes"
        every { prefs.getBoolean(Constants.KEY_MARKDOWN_EXPORT, any()) } returns true
        every { prefs.getInt(Constants.KEY_MAX_PARALLEL_CONNECTIONS, any()) } returns 1
        val context = mockk<Context> {
            every { filesDir } returns tmpDir
            every { getSharedPreferences(any(), any()) } returns prefs
        }
        storage = NotesStorage(context)
    }

    @After fun tearDown() {
        tmpDir.deleteRecursively()
    }

    @Test fun `trashed note triggers MD delete not export but still uploads JSON`() = runTest {
        var exportCalls = 0
        var deleteCalls = 0
        val uploader = NoteUploader(
            prefs = prefs,
            storage = storage,
            eTagCache = ETagCache(prefs),
            urlBuilder = SyncUrlBuilder(prefs),
            ioDispatcher = Dispatchers.Unconfined,
            folderStore = FolderStore(mockk(relaxed = true)),
            markdownExporter = { _, _, _, _ -> exportCalls++ },
            markdownDeleter = { _, _, _ -> deleteCalls++ }
        )

        storage.saveNote(
            Note(
                id = "tn",
                title = "Gone",
                content = "x",
                deviceId = "dev",
                syncStatus = SyncStatus.PENDING,
                trashedAt = 123L
            )
        )

        val webdav = mockk<WebDavClient>(relaxed = true) {
            every { exists(any()) } returns true
            every { list(any(), any()) } returns emptyList()
        }

        uploader.uploadAll(webdav, serverUrl)

        verify(exactly = 1) { webdav.put(match { it.endsWith("tn.json") }, any<ByteArray>(), any()) }
        assertEquals("MD export must be skipped for trashed note", 0, exportCalls)
        assertEquals("MD delete must run for trashed note", 1, deleteCalls)
        // trashedAt bleibt nach Upload erhalten (Sync-Payload).
        assertTrue(storage.loadNote("tn")!!.isTrashed)
    }

    private fun uploader() = NoteUploader(
        prefs = prefs,
        storage = storage,
        eTagCache = ETagCache(prefs),
        urlBuilder = SyncUrlBuilder(prefs),
        ioDispatcher = Dispatchers.Unconfined,
        folderStore = FolderStore(mockk(relaxed = true)),
        markdownExporter = { _, _, _, _ -> },
        markdownDeleter = { _, _, _ -> }
    )

    /**
     * 🆕 v2.14.0 Self-Heal: nur ein WebDAV-404/409 heißt „Verzeichnis weg" — ein 500 oder
     * Timeout darf das persistierte Flag nicht löschen, sonst kostet jeder Serverfehler
     * dauerhaft wieder Dir-Ensure-Requests.
     */
    private suspend fun uploadFailingWith(error: Exception): Int {
        storage.saveNote(
            Note(id = "n1", title = "A", content = "x", deviceId = "dev", syncStatus = SyncStatus.PENDING)
        )
        var missingDirCallbacks = 0
        val webdav = mockk<WebDavClient>(relaxed = true) {
            every { exists(any()) } returns true
            every { put(any(), any<ByteArray>(), any()) } throws error
        }
        NoteUploader(
            prefs = prefs,
            storage = storage,
            eTagCache = ETagCache(prefs),
            urlBuilder = SyncUrlBuilder(prefs),
            ioDispatcher = Dispatchers.Unconfined,
            folderStore = FolderStore(mockk(relaxed = true)),
            onMissingServerDir = { missingDirCallbacks++ }
        ).uploadAll(webdav, serverUrl)
        return missingDirCallbacks
    }

    @Test fun `a 409 on PUT fires the missing-server-dir callback`() = runTest {
        assertEquals(1, uploadFailingWith(WebDavException("conflict", 409)))
    }

    @Test fun `a 404 on PUT fires the missing-server-dir callback`() = runTest {
        assertEquals(1, uploadFailingWith(WebDavException("not found", 404)))
    }

    @Test fun `a 500 on PUT does not fire the missing-server-dir callback`() = runTest {
        assertEquals(0, uploadFailingWith(WebDavException("server error", 500)))
    }

    @Test fun `a timeout does not fire the missing-server-dir callback`() = runTest {
        assertEquals(0, uploadFailingWith(java.net.SocketTimeoutException("timeout")))
    }

    /** 🆕 v2.14.0: Ohne hochzuladende Notiz darf der notes-md/-Check gar nicht erst laufen. */
    @Test fun `no pending notes means no markdown directory check`() = runTest {
        val webdav = mockk<WebDavClient>(relaxed = true)

        uploader().uploadAll(webdav, serverUrl)

        verify(exactly = 0) { webdav.exists(any()) }
    }

    /** 🆕 v2.14.0: Liefert der PUT einen ETag, entfällt der Batch-PROPFIND komplett. */
    @Test fun `a PUT ETag makes the batch PROPFIND unnecessary`() = runTest {
        storage.saveNote(
            Note(id = "n1", title = "A", content = "x", deviceId = "dev", syncStatus = SyncStatus.PENDING)
        )
        val webdav = mockk<WebDavClient>(relaxed = true) {
            every { exists(any()) } returns true
            every { put(any(), any<ByteArray>(), any()) } returns "\"etag-1\""
        }

        uploader().uploadAll(webdav, serverUrl)

        verify(exactly = 0) { webdav.list(any(), any()) }
    }

    @Test fun `without a PUT ETag the batch PROPFIND runs for the affected folder only`() = runTest {
        storage.saveNote(
            Note(id = "root", title = "R", content = "x", deviceId = "dev", syncStatus = SyncStatus.PENDING)
        )
        storage.saveNote(
            Note(
                id = "sub",
                title = "S",
                content = "x",
                deviceId = "dev",
                syncStatus = SyncStatus.PENDING,
                folderName = "Rezepte"
            )
        )
        val webdav = mockk<WebDavClient>(relaxed = true) {
            every { exists(any()) } returns true
            every { list(any(), any()) } returns emptyList()
            // Nur die Root-Notiz bekommt einen ETag zurück.
            every { put(match { it.endsWith("sub.json") }, any<ByteArray>(), any()) } returns null
            every { put(match { !it.endsWith("sub.json") }, any<ByteArray>(), any()) } returns "\"etag-root\""
        }

        uploader().uploadAll(webdav, serverUrl)

        verify(exactly = 1) { webdav.list(any(), any()) }
        verify(exactly = 1) { webdav.list(match { it.endsWith("Rezepte/") }, any()) }
    }

    @Test fun `one pending note checks the markdown directory exactly once`() = runTest {
        storage.saveNote(
            Note(id = "n1", title = "A", content = "x", deviceId = "dev", syncStatus = SyncStatus.PENDING)
        )
        val webdav = mockk<WebDavClient>(relaxed = true) {
            every { exists(any()) } returns true
            every { list(any(), any()) } returns emptyList()
        }

        uploader().uploadAll(webdav, serverUrl)

        verify(exactly = 1) { webdav.exists(match { it.endsWith("notes-md/") }) }
    }
}
