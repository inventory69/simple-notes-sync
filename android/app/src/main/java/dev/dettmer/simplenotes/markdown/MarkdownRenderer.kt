package dev.dettmer.simplenotes.markdown

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.markdown.MarkdownEngine.MarkdownBlock

/**
 * 🆕 v1.9.0 (F07): Renders parsed [MarkdownBlock]s as Compose UI.
 *
 * Handles both block-level layout (headings, lists, code blocks, etc.)
 * and inline formatting (bold, italic, strikethrough, inline code, links).
 */
@Composable
fun MarkdownPreview(
    blocks: List<MarkdownBlock>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    HeadingBlock(block)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = parseInlineFormatting(block.text),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                is MarkdownBlock.UnorderedList -> {
                    UnorderedListBlock(block)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                is MarkdownBlock.CodeBlock -> {
                    CodeBlockSurface(block)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                MarkdownBlock.HorizontalRule -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HeadingBlock(heading: MarkdownBlock.Heading) {
    val style = when (heading.level) {
        1 -> MaterialTheme.typography.headlineLarge
        2 -> MaterialTheme.typography.headlineMedium
        else -> MaterialTheme.typography.headlineSmall
    }
    Text(
        text = parseInlineFormatting(heading.text),
        style = style,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun UnorderedListBlock(list: MarkdownBlock.UnorderedList) {
    Column {
        list.items.forEach { itemText ->
            Text(
                text = buildAnnotatedString {
                    append("  \u2022  ")
                    append(parseInlineFormatting(itemText))
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun CodeBlockSurface(codeBlock: MarkdownBlock.CodeBlock) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = codeBlock.code,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(12.dp)
                .horizontalScroll(rememberScrollState())
        )
    }
}


/**
 * Parses inline Markdown formatting into a Compose [AnnotatedString].
 *
 * Supported inline syntax:
 * - `**bold**` → Bold
 * - `*italic*` or `_italic_` → Italic
 * - `~~strikethrough~~` → Strikethrough
 * - `` `inline code` `` → Monospace with surface variant background
 * - `[text](url)` → Clickable link
 *
 * The parser processes patterns left-to-right with greedy matching.
 */
@Composable
fun parseInlineFormatting(text: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val codeColor = MaterialTheme.colorScheme.onSurfaceVariant

    return buildAnnotatedString {
        var remaining = text

        while (remaining.isNotEmpty()) {
            val patterns = listOf(
                InlinePattern.BOLD_ASTERISK,
                InlinePattern.BOLD_UNDERSCORE,
                InlinePattern.STRIKETHROUGH,
                InlinePattern.ITALIC_ASTERISK,
                InlinePattern.ITALIC_UNDERSCORE,
                InlinePattern.INLINE_CODE,
                InlinePattern.LINK
            )

            var earliestMatch: MatchResult? = null
            var earliestPattern: InlinePattern? = null

            for (pattern in patterns) {
                val match = pattern.regex.find(remaining)
                if (match != null && (earliestMatch == null || match.range.first < earliestMatch.range.first)) {
                    earliestMatch = match
                    earliestPattern = pattern
                }
            }

            if (earliestMatch == null || earliestPattern == null) {
                append(remaining)
                break
            }

            if (earliestMatch.range.first > 0) {
                append(remaining.substring(0, earliestMatch.range.first))
            }

            when (earliestPattern) {
                InlinePattern.BOLD_ASTERISK, InlinePattern.BOLD_UNDERSCORE -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(earliestMatch.groupValues[1])
                    }
                }
                InlinePattern.ITALIC_ASTERISK, InlinePattern.ITALIC_UNDERSCORE -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(earliestMatch.groupValues[1])
                    }
                }
                InlinePattern.STRIKETHROUGH -> {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(earliestMatch.groupValues[1])
                    }
                }
                InlinePattern.INLINE_CODE -> {
                    withStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBackground,
                        color = codeColor
                    )) {
                        append(earliestMatch.groupValues[1])
                    }
                }
                InlinePattern.LINK -> {
                    val linkText = earliestMatch.groupValues[1]
                    val linkUrl = earliestMatch.groupValues[2]
                    withLink(
                        LinkAnnotation.Url(
                            url = linkUrl,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                        )
                    ) {
                        append(linkText)
                    }
                }
            }

            remaining = remaining.substring(earliestMatch.range.last + 1)
        }
    }
}

/**
 * Inline Markdown patterns with their regex matchers.
 * Order matters — bold (**) must be checked before italic (*).
 */
private enum class InlinePattern(val regex: Regex) {
    BOLD_ASTERISK(Regex("""\*\*(.+?)\*\*""")),
    BOLD_UNDERSCORE(Regex("""__(.+?)__""")),
    STRIKETHROUGH(Regex("""~~(.+?)~~""")),
    ITALIC_ASTERISK(Regex("""\*(.+?)\*""")),
    ITALIC_UNDERSCORE(Regex("""_(.+?)_""")),
    INLINE_CODE(Regex("""`([^`]+)`""")),
    LINK(Regex("""\[([^\]]+)\]\(([^)]+)\)"""))
}
