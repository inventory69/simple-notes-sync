package dev.dettmer.simplenotes.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-Tests für Block-level-Bild-Parsing im MarkdownEngine (Bild-Attachments).
 */
class MarkdownEngineImageTest {
    @Test
    fun `standalone image line is parsed as Image block`() {
        val blocks = MarkdownEngine.parse("![a cat](.assets/abc123.webp)")

        assertEquals(1, blocks.size)
        val image = blocks[0] as MarkdownEngine.MarkdownBlock.Image
        assertEquals("a cat", image.altText)
        assertEquals("abc123.webp", image.assetName)
    }

    @Test
    fun `image with empty alt text is parsed`() {
        val blocks = MarkdownEngine.parse("![](.assets/xyz789.jpg)")

        val image = blocks[0] as MarkdownEngine.MarkdownBlock.Image
        assertEquals("", image.altText)
        assertEquals("xyz789.jpg", image.assetName)
    }

    @Test
    fun `image surrounded by paragraphs stays a separate block`() {
        val blocks = MarkdownEngine.parse("before\n\n![](.assets/mid.webp)\n\nafter")

        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MarkdownEngine.MarkdownBlock.Paragraph)
        assertTrue(blocks[1] is MarkdownEngine.MarkdownBlock.Image)
        assertTrue(blocks[2] is MarkdownEngine.MarkdownBlock.Paragraph)
    }

    @Test
    fun `inline image mixed with text on the same line is split into its own block`() {
        val blocks = MarkdownEngine.parse("text before ![alt](.assets/inline.webp) text after")

        assertEquals(3, blocks.size)
        assertEquals(
            "text before",
            (blocks[0] as MarkdownEngine.MarkdownBlock.Paragraph).text
        )
        val image = blocks[1] as MarkdownEngine.MarkdownBlock.Image
        assertEquals("alt", image.altText)
        assertEquals("inline.webp", image.assetName)
        assertEquals(
            "text after",
            (blocks[2] as MarkdownEngine.MarkdownBlock.Paragraph).text
        )
    }

    @Test
    fun `image glued directly to preceding text is still parsed`() {
        val blocks = MarkdownEngine.parse("Jxjsjjed![avocado](.assets/cfb711039203ae88.webp)")

        assertEquals(2, blocks.size)
        assertEquals(
            "Jxjsjjed",
            (blocks[0] as MarkdownEngine.MarkdownBlock.Paragraph).text
        )
        val image = blocks[1] as MarkdownEngine.MarkdownBlock.Image
        assertEquals("avocado", image.altText)
        assertEquals("cfb711039203ae88.webp", image.assetName)
    }

    @Test
    fun `http image link is not treated as an asset image`() {
        val blocks = MarkdownEngine.parse("![desc](http://example.com/img.png)")

        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownEngine.MarkdownBlock.Paragraph)
    }
}
