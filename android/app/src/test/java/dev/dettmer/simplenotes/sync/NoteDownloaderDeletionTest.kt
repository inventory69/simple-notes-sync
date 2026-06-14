package dev.dettmer.simplenotes.sync

import android.content.Context
import android.content.SharedPreferences
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.FolderStore
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.utils.Constants
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * 🆕 v2.9.0 (Trash): Tests für [NoteDownloader.detectDeletions] mit Papierkorb-Semantik.
 */
class NoteDownloaderDeletionTest {
    private lateinit var tmpDir: File
    private lateinit var prefs: SharedPreferences
    private lateinit var storage: NotesStorage
    private lateinit var downloader: NoteDownloader

    @Before fun setUp() {
        tmpDir = Files.createTempDirectory("notedl-deletion-test").toFile()
        prefs = mockk(relaxed = true)
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

    private fun note(
        id: String,
        status: SyncStatus = SyncStatus.SYNCED,
        trashedAt: Long? = null,
        updatedAt: Long = 12_345L
    ) = Note(
        id = id,
        title = "T-$id",
        content = "C",
        deviceId = "dev",
        syncStatus = status,
        trashedAt = trashedAt,
        updatedAt = updatedAt
    )

    @Test fun `active synced note missing on server is moved to trash`() = runTest {
        val n = note("a", trashedAt = null, updatedAt = 12_345L)
        storage.saveNote(n)

        val count = downloader.detectDeletions(serverNoteIds = setOf("present"), localNotes = listOf(n))

        assertEquals(1, count)
        val updated = storage.loadNote("a")!!
        assertEquals(SyncStatus.DELETED_ON_SERVER, updated.syncStatus)
        assertNotNull("trashedAt must be set", updated.trashedAt)
        assertEquals("updatedAt must NOT be bumped", 12_345L, updated.updatedAt)
    }

    @Test fun `trashed synced note missing on server is hard-deleted`() = runTest {
        val n = note("b", trashedAt = 999L)
        storage.saveNote(n)

        val count = downloader.detectDeletions(serverNoteIds = setOf("present"), localNotes = listOf(n))

        assertEquals("hard delete is not counted as moved-to-trash", 0, count)
        assertNull("note must be removed locally", storage.loadNote("b"))
    }

    @Test fun `empty serverNoteIds never deletes anything`() = runTest {
        val n = note("c")
        storage.saveNote(n)

        val count = downloader.detectDeletions(serverNoteIds = emptySet(), localNotes = listOf(n))

        assertEquals(0, count)
        assertNotNull(storage.loadNote("c"))
    }

    @Test fun `all-deleted guard aborts when every synced note is missing`() = runTest {
        val notes = (1..10).map { note("guard-$it") }
        notes.forEach { storage.saveNote(it) }

        val count = downloader.detectDeletions(serverNoteIds = setOf("unrelated"), localNotes = notes)

        assertEquals(0, count)
        notes.forEach { assertNotNull(storage.loadNote(it.id)) }
    }
}
