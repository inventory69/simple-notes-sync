package dev.dettmer.simplenotes.utils

import android.content.Context
import android.content.SharedPreferences
import dev.dettmer.simplenotes.models.ChecklistItem
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.storage.NotesStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Tests for NoteCorruptionRepair.repairIfNeeded.
 *
 * Since Note.fromJson (FIX-03, v2.2.0) already cleans corrupted checklist titles
 * in-memory on every load, the repair function sees clean titles at runtime and is
 * now effectively a fast no-op that only sets the done-flag. These tests verify the
 * flag-gating behaviour and the safety of the repair loop on various inputs.
 * The actual title-rescue logic is covered in NoteTest (fromJson FIX-03 tests).
 */
class NoteCorruptionRepairTest {
    private lateinit var tmpDir: File
    private lateinit var storage: NotesStorage
    private lateinit var prefs: SharedPreferences
    private lateinit var prefsEditor: SharedPreferences.Editor

    @Before fun setUp() {
        tmpDir = Files.createTempDirectory("corruption-repair-test").toFile()
        val sharedPrefs = mockk<SharedPreferences>(relaxed = true) {
            every { getString("device_id", null) } returns "test-device-id"
        }
        val context = mockk<Context> {
            every { filesDir } returns tmpDir
            every { getSharedPreferences(any(), any()) } returns sharedPrefs
        }
        storage = NotesStorage(context)

        prefsEditor = mockk(relaxed = true)
        prefs = mockk {
            every { getBoolean(any(), false) } returns false
            every { edit() } returns prefsEditor
        }
    }

    @After fun tearDown() {
        tmpDir.deleteRecursively()
    }

    // ─── Flag gating ──────────────────────────────────────────────────────────

    @Test fun `skips all work when repair flag is already set`() = runBlocking {
        every { prefs.getBoolean(any(), false) } returns true
        storage.saveNote(Note(id = "n1", title = "Title", content = "", deviceId = "dev"))

        NoteCorruptionRepair.repairIfNeeded(storage, prefs)

        verify(exactly = 0) { prefs.edit() }
    }

    @Test fun `sets the repair flag after completing`() = runBlocking {
        NoteCorruptionRepair.repairIfNeeded(storage, prefs)

        verify(exactly = 1) { prefs.edit() }
    }

    @Test fun `sets repair flag even when storage is empty`() = runBlocking {
        NoteCorruptionRepair.repairIfNeeded(storage, prefs)
        verify(exactly = 1) { prefs.edit() }
    }

    // ─── Note selection ───────────────────────────────────────────────────────

    @Test fun `text notes with checklist-like title are not repaired`() = runBlocking {
        // TEXT notes must never be touched regardless of title content
        val n = Note(
            id = "txt-skip",
            title = "Task- [ ] Something",
            content = "body",
            deviceId = "dev",
            noteType = NoteType.TEXT
        )
        storage.saveNote(n)
        NoteCorruptionRepair.repairIfNeeded(storage, prefs)

        val loaded = storage.loadNote("txt-skip")!!
        assertEquals(NoteType.TEXT, loaded.noteType)
        // Title is unchanged — repair only touches CHECKLIST notes
        assertEquals("Task- [ ] Something", loaded.title)
    }

    @Test fun `clean checklist note title is unchanged after repair`() = runBlocking {
        val n = Note(
            id = "cl-clean",
            title = "Groceries",
            content = "",
            deviceId = "dev",
            noteType = NoteType.CHECKLIST,
            checklistItems = listOf(ChecklistItem("i1", "Milk", false, 0))
        )
        storage.saveNote(n)
        NoteCorruptionRepair.repairIfNeeded(storage, prefs)

        assertEquals("Groceries", storage.loadNote("cl-clean")!!.title)
    }

    // ─── fromJson already applies FIX-03 ─────────────────────────────────────
    //
    // NoteCorruptionRepair loads notes via storage.loadAllNotes() → Note.fromJson(),
    // which applies FIX-03 in-memory. As a result the repair loop sees clean titles
    // and nothing gets re-saved. The item-rescue logic is verified in NoteTest
    // (`fromJson FIX-03 strips checklist pattern from title and rescues items`).

    @Test fun `corrupted title is clean in-memory even without repair re-saving`() = runBlocking {
        // Simulate a note whose on-disk JSON has a corrupted title (saved before FIX-03).
        // Write raw JSON directly to the notes directory to bypass the Note class.
        val notesDir = File(tmpDir, "notes").also { it.mkdirs() }
        File(notesDir, "corrupt.json").writeText(
            """{"id":"corrupt","title":"List- [ ] Milk- [x] Eggs","content":"","createdAt":1700000000000,""" +
                """"updatedAt":1700001000000,"deviceId":"dev","noteType":"CHECKLIST","checklistItems":[]}"""
        )

        NoteCorruptionRepair.repairIfNeeded(storage, prefs)

        // fromJson cleans the title on every load — note appears correct in memory
        val loaded = storage.loadNote("corrupt")
        assertNotNull(loaded)
        assertEquals("List", loaded!!.title)
        assertEquals(2, loaded.checklistItems!!.size)
        assertEquals("Milk", loaded.checklistItems!![0].text)
        assertEquals("Eggs", loaded.checklistItems!![1].text)
    }
}
