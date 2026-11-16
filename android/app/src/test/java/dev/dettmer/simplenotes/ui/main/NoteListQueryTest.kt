package dev.dettmer.simplenotes.ui.main

import dev.dettmer.simplenotes.models.ChecklistItem
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteFilter
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.models.SortDirection
import dev.dettmer.simplenotes.models.SortOption
import dev.dettmer.simplenotes.ui.theme.NoteColorPalette
import org.junit.Assert.assertEquals
import org.junit.Test

/** Die Listen-Pipeline aus [NoteListQuery.kt]: filtern, suchen, sortieren. */
class NoteListQueryTest {
    private fun note(
        id: String,
        title: String = id,
        type: NoteType = NoteType.TEXT,
        color: String? = null,
        archivedAt: Long? = null,
        updatedAt: Long = 0L,
        items: List<ChecklistItem>? = null
    ) = Note(
        id = id,
        title = title,
        content = "",
        deviceId = "dev",
        noteType = type,
        color = color,
        archivedAt = archivedAt,
        updatedAt = updatedAt,
        checklistItems = items
    )

    @Test fun `filter keeps archive view, type and color apart`() {
        val red = NoteColorPalette.slots.first().hex
        val notes = listOf(
            note("text", color = red),
            note("list", type = NoteType.CHECKLIST, color = red),
            note("plain"),
            note("archived", archivedAt = 1L, color = red)
        )

        assertEquals(listOf("text", "list", "plain"), filterNotes(notes, NoteFilter.ALL).map { it.id })
        assertEquals(listOf("archived"), filterNotes(notes, NoteFilter.ALL, showArchived = true).map { it.id })
        assertEquals(listOf("list"), filterNotes(notes, NoteFilter.CHECKLIST_ONLY).map { it.id })
        assertEquals(listOf("text"), filterNotes(notes, NoteFilter.TEXT_ONLY, colorFilter = red).map { it.id })
    }

    @Test fun `search matches title and checklist items, ignoring case`() {
        val notes = listOf(
            note("a", title = "Einkauf"),
            note("b", title = "Liste", type = NoteType.CHECKLIST, items = listOf(ChecklistItem(text = "Milch"))),
            note("c", title = "Sonstiges")
        )

        assertEquals(listOf("a"), searchNotes(notes, "  einKAUF ").map { it.id })
        assertEquals(listOf("b"), searchNotes(notes, "milch").map { it.id })
        assertEquals(notes, searchNotes(notes, " "))
    }

    @Test fun `sort by date follows the direction, color ignores it`() {
        val red = NoteColorPalette.slots[0].hex
        val second = NoteColorPalette.slots[1].hex
        val notes = listOf(
            note("old", updatedAt = 1L, color = second),
            note("new", updatedAt = 3L),
            note("mid", updatedAt = 2L, color = red)
        )

        assertEquals(
            listOf("new", "mid", "old"),
            sortNotes(notes, SortOption.UPDATED_AT, SortDirection.DESCENDING).map { it.id }
        )
        assertEquals(
            listOf("old", "mid", "new"),
            sortNotes(notes, SortOption.UPDATED_AT, SortDirection.ASCENDING).map { it.id }
        )
        // Palettenfolge, ohne Farbe am Ende, egal welche Richtung (#131)
        assertEquals(
            listOf("mid", "old", "new"),
            sortNotes(notes, SortOption.COLOR, SortDirection.DESCENDING).map { it.id }
        )
    }
}
