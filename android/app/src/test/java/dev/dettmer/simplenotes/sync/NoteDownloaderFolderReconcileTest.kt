package dev.dettmer.simplenotes.sync

import android.content.Context
import android.content.SharedPreferences
import com.thegrizzlylabs.sardineandroid.DavResource
import com.thegrizzlylabs.sardineandroid.Sardine
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.FolderStore
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.utils.Constants
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.nio.file.Files
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class NoteDownloaderFolderReconcileTest {
    private lateinit var tmpDir: File
    private lateinit var prefs: SharedPreferences
    private lateinit var storage: NotesStorage
    private lateinit var downloader: NoteDownloader

    private val serverUrl = "http://server:8080"
    private val noteId = "8aeff7d6-4aa0-447a-960d-572206faeaf5"
    private val lastSync = 2_000_000L
    private val serverModified = 1_000_000L // <= lastSync → Timestamp-Fallback-Skip

    @Before fun setUp() {
        tmpDir = Files.createTempDirectory("notedl-test").toFile()
        prefs = mockk(relaxed = true)
        every { prefs.getLong("last_sync_timestamp", 0L) } returns lastSync
        every { prefs.getString(Constants.KEY_SYNC_FOLDER_NAME, any()) } returns "notes"
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
    }

    @After fun tearDown() {
        tmpDir.deleteRecursively()
    }

    private fun folderDir(dirName: String): DavResource = mockk(relaxed = true) {
        every { isDirectory } returns true
        every { name } returns dirName
    }

    private fun noteFile(id: String, etagValue: String? = null): DavResource = mockk(relaxed = true) {
        every { isDirectory } returns false
        every { name } returns "$id.json"
        every { etag } returns etagValue
        every { modified } returns Date(serverModified)
    }

    private fun mockSardine(): Sardine = mockk(relaxed = true) {
        every { list(match { it.endsWith("/notes/") }) } returns listOf(folderDir("Noltenius"))
        every { list(match { it.contains("Noltenius") }) } returns listOf(noteFile(noteId))
    }

    @Test fun `stale folderName is healed to server path without download`() = runTest {
        storage.saveNote(
            Note(id = noteId, title = "T", content = "C", deviceId = "", syncStatus = SyncStatus.SYNCED, folderName = null)
        )
        val sardine = mockSardine()

        val result = downloader.downloadAll(sardine, serverUrl)

        assertEquals(0, result.downloadedCount)
        verify(exactly = 0) { sardine.get(any<String>()) }
        assertEquals(1, result.folderReconciledCount)
        val healed = storage.loadNote(noteId)!!
        assertEquals("Noltenius", healed.folderName)
        assertEquals(SyncStatus.SYNCED, healed.syncStatus)
    }

    @Test fun `pending note is not reconciled`() = runTest {
        storage.saveNote(
            Note(id = noteId, title = "T", content = "C", deviceId = "", syncStatus = SyncStatus.PENDING, folderName = null)
        )
        val result = downloader.downloadAll(mockSardine(), serverUrl)
        assertEquals(0, result.folderReconciledCount)
        assertNull(storage.loadNote(noteId)!!.folderName)
    }

    @Test fun `already correct folder is a no-op`() = runTest {
        storage.saveNote(
            Note(id = noteId, title = "T", content = "C", deviceId = "", syncStatus = SyncStatus.SYNCED, folderName = "Noltenius")
        )
        val result = downloader.downloadAll(mockSardine(), serverUrl)
        assertEquals(0, result.folderReconciledCount)
        assertEquals("Noltenius", storage.loadNote(noteId)!!.folderName)
    }

    @Test fun `reconcile clears trashedAt when DELETED_ON_SERVER note is present on server`() = runTest {
        storage.saveNote(
            Note(
                id = noteId,
                title = "T",
                content = "C",
                deviceId = "",
                syncStatus = SyncStatus.DELETED_ON_SERVER,
                folderName = null,
                trashedAt = 999L
            )
        )

        val result = downloader.downloadAll(mockSardine(), serverUrl)

        assertEquals(1, result.folderReconciledCount)
        val healed = storage.loadNote(noteId)!!
        assertEquals(SyncStatus.SYNCED, healed.syncStatus)
        assertNull("trashedAt must be cleared on heal", healed.trashedAt)
    }

    @Test fun `false DELETED_ON_SERVER is cleared when note is still present on server`() = runTest {
        storage.saveNote(
            Note(
                id = noteId,
                title = "T",
                content = "C",
                deviceId = "",
                syncStatus = SyncStatus.DELETED_ON_SERVER,
                folderName = null
            )
        )
        val sardine = mockSardine()

        val result = downloader.downloadAll(sardine, serverUrl)

        assertEquals(0, result.downloadedCount)
        verify(exactly = 0) { sardine.get(any<String>()) } // kein Download — lokaler Inhalt bleibt
        assertEquals(1, result.folderReconciledCount)
        val healed = storage.loadNote(noteId)!!
        assertEquals(SyncStatus.SYNCED, healed.syncStatus)
        assertEquals("Noltenius", healed.folderName)
    }
}
