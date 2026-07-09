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

    // ── v2: Alt-Tokens (Größe/Ausrichtung) ──

    @Test
    fun `size and align tokens are parsed and stripped from the block's alt text`() {
        val blocks = MarkdownEngine.parse("![Sonnenuntergang|50%|right](.assets/a1b2c3d4e5f60718.webp)")

        val image = blocks[0] as MarkdownEngine.MarkdownBlock.Image
        assertEquals("Sonnenuntergang", image.altText)
        assertEquals(50, image.sizePercent)
        assertEquals(ImageAlign.RIGHT, image.align)
    }

    @Test
    fun `image without tokens gets default size and align`() {
        val blocks = MarkdownEngine.parse("![a cat](.assets/abc123.webp)")

        val image = blocks[0] as MarkdownEngine.MarkdownBlock.Image
        assertEquals(50, image.sizePercent)
        assertEquals(ImageAlign.CENTER, image.align)
    }

    @Test
    fun `inline-only image on its own line falls through to paragraph`() {
        val blocks = MarkdownEngine.parse("![cat|inline](.assets/a.webp)")

        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MarkdownEngine.MarkdownBlock.Paragraph)
    }

    @Test
    fun `non-inline image still splits the line as before`() {
        val blocks = MarkdownEngine.parse("text before ![alt|right](.assets/inline.webp) text after")

        assertEquals(3, blocks.size)
        assertEquals("text before", (blocks[0] as MarkdownEngine.MarkdownBlock.Paragraph).text)
        val image = blocks[1] as MarkdownEngine.MarkdownBlock.Image
        assertEquals("alt", image.altText)
        assertEquals(ImageAlign.RIGHT, image.align)
        assertEquals("text after", (blocks[2] as MarkdownEngine.MarkdownBlock.Paragraph).text)
    }

    @Test
    fun `mixed inline and block image on one line keeps the block image only, inline stays in prefix text`() {
        val blocks = MarkdownEngine.parse("![i|inline](.assets/inline.webp) then ![block](.assets/block.webp)")

        assertEquals(2, blocks.size)
        val prefix = blocks[0] as MarkdownEngine.MarkdownBlock.Paragraph
        assertTrue(prefix.text.contains(".assets/inline.webp"))
        val image = blocks[1] as MarkdownEngine.MarkdownBlock.Image
        assertEquals("block", image.altText)
        assertEquals("block.webp", image.assetName)
        assertEquals(1, image.ordinal)
    }

    @Test
    fun `ordinal matches the global findAll index across headings, code fences and inline images`() {
        val text = """
            # Heading with ![h|inline](.assets/h.webp) link
            some ![p|inline](.assets/p.webp) paragraph text
            ```
            code with ![c|inline](.assets/c.webp) link
            ```
            ![first](.assets/first.webp)
            ![second](.assets/second.webp)
        """.trimIndent()

        val blocks = MarkdownEngine.parse(text)
        val images = blocks.filterIsInstance<MarkdownEngine.MarkdownBlock.Image>()
        assertEquals(2, images.size)

        val allMatches = MarkdownEngine.IMAGE_REGEX.findAll(text).toList()
        val firstOrdinal = allMatches.indexOfFirst { it.groupValues[2] == "first.webp" }
        val secondOrdinal = allMatches.indexOfFirst { it.groupValues[2] == "second.webp" }

        assertEquals(firstOrdinal, images.first { it.assetName == "first.webp" }.ordinal)
        assertEquals(secondOrdinal, images.first { it.assetName == "second.webp" }.ordinal)
    }
}
