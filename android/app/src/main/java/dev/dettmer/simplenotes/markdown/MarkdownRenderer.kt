package dev.dettmer.simplenotes.markdown

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.markdown.MarkdownEngine.MarkdownBlock
import dev.dettmer.simplenotes.ui.theme.Dimensions

private const val COMPACT_HEADING_LEVEL = 3

/**
 * 🆕 v1.9.0 (F07): Renders parsed [MarkdownBlock]s as Compose UI.
 *
 * Handles both block-level layout (headings, lists, code blocks, etc.)
 * and inline formatting (bold, italic, strikethrough, inline code, links).
 */
@Composable
fun MarkdownPreview(
    blocks: List<MarkdownBlock>,
    modifier: Modifier = Modifier,
    scrollEnabled: Boolean = true,
    compactHeaders: Boolean = false
) {
    val bodyStyle = if (compactHeaders) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
    SelectionContainer {
        val scrollModifier = if (scrollEnabled) Modifier.verticalScroll(rememberScrollState()) else Modifier
        Column(
            modifier = modifier
                .fillMaxWidth()
                .then(scrollModifier)
                .padding(horizontal = Dimensions.SpacingSmall)
        ) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Heading -> {
                        HeadingBlock(block, compactHeaders)
                        Spacer(modifier = Modifier.height(Dimensions.SpacingLarge))
                    }

                    is MarkdownBlock.Paragraph -> {
                        Text(
                            text = parseInlineFormatting(block.text),
                            style = bodyStyle,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(Dimensions.SpacingMediumLarge))
                    }

                    is MarkdownBlock.TaskList -> {
                        TaskListBlock(block, bodyStyle)
                        Spacer(modifier = Modifier.height(Dimensions.SpacingMediumLarge))
                    }

                    is MarkdownBlock.UnorderedList -> {
                        UnorderedListBlock(block, bodyStyle)
                        Spacer(modifier = Modifier.height(Dimensions.SpacingMediumLarge))
                    }

                    is MarkdownBlock.CodeBlock -> {
                        CodeBlockSurface(block)
                        Spacer(modifier = Modifier.height(Dimensions.SpacingMediumLarge))
                    }

                    MarkdownBlock.HorizontalRule -> {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = Dimensions.SpacingMediumLarge),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeadingBlock(heading: MarkdownBlock.Heading, compact: Boolean = false) {
    if (compact && heading.level == COMPACT_HEADING_LEVEL) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimensions.SpacingMediumLarge),
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = heading.text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(
                    horizontal = Dimensions.SpacingMediumLarge,
                    vertical = Dimensions.SpacingMedium
                )
            )
        }
        return
    }
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
private fun TaskListBlock(taskList: MarkdownBlock.TaskList, bodyStyle: TextStyle = MaterialTheme.typography.bodyLarge) {
    Column {
        taskList.items.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = Dimensions.SpacingSmall)
            ) {
                Checkbox(
                    checked = item.isChecked,
                    onCheckedChange = null, // Read-only in preview
                    modifier = Modifier.size(Dimensions.IconSizeMedium)
                )
                Spacer(modifier = Modifier.width(Dimensions.SpacingMedium))
                Text(
                    text = parseInlineFormatting(item.text),
                    style = bodyStyle,
                    color = if (item.isChecked) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (item.isChecked) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    }
                )
            }
            Spacer(modifier = Modifier.height(Dimensions.SpacingXSmall))
        }
    }
}

