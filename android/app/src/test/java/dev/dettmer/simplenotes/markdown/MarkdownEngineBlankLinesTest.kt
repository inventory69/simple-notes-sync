package dev.dettmer.simplenotes.markdown

import dev.dettmer.simplenotes.markdown.MarkdownEngine.MarkdownBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🆕 v2.16.0 (Issue #140): Mehrfache Leerzeilen überleben das Rendern.
 *
 * Markdown fasst sie laut Spezifikation zu einem Absatztrenner zusammen — für eine Notiz-App
 * ist das die falsche Regel: Wer dreimal Enter drückt, will die Lücke sehen. Die erste
 * Leerzeile bleibt der Trenner (Blockabstand des Renderers), gezählt wird nur der Rest.
 */
class MarkdownEngineBlankLinesTest {

    private fun blanks(text: String) = MarkdownEngine.parse(text).filterIsInstance<MarkdownBlock.BlankLines>()

    @Test fun `a single blank line stays the plain paragraph separator`() {
        val blocks = MarkdownEngine.parse("erste Zeile\n\nzweite Zeile")

        assertTrue("one blank line must not become a gap", blocks.none { it is MarkdownBlock.BlankLines })
        assertEquals(2, blocks.filterIsInstance<MarkdownBlock.Paragraph>().size)
    }

    @Test fun `three blank lines keep two of them as a gap`() {
        val blocks = MarkdownEngine.parse("oben\n\n\n\nunten")

        assertEquals(listOf(2), blanks("oben\n\n\n\nunten").map { it.count })
        // Reihenfolge muss stimmen: Absatz, Lücke, Absatz.
        assertTrue(blocks[0] is MarkdownBlock.Paragraph)
        assertTrue(blocks[1] is MarkdownBlock.BlankLines)
        assertTrue(blocks[2] is MarkdownBlock.Paragraph)
    }

    @Test fun `each run of blank lines is counted on its own`() {
        assertEquals(listOf(1, 3), blanks("a\n\n\nb\n\n\n\n\nc").map { it.count })
    }

    @Test fun `blank lines around structural blocks survive too`() {
        val blocks = MarkdownEngine.parse("# Titel\n\n\ntext\n\n\n- eins\n- zwei")

        assertEquals(listOf(1, 1), blocks.filterIsInstance<MarkdownBlock.BlankLines>().map { it.count })
        assertEquals(1, blocks.filterIsInstance<MarkdownBlock.Heading>().size)
        assertEquals(1, blocks.filterIsInstance<MarkdownBlock.UnorderedList>().size)
    }

    @Test fun `blank lines inside a fenced code block are left to the code block`() {
        val blocks = MarkdownEngine.parse("```\nfoo\n\n\n\nbar\n```")

        assertTrue("code content must not be split up", blocks.none { it is MarkdownBlock.BlankLines })
        assertEquals("foo\n\n\n\nbar", blocks.filterIsInstance<MarkdownBlock.CodeBlock>().single().code)
    }

    @Test fun `text without any blank lines produces no gap blocks`() {
        assertTrue(blanks("nur eine Zeile").isEmpty())
        assertTrue(blanks("").isEmpty())
    }
}
