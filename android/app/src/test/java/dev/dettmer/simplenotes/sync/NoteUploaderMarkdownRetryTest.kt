package dev.dettmer.simplenotes.sync

import android.content.Context
import android.content.SharedPreferences
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.FolderStore
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.webdav.WebDavClient
import dev.dettmer.simplenotes.utils.Constants
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 🆕 v2.19.0: Eine gescheiterte MD-Kopie wird im nächsten Sync gezielt nachgezogen —
 * ohne dass sich die Notiz ändern muss. Harness wie [NoteUploaderTrashTest].
 */
class NoteUploaderMarkdownRetryTest {
    private lateinit var tmpDir: File
    private lateinit var prefs: SharedPreferences
    private lateinit var storage: NotesStorage
    private val serverUrl = "http://server:8080"
    private val webdav = mockk<WebDavClient>(relaxed = true)
    private val exported = mutableListOf<String>()

    @Before fun setUp() {
        tmpDir = Files.createTempDirectory("uploader-md-retry-test").toFile()
        prefs = mockk(relaxed = true)
        every { prefs.getString(Constants.KEY_SYNC_FOLDER_NAME, any()) } returns "notes"
        val context = mockk<Context> {
            every { filesDir } returns tmpDir
            every { getSharedPreferences(any(), any()) } returns prefs
        }
        storage = NotesStorage(context)
    }

    @After fun tearDown() {
        tmpDir.deleteRecursively()
    }

    private fun uploader(failWith: Exception? = null) = NoteUploader(
        prefs = prefs,
        storage = storage,
        eTagCache = ETagCache(prefs),
        urlBuilder = SyncUrlBuilder(prefs),
        ioDispatcher = Dispatchers.Unconfined,
        folderStore = FolderStore(mockk(relaxed = true)),
        markdownExporter = { _, _, note, _ ->
            failWith?.let { throw it }
            exported += note.id
        },
        markdownDeleter = { _, _, _ -> }
    )

    private suspend fun save(id: String, status: SyncStatus) =
        storage.saveNote(Note(id = id, title = id, content = "x", deviceId = "dev", syncStatus = status))

    @Test fun `a synced note is exported again`() = runTest {
        save("s1", SyncStatus.SYNCED)

        val result = uploader().retryMarkdownMirrors(webdav, serverUrl, setOf("s1"))

        assertEquals(setOf("s1"), result.exportedIds)
        assertEquals(listOf("s1"), exported)
    }

    @Test fun `a throwing exporter keeps the id with its error`() = runTest {
        save("s1", SyncStatus.SYNCED)

        val result = uploader(failWith = java.io.IOException("409")).retryMarkdownMirrors(webdav, serverUrl, setOf("s1"))

        assertEquals(setOf("s1"), result.failed.keys)
        assertEquals(emptySet<String>(), result.exportedIds)
    }

    @Test fun `a missing note drops out`() = runTest {
        val result = uploader().retryMarkdownMirrors(webdav, serverUrl, setOf("gone"))

        assertEquals(MarkdownRetryResult(), result)
    }

    @Test fun `a pending note is carried over without an export`() = runTest {
        save("p1", SyncStatus.PENDING)

        val result = uploader().retryMarkdownMirrors(webdav, serverUrl, setOf("p1"))

        assertEquals(setOf("p1"), result.carriedOver)
        assertEquals(emptyList<String>(), exported)
    }

    @Test fun `an empty set costs no request`() = runTest {
        uploader().retryMarkdownMirrors(webdav, serverUrl, emptySet())

        verify { webdav wasNot Called }
    }
}