@Composable
private fun UnorderedListBlock(list: MarkdownBlock.UnorderedList, bodyStyle: TextStyle = MaterialTheme.typography.bodyLarge) {
    Column {
        list.items.forEach { itemText ->
            Text(
                text = buildAnnotatedString {
                    append("  \u2022  ")
                    append(parseInlineFormatting(itemText))
                },
                style = bodyStyle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(Dimensions.SpacingSmall))
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
                .padding(Dimensions.SpacingMediumLarge)
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
    return parseInlineFormattingWithColors(text, linkColor, codeBackground, codeColor)
}

internal fun parseInlineFormattingWithColors(
    text: String,
    linkColor: Color,
    codeBackground: Color,
    codeColor: Color
): AnnotatedString = buildAnnotatedString {
    var pos = 0
    for (match in INLINE_COMBINED_REGEX.findAll(text)) {
        if (match.range.first > pos) {
            append(text.substring(pos, match.range.first))
        }
        when {
            match.groups[1] != null -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[1]) }
            }

            match.groups[2] != null -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[2]) }
            }

            match.groups[INLINE_GROUP_STRIKETHROUGH] != null -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(match.groupValues[INLINE_GROUP_STRIKETHROUGH]) }
            }

            match.groups[INLINE_GROUP_ITALIC_ASTERISK] != null -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groupValues[INLINE_GROUP_ITALIC_ASTERISK]) }
            }

            match.groups[INLINE_GROUP_ITALIC_UNDERSCORE] != null -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groupValues[INLINE_GROUP_ITALIC_UNDERSCORE]) }
            }

            match.groups[INLINE_GROUP_INLINE_CODE] != null -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBackground,
                        color = codeColor
                    )
                ) {
                    append(match.groupValues[INLINE_GROUP_INLINE_CODE])
                }
            }

            match.groups[INLINE_GROUP_LINK_TEXT] != null -> {
                val linkText = match.groupValues[INLINE_GROUP_LINK_TEXT]
                val linkUrl = match.groupValues[INLINE_GROUP_LINK_URL].trimEnd('!', '?', ',', '.', ';', ':')
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
                ) { append(linkText) }
            }

            else -> {
                val url = match.value.trimEnd('!', '?', ',', '.', ';', ':')
                withLink(
                    LinkAnnotation.Url(
                        url = url,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                    )
                ) { append(url) }
                val trimmed = match.value.length - url.length
                if (trimmed > 0) append(match.value.takeLast(trimmed))
            }
        }
        pos = match.range.last + 1
    }
    if (pos < text.length) append(text.substring(pos))
}

internal fun buildMarkdownCardPreview(
    blocks: List<MarkdownBlock>,
    linkColor: Color,
    codeBackground: Color,
    codeColor: Color
): AnnotatedString = buildAnnotatedString {
    blocks.forEachIndexed { i, block ->
        if (i > 0) append("\n")
        when (block) {
            is MarkdownBlock.Heading -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(block.text) }
            }

            is MarkdownBlock.Paragraph -> {
                append(parseInlineFormattingWithColors(block.text, linkColor, codeBackground, codeColor))
            }

            is MarkdownBlock.TaskList -> {
                block.items.forEachIndexed { j, item ->
                    if (j > 0) append("\n")
                    append(if (item.isChecked) "☑ " else "☐ ")
                    val itemText = parseInlineFormattingWithColors(item.text, linkColor, codeBackground, codeColor)
                    if (item.isChecked) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(itemText) }
                    } else {
                        append(itemText)
                    }
                }
            }

            is MarkdownBlock.UnorderedList -> {
                block.items.forEachIndexed { j, itemText ->
                    if (j > 0) append("\n")
                    append("  •  ")
                    append(parseInlineFormattingWithColors(itemText, linkColor, codeBackground, codeColor))
                }
            }

            is MarkdownBlock.CodeBlock -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBackground,
                        color = codeColor
                    )
                ) {
                    append(block.code)
                }
            }

            MarkdownBlock.HorizontalRule -> Unit
        }
    }
}

@Composable
internal fun noteCardMarkdownPreview(content: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val codeColor = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(content, linkColor, codeBackground, codeColor) {
        val blocks = MarkdownEngine.parse(content)
        buildMarkdownCardPreview(blocks, linkColor, codeBackground, codeColor)
    }
}

/**
 * Single combined regex for all inline Markdown patterns.
 * Alternation order matters: bold (**) must appear before italic (*), double
 * underscore before single, so that `**` is never mis-parsed as two `*`.
 * A single findAll pass over this regex is sufficient — matched characters
 * cannot be re-consumed by a later alternative, which correctly handles
 * adjacent delimiters such as `*italic***bold**`.
 *
 * Capture groups:
 *  1 = bold asterisk content    (**…**)
 *  2 = bold underscore content  (__…__)
 *  3 = strikethrough content    (~~…~~)
 *  4 = italic asterisk content  (*…*)
 *  5 = italic underscore content(_…_)
 *  6 = inline code content      (`…`)
 *  — = auto URL                 (no capture group)
 *  7 = link display text        ([…](…))
 *  8 = link URL
 */
