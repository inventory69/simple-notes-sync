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
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Der Upload arbeitet auf einem Schnappschuss der Notiz und schreibt nach dem PUT ihren Status
 * zurück. Dazwischen liegen Requests (Sekunden bei langsamem Server oder Retries). Speichert der
 * Editor in dieser Zeit, darf der alte Schnappschuss die neuere Fassung nicht überschreiben: sie
 * wäre lokal weg und stünde als SYNCED da, ginge also auch nie hoch.
 */
class NoteUploaderLocalEditRaceTest {
    private lateinit var tmpDir: File
    private lateinit var prefs: SharedPreferences
    private lateinit var storage: NotesStorage

    private val serverUrl = "http://server:8080"
    private val noteId = "b2e1c3d4-5f6a-4b7c-8d9e-0f1a2b3c4d5e"

    @Before fun setUp() {
        tmpDir = Files.createTempDirectory("uploader-race").toFile()
        prefs = mockk(relaxed = true)
        every { prefs.getString(Constants.KEY_SYNC_FOLDER_NAME, any()) } returns "notes"
        every { prefs.getBoolean(Constants.KEY_MARKDOWN_EXPORT, any()) } returns false
        every { prefs.getInt(Constants.KEY_MAX_PARALLEL_CONNECTIONS, any()) } returns 1
        every { prefs.getString("etag_json_$noteId", null) } returns null
        every { prefs.getString("content_hash_$noteId", null) } returns null
        val context = mockk<Context> {
            every { filesDir } returns tmpDir
            every { getSharedPreferences(any(), any()) } returns prefs
        }
        storage = NotesStorage(context)
    }

    @After fun tearDown() {
        tmpDir.deleteRecursively()
    }

    private fun uploader() = NoteUploader(
        prefs = prefs,
        storage = storage,
        eTagCache = ETagCache(prefs),
        urlBuilder = SyncUrlBuilder(prefs),
        ioDispatcher = Dispatchers.Unconfined,
        folderStore = FolderStore(mockk(relaxed = true))
    )

    private val uploaded = Note(
        id = noteId,
        title = "Liste",
        content = "ALT",
        updatedAt = 1_000L,
        deviceId = "dev",
        syncStatus = SyncStatus.PENDING
    )

    /** Was der Editor speichert, während der Request läuft. */
    private val editedMeanwhile = uploaded.copy(content = "NEU", updatedAt = 2_000L)

    private fun webdav(onPut: () -> String?) = mockk<WebDavClient>(relaxed = true) {
        every { exists(any()) } returns true
        every { list(any(), any()) } returns emptyList()
        every { listOrNull(any()) } returns emptyList()
        every { put(any(), any(), any(), any()) } answers { onPut() }
    }

    @Test fun `an edit saved while the PUT is running survives and stays pending`() = runTest {
        storage.saveNote(uploaded)
        val webdav = webdav {
            runBlocking { storage.saveNote(editedMeanwhile) }
            "\"server-v1\""
        }

        val result = uploader().uploadAll(webdav, serverUrl)

        assertEquals("the old version did reach the server", 1, result.uploadedCount)
        val onDisk = storage.loadNote(noteId)!!
        assertEquals("NEU", onDisk.content)
        assertEquals("the newer version still has to go up", SyncStatus.PENDING, onDisk.syncStatus)
    }

    @Test fun `a failed upload does not roll back an edit saved meanwhile`() = runTest {
        storage.saveNote(uploaded)
        val webdav = webdav {
            runBlocking { storage.saveNote(editedMeanwhile) }
            throw IOException("timeout")
        }

        uploader().uploadAll(webdav, serverUrl)

        assertEquals("NEU", storage.loadNote(noteId)!!.content)
    }

    @Test fun `a conflict mark does not roll back an edit saved meanwhile`() = runTest {
        storage.saveNote(uploaded)
        val webdav = webdav {
            runBlocking { storage.saveNote(editedMeanwhile) }
            throw WebDavException("PUT failed: 412", 412)
        }

        uploader().uploadAll(webdav, serverUrl)

        assertEquals("NEU", storage.loadNote(noteId)!!.content)
    }

    @Test fun `a note deleted while the PUT is running is not written back`() = runTest {
        storage.saveNote(uploaded)
        val webdav = webdav {
            runBlocking { storage.deleteNote(noteId) }
            "\"server-v1\""
        }

        uploader().uploadAll(webdav, serverUrl)

        assertNull(storage.loadNote(noteId))
    }

    @Test fun `without a concurrent edit the note becomes SYNCED as before`() = runTest {
        storage.saveNote(uploaded)

        val result = uploader().uploadAll(webdav { "\"server-v1\"" }, serverUrl)

        assertEquals(1, result.uploadedCount)
        val onDisk = storage.loadNote(noteId)!!
        assertEquals(SyncStatus.SYNCED, onDisk.syncStatus)
        assertEquals("ALT", onDisk.content)
    }
}
