package dev.dettmer.simplenotes.sync

import android.content.Context
import android.content.SharedPreferences
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.webdav.WebDavClient
import dev.dettmer.simplenotes.utils.Constants
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 🆕 v2.16.0: Die beiden Wege aus einem Konflikt heraus.
 *
 * Wichtig ist nicht nur der Status danach, sondern was mit den Caches passiert: "Meine
 * behalten" muss den gecachten ETag **und** den Content-Hash löschen — sonst liefe der
 * nächste Upload wieder in das `If-Match`-412 (ETag) oder würde als unverändert übersprungen
 * (Hash), und die Notiz bliebe genauso hängen wie vorher.
 */
class SyncConflictResolverTest {
    private lateinit var tmpDir: File
    private lateinit var prefs: SharedPreferences
    private lateinit var storage: NotesStorage
    private lateinit var context: Context
    private val removedKeys = mutableListOf<String>()
    private val putStrings = mutableMapOf<String, String?>()

    private val noteId = "f4e6b1c8-2a37-4d90-8b15-7c9e0a2d4f61"

    @Before fun setUp() {
        tmpDir = Files.createTempDirectory("conflict-resolver").toFile()
        removedKeys.clear()
        putStrings.clear()
        prefs = mockk(relaxed = true)
        every { prefs.getString(Constants.KEY_SERVER_URL, any()) } returns "http://server:8080"
        every { prefs.getString(Constants.KEY_SYNC_FOLDER_NAME, any()) } returns "notes"
        every { prefs.getString("etag_json_$noteId", null) } returns "\"server-v1\""
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val removed = slot<String>()
        val putKey = slot<String>()
        val putValue = slot<String?>()
        every { prefs.edit() } returns editor
        every { editor.remove(capture(removed)) } answers { removedKeys.add(removed.captured); editor }
        every { editor.putString(capture(putKey), captureNullable(putValue)) } answers {
            putStrings[putKey.captured] = putValue.captured
            editor
        }
        context = mockk {
            every { filesDir } returns tmpDir
            every { getSharedPreferences(any(), any()) } returns prefs
        }
        storage = NotesStorage(context)
    }

    @After fun tearDown() {
        tmpDir.deleteRecursively()
    }

    private suspend fun givenConflictedNote() = storage.saveNote(
        Note(
            id = noteId,
            title = "Liste",
            content = "LOCAL",
            deviceId = "dev",
            updatedAt = 5_000_000L,
            syncStatus = SyncStatus.CONFLICT
        )
    )

    private fun resolver(webdav: WebDavClient? = null) = SyncConflictResolver(
        context = context,
        storage = storage,
        ioDispatcher = Dispatchers.Unconfined,
        prefsOverride = prefs,
        webdavProvider = { webdav }
    )

    @Test fun `keepLocal marks the note PENDING and drops E-Tag and content hash`() = runTest {
        givenConflictedNote()

        assertTrue(resolver().keepLocal(noteId))

        val note = storage.loadNote(noteId)!!
        assertEquals(SyncStatus.PENDING, note.syncStatus)
        assertEquals("local content must be untouched", "LOCAL", note.content)
        assertTrue("content hash must go, else the upload skips the note", "content_hash_$noteId" in removedKeys)
        // Der ETag wird über ETagCache.batchUpdate(null) invalidiert — als remove oder als
        // putString(null), je nach Implementierung. Beides erfüllt den Zweck.
        val etagKey = "etag_json_$noteId"
        assertTrue(
            "cached E-Tag must be invalidated, else the next PUT hits 412 again",
            etagKey in removedKeys || putStrings[etagKey] == null && etagKey in putStrings.keys
        )
    }

    @Test fun `useServer replaces the local content with the server version`() = runTest {
        givenConflictedNote()
        val serverJson = Note(
            id = noteId,
            title = "Liste",
            content = "SERVER",
            deviceId = "other",
            updatedAt = 6_000_000L,
            syncStatus = SyncStatus.SYNCED
        ).toJson()
        val webdav = mockk<WebDavClient>(relaxed = true)
        every { webdav.get(any<String>()) } answers { ByteArrayInputStream(serverJson.toByteArray()) }

        assertTrue(resolver(webdav).useServer(noteId))

        val note = storage.loadNote(noteId)!!
        assertEquals("SERVER", note.content)
        assertEquals(SyncStatus.SYNCED, note.syncStatus)
    }

    @Test fun `useServer keeps the local version when the server cannot be reached`() = runTest {
        givenConflictedNote()
        val webdav = mockk<WebDavClient>(relaxed = true)
        every { webdav.get(any<String>()) } throws IOException("offline")

        assertFalse(resolver(webdav).useServer(noteId))

        val note = storage.loadNote(noteId)!!
        assertEquals("nothing may be lost on a failed fetch", "LOCAL", note.content)
        assertEquals(SyncStatus.CONFLICT, note.syncStatus)
    }

    @Test fun `fetchServerVersion returns the server copy without touching the local one`() = runTest {
        givenConflictedNote()
        val serverJson = Note(
            id = noteId,
            title = "Liste",
            content = "SERVER",
            deviceId = "other",
            updatedAt = 6_000_000L,
            syncStatus = SyncStatus.SYNCED
        ).toJson()
        val webdav = mockk<WebDavClient>(relaxed = true)
        every { webdav.get(any<String>()) } answers { ByteArrayInputStream(serverJson.toByteArray()) }

        val remote = resolver(webdav).fetchServerVersion(noteId)

        assertEquals("SERVER", remote!!.content)
        assertEquals(6_000_000L, remote.updatedAt)
        // Die Vorschau darf nichts schreiben — sonst wäre "Vergleichen" bereits die Entscheidung.
        assertEquals("LOCAL", storage.loadNote(noteId)!!.content)
        assertEquals(SyncStatus.CONFLICT, storage.loadNote(noteId)!!.syncStatus)
    }

    @Test fun `fetchServerVersion returns null when the server cannot be reached`() = runTest {
        givenConflictedNote()
        val webdav = mockk<WebDavClient>(relaxed = true)
        every { webdav.get(any<String>()) } throws IOException("offline")

        assertNull(resolver(webdav).fetchServerVersion(noteId))
    }

    @Test fun `resolving a note that no longer exists fails cleanly`() = runTest {
        assertFalse(resolver().keepLocal("missing"))
        assertFalse(resolver(mockk(relaxed = true)).useServer("missing"))
    }
}
