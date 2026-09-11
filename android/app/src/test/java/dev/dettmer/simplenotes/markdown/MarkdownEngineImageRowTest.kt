package dev.dettmer.simplenotes.markdown

import dev.dettmer.simplenotes.markdown.MarkdownEngine.MarkdownBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deckt die Pack-Regel für responsive Bildreihen ab: [MarkdownBlock.Image.startsNewRow]
 * (wann eine Reihe neu beginnt) und [MarkdownEngine.imageRowLength] (wie viele Bilder
 * hineinpassen). Renderer und PDF-Export teilen sich beides.
 */
class MarkdownEngineImageRowTest {

    private fun images(text: String) = MarkdownEngine.parse(text).filterIsInstance<MarkdownBlock.Image>()

    @Test
    fun `erstes Bild beginnt immer eine neue Reihe`() {
        assertTrue(images("![|25%](.assets/a.webp)").single().startsNewRow)
    }

    @Test
    fun `direkt aufeinanderfolgende Bildzeilen bleiben in derselben Reihe`() {
        val result = images("![|25%](.assets/a.webp)\n![|25%](.assets/b.webp)\n![|25%](.assets/c.webp)")
        assertEquals(listOf(true, false, false), result.map { it.startsNewRow })
    }

    @Test
    fun `Leerzeile erzwingt eine neue Reihe`() {
        val result = images("![|50%](.assets/a.webp)\n\n![|50%](.assets/b.webp)")
        assertEquals(listOf(true, true), result.map { it.startsNewRow })
    }

    @Test
    fun `zwei Bilder auf derselben Zeile bleiben in derselben Reihe`() {
        val result = images("![|25%](.assets/a.webp) ![|25%](.assets/b.webp)")
        assertEquals(listOf(true, false), result.map { it.startsNewRow })
    }

    @Test
    fun `Paragraph Heading und Liste trennen Reihen`() {
        for (separator in listOf("Text dazwischen", "## Überschrift", "- Punkt")) {
            val result = images("![|25%](.assets/a.webp)\n$separator\n![|25%](.assets/b.webp)")
            assertTrue("Trenner: $separator", result[1].startsNewRow)
        }
    }

    @Test
    fun `Textpraefix auf der Bildzeile trennt die Reihe`() {
        val result = images("![|25%](.assets/a.webp)\nHinweis ![|25%](.assets/b.webp)")
        assertTrue(result[1].startsNewRow)
    }

    @Test
    fun `imageRowLength packt bis 100 Prozent und bricht darueber`() {
        assertEquals(4, rowLength("![|25%](.assets/a.webp)\n![|25%](.assets/b.webp)\n![|25%](.assets/c.webp)\n![|25%](.assets/d.webp)"))
        assertEquals(2, rowLength("![|50%](.assets/a.webp)\n![|50%](.assets/b.webp)"))
        assertEquals(1, rowLength("![|50%](.assets/a.webp)\n![|75%](.assets/b.webp)"))
        assertEquals(1, rowLength("![|100%](.assets/a.webp)\n![|25%](.assets/b.webp)"))
    }

    @Test
    fun `imageRowLength bricht bei startsNewRow`() {
        assertEquals(1, rowLength("![|25%](.assets/a.webp)\n\n![|25%](.assets/b.webp)"))
    }

    @Test
    fun `imageRowLength ist 0 fuer Nicht-Bild-Bloecke`() {
        val blocks = MarkdownEngine.parse("Nur Text\n![|25%](.assets/a.webp)")
        assertEquals(0, MarkdownEngine.imageRowLength(blocks, 0))
        assertEquals(1, MarkdownEngine.imageRowLength(blocks, 1))
        assertEquals(0, MarkdownEngine.imageRowLength(blocks, blocks.size))
    }

    @Test
    fun `zweite Reihe startet erst nach der ersten`() {
        val blocks = MarkdownEngine.parse("![|50%](.assets/a.webp)\n![|50%](.assets/b.webp)\n![|50%](.assets/c.webp)")
        assertEquals(2, MarkdownEngine.imageRowLength(blocks, 0))
        assertEquals(1, MarkdownEngine.imageRowLength(blocks, 2))
        assertFalse(blocks.filterIsInstance<MarkdownBlock.Image>()[2].startsNewRow)
    }

    @Test
    fun `imageRowSeparators trennt Reihen mit Umbruch und Bilder darin mit Leerzeichen`() {
        val blocks = MarkdownEngine.parse("Text\n![|50%](.assets/a.webp)\n![|50%](.assets/b.webp)\n\n![|50%](.assets/c.webp)")
            .filterNot { it is MarkdownBlock.BlankLines }
        assertEquals(listOf("", "\n", " ", "\n"), MarkdownEngine.imageRowSeparators(blocks))
    }

    @Test
    fun `joinImageRows verbindet aufeinanderfolgende Bildzeilen`() {
        assertEquals(
            "![|25%](.assets/a.webp) ![|25%](.assets/b.webp) ![|25%](.assets/c.webp)",
            MarkdownEngine.joinImageRows("![|25%](.assets/a.webp)\n![|25%](.assets/b.webp)\n![|25%](.assets/c.webp)")
        )
    }

    @Test
    fun `joinImageRows laesst Leerzeile und Textpraefix als Trenner stehen`() {
        val blankLine = "![](.assets/a.webp)\n\n![](.assets/b.webp)"
        assertEquals(blankLine, MarkdownEngine.joinImageRows(blankLine))
        val textPrefix = "![](.assets/a.webp)\nHinweis ![](.assets/b.webp)"
        assertEquals(textPrefix, MarkdownEngine.joinImageRows(textPrefix))
    }

    private fun rowLength(text: String) = MarkdownEngine.imageRowLength(MarkdownEngine.parse(text), 0)
}
