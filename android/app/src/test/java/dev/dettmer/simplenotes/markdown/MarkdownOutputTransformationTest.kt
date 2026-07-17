package dev.dettmer.simplenotes.markdown

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownOutputTransformationTest {
    private val linkColor = Color(0xFF0000FFu)
    private val codeBg = Color(0xFFAAAAAAu)
    private val codeColor = Color(0xFF333333u)
    private val markerColor = Color(0xFF888888u)
    private val t = MarkdownOutputTransformation(linkColor, codeBg, codeColor, markerColor)

    // ── inline code ─────────────────────────────────────────────────────────

    @Test
    fun `inline code - background covers only the code text`() {
        // "`code`" spans indices 7..12; content "code" is at [8, 12)
        val text = "prefix `code` suffix"
        val spans = t.computeMarkdownSpans(text)
        val content = spans.firstOrNull { it.start == 8 && it.end == 12 }
        assertNotNull("code content span missing", content)
        assertEquals(FontFamily.Monospace, content!!.style.fontFamily)
        assertEquals(codeBg, content.style.background)
    }

    @Test
    fun `inline code - backticks carry markerColor`() {
        val text = "prefix `code` suffix"
        val spans = t.computeMarkdownSpans(text)
        val open = spans.firstOrNull { it.start == 7 && it.end == 8 }
        assertNotNull("opening backtick marker missing", open)
        assertEquals(markerColor, open!!.style.color)
        val close = spans.firstOrNull { it.start == 12 && it.end == 13 }
        assertNotNull("closing backtick marker missing", close)
        assertEquals(markerColor, close!!.style.color)
    }

    // ── bold / italic / bold+italic ─────────────────────────────────────────

    private fun spansOf(text: String) = t.computeMarkdownSpans(text)

    private fun assertSpan(
        spans: List<StyleSpan>,
        start: Int,
        end: Int,
        message: String,
        predicate: (StyleSpan) -> Boolean
    ) = assertNotNull(message, spans.firstOrNull { it.start == start && it.end == end && predicate(it) })

    @Test
    fun `bold plus italic asterisk - inner text is bold and italic, markers are 3 chars`() {
        // "***hello***" — markers [0,3) and [8,11), content "hello" at [3, 8)
        val spans = spansOf("***hello***")
        assertSpan(spans, 3, 8, "bold span over ***hello*** content missing") { it.style.fontWeight == FontWeight.Bold }
        assertSpan(spans, 3, 8, "italic span over ***hello*** content missing") { it.style.fontStyle == FontStyle.Italic }
        assertSpan(spans, 0, 3, "opening *** marker missing") { it.style.color == markerColor }
        assertSpan(spans, 8, 11, "closing *** marker missing") { it.style.color == markerColor }
    }

    @Test
    fun `bold plus italic underscore - inner text is bold and italic, markers are 3 chars`() {
        val spans = spansOf("___hello___")
        assertSpan(spans, 3, 8, "bold span over ___hello___ content missing") { it.style.fontWeight == FontWeight.Bold }
        assertSpan(spans, 3, 8, "italic span over ___hello___ content missing") { it.style.fontStyle == FontStyle.Italic }
        assertSpan(spans, 0, 3, "opening ___ marker missing") { it.style.color == markerColor }
        assertSpan(spans, 8, 11, "closing ___ marker missing") { it.style.color == markerColor }
    }

    @Test
    fun `plain bold stays bold and not italic`() {
        // "**bold**" — content at [2, 6)
        val spans = spansOf("**bold**")
        assertSpan(spans, 2, 6, "bold content span missing") { it.style.fontWeight == FontWeight.Bold }
        assertNull(
            "plain **bold** must not produce an italic span",
            spans.firstOrNull { it.style.fontStyle == FontStyle.Italic }
        )
    }

    @Test
    fun `plain italic stays italic and not bold`() {
        // "*italic*" — content at [1, 7)
        val spans = spansOf("*italic*")
        assertSpan(spans, 1, 7, "italic content span missing") { it.style.fontStyle == FontStyle.Italic }
        assertNull(
            "plain *italic* must not produce a bold span",
            spans.firstOrNull { it.style.fontWeight == FontWeight.Bold }
        )
    }

    @Test
    fun `adjacent delimiters - italic followed directly by bold stays paired`() {
        // regression guard for e15ecb0: "*italic***bold**"
        //  *italic* = [0, 8), content [1, 7) · **bold** = [8, 16), content [10, 14)
        val spans = spansOf("*italic***bold**")
        assertSpan(spans, 1, 7, "italic content span missing") { it.style.fontStyle == FontStyle.Italic }
        assertSpan(spans, 10, 14, "bold content span missing") { it.style.fontWeight == FontWeight.Bold }
    }

    @Test
    fun `bold plus italic does not shift marker pairing for the rest of the line`() {
        // "***bi*** and **b** and *i*"
        //  0        8     13    18   23
        //  ***bi*** = [0, 8), content [3, 5)
        //  **b**    = [13, 18), content [15, 16)
        //  *i*      = [23, 26), content [24, 25)
        val spans = spansOf("***bi*** and **b** and *i*")
        assertSpan(spans, 3, 5, "bold+italic content span missing") {
            it.style.fontWeight == FontWeight.Bold && it.style.fontStyle == FontStyle.Italic
        }
        assertSpan(spans, 15, 16, "bold content span missing or shifted") { it.style.fontWeight == FontWeight.Bold }
        assertSpan(spans, 24, 25, "italic content span missing or shifted") { it.style.fontStyle == FontStyle.Italic }
    }

    @Test
    fun `stripInlineFormatting removes triple markers`() {
        assertEquals("x", stripInlineFormatting("***x***"))
        assertEquals("x", stripInlineFormatting("___x___"))
    }

    @Test
    fun `markdownInlineToHtml emits nested bold and italic tags`() {
        assertEquals("<b><i>x</i></b>", markdownInlineToHtml("***x***"))
        assertEquals("<b><i>x</i></b>", markdownInlineToHtml("___x___"))
    }

    // ── regression: intraword underscores must not be parsed as italics ─────

    @Test
    fun `snake_case identifier - no italic span`() {
        val text = "call sync_engine.rs and try_lock now"
        val spans = t.computeMarkdownSpans(text)
        val italic = spans.firstOrNull { it.style.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic }
        assertNull("snake_case must not produce an italic span", italic)
    }

    @Test
    fun `inline code spans stay paired around snake_case identifiers`() {
        val text = "in `webdav.rs`/`sync_engine.rs` überhaupt"
        val spans = t.computeMarkdownSpans(text)
        val codeSpans = spans.filter { it.style.fontFamily == FontFamily.Monospace && it.style.background == codeBg }
        assertEquals(2, codeSpans.size)
    }

    @Test
    fun `leading and parenthesized underscore italics still work`() {
        val leading = t.computeMarkdownSpans("_lead_ text")
        assertNotNull(
            "leading _italic_ must still render",
            leading.firstOrNull { it.style.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic }
        )
        val parenthesized = t.computeMarkdownSpans("(_note_)")
        assertNotNull(
            "(_italic_) must still render",
            parenthesized.firstOrNull { it.style.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic }
        )
    }

    // ── regression: inline spans after a fenced block must not be shifted ───

    @Test
    fun `inline code after fenced block - not shifted onto trailing text`() {
        // "```\ncode\n```\n`inline`"
        //  0123 4567 8901  234567890
        //            11   1111111112
        // fence range 0..12 (includes trailing \n at 12)
        // `inline` is at [13..20], content "inline" at [14, 20)
        val text = "```\ncode\n```\n`inline`"
        val spans = t.computeMarkdownSpans(text)
        val inlineContent = spans.firstOrNull { it.start == 14 && it.end == 20 }
        assertNotNull("inline code content span is absent or shifted", inlineContent)
        assertEquals(FontFamily.Monospace, inlineContent!!.style.fontFamily)
        assertEquals(codeBg, inlineContent.style.background)
    }

    @Test
    fun `inline code after fenced block - suffix text has no code background`() {
        val text = "```\ncode\n```\n`inline` plain"
        val spans = t.computeMarkdownSpans(text)
        // "plain" starts at index 22 — no span should give it code background
        val wrongSpan = spans.firstOrNull { span ->
            span.style.background == codeBg && span.start >= 22
        }
        assertNull("unexpected code background on text after inline span", wrongSpan)
    }

    // ── fenced code block styling ────────────────────────────────────────────

    @Test
    fun `fenced code block - monospace background covers entire block`() {
        val text = "```\ncode\n```\n`inline`"
        val spans = t.computeMarkdownSpans(text)
        // fence range 0..12 → block span [0, 13)
        val block = spans.firstOrNull { it.start == 0 && it.end == 13 && it.style.fontFamily == FontFamily.Monospace }
        assertNotNull("fenced block background span missing", block)
        assertEquals(codeBg, block!!.style.background)
        assertEquals(codeColor, block.style.color)
    }

    @Test
    fun `fenced code block - opening fence line carries markerColor`() {
        // "```\n" = indices 0-3, openFenceEnd = 4
        val text = "```\ncode\n```\n`inline`"
        val spans = t.computeMarkdownSpans(text)
        val openFence = spans.firstOrNull { it.start == 0 && it.end == 4 && it.style.color == markerColor }
        assertNotNull("opening fence marker span missing", openFence)
    }

    @Test
    fun `fenced code block - closing fence line carries markerColor`() {
        // "```" at indices 9-11, then '\n' at 12; fence range last=12
        // lastIndexOf('\n', range.last - 1 = 11) finds '\n' at 8 → closeFenceStart=9
        // closing fence marker [9, 13)
        val text = "```\ncode\n```\n`inline`"
        val spans = t.computeMarkdownSpans(text)
        val closeFence = spans.firstOrNull { it.start == 9 && it.end == 13 && it.style.color == markerColor }
        assertNotNull("closing fence marker span missing", closeFence)
    }

    @Test
    fun `fenced code block - interior lines have no extra marker span`() {
        // Only the two fence lines should have markerColor; "code\n" at [4, 9) must not
        val text = "```\ncode\n```"
        val spans = t.computeMarkdownSpans(text)
        val interiorMarker = spans.firstOrNull { span ->
            span.style.color == markerColor && span.start in 4 until 9
        }
        assertNull("unexpected markerColor span in fence interior", interiorMarker)
    }

    // ── findCodeBlockRanges ──────────────────────────────────────────────────

    @Test
    fun `findCodeBlockRanges - single fence with surrounding text`() {
        val text = "before\n```\ncode\n```\nafter"
        // "```" opens at charOffset=7, closes at charOffset=16 → 7..(16+3)=7..19
        val ranges = findCodeBlockRanges(text)
        assertEquals(1, ranges.size)
        assertEquals(7, ranges[0].first)
        assertEquals(19, ranges[0].last)
    }

    @Test
    fun `findCodeBlockRanges - unclosed fence yields no range`() {
        val text = "```\nno closing fence"
        val ranges = findCodeBlockRanges(text)
        assertEquals(0, ranges.size)
    }

    @Test
    fun `findCodeBlockRanges - two consecutive fences`() {
        val text = "```\nfirst\n```\n```\nsecond\n```"
        val ranges = findCodeBlockRanges(text)
        assertEquals(2, ranges.size)
    }

    @Test
    fun `findCodeBlockRanges - language tag after fence markers is included`() {
        val text = "```promql\nrate(x[5m])\n```"
        val ranges = findCodeBlockRanges(text)
        assertEquals(1, ranges.size)
        assertEquals(0, ranges[0].first)
    }
}
