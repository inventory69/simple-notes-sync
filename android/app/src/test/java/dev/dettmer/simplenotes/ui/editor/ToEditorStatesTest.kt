package dev.dettmer.simplenotes.ui.editor

import dev.dettmer.simplenotes.models.ChecklistItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ToEditorStatesTest {
    @Test
    fun `sorts by order and keeps current fields`() {
        val states = listOf(
            ChecklistItem(id = "b", order = 1, originalOrder = 5, createdAt = 200L, indentationLevel = 1),
            ChecklistItem(id = "a", order = 0, originalOrder = 7, createdAt = 100L)
        ).toEditorStates()

        assertEquals(listOf("a", "b"), states.map { it.id })
        assertEquals(listOf(7, 5), states.map { it.originalOrder })
        assertEquals(listOf(100L, 200L), states.map { it.createdAt })
        assertEquals(1, states[1].indentationLevel)
    }

    @Test
    fun `old notes fall back to order and position`() {
        val states = listOf(
            ChecklistItem(id = "b", order = 3, originalOrder = 0, createdAt = 0L),
            ChecklistItem(id = "a", order = 1, originalOrder = 0, createdAt = 0L)
        ).toEditorStates()

        assertEquals(listOf(1, 3), states.map { it.originalOrder })
        assertEquals(listOf(0L, 1L), states.map { it.createdAt })
    }
}