internal val INLINE_COMBINED_REGEX = Regex(
    """\*\*(.+?)\*\*|__(.+?)__|~~(.+?)~~|\*(.+?)\*|_(.+?)_|`([^`]+)`|https?://[^\s<>"')\]!]+|\[([^\]]+)\]\(([^)]+)\)"""
)

internal const val INLINE_GROUP_STRIKETHROUGH = 3
internal const val INLINE_GROUP_ITALIC_ASTERISK = 4
internal const val INLINE_GROUP_ITALIC_UNDERSCORE = 5
internal const val INLINE_GROUP_INLINE_CODE = 6
internal const val INLINE_GROUP_LINK_TEXT = 7
internal const val INLINE_GROUP_LINK_URL = 8

/**
 * Strips inline Markdown delimiters from [text], returning plain readable text.
 * Used where AnnotatedString is unavailable (e.g. Glance widgets).
 *
 * `**bold**` → `bold`, `_italic_` → `italic`, `[text](url)` → `text`, bare URLs kept as-is.
 */
internal fun stripInlineFormatting(text: String): String =
    INLINE_COMBINED_REGEX.replace(text) { match ->
        when {
            match.groups[1] != null -> match.groupValues[1]
            match.groups[2] != null -> match.groupValues[2]
            match.groups[INLINE_GROUP_STRIKETHROUGH] != null -> match.groupValues[INLINE_GROUP_STRIKETHROUGH]
            match.groups[INLINE_GROUP_ITALIC_ASTERISK] != null -> match.groupValues[INLINE_GROUP_ITALIC_ASTERISK]
            match.groups[INLINE_GROUP_ITALIC_UNDERSCORE] != null -> match.groupValues[INLINE_GROUP_ITALIC_UNDERSCORE]
            match.groups[INLINE_GROUP_INLINE_CODE] != null -> match.groupValues[INLINE_GROUP_INLINE_CODE]
            match.groups[INLINE_GROUP_LINK_TEXT] != null -> match.groupValues[INLINE_GROUP_LINK_TEXT]
            else -> match.value // bare URL — keep as-is
        }
    }

private fun String.escapeHtml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

/**
 * Converts inline Markdown in [text] to an HTML string suitable for [android.text.Html.fromHtml].
 *
 * Bold → `<b>`, italic → `<i>`, strikethrough → `<s>`, inline code → `<tt>`,
 * links → link text only (URL discarded), bare URLs → kept as escaped plain text.
 * All captured content is HTML-escaped to prevent injection into [android.text.Html.fromHtml].
 */
internal fun markdownInlineToHtml(text: String): String = buildString {
    var pos = 0
    for (match in INLINE_COMBINED_REGEX.findAll(text)) {
        if (match.range.first > pos) {
            append(text.substring(pos, match.range.first).escapeHtml())
        }
        when {
            match.groups[1] != null -> append("<b>${match.groupValues[1].escapeHtml()}</b>")
            match.groups[2] != null -> append("<b>${match.groupValues[2].escapeHtml()}</b>")
            match.groups[INLINE_GROUP_STRIKETHROUGH] != null ->
                append("<s>${match.groupValues[INLINE_GROUP_STRIKETHROUGH].escapeHtml()}</s>")

            match.groups[INLINE_GROUP_ITALIC_ASTERISK] != null ->
                append("<i>${match.groupValues[INLINE_GROUP_ITALIC_ASTERISK].escapeHtml()}</i>")

            match.groups[INLINE_GROUP_ITALIC_UNDERSCORE] != null ->
                append("<i>${match.groupValues[INLINE_GROUP_ITALIC_UNDERSCORE].escapeHtml()}</i>")

            match.groups[INLINE_GROUP_INLINE_CODE] != null ->
                append("<tt>${match.groupValues[INLINE_GROUP_INLINE_CODE].escapeHtml()}</tt>")

            match.groups[INLINE_GROUP_LINK_TEXT] != null ->
                append(match.groupValues[INLINE_GROUP_LINK_TEXT].escapeHtml())

            else -> append(match.value.escapeHtml()) // bare URL — keep as plain text
        }
        pos = match.range.last + 1
    }
    if (pos < text.length) append(text.substring(pos).escapeHtml())
}
