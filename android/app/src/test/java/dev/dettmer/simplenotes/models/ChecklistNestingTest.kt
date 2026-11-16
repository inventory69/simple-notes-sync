package dev.dettmer.simplenotes.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unterpunkte in Checklisten (Issues #64, #71).
 *
 * Kurzschreibweise: Einträge durch Leerzeichen getrennt, `-` vorne = Ebene 1, `*` hinten = abgehakt.
 * `"A -a1 B*"` = offener Oberpunkt A mit Unterpunkt a1, danach abgehakter Oberpunkt B.
 * Die Kennung ist zugleich Text, originalOrder ist die Position in der Kurzschreibweise.
 */
class ChecklistNestingTest {
    private fun list(spec: String): List<ChecklistItem> = spec.split(" ").mapIndexed { i, token ->
        val name = token.removePrefix("-").removeSuffix("*")
        ChecklistItem(
            id = name,
            text = name,
            isChecked = token.endsWith("*"),
            order = i,
            originalOrder = i,
            createdAt = i.toLong(),
            indentationLevel = if (token.startsWith("-")) 1 else 0
        )
    }

    private fun spec(items: List<ChecklistItem>): String = items.joinToString(" ") {
        (if (it.indentationLevel > 0) "-" else "") + it.text + (if (it.isChecked) "*" else "")
    }

    // ── Gruppen und Trenner ──

    @Test
    fun `openRowCount counts rows of open groups including checked children`() {
        assertEquals(3, ChecklistNesting.openRowCount(list("A -a1* -a2 B* -b1*")))
        assertEquals(0, ChecklistNesting.openRowCount(list("A* -a1*")))
        assertEquals(2, ChecklistNesting.openRowCount(list("A B")))
    }

    @Test
    fun `flat list behaves like before`() {
        val items = list("A B C* D*")
        assertEquals(items.count { !it.isChecked }, ChecklistNesting.openRowCount(items))
        ChecklistSortOption.entries.forEach { option ->
            val flat = ChecklistSorter.sort(items, option)
            assertTrue(flat.all { it.indentationLevel == 0 })
            assertEquals(items.size, flat.size)
        }
    }

    @Test
    fun `normalize caps level and makes first row a parent`() {
        val items = list("-A B").map { if (it.id == "B") it.copy(indentationLevel = 3) else it }
        assertEquals("A -B", spec(ChecklistNesting.normalize(items)))
    }

    @Test
    fun `levelOfLine reads two spaces or a tab as child`() {
        assertEquals(0, ChecklistNesting.levelOfLine("- [ ] a"))
        assertEquals(0, ChecklistNesting.levelOfLine(" - [ ] a"))
        assertEquals(1, ChecklistNesting.levelOfLine("  - [ ] a"))
        assertEquals(1, ChecklistNesting.levelOfLine("\t- [ ] a"))
    }

    // ── Abhaken ──

    @Test
    fun `checking a parent checks its children`() {
        assertEquals("A* -a1* -a2* B", spec(ChecklistNesting.toggle(list("A -a1 -a2* B"), "A", true)))
    }

    @Test
    fun `unchecking a parent changes only the parent`() {
        assertEquals("A -a1* -a2*", spec(ChecklistNesting.toggle(list("A* -a1* -a2*"), "A", false)))
    }

    @Test
    fun `unchecking a child under a checked parent unchecks the parent`() {
        assertEquals("A -a1* -a2", spec(ChecklistNesting.toggle(list("A* -a1* -a2*"), "a2", false)))
    }

    @Test
    fun `child under open parent toggles alone and does not move`() {
        val toggled = ChecklistNesting.toggle(list("A -a1 -a2 B"), "a1", true)
        assertEquals("A -a1* -a2 B", spec(ChecklistSorter.sort(toggled, ChecklistSortOption.MANUAL)))
    }

    // ── Sortierung ──

    @Test
    fun `checked group moves below separator as a whole`() {
        val toggled = ChecklistNesting.toggle(list("A -a1 B C"), "A", true)
        assertEquals("B C A* -a1*", spec(ChecklistSorter.sort(toggled, ChecklistSortOption.MANUAL)))
    }

    @Test
    fun `every sort option keeps children with their parent`() {
        // Oberpunkte b, A, c (Text gemischt groß/klein), Unterpunkte in absichtlich unsortierter Reihenfolge
        val items = list("b -z -y A* -x* c")
        ChecklistSortOption.entries.forEach { option ->
            val sorted = spec(ChecklistSorter.sort(items, option))
            assertTrue("$option: $sorted", sorted.contains("b -z -y"))
            assertTrue("$option: $sorted", sorted.contains("A* -x*"))
        }
        assertEquals("A* -x* b -z -y c", spec(ChecklistSorter.sort(items, ChecklistSortOption.ALPHABETICAL_ASC)))
        assertEquals("c b -z -y A* -x*", spec(ChecklistSorter.sort(items, ChecklistSortOption.ALPHABETICAL_DESC)))
        assertEquals("b -z -y c A* -x*", spec(ChecklistSorter.sort(items, ChecklistSortOption.MANUAL)))
        assertEquals("b -z -y c A* -x*", spec(ChecklistSorter.sort(items, ChecklistSortOption.UNCHECKED_FIRST)))
        assertEquals("A* -x* b -z -y c", spec(ChecklistSorter.sort(items, ChecklistSortOption.CHECKED_FIRST)))
        assertEquals("b -z -y c A* -x*", spec(ChecklistSorter.sort(items, ChecklistSortOption.CREATION_DATE)))
        assertEquals("c b -z -y A* -x*", spec(ChecklistSorter.sort(items, ChecklistSortOption.CREATION_DATE_DESC)))
    }

    @Test
    fun `sort reassigns order sequentially`() {
        val sorted = ChecklistSorter.sort(list("A* -a1* B"), ChecklistSortOption.MANUAL)
        assertEquals(listOf(0, 1, 2), sorted.map { it.order })
    }

    // ── Ziehen ──

    @Test
    fun `move across separator takes over the section state`() {
        // Trenner nach a1 (A offen). a1 auf B ziehen: a1 landet in Bs Gruppe und wird abgehakt.
        assertEquals("A B* -a1*", spec(ChecklistNesting.move(list("A -a1 B*"), 1, 2)))
        // B nach oben über den Trenner: wird offen
        assertEquals("B A -a1", spec(ChecklistNesting.move(list("A -a1 B*"), 2, 0)))
    }

    @Test
    fun `child dragged directly below the separator becomes a parent`() {
        // b1 tauscht mit seinem abgehakten Oberpunkt B und stünde sonst positionsbedingt unter A
        assertEquals("A b1* B*", spec(ChecklistNesting.move(list("A B* -b1*"), 2, 1)))
    }

    @Test
    fun `finishDrag reattaches former children behind the dragged parent`() {
        // A mit a1 wurde ans Ende gezogen; a1 hing während des Ziehens an B
        assertEquals("B A -a1", spec(ChecklistNesting.finishDrag(list("B -a1 A"), "A", listOf("a1"))))
    }

    @Test
    fun `finishDrag into a group makes the row a child`() {
        assertEquals("A -X -a1", spec(ChecklistNesting.finishDrag(list("A X -a1"), "X", emptyList())))
    }

    @Test
    fun `finishDrag checks the group when the parent is checked`() {
        // A (offen, mit a1) wurde unter den Trenner gezogen und dabei abgehakt
        assertEquals("B A* -a1*", spec(ChecklistNesting.finishDrag(list("B -a1 A*"), "A", listOf("a1"))))
    }

    // ── Einrücken, Ausrücken, Löschen, neue Zeilen ──

    @Test
    fun `indent is blocked for the first row of a section`() {
        val items = list("A B C* D*")
        assertFalse(ChecklistNesting.canIndent(items, 0))
        assertTrue(ChecklistNesting.canIndent(items, 1))
        assertFalse(ChecklistNesting.canIndent(items, 2))
        assertTrue(ChecklistNesting.canIndent(items, 3))
        assertEquals("A -B C* D*", spec(ChecklistNesting.indent(items, "B")))
        assertEquals(items, ChecklistNesting.indent(items, "C"))
    }

    @Test
    fun `outdent moves the row behind its group`() {
        assertEquals("A -a2 a1 B", spec(ChecklistNesting.outdent(list("A -a1 -a2 B"), "a1")))
        assertEquals("A -a1 a2 B", spec(ChecklistNesting.outdent(list("A -a1 -a2 B"), "a2")))
    }

    @Test
    fun `deleting a parent promotes its children`() {
        assertEquals("a1 a2 B", spec(ChecklistNesting.delete(list("A -a1 -a2 B"), "A")))
        assertEquals("A -a2 B", spec(ChecklistNesting.delete(list("A -a1 -a2 B"), "a1")))
    }

    @Test
    fun `new row after a parent with children becomes the first child`() {
        val items = list("A -a1 B")
        assertEquals(1, ChecklistNesting.levelForNewRowAfter(items, 0))
        assertEquals(1, ChecklistNesting.levelForNewRowAfter(items, 1))
        assertEquals(0, ChecklistNesting.levelForNewRowAfter(items, 2))
    }

    // ── Markdown ──

    @Test
    fun `markdown mirror stays flat for older clients and import restores the levels`() {
        val items = list("A -a1* B*")
        val note = Note(title = "T", content = "", noteType = NoteType.CHECKLIST, checklistItems = items, deviceId = "test")
        val md = note.toMarkdown()
        // v2.23.0 und älter lesen nur `^[-*]\s+\[`: eingerückte Zeilen gingen beim Force-Import verloren
        assertFalse(md, md.contains("\n  - ["))
        val parsed = requireNotNull(Note.fromMarkdown(md)).checklistItems
        assertEquals("A a1* B*", spec(parsed.orEmpty()))
        assertEquals("A -a1* B*", spec(ChecklistNesting.withLevelsFrom(parsed, items).orEmpty()))
    }

    @Test
    fun `fromMarkdown reads indentation written by hand or later versions`() {
        val md = "---\nid: x\ncreated: 2026-01-01T00:00:00Z\nupdated: 2026-01-01T00:00:00Z\ndevice: d\ntype: checklist\n---\n" +
            "# T\n\n- [ ] A\n  - [x] a1\n- [x] B"
        val parsed = requireNotNull(Note.fromMarkdown(md)).checklistItems
        assertEquals("A -a1* B*", spec(parsed.orEmpty()))
        // Eingerückte Datei gilt wie geschrieben, auch gegen eine flache lokale Fassung
        assertEquals("A -a1* B*", spec(ChecklistNesting.withLevelsFrom(parsed, list("A a1* B*")).orEmpty()))
    }

    @Test
    fun `withLevelsFrom matches by text and leaves new rows flat`() {
        val md = list("A x B a1*")
        assertEquals("A x B -a1*", spec(ChecklistNesting.withLevelsFrom(md, list("A B -a1*")).orEmpty()))
    }

    // ── Eingelesene Daten und Randfälle (Review) ──

    @Test
    fun `normalize checks open children of a checked parent`() {
        assertEquals("A* -a1* B -b1", spec(ChecklistNesting.normalize(list("A* -a1 B -b1"))))
    }

    @Test
    fun `checked child dragged to the top becomes an open parent`() {
        assertEquals("a1 A B*", spec(ChecklistNesting.move(list("A -a1* B*"), 1, 0)))
    }

    @Test
    fun `withoutBlank releases the children of an empty parent`() {
        val items = list("A -a1 W* -n1*").map { if (it.id == "W") it.copy(text = " ") else it }
        assertEquals("A -a1 n1*", spec(ChecklistNesting.withoutBlank(items)))
    }

    @Test
    fun `json reader clamps foreign indentation levels`() {
        val json = """{"id":"n","title":"T","content":"","noteType":"CHECKLIST","checklistItems":[""" +
            """{"id":"a","text":"A","isChecked":false,"order":0,"indentationLevel":-1},""" +
            """{"id":"b","text":"B","isChecked":false,"order":1,"indentationLevel":99}]}"""
        val note = requireNotNull(Note.fromJson(json))
        assertEquals(listOf(0, ChecklistNesting.MAX_STORED_LEVEL), note.checklistItems.orEmpty().map { it.indentationLevel })
        note.toJson() // darf nicht werfen (TEXT_INDENT.repeat)
    }
}
