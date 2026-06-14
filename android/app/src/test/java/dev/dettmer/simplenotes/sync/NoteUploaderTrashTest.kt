package dev.dettmer.simplenotes.sync

import android.content.Context
import android.content.SharedPreferences
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

        val sardine = mockk<Sardine>(relaxed = true) {
            every { exists(any()) } returns true
            every { list(any(), any()) } returns emptyList()
        }

        uploader.uploadAll(sardine, serverUrl)

        verify(exactly = 1) { sardine.put(match { it.endsWith("tn.json") }, any<ByteArray>(), any()) }
        assertEquals("MD export must be skipped for trashed note", 0, exportCalls)
        assertEquals("MD delete must run for trashed note", 1, deleteCalls)
        // trashedAt bleibt nach Upload erhalten (Sync-Payload).
        assertTrue(storage.loadNote("tn")!!.isTrashed)
    }
}
