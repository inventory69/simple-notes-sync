package dev.dettmer.simplenotes.ui.editor

import org.junit.Assert.*
import org.junit.Test

/**
 * 🆕 v1.8.0 (IMPL_017): Unit Tests für Checklisten-Sortierung
 *
 * Validiert die Auto-Sort Funktionalität:
 * - Unchecked items erscheinen vor checked items
 * - Relative Reihenfolge innerhalb jeder Gruppe bleibt erhalten (stabile Sortierung)
 * - Order-Werte werden korrekt neu zugewiesen
 */
class ChecklistSortingTest {

    /**
     * Helper function to create a test ChecklistItemState
     */
    private fun item(id: String, checked: Boolean, order: Int): ChecklistItemState {
        return ChecklistItemState(
            id = id,
            text = "Item $id",
            isChecked = checked,
            order = order
        )
    }

    /**
     * Simulates the sortChecklistItems() function from NoteEditorViewModel
     * (Since it's private, we test the logic here)
     */
    private fun sortChecklistItems(items: List<ChecklistItemState>): List<ChecklistItemState> {
        val unchecked = items.filter { !it.isChecked }
        val checked = items.filter { it.isChecked }

        return (unchecked + checked).mapIndexed { index, item ->
            item.copy(order = index)
        }
    }

    @Test
    fun `unchecked items appear before checked items`() {
        val items = listOf(
            item("a", checked = true,  order = 0),
            item("b", checked = false, order = 1),
            item("c", checked = true,  order = 2),
            item("d", checked = false, order = 3)
        )

        val sorted = sortChecklistItems(items)

        assertFalse("First item should be unchecked", sorted[0].isChecked)  // b
        assertFalse("Second item should be unchecked", sorted[1].isChecked) // d
        assertTrue("Third item should be checked", sorted[2].isChecked)    // a
        assertTrue("Fourth item should be checked", sorted[3].isChecked)   // c
    }

    @Test
    fun `relative order within groups is preserved (stable sort)`() {
        val items = listOf(
            item("first-checked",   checked = true,  order = 0),
            item("first-unchecked", checked = false, order = 1),
            item("second-checked",  checked = true,  order = 2),
            item("second-unchecked",checked = false, order = 3)
        )

        val sorted = sortChecklistItems(items)

        assertEquals("first-unchecked",  sorted[0].id)
        assertEquals("second-unchecked", sorted[1].id)
        assertEquals("first-checked",    sorted[2].id)
        assertEquals("second-checked",   sorted[3].id)
    }

    @Test
    fun `all unchecked - no change needed`() {
        val items = listOf(
            item("a", checked = false, order = 0),
            item("b", checked = false, order = 1)
        )

        val sorted = sortChecklistItems(items)

        assertEquals("a", sorted[0].id)
        assertEquals("b", sorted[1].id)
    }

    @Test
    fun `all checked - no change needed`() {
        val items = listOf(
            item("a", checked = true, order = 0),
            item("b", checked = true, order = 1)
        )

        val sorted = sortChecklistItems(items)

        assertEquals("a", sorted[0].id)
        assertEquals("b", sorted[1].id)
    }

    @Test
    fun `order values are reassigned after sort`() {
        val items = listOf(
            item("a", checked = true,  order = 0),
            item("b", checked = false, order = 1)
        )

        val sorted = sortChecklistItems(items)

        assertEquals(0, sorted[0].order)  // b → order 0
        assertEquals(1, sorted[1].order)  // a → order 1
    }

    @Test
    fun `empty list returns empty list`() {
        val items = emptyList<ChecklistItemState>()
        val sorted = sortChecklistItems(items)
        assertTrue("Empty list should remain empty", sorted.isEmpty())
    }

    @Test
    fun `single item list returns unchanged`() {
        val items = listOf(item("a", checked = false, order = 0))
        val sorted = sortChecklistItems(items)

        assertEquals(1, sorted.size)
        assertEquals("a", sorted[0].id)
        assertEquals(0, sorted[0].order)
    }

    @Test
    fun `mixed list with multiple items maintains correct grouping`() {
        val items = listOf(
            item("1", checked = false, order = 0),
            item("2", checked = true,  order = 1),
            item("3", checked = false, order = 2),
            item("4", checked = true,  order = 3),
            item("5", checked = false, order = 4)
        )

        val sorted = sortChecklistItems(items)

        // First 3 should be unchecked
        assertFalse(sorted[0].isChecked)
        assertFalse(sorted[1].isChecked)
        assertFalse(sorted[2].isChecked)

        // Last 2 should be checked
        assertTrue(sorted[3].isChecked)
        assertTrue(sorted[4].isChecked)

        // Verify order within unchecked group (1, 3, 5)
        assertEquals("1", sorted[0].id)
        assertEquals("3", sorted[1].id)
        assertEquals("5", sorted[2].id)

        // Verify order within checked group (2, 4)
        assertEquals("2", sorted[3].id)
        assertEquals("4", sorted[4].id)
    }

    @Test
    fun `orders are sequential after sorting`() {
        val items = listOf(
            item("a", checked = true,  order = 10),
            item("b", checked = false, order = 5),
            item("c", checked = false, order = 20)
        )

        val sorted = sortChecklistItems(items)

        // Orders should be 0, 1, 2 regardless of input
        assertEquals(0, sorted[0].order)
        assertEquals(1, sorted[1].order)
        assertEquals(2, sorted[2].order)
    }
}
