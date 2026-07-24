package dev.dettmer.simplenotes.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-Tests für TaskList-Parsing im MarkdownEngine.
 *
 * v1.9.0: Import Wizard Fix — Checklist-Syntax wird als TaskList-Block erkannt
 * statt als NoteType.CHECKLIST importiert zu werden.
 */
class MarkdownEngineTaskListTest {
    @Test
    fun `task list items are parsed as TaskList block`() {
        val md = "- [ ] Unchecked item\n- [x] Checked item\n- [X] Also checked"
        val blocks = MarkdownEngine.parse(md)

        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownEngine.MarkdownBlock.TaskList)

        val taskList = blocks[0] as MarkdownEngine.MarkdownBlock.TaskList
        assertEquals(3, taskList.items.size)
        assertEquals("Unchecked item", taskList.items[0].text)
        assertEquals(false, taskList.items[0].isChecked)
        assertEquals("Checked item", taskList.items[1].text)
        assertEquals(true, taskList.items[1].isChecked)
        assertEquals("Also checked", taskList.items[2].text)
        assertEquals(true, taskList.items[2].isChecked)
    }

    @Test
    fun `task list is separate from regular unordered list`() {
        val md = "- Regular item 1\n- Regular item 2\n\n- [ ] Task 1\n- [x] Task 2"
        val blocks = MarkdownEngine.parse(md)

        assertEquals(2, blocks.size)
        assertTrue(
            "First block should be UnorderedList",
            blocks[0] is MarkdownEngine.MarkdownBlock.UnorderedList
        )
        assertTrue(
            "Second block should be TaskList",
            blocks[1] is MarkdownEngine.MarkdownBlock.TaskList
        )
    }

    @Test
    fun `mixed content with embedded task list`() {
        val md = """
            # Meeting Notes
            
            Some text before tasks.
            
            - [x] Task done
            - [ ] Task pending
            
            More text after tasks.
        """.trimIndent()

        val blocks = MarkdownEngine.parse(md)

        assertTrue(
            "Should contain Heading",
            blocks.any { it is MarkdownEngine.MarkdownBlock.Heading }
        )
        assertTrue(
            "Should contain Paragraph",
            blocks.any { it is MarkdownEngine.MarkdownBlock.Paragraph }
        )
        assertTrue(
            "Should contain TaskList",
            blocks.any { it is MarkdownEngine.MarkdownBlock.TaskList }
        )

        val taskList = blocks.filterIsInstance<MarkdownEngine.MarkdownBlock.TaskList>().first()
        assertEquals(2, taskList.items.size)
        assertEquals(true, taskList.items[0].isChecked)
        assertEquals(false, taskList.items[1].isChecked)
    }

    @Test
    fun `regular list items are not parsed as task list`() {
        val md = "- Normal item 1\n- Normal item 2"
        val blocks = MarkdownEngine.parse(md)

        assertEquals(1, blocks.size)
        assertTrue(
            "Should be UnorderedList, not TaskList",
            blocks[0] is MarkdownEngine.MarkdownBlock.UnorderedList
        )
    }

    @Test
    fun `single task item creates TaskList block`() {
        val md = "- [ ] Only one task"
        val blocks = MarkdownEngine.parse(md)

        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownEngine.MarkdownBlock.TaskList)
        val taskList = blocks[0] as MarkdownEngine.MarkdownBlock.TaskList
        assertEquals(1, taskList.items.size)
        assertEquals("Only one task", taskList.items[0].text)
        assertEquals(false, taskList.items[0].isChecked)
    }

    // ── Bullet-Liste direkt vor Task-Zeilen (ohne Leerzeile) ──
    // LIST_ITEM_REGEX matcht "- [x] foo" ebenfalls; ohne Guard in der Sammelschleife
    // verschluckt die Bullet-Liste die komplette Checkliste. Genau so entsteht der Fall
    // in der App: der Toolbar-Button macht aus "- foo" in-place "- [ ] foo".

    @Test
    fun `bullet directly followed by task lines keeps the tasks`() {
        val md = "- Regular\n- [ ] Task 1\n- [x] Task 2"
        val blocks = MarkdownEngine.parse(md)

        assertEquals(2, blocks.size)
        val list = blocks[0] as MarkdownEngine.MarkdownBlock.UnorderedList
        assertEquals(listOf("Regular"), list.items)

        val taskList = blocks[1] as MarkdownEngine.MarkdownBlock.TaskList
        assertEquals(2, taskList.items.size)
        assertEquals("Task 1", taskList.items[0].text)
        assertEquals(false, taskList.items[0].isChecked)
        assertEquals("Task 2", taskList.items[1].text)
        assertEquals(true, taskList.items[1].isChecked)
    }

    @Test
    fun `task lines directly followed by bullets split into two blocks`() {
        val md = "- [ ] Task\n- Regular"
        val blocks = MarkdownEngine.parse(md)

        assertEquals(2, blocks.size)
        assertTrue(blocks[0] is MarkdownEngine.MarkdownBlock.TaskList)
        assertEquals(
            listOf("Regular"),
            (blocks[1] as MarkdownEngine.MarkdownBlock.UnorderedList).items
        )
    }

    @Test
    fun `alternating bullets and tasks produce alternating blocks`() {
        val md = "- [ ] A\n- B\n- [x] C"
        val blocks = MarkdownEngine.parse(md)

        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MarkdownEngine.MarkdownBlock.TaskList)
        assertTrue(blocks[1] is MarkdownEngine.MarkdownBlock.UnorderedList)
        assertTrue(blocks[2] is MarkdownEngine.MarkdownBlock.TaskList)
    }

    // ── Marker-/Klammer-Toleranzen ──

    @Test
    fun `asterisk and plus markers are task items too`() {
        val md = "* [ ] Star\n+ [x] Plus"
        val blocks = MarkdownEngine.parse(md)

        assertEquals(1, blocks.size)
        val taskList = blocks[0] as MarkdownEngine.MarkdownBlock.TaskList
        assertEquals(2, taskList.items.size)
        assertEquals("Star", taskList.items[0].text)
        assertEquals(false, taskList.items[0].isChecked)
        assertEquals("Plus", taskList.items[1].text)
        assertEquals(true, taskList.items[1].isChecked)
    }

    @Test
    fun `empty brackets count as unchecked task`() {
        val md = "- [] Typo brackets"
        val blocks = MarkdownEngine.parse(md)

        assertEquals(1, blocks.size)
        val taskList = blocks[0] as MarkdownEngine.MarkdownBlock.TaskList
        assertEquals("Typo brackets", taskList.items[0].text)
        assertEquals(false, taskList.items[0].isChecked)
    }

    @Test
    fun `task without text is an empty checkbox`() {
        // "- [ ] " ist exakt das, was der Toolbar-Button auf einer leeren Zeile einfügt.
        val blocks = MarkdownEngine.parse("- [ ] \n- [x]")

        assertEquals(1, blocks.size)
        val taskList = blocks[0] as MarkdownEngine.MarkdownBlock.TaskList
        assertEquals(2, taskList.items.size)
        assertEquals("", taskList.items[0].text)
        assertEquals(false, taskList.items[0].isChecked)
        assertEquals("", taskList.items[1].text)
        assertEquals(true, taskList.items[1].isChecked)
    }

    @Test
    fun `indented task item is still a task`() {
        val blocks = MarkdownEngine.parse("  - [x] Indented")

        assertEquals(1, blocks.size)
        val taskList = blocks[0] as MarkdownEngine.MarkdownBlock.TaskList
        assertEquals("Indented", taskList.items[0].text)
        assertEquals(true, taskList.items[0].isChecked)
    }

    // ── Abgrenzung: was NICHT zur Checkbox werden darf ──

    @Test
    fun `bullet with a link labelled x stays an unordered list`() {
        val md = "- [x](https://example.com)"
        val blocks = MarkdownEngine.parse(md)

        assertEquals(1, blocks.size)
        assertEquals(
            listOf("[x](https://example.com)"),
            (blocks[0] as MarkdownEngine.MarkdownBlock.UnorderedList).items
        )
    }

    @Test
    fun `missing space after brackets stays an unordered list`() {
        val blocks = MarkdownEngine.parse("- [ ]x")

        assertEquals(1, blocks.size)
        assertEquals(
            listOf("[ ]x"),
            (blocks[0] as MarkdownEngine.MarkdownBlock.UnorderedList).items
        )
    }

    @Test
    fun `paragraph directly followed by task lines splits correctly`() {
        val blocks = MarkdownEngine.parse("Some text\n- [x] Task")

        assertEquals(2, blocks.size)
        assertEquals("Some text", (blocks[0] as MarkdownEngine.MarkdownBlock.Paragraph).text)
        assertTrue(blocks[1] is MarkdownEngine.MarkdownBlock.TaskList)
    }
}
