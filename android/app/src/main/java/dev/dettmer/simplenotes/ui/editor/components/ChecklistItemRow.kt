package dev.dettmer.simplenotes.ui.editor.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.editor.ChecklistItemState

/**
 * A single row in the checklist editor with drag handle, checkbox, text input, and delete button.
 *
 * v1.5.0: Jetpack Compose NoteEditor Redesign
 * v1.8.0: Long text UX improvements (gradient fade, auto-expand on focus)
 */
@Composable
fun ChecklistItemRow(
    item: ChecklistItemState,
    onTextChange: (String) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onAddNewItem: () -> Unit,
    requestFocus: Boolean = false,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var textFieldValue by remember(item.id) {
        mutableStateOf(TextFieldValue(text = item.text, selection = TextRange(item.text.length)))
    }

    // 🆕 v1.8.0: Focus-State tracken für Expand/Collapse
    var isFocused by remember { mutableStateOf(false) }

    // 🆕 v1.8.0: Overflow erkennen (Text länger als maxLines)
    var hasOverflow by remember { mutableStateOf(false) }

    // 🆕 v1.8.0: Dynamische maxLines basierend auf Focus
    val currentMaxLines = if (isFocused) Int.MAX_VALUE else COLLAPSED_MAX_LINES
    
    // v1.5.0: Auto-focus AND show keyboard when requestFocus is true (new items)
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    
    // Update text field when external state changes
    LaunchedEffect(item.text) {
        if (textFieldValue.text != item.text) {
            textFieldValue = TextFieldValue(
                text = item.text,
                selection = TextRange(item.text.length)
            )
        }
    }
    
    val alpha = if (item.isChecked) 0.6f else 1.0f
    val textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
    
    @Suppress("MagicNumber") // UI padding values are self-explanatory
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top  // 🆕 v1.8.0: Top statt CenterVertically für lange Texte
    ) {
        // Drag Handle
        Icon(
            imageVector = Icons.Default.DragHandle,
            contentDescription = stringResource(R.string.drag_to_reorder),
            modifier = Modifier
                .size(24.dp)
                .padding(top = 12.dp)  // 🆕 v1.8.0: Visuell am oberen Rand ausrichten
                .alpha(0.5f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.width(4.dp))
        
        // Checkbox
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.alpha(alpha)
        )
        
        Spacer(modifier = Modifier.width(4.dp))

        // 🆕 v1.8.0: Text Input mit Overflow-Gradient
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = { newValue ->
                    // Check for newline (Enter key)
                    if (newValue.text.contains("\n")) {
                        val cleanText = newValue.text.replace("\n", "")
                        textFieldValue = TextFieldValue(
                            text = cleanText,
                            selection = TextRange(cleanText.length)
                        )
                        onTextChange(cleanText)
                        onAddNewItem()
                    } else {
                        textFieldValue = newValue
                        onTextChange(newValue.text)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                    }
                    .alpha(alpha),
                textStyle = LocalTextStyle.current.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = textDecoration
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { onAddNewItem() }
                ),
                singleLine = false,
                maxLines = currentMaxLines,  // 🆕 v1.8.0: Dynamisch
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                onTextLayout = { textLayoutResult ->
                    // 🆕 v1.8.0: Overflow erkennen
                    hasOverflow = textLayoutResult.hasVisualOverflow ||
                        textLayoutResult.lineCount > COLLAPSED_MAX_LINES
                },
                decorationBox = { innerTextField ->
                    Box {
                        if (textFieldValue.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.item_placeholder),
                                style = LocalTextStyle.current.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // 🆕 v1.8.0: Gradient-Fade Overlay wenn Text überläuft
            // Zeige nur Gradient oben, da man am unteren Rand startet und nach oben scrollt
            if (hasOverflow && !isFocused) {
                // Gradient oben (zeigt: es gibt Text oberhalb der sichtbaren Zeilen)
                OverflowGradient(
                    modifier = Modifier.align(Alignment.TopCenter),
                    isTopGradient = true
                )
            }
        }
        
        Spacer(modifier = Modifier.width(4.dp))
        
        // Delete Button
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(36.dp)
                .padding(top = 4.dp)  // 🆕 v1.8.0: Ausrichtung mit Top-aligned Text
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.delete_item),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// 🆕 v1.8.0: Maximum lines when collapsed (not focused)
private const val COLLAPSED_MAX_LINES = 5

// ════════════════════════════════════════════════════════════════
// 🆕 v1.8.0: Preview Composables for Manual Testing
// ════════════════════════════════════════════════════════════════

@Preview(showBackground = true)
@Composable
private fun ChecklistItemRowShortTextPreview() {
    ChecklistItemRow(
        item = ChecklistItemState(
            id = "preview-1",
            text = "Kurzer Text",
            isChecked = false
        ),
        onTextChange = {},
        onCheckedChange = {},
        onDelete = {},
        onAddNewItem = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun ChecklistItemRowLongTextPreview() {
    ChecklistItemRow(
        item = ChecklistItemState(
            id = "preview-2",
            text = "Dies ist ein sehr langer Text der sich über viele Zeilen erstreckt " +
                "und dazu dient den Overflow-Gradient zu demonstrieren. Er hat deutlich " +
                "mehr als fünf Zeilen wenn er in der normalen Breite eines Smartphones " +
                "angezeigt wird und sollte einen schönen Fade-Effekt am unteren Rand zeigen. " +
                "Dieser zusätzliche Text sorgt dafür, dass wir wirklich genug Zeilen haben " +
                "um den Gradient sichtbar zu machen.",
            isChecked = false
        ),
        onTextChange = {},
        onCheckedChange = {},
        onDelete = {},
        onAddNewItem = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun ChecklistItemRowCheckedPreview() {
    ChecklistItemRow(
        item = ChecklistItemState(
            id = "preview-3",
            text = "Erledigte Aufgabe mit durchgestrichenem Text",
            isChecked = true
        ),
        onTextChange = {},
        onCheckedChange = {},
        onDelete = {},
        onAddNewItem = {}
    )
}
