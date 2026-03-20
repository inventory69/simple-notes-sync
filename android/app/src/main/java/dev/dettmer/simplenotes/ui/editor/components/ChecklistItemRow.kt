package dev.dettmer.simplenotes.ui.editor.components

import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.BuildConfig
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.editor.ChecklistItemState
import kotlinx.coroutines.delay

/**
 * A single row in the checklist editor with drag handle, checkbox, text input, and delete button.
 *
 * v1.5.0: Jetpack Compose NoteEditor Redesign
 * v1.8.0: Long text UX improvements (gradient fade, auto-expand on focus)
 * v1.8.0: IMPL_023 - Enlarged drag handle (48dp touch target) + drag modifier
 *
 * Note: Using 10 parameters for Composable is acceptable for complex UI components.
 * @suppress LongParameterList - Composables naturally have many parameters
 */
@Suppress("LongParameterList")
@Composable
fun ChecklistItemRow(
    item: ChecklistItemState,
    onTextChange: (String) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onAddNewItem: () -> Unit,
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier, // 🆕 v1.8.0: IMPL_023 - Drag modifier for handle
    requestFocus: Boolean = false,
    isDragging: Boolean = false, // 🆕 v1.8.0: IMPL_023 - Drag state
    isAnyItemDragging: Boolean = false, // 🆕 v1.8.0: IMPL_023 - Hide gradient during any drag
    onHeightChanged: (() -> Unit)? = null // 🆕 v1.8.1: IMPL_05 - Auto-scroll callback
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    var textFieldValue by remember(item.id) {
        mutableStateOf(TextFieldValue(text = item.text, selection = TextRange(0)))
    }

    // 🆕 v1.8.0: Focus-State tracken für Expand/Collapse
    var isFocused by remember { mutableStateOf(false) }

    // 🆕 v1.8.0: Overflow erkennen (Text länger als maxLines)
    var hasOverflow by remember { mutableStateOf(false) }

    // 🆕 v1.8.0: Höhe für collapsed-Ansicht (aus TextLayout berechnet)
    var collapsedHeightDp by remember { mutableStateOf<Dp?>(null) }

    // 🆕 v1.8.0: ScrollState für dynamischen Gradient
    val scrollState = rememberScrollState()

    // 🆕 v1.8.1: IMPL_05 - Letzte Zeilenanzahl tracken für Auto-Scroll
    var lastLineCount by remember { mutableIntStateOf(0) }

    // Gradient-Sichtbarkeit via derivedStateOf: alle Conditions inline (kein stale-val Problem)
    // rememberUpdatedState für isAnyItemDragging-Parameter, da kein Compose-State
    val currentIsAnyItemDragging by rememberUpdatedState(isAnyItemDragging)
    // IMPL_29e: Per-item isDragging — nur DAS gedraggte Item expandiert (Bug 3 Fix)
    val currentIsDragging by rememberUpdatedState(isDragging)
    val showTopGradient by remember {
        derivedStateOf {
            hasOverflow &&
                collapsedHeightDp != null &&
                !isFocused &&
                !currentIsAnyItemDragging &&
                scrollState.value > 0
        }
    }
    val showBottomGradient by remember {
        derivedStateOf {
            hasOverflow &&
                collapsedHeightDp != null &&
                !isFocused &&
                !currentIsAnyItemDragging &&
                scrollState.value < scrollState.maxValue
        }
    }

    // v1.5.0: Auto-focus AND show keyboard when requestFocus is true (new items)
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // IMPL_29d: Clear focus when drag starts on THIS item.
    // Prevents oversize (424px instead of 271px) which causes viewport overflow after separator crossing.
    LaunchedEffect(isDragging) {
        if (isDragging && isFocused) {
            focusManager.clearFocus()
        }
    }

    // 🆕 v1.8.0: Cursor ans Ende setzen wenn fokussiert (für Bearbeitung)
    LaunchedEffect(isFocused) {
        if (isFocused && textFieldValue.selection.start == 0) {
            textFieldValue = textFieldValue.copy(
                selection = TextRange(textFieldValue.text.length)
            )
        }
    }

    // Update text field when external state changes
    LaunchedEffect(item.text) {
        if (textFieldValue.text != item.text) {
            textFieldValue = TextFieldValue(
                text = item.text,
                selection = if (isFocused) TextRange(item.text.length) else TextRange(0)
            )
        }
    }

    // IMPL_29b: Force gradient state refresh after drag ends.
    // During drag, onTextLayout guard prevents hasOverflow/collapsedHeightDp updates.
    // After drag, onTextLayout may not refire (no text change).
    // Coercing scroll position triggers scrollState change → gradient derivedStateOf re-evaluates.
    LaunchedEffect(isAnyItemDragging) {
        if (!isAnyItemDragging && hasOverflow && !isFocused) {
            // IMPL_29d: Wait for layout pass to re-establish scrollState.maxValue after
            // verticalScroll modifier is re-added. Without delay, maxValue may still be 0.
            delay(DRAG_END_LAYOUT_DELAY_MS)
            if (scrollState.maxValue > 0) {
                scrollState.scrollTo(scrollState.value.coerceIn(0, scrollState.maxValue))
            }
        }
    }

    // IMPL_29d: Animated alpha for gradient fade-in/out (~200ms)
    val topGradientAlpha by animateFloatAsState(
        targetValue = if (showTopGradient) 1f else 0f,
        animationSpec = tween(GRADIENT_FADE_DURATION_MS),
        label = "topGradientAlpha"
    )
    val bottomGradientAlpha by animateFloatAsState(
        targetValue = if (showBottomGradient) 1f else 0f,
        animationSpec = tween(GRADIENT_FADE_DURATION_MS),
        label = "bottomGradientAlpha"
    )

    val alpha = if (item.isChecked) 0.6f else 1.0f
    val textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None

    @Suppress("MagicNumber") // UI padding values are self-explanatory
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 8.dp, top = 4.dp, bottom = 4.dp), // 🆕 v1.8.0: IMPL_023 - links kein Padding (Handle hat eigene Fläche)
        verticalAlignment = if (hasOverflow) Alignment.Top else Alignment.CenterVertically // 🆕 v1.8.0: Dynamisch
    ) {
        // 🆕 v1.8.0: IMPL_023 - Vergrößerter Drag Handle (48dp Touch-Target)
        Box(
            modifier = dragModifier
                .size(48.dp) // Material Design minimum touch target
                .alpha(if (isDragging) 1.0f else 0.6f), // Visual feedback beim Drag
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = stringResource(R.string.drag_to_reorder),
                modifier = Modifier.size(28.dp), // Icon größer als vorher (24dp → 28dp)
                tint = if (isDragging) {
                    MaterialTheme.colorScheme.primary // Primary color während Drag
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        // Checkbox
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.alpha(alpha)
        )

        Spacer(modifier = Modifier.width(4.dp))

        // 🆕 v1.8.0: Text Input mit dynamischem Overflow-Gradient
        Box(modifier = Modifier.weight(1f)) {
            // Scrollbarer Wrapper: begrenzt Höhe auf ~5 Zeilen wenn collapsed.
            // IMPL_29f F1: Gedraggtes Item behält IMMER seine collapsed Höhe.
            // Height-Change während Drag → Layout-Repass → Pointer-Verlust (RC-1).
            // Expansion erst nach Drop (isDragging → false).
            Box(
                modifier = if (currentIsDragging) {
                    // Collapsed bleiben (gleiche heightIn), aber ohne verticalScroll
                    // (unnötig, da Item während Drag nicht interaktiv ist).
                    collapsedHeightDp?.takeIf { hasOverflow }?.let { height ->
                        Modifier.heightIn(max = height)
                    } ?: Modifier
                } else {
                    collapsedHeightDp?.takeIf { !isFocused && hasOverflow }?.let { height ->
                        Modifier
                            .heightIn(max = height)
                            .verticalScroll(scrollState)
                    } ?: Modifier
                }
            ) {
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
                    // 🆕 v1.8.2: Auto-Großschreibung für Satzanfänge in Checklist-Items
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { onAddNewItem() }
                    ),
                    singleLine = false,
                    // 🆕 v1.8.1: maxLines IMMER Int.MAX_VALUE — keine Oszillation möglich
                    // Höhenbegrenzung erfolgt ausschließlich über heightIn-Modifier oben.
                    // Vorher: maxLines=5 → lineCount gedeckelt → Overflow nie erkannt → Deadlock
                    maxLines = Int.MAX_VALUE,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    onTextLayout = { textLayoutResult ->
                        // 🆕 v1.8.1: lineCount ist jetzt akkurat (maxLines=MAX_VALUE deckelt nicht)
                        val lineCount = textLayoutResult.lineCount
                        // IMPL_29e: Nur das gedraggte Item pausiert Overflow-Erkennung.
                        // Nicht-gedraggte Items dürfen auch während eines Drags aktualisieren (Bug 2).
                        if (!isDragging) {
                            val overflow = lineCount > COLLAPSED_MAX_LINES
                            hasOverflow = overflow
                            // Höhe der ersten 5 Zeilen berechnen (einmalig)
                            if (overflow && collapsedHeightDp == null) {
                                collapsedHeightDp = with(density) {
                                    textLayoutResult.getLineBottom(COLLAPSED_MAX_LINES - 1).toDp()
                                }
                            }
                            // Reset wenn Text gekürzt wird
                            if (!overflow) {
                                collapsedHeightDp = null
                            }
                        }
                        if (BuildConfig.DEBUG && lineCount > COLLAPSED_MAX_LINES) {
                            Log.d(
                                "DragDrop",
                                "[GRADIENT:${item.id.takeLast(6)}] " +
                                    "lines=$lineCount overflow=$hasOverflow " +
                                    "collapsed=$collapsedHeightDp isDrag=$isDragging focused=$isFocused"
                            )
                        }
                        // 🆕 v1.8.1 (IMPL_05): Höhenänderung bei Zeilenumbruch melden
                        if (isFocused && lineCount > lastLineCount && lastLineCount > 0) {
                            onHeightChanged?.invoke()
                        }
                        lastLineCount = lineCount
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
            }

            // 🆕 v1.8.0: Dynamischer Gradient basierend auf Scroll-Position
            // IMPL_29d: animateFloatAsState für sanften Fade-Effekt (~200ms)
            // Oben: sichtbar wenn nach unten gescrollt (Text oberhalb versteckt)
            if (topGradientAlpha > 0f) {
                OverflowGradient(
                    modifier = Modifier.align(Alignment.TopCenter).alpha(topGradientAlpha),
                    isTopGradient = true
                )
            }

            // Unten: sichtbar wenn noch Text unterhalb vorhanden
            if (bottomGradientAlpha > 0f) {
                OverflowGradient(
                    modifier = Modifier.align(Alignment.BottomCenter).alpha(bottomGradientAlpha),
                    isTopGradient = false
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Delete Button
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(36.dp)
                .padding(top = 4.dp) // 🆕 v1.8.0: Ausrichtung mit Top-aligned Text
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

// IMPL_29d: Duration for gradient fade animation (ms)
private const val GRADIENT_FADE_DURATION_MS = 200

// IMPL_29d: Delay after drag ends to allow layout pass before reading scrollState.maxValue
private const val DRAG_END_LAYOUT_DELAY_MS = 50L

// ════════════════════════════════════════════════════════════════
// 🆕 v1.8.0: Preview Composables for Manual Testing
// ════════════════════════════════════════════════════════════════

@Suppress("UnusedPrivateMember")
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
        onAddNewItem = {},
        isDragging = false,
        dragModifier = Modifier
    )
}

@Suppress("UnusedPrivateMember")
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
        onAddNewItem = {},
        isDragging = false,
        dragModifier = Modifier
    )
}

@Suppress("UnusedPrivateMember")
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
        onAddNewItem = {},
        isDragging = false,
        dragModifier = Modifier
    )
}

// 🆕 v1.8.0: IMPL_023 - Preview for dragging state
@Suppress("UnusedPrivateMember")
@Preview(showBackground = true)
@Composable
private fun ChecklistItemRowDraggingPreview() {
    ChecklistItemRow(
        item = ChecklistItemState(
            id = "preview-4",
            text = "Wird gerade verschoben - Handle ist highlighted",
            isChecked = false
        ),
        onTextChange = {},
        onCheckedChange = {},
        onDelete = {},
        onAddNewItem = {},
        isDragging = true,
        dragModifier = Modifier
    )
}
