package dev.dettmer.simplenotes.markdown

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val headingSize1 = 24.sp
private val headingSize2 = 20.sp
private val headingSize3 = 18.sp

private val mdHeadingRegex = Regex("""^(#{1,3})\s+(.+)$""")
private val mdTaskRegex = Regex("""^\s*-\s+\[([ xX])\]\s+(.+)$""")
private val mdListRegex = Regex("""^\s*[-*+]\s+(.+)$""")
private val mdHorizontalRuleRegex = Regex("""^\s*([-*_])\s*(?:\1\s*){2,}$""")

class MarkdownOutputTransformation(
    private val linkColor: Color,
    private val codeBackground: Color,
    private val codeColor: Color,
    private val markerColor: Color,
) : OutputTransformation {

    override fun TextFieldBuffer.transformOutput() {
        val text = toString()
        val len = text.length
        for (s in computeMarkdownSpans(text)) {
            val start = s.start.coerceIn(0, len)
            val end = s.end.coerceIn(start, len)
            if (end > start) addStyle(s.style, start, end)
        }
    }

    internal fun computeMarkdownSpans(text: String): List<StyleSpan> {
        val codeRanges = findCodeBlockRanges(text)
        val styles = mutableListOf<StyleSpan>()
        collectLineStyles(text, codeRanges, styles)
        collectCodeBlockStyles(text, codeRanges, styles)
        collectInlineStyles(text, codeRanges, styles)
        return styles
    }

    private fun collectLineStyles(
        text: String,
        codeRanges: List<IntRange>,
        styles: MutableList<StyleSpan>,
    ) {
        var lineStart = 0
        for (line in text.split('\n')) {
            if (codeRanges.none { lineStart in it }) {
                lineStyles(line, lineStart, styles)
            }
            lineStart += line.length + 1
        }
    }

    private fun lineStyles(
        line: String,
        lineStart: Int,
        styles: MutableList<StyleSpan>,
    ) {
        val headingMatch = mdHeadingRegex.matchEntire(line)
        val taskMatch = mdTaskRegex.matchEntire(line)
        val marker = SpanStyle(color = markerColor)
        when {
            mdHorizontalRuleRegex.matchEntire(line) != null -> {
                styles += StyleSpan(
                    lineStart,
                    lineStart + line.length,
                    SpanStyle(
                        color = markerColor,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.em,
                    ),
                )
            }
            headingMatch != null -> {
                val level = headingMatch.groupValues[1].length.coerceAtMost(3)
                val prefixLen = level + 1
                val fontSize = when (level) { 1 -> headingSize1; 2 -> headingSize2; else -> headingSize3 }
                styles += StyleSpan(lineStart, lineStart + prefixLen, marker)
                styles += StyleSpan(
                    lineStart + prefixLen,
                    lineStart + line.length,
                    SpanStyle(fontWeight = FontWeight.Bold, fontSize = fontSize),
                )
            }
            taskMatch != null -> {
                val isChecked = taskMatch.groupValues[1].lowercase() == "x"
                val taskText = taskMatch.groupValues[2]
                val prefixLen = line.length - taskText.length
                styles += StyleSpan(lineStart, lineStart + prefixLen, marker)
                if (isChecked) {
                    styles += StyleSpan(
                        lineStart + prefixLen,
                        lineStart + line.length,
                        SpanStyle(textDecoration = TextDecoration.LineThrough, color = markerColor),
                    )
                }
            }
            mdListRegex.matchEntire(line) != null -> {
                val bulletIdx = line.indexOfFirst { it == '-' || it == '*' || it == '+' }
                if (bulletIdx >= 0) {
                    styles += StyleSpan(lineStart + bulletIdx, lineStart + bulletIdx + 1, marker)
                }
            }
        }
    }

    private fun collectCodeBlockStyles(
        text: String,
        codeRanges: List<IntRange>,
        styles: MutableList<StyleSpan>,
    ) {
        val blockStyle = SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground, color = codeColor)
        val markerStyle = SpanStyle(color = markerColor)
        for (range in codeRanges) {
            styles += StyleSpan(range.first, range.last + 1, blockStyle)
            val firstNewline = text.indexOf('\n', range.first)
            val openFenceEnd = if (firstNewline < 0) range.last + 1 else firstNewline + 1
            styles += StyleSpan(range.first, openFenceEnd, markerStyle)
            val lastNewlineBeforeClose = text.lastIndexOf('\n', range.last - 1)
            val closeFenceStart = if (lastNewlineBeforeClose < 0) range.first else lastNewlineBeforeClose + 1
            if (closeFenceStart > openFenceEnd) {
                styles += StyleSpan(closeFenceStart, range.last + 1, markerStyle)
            }
        }
    }

    private fun collectInlineStyles(
        text: String,
        codeRanges: List<IntRange>,
        styles: MutableList<StyleSpan>,
    ) {
        val masked = buildString(text.length) {
            text.forEachIndexed { i, c -> append(if (codeRanges.any { i in it }) ' ' else c) }
        }
        for (match in INLINE_COMBINED_REGEX.findAll(masked)) {
            inlineStyles(match, styles)
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private fun inlineStyles(
        match: MatchResult,
        styles: MutableList<StyleSpan>,
    ) {
        val start = match.range.first
        val end = match.range.last + 1
        val marker = SpanStyle(color = markerColor)
        when {
            match.groups[1] != null || match.groups[2] != null -> {
                styles += StyleSpan(start, start + 2, marker)
                styles += StyleSpan(start + 2, end - 2, SpanStyle(fontWeight = FontWeight.Bold))
                styles += StyleSpan(end - 2, end, marker)
            }
            match.groups[INLINE_GROUP_STRIKETHROUGH] != null -> {
                styles += StyleSpan(start, start + 2, marker)
                styles += StyleSpan(start + 2, end - 2, SpanStyle(textDecoration = TextDecoration.LineThrough))
                styles += StyleSpan(end - 2, end, marker)
            }
            match.groups[INLINE_GROUP_ITALIC_ASTERISK] != null || match.groups[INLINE_GROUP_ITALIC_UNDERSCORE] != null -> {
                styles += StyleSpan(start, start + 1, marker)
                styles += StyleSpan(start + 1, end - 1, SpanStyle(fontStyle = FontStyle.Italic))
                styles += StyleSpan(end - 1, end, marker)
            }
            match.groups[INLINE_GROUP_INLINE_CODE] != null -> {
                styles += StyleSpan(start, start + 1, marker)
                styles += StyleSpan(
                    start + 1,
                    end - 1,
                    SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground, color = codeColor),
                )
                styles += StyleSpan(end - 1, end, marker)
            }
            match.groups[INLINE_GROUP_LINK_TEXT] != null -> {
                val textEnd = start + 1 + match.groupValues[INLINE_GROUP_LINK_TEXT].length
                styles += StyleSpan(start, start + 1, marker)
                styles += StyleSpan(
                    start + 1,
                    textEnd,
                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                )
                styles += StyleSpan(textEnd, end, marker)
            }
            else -> {
                styles += StyleSpan(start, end, SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
            }
        }
    }
}

internal fun findCodeBlockRanges(text: String): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var charOffset = 0
    var inBlock = false
    var blockStart = 0
    for (line in text.split('\n')) {
        if (line.trimStart().startsWith("```")) {
            if (!inBlock) {
                blockStart = charOffset
                inBlock = true
            } else {
                ranges.add(blockStart..charOffset + line.length)
                inBlock = false
            }
        }
        charOffset += line.length + 1
    }
    return ranges
}

internal data class StyleSpan(val start: Int, val end: Int, val style: SpanStyle)
