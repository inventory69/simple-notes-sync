package dev.dettmer.simplenotes.models

import dev.dettmer.simplenotes.models.NoteSize.Companion.SMALL_LINE_THRESHOLD
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 🎨 v1.7.0: Tests for Note Size Classification (Staggered Grid Layout)
 * 🔧 v2.11.0: Updated for the line-based estimator (estimateDisplayLines, 32 chars/line,
 * SMALL_LINE_THRESHOLD=3) that replaced the old char-count/item-count thresholds.
 */
class NoteSizeTest {
    @Test
    fun `text note with short single line is SMALL`() {
        val note = Note(
            id = "test1",
            title = "Test",
            content = "Short content", // 13 chars -> 1 estimated line
            deviceId = "test-device",
            noteType = NoteType.TEXT
        )

        assertEquals(NoteSize.SMALL, note.getSize())
    }

    @Test
    fun `text note at exactly the line threshold is SMALL`() {
        val content = "x".repeat(79) // ceil(79/32) = 3 estimated lines = threshold
        val note = Note(
            id = "test2",
            title = "Test",
            content = content,
            deviceId = "test-device",
            noteType = NoteType.TEXT
        )

        assertEquals(NoteSize.SMALL, note.getSize())
    }

    @Test
    fun `text note just above the line threshold is LARGE`() {
        val content = "x".repeat(97) // ceil(97/32) = 4 estimated lines > threshold
        val note = Note(
            id = "test3",
            title = "Test",
            content = content,
            deviceId = "test-device",
            noteType = NoteType.TEXT
        )

        assertEquals(NoteSize.LARGE, note.getSize())
    }

    @Test
    fun `text note with many long lines is LARGE`() {
        val content = "This is a long note with more than 80 characters. " +
            "It should be classified as LARGE for grid layout display."
        val note = Note(
            id = "test4",
            title = "Test",
            content = content,
            deviceId = "test-device",
            noteType = NoteType.TEXT
        )

        assertEquals(NoteSize.LARGE, note.getSize())
    }

    @Test
    fun `checklist with 1 short item is SMALL`() {
        val note = Note(
            id = "test5",
            title = "Shopping",
            content = "",
            deviceId = "test-device",
            noteType = NoteType.CHECKLIST,
            checklistItems = listOf(
                ChecklistItem("id1", "Milk", false)
            )
        )

        assertEquals(NoteSize.SMALL, note.getSize())
    }

    @Test
    fun `checklist with 3 short items is SMALL`() {
        val note = Note(
            id = "test6",
            title = "Shopping",
            content = "",
            deviceId = "test-device",
            noteType = NoteType.CHECKLIST,
            checklistItems = listOf(
                ChecklistItem("id1", "Milk", false),
                ChecklistItem("id2", "Bread", false),
                ChecklistItem("id3", "Eggs", false)
            )
        )

        assertEquals(NoteSize.SMALL, note.getSize())
    }

    @Test
    fun `checklist with 4 short items is LARGE`() {
        val note = Note(
            id = "test7",
            title = "Shopping",
            content = "",
            deviceId = "test-device",
            noteType = NoteType.CHECKLIST,
            checklistItems = listOf(
                ChecklistItem("id1", "Milk", false),
                ChecklistItem("id2", "Bread", false),
                ChecklistItem("id3", "Eggs", false),
                ChecklistItem("id4", "Butter", false) // 4th line -> LARGE
            )
        )

        assertEquals(NoteSize.LARGE, note.getSize())
    }

    @Test
    fun `checklist with one long item is LARGE`() {
        // A single item can push past the threshold on its own via line-wrapping.
        val note = Note(
            id = "test8",
            title = "Long List",
            content = "",
            deviceId = "test-device",
            noteType = NoteType.CHECKLIST,
            checklistItems = listOf(ChecklistItem("id1", "x".repeat(97), false))
        )

        assertEquals(NoteSize.LARGE, note.getSize())
    }

    @Test
    fun `empty checklist is SMALL`() {
        val note = Note(
            id = "test9",
            title = "Empty",
            content = "",
            deviceId = "test-device",
            noteType = NoteType.CHECKLIST,
            checklistItems = emptyList()
        )

        assertEquals(NoteSize.SMALL, note.getSize())
    }

    @Test
    fun `checklist with null items is SMALL`() {
        val note = Note(
            id = "test10",
            title = "Null Items",
            content = "",
            deviceId = "test-device",
            noteType = NoteType.CHECKLIST,
            checklistItems = null
        )

        assertEquals(NoteSize.SMALL, note.getSize())
    }

    @Test
    fun `line threshold constant is 3`() {
        assertEquals(3, SMALL_LINE_THRESHOLD)
    }
}
