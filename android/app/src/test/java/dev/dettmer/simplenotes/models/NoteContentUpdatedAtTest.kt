package dev.dettmer.simplenotes.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 🆕 v2.21.0: Inhaltszeit getrennt von der Sync-Uhr `updatedAt`. */
class NoteContentUpdatedAtTest {
    private val text = Note(title = "T", content = "Body", updatedAt = 1_000L, deviceId = "d")
    private val checklist = Note(
        title = "List",
        content = "[ ] a",
        updatedAt = 1_000L,
        deviceId = "d",
        noteType = NoteType.CHECKLIST,
        checklistItems = listOf(ChecklistItem(id = "1", text = "a", order = 0), ChecklistItem(id = "2", text = "b", order = 1))
    )

    @Test fun `round trip keeps contentUpdatedAt`() {
        val note = text.copy(updatedAt = 5_000L, contentUpdatedAt = 2_000L)

        val parsed = Note.fromJson(note.toJson())!!

        assertEquals(2_000L, parsed.contentUpdatedAt)
        assertEquals(5_000L, parsed.updatedAt)
    }

    @Test fun `old json without the field falls back to updatedAt`() {
        val json = """{"id":"x","title":"T","content":"c","createdAt":1,"updatedAt":7000,"deviceId":"d"}"""

        assertEquals(7_000L, Note.fromJson(json)!!.editedAt)
    }

    @Test fun `existing note keeps its time through copy`() {
        val moved = text.copy(folderName = "Work", isPinned = true, color = "#FF0000", updatedAt = 9_000L)

        assertEquals(1_000L, moved.editedAt)
    }

    @Test fun `metadata only save keeps the content time`() {
        val recolored = text.copy(color = "#00FF00", updatedAt = 9_000L).withContentTouched(text)

        assertEquals(1_000L, recolored.editedAt)
    }

    @Test fun `text change moves the content time`() {
        val edited = text.copy(content = "Body 2", updatedAt = 9_000L).withContentTouched(text)

        assertEquals(9_000L, edited.editedAt)
    }

    @Test fun `checklist compares items, not the fallback content`() {
        // Der Editor speichert Checklisten mit content = "", gespeichert steht der Fallback-Text
        val sameItems = checklist.copy(content = "", color = "#00FF00", updatedAt = 9_000L)
        assertFalse(sameItems.contentDiffers(checklist))
        assertEquals(1_000L, sameItems.withContentTouched(checklist).editedAt)

        val items = checklist.checklistItems!!
        val ticked = checklist.copy(checklistItems = listOf(items[0].copy(isChecked = true), items[1]))
        assertTrue(ticked.contentDiffers(checklist))

        val reordered = checklist.copy(checklistItems = listOf(items[1].copy(order = 0), items[0].copy(order = 1)))
        assertTrue(reordered.contentDiffers(checklist))
    }

    @Test fun `new note takes its own updatedAt`() {
        assertEquals(1_000L, text.withContentTouched(null).editedAt)
    }
}
