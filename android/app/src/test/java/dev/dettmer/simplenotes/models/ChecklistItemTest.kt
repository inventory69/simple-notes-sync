package dev.dettmer.simplenotes.models

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit-Tests für ChecklistItem.
 */
class ChecklistItemTest {

    @Test
    fun `default values are correct`() {
        val item = ChecklistItem()
        assertTrue(item.id.isNotBlank())
        assertEquals("", item.text)
        assertFalse(item.isChecked)
        assertEquals(0, item.order)
    }

    @Test
    fun `createEmpty returns empty item with correct order`() {
        val item = ChecklistItem.createEmpty(5)
        assertEquals("", item.text)
        assertFalse(item.isChecked)
        assertEquals(5, item.order)
        assertTrue(item.id.isNotBlank())
    }

    @Test
    fun `createEmpty generates unique IDs`() {
        val item1 = ChecklistItem.createEmpty(0)
        val item2 = ChecklistItem.createEmpty(1)
        assertNotEquals(item1.id, item2.id)
    }

    @Test
    fun `constructor with all values preserves them`() {
        val item = ChecklistItem("custom-id", "Buy milk", true, 3)
        assertEquals("custom-id", item.id)
        assertEquals("Buy milk", item.text)
        assertTrue(item.isChecked)
        assertEquals(3, item.order)
    }

    @Test
    fun `isChecked is mutable`() {
        val item = ChecklistItem(text = "Item", isChecked = false)
        assertFalse(item.isChecked)
        item.isChecked = true
        assertTrue(item.isChecked)
    }

    @Test
    fun `order is mutable`() {
        val item = ChecklistItem(text = "Item", order = 0)
        assertEquals(0, item.order)
        item.order = 5
        assertEquals(5, item.order)
    }

    @Test
    fun `data class copy works correctly`() {
        val original = ChecklistItem("id-1", "Original", false, 0)
        val copied = original.copy(text = "Copied", isChecked = true)

        assertEquals("id-1", copied.id)
        assertEquals("Copied", copied.text)
        assertTrue(copied.isChecked)
        assertEquals(0, copied.order)
    }

    @Test
    fun `data class equals works correctly`() {
        val item1 = ChecklistItem("id-1", "Text", false, 0)
        val item2 = ChecklistItem("id-1", "Text", false, 0)
        assertEquals(item1, item2)
    }

    @Test
    fun `data class not equal with different values`() {
        val item1 = ChecklistItem("id-1", "Text A", false, 0)
        val item2 = ChecklistItem("id-1", "Text B", false, 0)
        assertNotEquals(item1, item2)
    }
}
