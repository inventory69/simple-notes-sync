package dev.dettmer.simplenotes.storage

import android.content.Context
import android.content.SharedPreferences
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.models.SyncStatus
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotesStorageTest {

    private lateinit var tmpDir: File
    private lateinit var storage: NotesStorage

    @Before fun setUp() {
        tmpDir = Files.createTempDirectory("notes-storage-test").toFile()
        val sharedPrefs = mockk<SharedPreferences>(relaxed = true) {
            every { getString("device_id", null) } returns "test-device-id"
        }
        val context = mockk<Context> {
            every { filesDir } returns tmpDir
            every { getSharedPreferences(any(), any()) } returns sharedPrefs
        }
        storage = NotesStorage(context)
    }

    @After fun tearDown() { tmpDir.deleteRecursively() }

    private fun note(
        id: String = "note-1",
        title: String = "Title",
        content: String = "Body",
    ) = Note(id = id, title = title, content = content, deviceId = "dev")

    // ─── save / load ──────────────────────────────────────────────────────────

    @Test fun `saveNote persists and loadNote returns it`() = runBlocking {
        val n = note()
        storage.saveNote(n)
        val loaded = storage.loadNote(n.id)
        assertNotNull(loaded)
        assertEquals(n.id, loaded!!.id)
        assertEquals(n.title, loaded.title)
    }

    @Test fun `loadNote returns null for unknown id`() = runBlocking {
        assertNull(storage.loadNote("does-not-exist"))
    }

    @Test fun `loadAllNotes returns all saved notes`() = runBlocking {
        storage.saveNote(note("a"))
        storage.saveNote(note("b"))
        storage.saveNote(note("c"))
        val all = storage.loadAllNotes()
        assertEquals(3, all.size)
        assertTrue(all.map { it.id }.containsAll(listOf("a", "b", "c")))
    }

    // ─── cache ────────────────────────────────────────────────────────────────

    @Test fun `second loadAllNotes within TTL returns cached result`() = runBlocking {
        storage.saveNote(note("x"))
        val first = storage.loadAllNotes()
        assertEquals(1, first.size)

        // Write a second note directly to disk without going through storage (to bypass cache invalidation)
        val notesDir = File(tmpDir, "notes")
        val extra = note("y")
        File(notesDir, "${extra.id}.json").writeText(extra.toJson())

        // Cache should still return 1 note
        val cached = storage.loadAllNotes()
        assertEquals(1, cached.size)
    }

    @Test fun `forceReload bypasses cache and returns fresh data`() = runBlocking {
        storage.saveNote(note("x"))
        storage.loadAllNotes() // populate cache

        val notesDir = File(tmpDir, "notes")
        val extra = note("y")
        File(notesDir, "${extra.id}.json").writeText(extra.toJson())

        val fresh = storage.loadAllNotes(forceReload = true)
        assertEquals(2, fresh.size)
    }

    @Test fun `saveNote invalidates cache`() = runBlocking {
        storage.saveNote(note("x"))
        val first = storage.loadAllNotes()
        assertEquals(1, first.size)

        storage.saveNote(note("y"))

        val second = storage.loadAllNotes()
        assertEquals(2, second.size)
    }

    // ─── delete ───────────────────────────────────────────────────────────────

    @Test fun `deleteNote removes note from storage`() = runBlocking {
        storage.saveNote(note("del"))
        val deleted = storage.deleteNote("del")
        assertTrue(deleted)
        assertNull(storage.loadNote("del"))
    }

    @Test fun `deleteNote returns false for non-existent note`() = runBlocking {
        val result = storage.deleteNote("ghost")
        assertFalse(result)
    }

    @Test fun `deleteNote invalidates cache`() = runBlocking {
        storage.saveNote(note("rm"))
        storage.loadAllNotes() // populate cache
        storage.deleteNote("rm")
        val all = storage.loadAllNotes()
        assertTrue(all.none { it.id == "rm" })
    }

    // ─── moveNote ─────────────────────────────────────────────────────────────

    @Test fun `moveNote updates folderName and marks PENDING`() = runBlocking {
        val n = note("mv").copy(syncStatus = SyncStatus.SYNCED)
        storage.saveNote(n)
        storage.moveNote("mv", "Work")
        val moved = storage.loadNote("mv")
        assertNotNull(moved)
        assertEquals("Work", moved!!.folderName)
        assertEquals(SyncStatus.PENDING, moved.syncStatus)
    }

    @Test fun `moveNote to same folder is no-op`() = runBlocking {
        val n = note("same").copy(syncStatus = SyncStatus.SYNCED)
        storage.saveNote(n.copy(folderName = "A", updatedAt = 1000L))
        val before = storage.loadNote("same")!!.updatedAt
        storage.moveNote("same", "A")
        val after = storage.loadNote("same")!!.updatedAt
        assertEquals(before, after)
    }

    @Test fun `moveNote for unknown id is no-op`() = runBlocking {
        storage.moveNote("no-such-note", "Folder")
        // Should not throw
    }

    @Test fun `moveNote to null moves back to root`() = runBlocking {
        val n = note("root").copy(folderName = "Projects")
        storage.saveNote(n)
        storage.moveNote("root", null)
        assertNull(storage.loadNote("root")!!.folderName)
    }

    // ─── checklist note ───────────────────────────────────────────────────────

    @Test fun `save and load preserves checklist note type`() = runBlocking {
        val n = Note(
            id = "cl-1",
            title = "Shopping",
            content = "",
            deviceId = "dev",
            noteType = NoteType.CHECKLIST,
        )
        storage.saveNote(n)
        val loaded = storage.loadNote("cl-1")
        assertNotNull(loaded)
        assertEquals(NoteType.CHECKLIST, loaded!!.noteType)
    }
}
