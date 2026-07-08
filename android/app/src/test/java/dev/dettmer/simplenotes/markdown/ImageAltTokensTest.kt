package dev.dettmer.simplenotes.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageAltTokensTest {
    // ── parseImageAlt ──

    @Test
    fun `empty alt gives defaults`() {
        val info = parseImageAlt("")
        assertEquals(ImageAltInfo("", 50, ImageAlign.CENTER), info)
    }

    @Test
    fun `plain alt without tokens gives defaults`() {
        val info = parseImageAlt("a cat")
        assertEquals(ImageAltInfo("a cat", 50, ImageAlign.CENTER), info)
    }

    @Test
    fun `size token is parsed and stripped`() {
        val info = parseImageAlt("cat|50%")
        assertEquals(ImageAltInfo("cat", 50, ImageAlign.CENTER), info)
    }

    @Test
    fun `align tokens are parsed case-insensitively and stripped`() {
        assertEquals(ImageAlign.LEFT, parseImageAlt("cat|LEFT").align)
        assertEquals(ImageAlign.RIGHT, parseImageAlt("cat|Right").align)
        assertEquals(ImageAlign.INLINE, parseImageAlt("cat|inline").align)
        assertEquals(ImageAlign.CENTER, parseImageAlt("cat|center").align)
    }

    @Test
    fun `size and align combined`() {
        val info = parseImageAlt("Sonnenuntergang|50%|right")
        assertEquals(ImageAltInfo("Sonnenuntergang", 50, ImageAlign.RIGHT), info)
    }

    @Test
    fun `unknown segment is kept as part of clean alt`() {
        val info = parseImageAlt("my|cat")
        assertEquals(ImageAltInfo("my|cat", 50, ImageAlign.CENTER), info)
    }

    @Test
    fun `last size token wins`() {
        val info = parseImageAlt("cat|25%|75%")
        assertEquals(75, info.sizePercent)
        assertEquals("cat", info.cleanAlt)
    }

    @Test
    fun `last align token wins`() {
        val info = parseImageAlt("cat|left|right")
        assertEquals(ImageAlign.RIGHT, info.align)
        assertEquals("cat", info.cleanAlt)
    }

    @Test
    fun `0 percent coerces to 1`() {
        assertEquals(1, parseImageAlt("cat|0%").sizePercent)
    }

    @Test
    fun `150 percent coerces to 100`() {
        assertEquals(100, parseImageAlt("cat|150%").sizePercent)
    }

    // ── buildImageAlt ──

    @Test
    fun `build omits default tokens`() {
        assertEquals("cat", buildImageAlt("cat", 50, ImageAlign.CENTER))
    }

    @Test
    fun `build includes non-default size`() {
        assertEquals("cat|100%", buildImageAlt("cat", 100, ImageAlign.CENTER))
    }

    @Test
    fun `build includes non-default align`() {
        assertEquals("cat|right", buildImageAlt("cat", 50, ImageAlign.RIGHT))
    }

    @Test
    fun `build includes both non-default tokens in size-then-align order`() {
        assertEquals("Sonnenuntergang|75%|right", buildImageAlt("Sonnenuntergang", 75, ImageAlign.RIGHT))
    }

    @Test
    fun `round-trip build then parse yields original info`() {
        val original = ImageAltInfo("my|cat", 33, ImageAlign.LEFT)
        val built = buildImageAlt(original.cleanAlt, original.sizePercent, original.align)
        assertEquals(original, parseImageAlt(built))
    }

    // ── computeImageRewrite ──

    @Test
    fun `rewrite changes size and align of the addressed occurrence`() {
        val text = "![cat](.assets/a.webp)"
        val result = computeImageRewrite(text, ordinal = 0, assetName = "a.webp", sizePercent = 75, align = ImageAlign.RIGHT)
        requireNotNull(result)
        val (range, replacement) = result
        assertEquals(text.indices, range)
        assertEquals("![cat|75%|right](.assets/a.webp)", replacement)
    }

    @Test
    fun `rewrite preserves clean alt with literal pipes`() {
        val text = "![my|cat](.assets/a.webp)"
        val result = computeImageRewrite(text, ordinal = 0, assetName = "a.webp", sizePercent = 50, align = ImageAlign.CENTER)
        requireNotNull(result)
        assertEquals("![my|cat](.assets/a.webp)", result.second)
    }

    @Test
    fun `rewrite with cleanAlt override changes the alt text`() {
        val text = "![old alt](.assets/a.webp)"
        val result = computeImageRewrite(
            text,
            ordinal = 0,
            assetName = "a.webp",
            sizePercent = 50,
            align = ImageAlign.CENTER,
            cleanAlt = "new alt"
        )
        requireNotNull(result)
        assertEquals("![new alt](.assets/a.webp)", result.second)
    }

    @Test
    fun `rewrite only touches the addressed occurrence among identical links`() {
        val text = "![cat](.assets/a.webp) and ![cat](.assets/a.webp)"
        val result = computeImageRewrite(text, ordinal = 1, assetName = "a.webp", sizePercent = 25, align = ImageAlign.LEFT)
        requireNotNull(result)
        val (range, replacement) = result
        val secondMatch = MarkdownEngine.IMAGE_REGEX.findAll(text).toList()[1]
        assertEquals(secondMatch.range, range)
        assertEquals("![cat|25%|left](.assets/a.webp)", replacement)

        val rewritten = text.replaceRange(range, replacement)
        assertEquals("![cat](.assets/a.webp) and ![cat|25%|left](.assets/a.webp)", rewritten)
    }

    @Test
    fun `rewrite returns null on asset name mismatch`() {
        val text = "![cat](.assets/a.webp)"
        val result = computeImageRewrite(text, ordinal = 0, assetName = "different.webp", sizePercent = 50, align = ImageAlign.LEFT)
        assertNull(result)
    }

    @Test
    fun `rewrite returns null on out-of-range ordinal`() {
        val text = "![cat](.assets/a.webp)"
        val result = computeImageRewrite(text, ordinal = 1, assetName = "a.webp", sizePercent = 50, align = ImageAlign.LEFT)
        assertNull(result)
    }
}
