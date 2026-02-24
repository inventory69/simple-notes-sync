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
        assertTrue("First block should be UnorderedList",
            blocks[0] is MarkdownEngine.MarkdownBlock.UnorderedList)
        assertTrue("Second block should be TaskList",
            blocks[1] is MarkdownEngine.MarkdownBlock.TaskList)
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

        assertTrue("Should contain Heading",
            blocks.any { it is MarkdownEngine.MarkdownBlock.Heading })
        assertTrue("Should contain Paragraph",
            blocks.any { it is MarkdownEngine.MarkdownBlock.Paragraph })
        assertTrue("Should contain TaskList",
            blocks.any { it is MarkdownEngine.MarkdownBlock.TaskList })

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
        assertTrue("Should be UnorderedList, not TaskList",
            blocks[0] is MarkdownEngine.MarkdownBlock.UnorderedList)
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
}
