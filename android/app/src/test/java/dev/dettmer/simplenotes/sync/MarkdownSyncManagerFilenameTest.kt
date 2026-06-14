package dev.dettmer.simplenotes.sync

import android.content.SharedPreferences
import com.thegrizzlylabs.sardineandroid.Sardine
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.storage.NotesStorage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MarkdownSyncManagerFilenameTest {
    private lateinit var manager: MarkdownSyncManager

    @Before fun setUp() {
        manager = MarkdownSyncManager(
            prefs = mockk<SharedPreferences>(relaxed = true),
            storage = mockk<NotesStorage>(relaxed = true),
            eTagCache = mockk(relaxed = true),
            urlBuilder = mockk(relaxed = true),
            connectionManager = mockk(relaxed = true),
            timestampManager = mockk(relaxed = true),
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    // ─── sanitizeFilename ─────────────────────────────────────────────────────

    @Test fun `sanitizeFilename replaces illegal chars with underscore`() {
        val illegal = listOf('<', '>', ':', '"', '/', '\\', '|', '?', '*')
        for (ch in illegal) {
            val result = manager.sanitizeFilename("title${ch}name")
            assertFalse("Expected no '$ch' in sanitized name", result.contains(ch))
            assertTrue("Expected '_' as replacement", result.contains('_'))
        }
    }

    @Test fun `sanitizeFilename collapses multiple spaces`() {
        val result = manager.sanitizeFilename("My   Note   Title")
        assertEquals("My Note Title", result)
    }

    @Test fun `sanitizeFilename trims leading and trailing underscores and spaces`() {
        val result = manager.sanitizeFilename("  /Note Title/  ")
        assertFalse(result.startsWith("_") || result.startsWith(" "))
        assertFalse(result.endsWith("_") || result.endsWith(" "))
    }

    @Test fun `sanitizeFilename passes through normal alphanumeric title`() {
        assertEquals("My Shopping List", manager.sanitizeFilename("My Shopping List"))
    }

    @Test fun `sanitizeFilename truncates to 200 chars`() {
        val long = "A".repeat(250)
        val result = manager.sanitizeFilename(long)
        assertTrue(result.length <= 200)
    }

    @Test fun `sanitizeFilename handles empty string`() {
        val result = manager.sanitizeFilename("")
        assertEquals("", result)
    }

    // ─── getUniqueFilename ────────────────────────────────────────────────────

    @Test fun `getUniqueFilename returns base name on first use`() {
        val note = Note(id = "abc12345-0000-0000-0000-000000000000", title = "Groceries", content = "", deviceId = "dev")
        val used = mutableSetOf<String>()
        val result = manager.getUniqueFilename(note, used)
        assertEquals("Groceries", result)
        assertTrue(used.contains("Groceries"))
    }

    @Test fun `getUniqueFilename appends short id on duplicate title`() {
        val note = Note(id = "abc12345-0000-0000-0000-000000000000", title = "Groceries", content = "", deviceId = "dev")
        val used = mutableSetOf("Groceries")
        val result = manager.getUniqueFilename(note, used)
        assertEquals("Groceries_abc12345", result)
    }

    @Test fun `getUniqueFilename adds deduplicated name to used set`() {
        val note = Note(id = "abc12345-0000-0000-0000-000000000000", title = "Note", content = "", deviceId = "dev")
        val used = mutableSetOf("Note")
        manager.getUniqueFilename(note, used)
        assertTrue(used.contains("Note_abc12345"))
    }

    @Test fun `getUniqueFilename two notes same title get different filenames`() {
        val note1 = Note(id = "aaaa1111-0000-0000-0000-000000000000", title = "Daily", content = "", deviceId = "dev")
        val note2 = Note(id = "bbbb2222-0000-0000-0000-000000000000", title = "Daily", content = "", deviceId = "dev")
        val used = mutableSetOf<String>()
        val name1 = manager.getUniqueFilename(note1, used)
        val name2 = manager.getUniqueFilename(note2, used)
        assertEquals("Daily", name1)
        assertEquals("Daily_bbbb2222", name2)
    }

    // ─── v2.9.0 (Trash): deleteSingle ──────────────────────────────────────────

    private fun trashNote() = Note(
        id = "trash-1",
        title = "Gone",
        content = "x",
        deviceId = "dev",
        trashedAt = 123L
    )

    @Test fun `deleteSingle tolerates 404 (already gone)`() {
        val sardine = mockk<Sardine>(relaxed = true)
        every { sardine.delete(any()) } throws IOException("HTTP 404 Not Found")
        // Must not throw — parallel purge on two devices is safe.
        manager.deleteSingle(sardine, "http://server", trashNote())
        verify { sardine.delete(any()) }
    }

    @Test fun `deleteSingle rethrows non-404 errors`() {
        val sardine = mockk<Sardine>(relaxed = true)
        every { sardine.delete(any()) } throws IOException("HTTP 500 Server Error")
        assertThrows(IOException::class.java) {
            manager.deleteSingle(sardine, "http://server", trashNote())
        }
    }
}
