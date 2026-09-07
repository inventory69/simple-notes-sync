package dev.dettmer.simplenotes.sync

import android.content.Context
import android.content.SharedPreferences
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.FolderStore
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.webdav.WebDavClient
import dev.dettmer.simplenotes.sync.webdav.WebDavException
import dev.dettmer.simplenotes.sync.webdav.WebDavResource
import dev.dettmer.simplenotes.utils.Constants
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 🆕 v2.16.0: Der Upload PUTet nicht mehr blind.
 *
 * Vorher gewann, wer als Zweiter synct: `uploadSingle()` setzte die Datei ohne `If-Match`,
 * ohne ETag-Vergleich und ohne Blick auf `updatedAt` der Server-Fassung — die fremde Änderung
 * war weg, bevor der Download sie überhaupt gesehen hatte. Jetzt trägt das PUT den gecachten
 * ETag als Precondition; `412` wird zum Konflikt statt zum Überschreiben.
 */
class NoteUploaderPreconditionTest {
    private lateinit var tmpDir: File
    private lateinit var prefs: SharedPreferences
    private lateinit var storage: NotesStorage

    private val serverUrl = "http://server:8080"
    private val noteId = "a1f0d2b3-4c5e-4f60-9a71-2b3c4d5e6f70"
    private val cachedETag = "\"server-v1\""

