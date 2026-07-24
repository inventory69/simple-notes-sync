package dev.dettmer.simplenotes.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-Tests für die TEXT → CHECKLIST-Konversion.
 *
 * Deckungsgleich mit MarkdownEngineTaskListTest: was die Preview als Checkbox rendert,
 * muss beim Umschalten des Notiztyps auch als Checklist-Item ankommen.
 */
class NormalizeLineToChecklistItemTest {
    private fun item(line: String) = normalizeLineToChecklistItem(line, 0)

    @Test
    fun `gfm dash task keeps text and checked state`() {
        assertEquals("Task", item("- [x] Task").text)
        assertTrue(item("- [x] Task").isChecked)
        assertEquals("Task", item("- [ ] Task").text)
        assertFalse(item("- [ ] Task").isChecked)
    }

    @Test
    fun `uppercase mark counts as checked`() {
        assertTrue(item("- [X] Task").isChecked)
    }

    @Test
    fun `asterisk and plus markers are accepted`() {
        assertEquals("Star", item("* [ ] Star").text)
        assertFalse(item("* [ ] Star").isChecked)
        assertEquals("Plus", item("+ [x] Plus").text)
        assertTrue(item("+ [x] Plus").isChecked)
    }

    @Test
    fun `empty brackets are unchecked, not checked`() {
        // Der eigentliche Fallstrick: mit "!= \" \"" statt "== \"x\"" wäre das abgehakt.
        val result = item("- [] Typo")
        assertEquals("Typo", result.text)
        assertFalse("empty brackets must be unchecked", result.isChecked)
    }

    @Test
    fun `bare checkbox without marker still works`() {
        assertTrue(item("[x] Bare").isChecked)
        assertEquals("Bare", item("[x] Bare").text)
        assertFalse(item("[] Bare").isChecked)
    }

    @Test
    fun `plain bullet loses only the marker`() {
        assertEquals("Plain", item("- Plain").text)
        assertFalse(item("- Plain").isChecked)
    }

    @Test
    fun `checkmark glyph counts as checked`() {
        assertEquals("Done", item("✓ Done").text)
        assertTrue(item("✓ Done").isChecked)
    }

    @Test
    fun `plain text without any marker is kept verbatim`() {
        assertEquals("Just text", item("Just text").text)
        assertFalse(item("Just text").isChecked)
    }
}
