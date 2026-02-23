package dev.dettmer.simplenotes.ui.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.InsertLink
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R

private const val TOOLBAR_ICON_SIZE = 22
private const val LINK_URL_PLACEHOLDER = "url"
private const val LINK_BRACKET_OFFSET = 3  // "](".length + accounts for indexing

/**
 * 🆕 v1.9.0 (F07): Markdown formatting toolbar for TEXT notes.
 *
 * Provides quick-insert buttons for common Markdown syntax:
 * Bold, Italic, Strikethrough, Heading, Code, Link, List, Horizontal Rule.
 *
 * Wraps selected text or inserts placeholder at cursor position.
 */
@Composable
fun MarkdownToolbar(
    textFieldState: TextFieldState,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            ToolbarButton(
                icon = Icons.Filled.FormatBold,
                contentDescription = stringResource(R.string.md_toolbar_bold),
                onClick = { wrapSelection(textFieldState, "**", "**") }
            )
            ToolbarButton(
                icon = Icons.Filled.FormatItalic,
                contentDescription = stringResource(R.string.md_toolbar_italic),
                onClick = { wrapSelection(textFieldState, "*", "*") }
            )
            ToolbarButton(
                icon = Icons.Filled.FormatStrikethrough,
                contentDescription = stringResource(R.string.md_toolbar_strikethrough),
                onClick = { wrapSelection(textFieldState, "~~", "~~") }
            )
            ToolbarButton(
                icon = Icons.Filled.Title,
                contentDescription = stringResource(R.string.md_toolbar_heading),
                onClick = { insertHeading(textFieldState) }
            )
            ToolbarButton(
                icon = Icons.Filled.Code,
                contentDescription = stringResource(R.string.md_toolbar_code),
                onClick = { wrapSelection(textFieldState, "`", "`") }
            )
            ToolbarButton(
                icon = Icons.Filled.InsertLink,
                contentDescription = stringResource(R.string.md_toolbar_link),
                onClick = { insertLink(textFieldState) }
            )
            ToolbarButton(
                icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                contentDescription = stringResource(R.string.md_toolbar_list),
                onClick = { insertListItem(textFieldState) }
            )
            ToolbarButton(
                icon = Icons.Filled.HorizontalRule,
                contentDescription = stringResource(R.string.md_toolbar_rule),
                onClick = { insertHorizontalRule(textFieldState) }
            )
        }
    }
}

@Composable
private fun ToolbarButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(TOOLBAR_ICON_SIZE.dp)
        )
    }
}

/**
 * Wraps the current selection with [prefix] and [suffix].
 * If nothing is selected, inserts `prefix + suffix` and places cursor between them.
 */
private fun wrapSelection(state: TextFieldState, prefix: String, suffix: String) {
    state.edit {
        val sel = selection
        if (sel.collapsed) {
            // No selection — insert prefix+suffix at cursor, place cursor between
            insert(sel.start, "$prefix$suffix")
            selection = TextRange(sel.start + prefix.length)
        } else {
            // Wrap selected text
            val selectedText = toString().substring(sel.start, sel.end)
            delete(sel.start, sel.end)
            insert(sel.start, "$prefix$selectedText$suffix")
            selection = TextRange(sel.start + prefix.length, sel.start + prefix.length + selectedText.length)
        }
    }
}

/**
 * Inserts `# ` at start of current line, cycling H1→H2→H3→remove.
 */
private fun insertHeading(state: TextFieldState) {
    state.edit {
        val text = toString()
        val cursorPos = selection.start
        val lineStart = text.lastIndexOf('\n', cursorPos - 1) + 1

        when {
            text.startsWith("### ", lineStart) -> {
                // H3 → remove heading
                delete(lineStart, lineStart + "### ".length)
            }
            text.startsWith("## ", lineStart) -> {
                // H2 → H3
                insert(lineStart + "## ".length - 1, "#")
            }
            text.startsWith("# ", lineStart) -> {
                // H1 → H2
                insert(lineStart + "# ".length - 1, "#")
            }
            else -> {
                // No heading → H1
                insert(lineStart, "# ")
            }
        }
    }
}

/**
 * Inserts a Markdown link `[text](url)` using selected text as link text,
 * or a placeholder if nothing is selected.
 */
private fun insertLink(state: TextFieldState) {
    state.edit {
        val sel = selection
        if (sel.collapsed) {
            insert(sel.start, "[](${LINK_URL_PLACEHOLDER})")
            selection = TextRange(sel.start + 1)
        } else {
            val selectedText = toString().substring(sel.start, sel.end)
            delete(sel.start, sel.end)
            val link = "[$selectedText](${LINK_URL_PLACEHOLDER})"
            insert(sel.start, link)
            // Place cursor inside (url), selecting "url"
            val urlStart = sel.start + selectedText.length + LINK_BRACKET_OFFSET
            selection = TextRange(urlStart, urlStart + LINK_URL_PLACEHOLDER.length)
        }
    }
}

/**
 * Inserts `- ` at the start of the current line for an unordered list item.
 */
private fun insertListItem(state: TextFieldState) {
    state.edit {
        val text = toString()
        val cursorPos = selection.start
        val lineStart = text.lastIndexOf('\n', cursorPos - 1) + 1

        if (text.startsWith("- ", lineStart)) {
            // Already a list item — remove the prefix
            delete(lineStart, lineStart + "- ".length)
        } else {
            insert(lineStart, "- ")
        }
    }
}

/**
 * Inserts a horizontal rule `---` on a new line.
 */
private fun insertHorizontalRule(state: TextFieldState) {
    state.edit {
        val text = toString()
        val cursorPos = selection.start
        val prefix = if (cursorPos > 0 && text[cursorPos - 1] != '\n') "\n" else ""
        val rule = "$prefix---\n"
        insert(cursorPos, rule)
        selection = TextRange(cursorPos + rule.length)
    }
}
