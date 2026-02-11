package dev.dettmer.simplenotes.ui.editor

import dev.dettmer.simplenotes.models.ChecklistSortOption
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

    // ═══════════════════════════════════════════════════════════════════════
    // 🆕 v1.8.1 (IMPL_15): Tests für Add-Item Insert-Position
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Simulates calculateInsertIndexForNewItem() from NoteEditorViewModel.
     * Tests the insert position logic for new unchecked items.
     */
    private fun calculateInsertIndexForNewItem(
        items: List<ChecklistItemState>,
        sortOption: ChecklistSortOption
    ): Int {
        return when (sortOption) {
            ChecklistSortOption.MANUAL,
            ChecklistSortOption.UNCHECKED_FIRST -> {
                val firstCheckedIndex = items.indexOfFirst { it.isChecked }
                if (firstCheckedIndex >= 0) firstCheckedIndex else items.size
            }
            else -> items.size
        }
    }

    /**
     * Simulates the full addChecklistItemAtEnd() logic:
     * 1. Calculate insert index
     * 2. Insert new item
     * 3. Reassign order values
     */
    private fun simulateAddItemAtEnd(
        items: List<ChecklistItemState>,
        sortOption: ChecklistSortOption
    ): List<ChecklistItemState> {
        val newItem = ChecklistItemState(id = "new", text = "", isChecked = false, order = 0)
        val insertIndex = calculateInsertIndexForNewItem(items, sortOption)
        val newList = items.toMutableList()
        newList.add(insertIndex, newItem)
        return newList.mapIndexed { i, item -> item.copy(order = i) }
    }

    @Test
    fun `IMPL_15 - add item at end inserts before separator in MANUAL mode`() {
        // Ausgangslage: 2 unchecked, 1 checked (sortiert)
        val items = listOf(
            item("a", checked = false, order = 0),
            item("b", checked = false, order = 1),
            item("c", checked = true,  order = 2)
        )

        val result = simulateAddItemAtEnd(items, ChecklistSortOption.MANUAL)

        // Neues Item muss an Index 2 stehen (vor dem checked Item)
        assertEquals(4, result.size)
        assertEquals("a",   result[0].id)
        assertEquals("b",   result[1].id)
        assertEquals("new", result[2].id)  // ← Neues Item VOR Separator
        assertFalse(result[2].isChecked)
        assertEquals("c",   result[3].id)  // ← Checked Item bleibt UNTER Separator
        assertTrue(result[3].isChecked)
    }

    @Test
    fun `IMPL_15 - add item at end inserts before separator in UNCHECKED_FIRST mode`() {
        val items = listOf(
            item("a", checked = false, order = 0),
            item("b", checked = true,  order = 1),
            item("c", checked = true,  order = 2)
        )

        val result = simulateAddItemAtEnd(items, ChecklistSortOption.UNCHECKED_FIRST)

        assertEquals(4, result.size)
        assertEquals("a",   result[0].id)
        assertEquals("new", result[1].id)  // ← Neues Item direkt nach letztem unchecked
        assertFalse(result[1].isChecked)
        assertEquals("b",   result[2].id)
        assertEquals("c",   result[3].id)
    }

    @Test
    fun `IMPL_15 - add item at end appends at end in CHECKED_FIRST mode`() {
        val items = listOf(
            item("a", checked = true,  order = 0),
            item("b", checked = false, order = 1)
        )

        val result = simulateAddItemAtEnd(items, ChecklistSortOption.CHECKED_FIRST)

        assertEquals(3, result.size)
        assertEquals("a",   result[0].id)
        assertEquals("b",   result[1].id)
        assertEquals("new", result[2].id)  // ← Am Ende (kein Separator)
    }

    @Test
    fun `IMPL_15 - add item at end appends at end in ALPHABETICAL_ASC mode`() {
        val items = listOf(
            item("a", checked = false, order = 0),
            item("b", checked = true,  order = 1)
        )

        val result = simulateAddItemAtEnd(items, ChecklistSortOption.ALPHABETICAL_ASC)

        assertEquals(3, result.size)
        assertEquals("new", result[2].id)  // ← Am Ende
    }

    @Test
    fun `IMPL_15 - add item at end appends at end in ALPHABETICAL_DESC mode`() {
        val items = listOf(
            item("a", checked = true,  order = 0),
            item("b", checked = false, order = 1)
        )

        val result = simulateAddItemAtEnd(items, ChecklistSortOption.ALPHABETICAL_DESC)

        assertEquals(3, result.size)
        assertEquals("new", result[2].id)  // ← Am Ende
    }

    @Test
    fun `IMPL_15 - add item with no checked items appends at end`() {
        val items = listOf(
            item("a", checked = false, order = 0),
            item("b", checked = false, order = 1)
        )

        val result = simulateAddItemAtEnd(items, ChecklistSortOption.MANUAL)

        assertEquals(3, result.size)
        assertEquals("new", result[2].id)  // Kein checked Item → ans Ende
    }

    @Test
    fun `IMPL_15 - add item with all checked items inserts at position 0`() {
        val items = listOf(
            item("a", checked = true, order = 0),
            item("b", checked = true, order = 1)
        )

        val result = simulateAddItemAtEnd(items, ChecklistSortOption.MANUAL)

        assertEquals(3, result.size)
        assertEquals("new", result[0].id)  // ← Ganz oben (vor allen checked Items)
        assertFalse(result[0].isChecked)
        assertEquals("a",   result[1].id)
        assertEquals("b",   result[2].id)
    }

    @Test
    fun `IMPL_15 - add item to empty list in MANUAL mode`() {
        val items = emptyList<ChecklistItemState>()

        val result = simulateAddItemAtEnd(items, ChecklistSortOption.MANUAL)

        assertEquals(1, result.size)
        assertEquals("new", result[0].id)
        assertEquals(0, result[0].order)
    }

    @Test
    fun `IMPL_15 - order values are sequential after add item`() {
        val items = listOf(
            item("a", checked = false, order = 0),
            item("b", checked = false, order = 1),
            item("c", checked = true,  order = 2)
        )

        val result = simulateAddItemAtEnd(items, ChecklistSortOption.MANUAL)

        result.forEachIndexed { index, item ->
            assertEquals("Order at index $index should be $index", index, item.order)
        }
    }

    @Test
    fun `IMPL_15 - existing items do not change position after add item`() {
        // Kernforderung: Kein Item darf sich verschieben
        val items = listOf(
            item("cashews",  checked = false, order = 0),
            item("noodles",  checked = false, order = 1),
            item("coffee",   checked = true,  order = 2)
        )

        val result = simulateAddItemAtEnd(items, ChecklistSortOption.MANUAL)

        // Relative Reihenfolge der bestehenden Items prüfen
        val existingIds = result.filter { it.id != "new" }.map { it.id }
        assertEquals(listOf("cashews", "noodles", "coffee"), existingIds)

        // Cashews und Noodles müssen VOR dem neuen Item sein
        val cashewsIdx = result.indexOfFirst { it.id == "cashews" }
        val noodlesIdx = result.indexOfFirst { it.id == "noodles" }
        val newIdx     = result.indexOfFirst { it.id == "new" }
        val coffeeIdx  = result.indexOfFirst { it.id == "coffee" }

        assertTrue("Cashews before new", cashewsIdx < newIdx)
        assertTrue("Noodles before new", noodlesIdx < newIdx)
        assertTrue("New before Coffee",  newIdx < coffeeIdx)
    }
}
