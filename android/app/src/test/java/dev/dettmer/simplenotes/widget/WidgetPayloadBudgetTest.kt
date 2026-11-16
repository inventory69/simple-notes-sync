package dev.dettmer.simplenotes.widget

import dev.dettmer.simplenotes.models.ChecklistItem
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #154: Die Zeilenzahl im Widget ist ein Stabilitätslimit, kein Schönheitslimit.
 * `AppWidgetService` stellt alle Widgets eines Hosts in einem Schwung zu, und dafür steht
 * zusammen nur die Hälfte des 1-MB-Binder-Puffers zur Verfügung. Der Test hält die Eigenschaften
 * fest, ohne die das Budget seinen Zweck verfehlt — nicht die konkreten Zahlen, die sich mit
 * neuen Messwerten ändern dürfen.
 */
class WidgetPayloadBudgetTest {
    @Test
    fun `more widgets means fewer rows each`() {
        val one = WidgetPayloadBudget.forWidgetCount(1)
        val two = WidgetPayloadBudget.forWidgetCount(2)
        val four = WidgetPayloadBudget.forWidgetCount(4)

        assertTrue("1 Widget darf nicht weniger als 2 bekommen", one.listRows > two.listRows)
        assertTrue("2 Widgets dürfen nicht weniger als 4 bekommen", two.listRows > four.listRows)
        assertTrue(one.checklistItems > two.checklistItems)
        assertTrue(two.checklistItems > four.checklistItems)
    }

    @Test
    fun `a single widget reaches the pre-issue limits`() {
        // Der Punkt der ganzen Übung: wer genau ein Widget platziert hat, soll wieder
        // annähernd so viel sehen wie vor Issue #154 (50 Zeilen / 100 Items / 50 Items).
        val caps = WidgetPayloadBudget.forWidgetCount(1)
        assertTrue("Liste zu knapp: ${caps.listRows}", caps.listRows >= 40)
        assertTrue("Checkliste zu knapp: ${caps.checklistItems}", caps.checklistItems >= 80)
        assertEquals(50, caps.markdownItems)
    }

    @Test
    fun `caps never leave their bounds`() {
        for (count in listOf(0, 1, 2, 3, 5, 10, 50, 500)) {
            val caps = WidgetPayloadBudget.forWidgetCount(count)
            assertTrue("listRows: ${caps.listRows} bei count=$count", caps.listRows in 5..50)
            assertTrue("checklistItems: ${caps.checklistItems} bei count=$count", caps.checklistItems in 5..100)
            assertTrue("markdownItems: ${caps.markdownItems} bei count=$count", caps.markdownItems in 5..50)
        }
    }

    @Test
    fun `no widgets is treated like one`() {
        // getGlanceIds kann 0 liefern, bevor der Receiver das erste Mal gelaufen ist —
        // eine Division durch 0 waere hier ein Absturz im Widget-Rendering.
        assertEquals(WidgetPayloadBudget.forWidgetCount(1), WidgetPayloadBudget.forWidgetCount(0))
    }

    @Test
    fun `markdown text budget shrinks with more widgets but keeps a floor`() {
        val one = WidgetPayloadBudget.forWidgetCount(1).markdownChars
        val three = WidgetPayloadBudget.forWidgetCount(3).markdownChars
        val many = WidgetPayloadBudget.forWidgetCount(500).markdownChars

        assertTrue("1 Widget: $one, 3 Widgets: $three", one > three)
        assertTrue("Untergrenze verfehlt: $many", many >= 1_000)
    }

    @Test
    fun `a huge note is clipped before it reaches the widget`() {
        // Ohne Kürzen ging jede Notiz ganz durch den Binder: 250k Zeichen sind 1 MB (UTF-16,
        // Hoch- und Querformat) und legten alle Widgets des Launchers lahm.
        val huge = "x".repeat(250_000)
        val note = Note(
            id = "n1",
            title = huge,
            content = huge,
            deviceId = "test",
            noteType = NoteType.CHECKLIST,
            checklistItems = listOf(ChecklistItem(id = "i1", text = huge, isChecked = true, order = 0))
        )
        val caps = WidgetPayloadBudget.forWidgetCount(1)

        val clipped = note.clippedForWidget(caps.markdownChars, WidgetPayloadBudget.CHECKLIST_ITEM_CHARS)

        assertEquals(caps.markdownChars + 1, clipped.content.length)
        assertTrue(clipped.content.endsWith("…"))
        assertEquals(WidgetPayloadBudget.PREVIEW_LINE_CHARS + 1, clipped.title.length)
        assertEquals(WidgetPayloadBudget.CHECKLIST_ITEM_CHARS + 1, clipped.checklistItems!!.single().text.length)
        // Alles außer den Texten bleibt, sonst kippen Checkbox-Zustand und Aktionen
        assertEquals(
            note.copy(title = "", content = "", checklistItems = null),
            clipped.copy(title = "", content = "", checklistItems = null)
        )
        assertEquals(note.checklistItems!!.single().copy(text = ""), clipped.checklistItems!!.single().copy(text = ""))
    }

    @Test
    fun `clipping leaves short text alone and never splits an emoji`() {
        val short = "kurz"
        assertSame(short, short.clipForWidget(10))
        // 😀 ist ein Surrogatpaar: bei 2 Zeichen läge der Schnitt mitten darin
        assertEquals("a…", "a😀b".clipForWidget(2))
    }
}
