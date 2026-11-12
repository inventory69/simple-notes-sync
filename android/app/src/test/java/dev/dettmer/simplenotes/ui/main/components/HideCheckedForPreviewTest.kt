package dev.dettmer.simplenotes.ui.main.components

import android.content.res.Resources
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.ChecklistItem
import dev.dettmer.simplenotes.models.ChecklistSortOption
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Issue #118: erledigte Einträge in den Vorschauen ausblenden, darunter „+N erledigt“. */
class HideCheckedForPreviewTest {
    private val milk = ChecklistItem(id = "a", text = "Milch", isChecked = false, order = 0)
    private val bread = ChecklistItem(id = "b", text = "Brot", isChecked = true, order = 1)
    private val eggs = ChecklistItem(id = "c", text = "Eier", isChecked = false, order = 2)
    private val butter = ChecklistItem(id = "d", text = "Butter", isChecked = true, order = 3)
    private val items = listOf(milk, bread, eggs, butter)

    private fun preview(sort: ChecklistSortOption, hide: Boolean) =
        hideCheckedForPreview(sortChecklistItemsForPreview(items, sort.name), hide)

    @Test
    fun `gemischt - nur offene, Zahl der erledigten`() {
        val (visible, hidden) = preview(ChecklistSortOption.MANUAL, hide = true)
        assertEquals(listOf(milk, eggs), visible)
        assertEquals(2, hidden)
    }

    @Test
    fun `CHECKED_FIRST - erledigte trotzdem weg`() {
        val (visible, hidden) = preview(ChecklistSortOption.CHECKED_FIRST, hide = true)
        assertEquals(listOf(milk, eggs), visible)
        assertEquals(2, hidden)
    }

    @Test
    fun `alles erledigt - keine Eintraege, nur die Zahl`() {
        val (visible, hidden) = hideCheckedForPreview(listOf(bread, butter), hideChecked = true)
        assertEquals(emptyList<ChecklistItem>(), visible)
        assertEquals(2, hidden)
    }

    @Test
    fun `Schalter aus - unveraendert`() {
        val sorted = sortChecklistItemsForPreview(items, ChecklistSortOption.CHECKED_FIRST.name)
        assertEquals(sorted to 0, hideCheckedForPreview(sorted, hideChecked = false))
    }

    @Test
    fun `Fusszeile setzt weitere und erledigte zusammen`() {
        val res = mockk<Resources> {
            every { getString(R.string.checklist_items_more, any()) } answers { "+${(args[1] as Array<*>)[0]} more" }
            every { getQuantityString(R.plurals.checked_items_count, any(), any()) } answers { "${args[1]} completed" }
        }
        assertEquals("+3 more · +2 completed", checklistPreviewFooter(res, moreCount = 3, hiddenCheckedCount = 2))
        assertEquals("+2 completed", checklistPreviewFooter(res, moreCount = 0, hiddenCheckedCount = 2))
        assertEquals("+3 more", checklistPreviewFooter(res, moreCount = 3, hiddenCheckedCount = 0))
        assertNull(checklistPreviewFooter(res, moreCount = 0, hiddenCheckedCount = 0))
    }
}