    @Before fun setUp() {
        tmpDir = Files.createTempDirectory("uploader-precondition").toFile()
        prefs = mockk(relaxed = true)
        every { prefs.getString(Constants.KEY_SYNC_FOLDER_NAME, any()) } returns "notes"
        every { prefs.getBoolean(Constants.KEY_MARKDOWN_EXPORT, any()) } returns false
        every { prefs.getInt(Constants.KEY_MAX_PARALLEL_CONNECTIONS, any()) } returns 1
        // Gecachter ETag vorhanden, Content-Hash absichtlich nicht → kein Skip, echter PUT.
        every { prefs.getString("etag_json_$noteId", null) } returns cachedETag
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

    private fun uploader(connectionManager: ConnectionManager? = null) = NoteUploader(
        prefs = prefs,
        storage = storage,
        eTagCache = ETagCache(prefs),
        urlBuilder = SyncUrlBuilder(prefs),
        ioDispatcher = Dispatchers.Unconfined,
        folderStore = FolderStore(mockk(relaxed = true)),
        connectionManager = connectionManager
    )

    private suspend fun givenPendingNote() = storage.saveNote(
        Note(
            id = noteId,
            title = "Liste",
            content = "LOCAL",
            deviceId = "dev",
            syncStatus = SyncStatus.PENDING
        )
    )

    private fun webdav(block: WebDavClient.() -> Unit = {}) = mockk<WebDavClient>(relaxed = true) {
        every { exists(any()) } returns true
        every { list(any(), any()) } returns emptyList()
        every { listOrNull(any()) } returns emptyList()
        block()
    }

    /** Server-Listing, das die Notiz mit [etag] enthält (Pre-Upload-Snapshot). */
    private fun WebDavClient.serverHasNote(etag: String?) {
        every { listOrNull(any()) } returns listOf(
            WebDavResource(
                href = URI("/notes/$noteId.json"),
                modified = Date(),
                contentLength = 1,
                isDirectory = false,
                etag = etag
            )
        )
    }

    @Test fun `PUT carries the cached E-Tag as If-Match`() = runTest {
        givenPendingNote()
        val ifMatch = slot<String>()
        val webdav = webdav {
            every { put(any(), any(), any(), capture(ifMatch)) } returns "\"server-v2\""
        }

        uploader().uploadAll(webdav, serverUrl)

        assertEquals("\"server-v1\"", ifMatch.captured)
    }

    @Test fun `a 412 marks the note CONFLICT instead of overwriting the server version`() = runTest {
        givenPendingNote()
        val webdav = webdav {
            every { put(any(), any(), any(), any()) } throws WebDavException("PUT failed: 412", 412)
        }

        val result = uploader().uploadAll(webdav, serverUrl)

        assertEquals("upload must not count as done", 0, result.uploadedCount)
        assertEquals(1, result.conflictCount)
        val kept = storage.loadNote(noteId)!!
        assertEquals(SyncStatus.CONFLICT, kept.syncStatus)
        assertEquals("LOCAL", kept.content)
        // Kein zweiter Versuch: der Retry bekäme dieselbe Antwort.
        verify(exactly = 1) { webdav.put(any(), any(), any(), any()) }
    }

    @Test fun `a server rejecting If-Match falls back once and remembers it`() = runTest {
        givenPendingNote()
        val connectionManager = mockk<ConnectionManager>(relaxed = true) {
            every { preconditionsUnsupported } returns false
        }
        val flag = slot<Boolean>()
        every { connectionManager.preconditionsUnsupported = capture(flag) } just Runs
        val webdav = webdav {
            every {
                put(any(), any(), any(), isNull(inverse = true))
            } throws WebDavException("PUT failed: 501", 501)
            every { put(any(), any(), any(), isNull()) } returns "\"server-v2\""
        }

        val result = uploader(connectionManager).uploadAll(webdav, serverUrl)

        assertEquals("the note must still reach the server", 1, result.uploadedCount)
        assertEquals(0, result.conflictCount)
        assertTrue("server must be remembered as precondition-less", flag.captured)
        assertEquals(SyncStatus.SYNCED, storage.loadNote(noteId)!!.syncStatus)
    }

    // ── Server-unabhängiger Schutz (Pre-Upload-Snapshot) ──────────────────────────────────
    // Der in server/README.md empfohlene Server (hacdias/webdav) wertet If-Match beim PUT
    // nicht aus — ein falscher Wert kommt dort als 201 zurück. Der Vergleich muss deshalb in
    // der App passieren, mit Daten, die jeder WebDAV-Server liefert.

    @Test fun `a changed server E-Tag stops the upload before anything is written`() = runTest {
        givenPendingNote()
        val webdav = webdav()
        webdav.serverHasNote("\"server-v2-from-other-device\"") // cached ist "server-v1"

        val result = uploader().uploadAll(webdav, serverUrl)

        assertEquals(0, result.uploadedCount)
        assertEquals(1, result.conflictCount)
        verify(exactly = 0) { webdav.put(any(), any(), any(), any()) }
        val kept = storage.loadNote(noteId)!!
        assertEquals(SyncStatus.CONFLICT, kept.syncStatus)
        assertEquals("LOCAL", kept.content)
    }

    @Test fun `an unchanged server E-Tag lets the upload through`() = runTest {
        givenPendingNote()
        val webdav = webdav()
        // Anderes Format, gleicher Wert — etagsMatch muss das ausgleichen, sonst meldet
        // jeder zweite Sync einen Konflikt.
        webdav.serverHasNote("W/\"server-v1\"")
        every { webdav.put(any(), any(), any(), any()) } returns "\"server-v2\""

        val result = uploader().uploadAll(webdav, serverUrl)

        assertEquals(1, result.uploadedCount)
        assertEquals(0, result.conflictCount)
    }

    @Test fun `a failed listing never blocks the upload`() = runTest {
        givenPendingNote()
        val webdav = webdav()
        every { webdav.listOrNull(any()) } throws java.io.IOException("server hiccup")
        every { webdav.put(any(), any(), any(), any()) } returns "\"server-v2\""

        val result = uploader().uploadAll(webdav, serverUrl)

        assertEquals("no judgement possible → behave as before", 1, result.uploadedCount)
        assertEquals(0, result.conflictCount)
    }

    @Test fun `a note missing on the server is uploaded, not flagged`() = runTest {
        givenPendingNote()
        val webdav = webdav() // Listing leer → Datei existiert dort nicht
        every { webdav.put(any(), any(), any(), any()) } returns "\"server-v1\""

        val result = uploader().uploadAll(webdav, serverUrl)

        assertEquals(1, result.uploadedCount)
        assertEquals(0, result.conflictCount)
    }

    @Test fun `without a cached E-Tag no listing is fetched at all`() = runTest {
        every { prefs.getString("etag_json_$noteId", null) } returns null
        givenPendingNote()
        val webdav = webdav()
        every { webdav.put(any(), any(), any(), any()) } returns "\"server-v1\""

        uploader().uploadAll(webdav, serverUrl)

        // Erst-Upload lauter neuer Notizen darf keinen einzigen Request extra kosten.
        verify(exactly = 0) { webdav.listOrNull(any()) }
    }

    @Test fun `a blank cached E-Tag does not become an empty If-Match`() = runTest {
        every { prefs.getString("etag_json_$noteId", null) } returns ""
        givenPendingNote()
        val ifMatch = slot<String?>()
        val webdav = webdav {
            every { put(any(), any(), any(), captureNullable(ifMatch)) } returns "\"server-v2\""
        }

        val result = uploader().uploadAll(webdav, serverUrl)

        assertEquals(1, result.uploadedCount)
        assertNull("an empty E-Tag would produce If-Match: \"\" and be rejected", ifMatch.captured)
    }

    @Test fun `a note without a cached E-Tag uploads without precondition`() = runTest {
        every { prefs.getString("etag_json_$noteId", null) } returns null
        givenPendingNote()
        val ifMatch = slot<String?>()
        val webdav = webdav {
            every { put(any(), any(), any(), captureNullable(ifMatch)) } returns "\"server-v1\""
        }

        val result = uploader().uploadAll(webdav, serverUrl)

        assertEquals(1, result.uploadedCount)
        assertNull("a first upload has nothing to match against", ifMatch.captured)
    }
}
