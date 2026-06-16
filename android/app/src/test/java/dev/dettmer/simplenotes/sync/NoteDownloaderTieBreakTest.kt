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
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression for the "server file hand-edited without bumping updatedAt" sync loop.
 * When the server JSON content differs from local at an identical updatedAt and the
 * server E-Tag changed, the download must adopt the server edit (server wins) and mark
 * the note PENDING so the MD mirror is refreshed — instead of marking PENDING forever
 * while the uploader skips the unchanged-hash re-upload.
 */
class NoteDownloaderTieBreakTest {
    private lateinit var tmpDir: File
    private lateinit var prefs: SharedPreferences
    private lateinit var storage: NotesStorage
    private lateinit var downloader: NoteDownloader

    private val serverUrl = "http://server:8080"
    private val noteId = "8aeff7d6-4aa0-447a-960d-572206faeaf5"
    private val lastSync = 2_000_000L
    private val serverModified = 3_000_000L // > lastSync
    private val tiedTimestamp = 5_000_000L
    private val folder = "Noltenius"

    @Before fun setUp() {
        tmpDir = Files.createTempDirectory("notedl-tiebreak").toFile()
        prefs = mockk(relaxed = true)
        every { prefs.getLong("last_sync_timestamp", 0L) } returns lastSync
        every { prefs.getString(Constants.KEY_SYNC_FOLDER_NAME, any()) } returns "notes"
        every {
            prefs.getInt(Constants.KEY_MAX_PARALLEL_CONNECTIONS, Constants.DEFAULT_MAX_PARALLEL_CONNECTIONS)
        } returns Constants.DEFAULT_MAX_PARALLEL_CONNECTIONS
        // Cached JSON E-Tag differs from the server's → file is treated as changed.
        every { prefs.getString("etag_json_$noteId", null) } returns "etag-old"
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

    private fun noteFile(): DavResource = mockk(relaxed = true) {
        every { isDirectory } returns false
        every { name } returns "$noteId.json"
        every { etag } returns "etag-new"
        every { modified } returns Date(serverModified)
    }

    private fun mockSardine(remoteJson: String): Sardine {
        val sardine = mockk<Sardine>(relaxed = true)
        every { sardine.list(match { it.endsWith("/notes/") }) } returns listOf(folderDir(folder))
        every { sardine.list(match { it.contains(folder) }) } returns listOf(noteFile())
        every { sardine.get(any<String>()) } answers { ByteArrayInputStream(remoteJson.toByteArray()) }
        return sardine
    }

    @Test fun `tied timestamp with changed server content adopts remote and marks PENDING`() = runTest {
        storage.saveNote(
            Note(
                id = noteId, title = "T", content = "LOCAL", deviceId = "",
                updatedAt = tiedTimestamp, syncStatus = SyncStatus.SYNCED, folderName = folder
            )
        )
        val remoteJson = Note(
            id = noteId, title = "T", content = "SERVER", deviceId = "",
            updatedAt = tiedTimestamp, syncStatus = SyncStatus.SYNCED, folderName = folder
        ).toJson()

        val result = downloader.downloadAll(mockSardine(remoteJson), serverUrl)

        assertEquals(1, result.downloadedCount)
        assertTrue("note id must be flagged as adopted", noteId in result.adoptedNoteIds)
        val healed = storage.loadNote(noteId)!!
        assertEquals("SERVER", healed.content)
        assertEquals(SyncStatus.PENDING, healed.syncStatus)
    }

    @Test fun `genuinely newer local content is kept and not adopted from remote`() = runTest {
        storage.saveNote(
            Note(
                id = noteId, title = "T", content = "LOCAL", deviceId = "",
                updatedAt = tiedTimestamp + 1_000L, syncStatus = SyncStatus.SYNCED, folderName = folder
            )
        )
        val remoteJson = Note(
            id = noteId, title = "T", content = "SERVER", deviceId = "",
            updatedAt = tiedTimestamp, syncStatus = SyncStatus.SYNCED, folderName = folder
        ).toJson()

        val result = downloader.downloadAll(mockSardine(remoteJson), serverUrl)

        assertEquals(0, result.downloadedCount)
        assertTrue("must not be adopted", noteId !in result.adoptedNoteIds)
        val local = storage.loadNote(noteId)!!
        assertEquals("LOCAL", local.content)
        assertEquals(SyncStatus.PENDING, local.syncStatus)
    }
}
