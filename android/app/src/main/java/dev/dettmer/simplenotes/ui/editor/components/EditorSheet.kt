package dev.dettmer.simplenotes.ui.editor.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationSource
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.markdown.MarkdownOutputTransformation
import dev.dettmer.simplenotes.markdown.TapAnchor
import dev.dettmer.simplenotes.ui.theme.Dimensions
import kotlin.math.roundToInt

// UI-Beta: Bausteine für den Editor als Blatt (E1, E5, E7, E9). Der alte Editor nutzt sie nicht.

/** E7: so weit läuft die Notizfarbe von oben in die Seite aus. */
private val GRADIENT_HEIGHT = 330.dp
private const val GRADIENT_ALPHA_DARK = 0.7f
private const val GRADIENT_ALPHA_LIGHT = 0.75f

/**
 * E7: Notizfarbe als Verlauf von oben statt 3-dp-Strich. Malt die Fläche selbst (Scaffold und Leiste
 * sind durchsichtig), damit der Verlauf hinter Statusleiste und Leiste beginnt. Der Text darunter
 * steht auf neutralem Grund, weil der Verlauf nach [GRADIENT_HEIGHT] ausgelaufen ist.
 */
fun Modifier.noteColorGradient(accent: Color?, surface: Color, isDark: Boolean): Modifier = drawBehind {
    drawRect(surface)
    if (accent != null) {
        val alpha = if (isDark) GRADIENT_ALPHA_DARK else GRADIENT_ALPHA_LIGHT
        drawRect(
            Brush.verticalGradient(
                0f to accent.copy(alpha = alpha),
                1f to Color.Transparent,
                endY = GRADIENT_HEIGHT.toPx()
            )
        )
    }
}

/**
 * E9: wie weit die Tastatur offen ist (0 bis 1), in jedem Bild aus den Insets. Nur in Layout oder Zeichnen
 * lesen, dann folgt der Kopf der Tastatur, ohne dass der Bildschirm pro Bild neu komponiert.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun rememberImeOpenFraction(): State<Float> {
    val ime = WindowInsets.ime
    val source = WindowInsets.imeAnimationSource
    val target = WindowInsets.imeAnimationTarget
    val density = LocalDensity.current
    return remember(ime, source, target, density) {
        derivedStateOf {
            val full = maxOf(source.getBottom(density), target.getBottom(density))
            if (full == 0) 0f else (ime.getBottom(density).toFloat() / full).coerceIn(0f, 1f)
        }
    }
}

/** E9: schiebt den Inhalt um [fraction] nach oben aus dem Bild (0 = ganz da, 1 = weg) und blendet ihn dabei aus. */
internal fun Modifier.collapseUp(fraction: () -> Float): Modifier = clipToBounds().layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val f = fraction()
    val height = (placeable.height * (1f - f)).roundToInt()
    layout(placeable.width, height) {
        placeable.placeWithLayer(0, height - placeable.height) { alpha = 1f - f }
    }
}

/** E1: Titel ohne Rahmen als große Überschrift, Platzhalter statt Label. */
@Composable
fun SheetTitleField(
    title: String,
    onTitleChange: (String) -> Unit,
    keyboardActions: KeyboardActions,
    modifier: Modifier = Modifier
) {
    PlainTitleField(
        title = title,
        onTitleChange = onTitleChange,
        keyboardActions = keyboardActions,
        style = MaterialTheme.typography.headlineMedium,
        // Gleicher Rand wie der Text darunter (MarkdownPreview und Feld nutzen SpacingSmall).
        modifier = modifier.padding(horizontal = Dimensions.SpacingSmall, vertical = Dimensions.SpacingSmall)
    )
}

/**
 * E9 (#131): Titel in der Leiste, solange die Tastatur offen ist. Zeigt Titel (nur mit [showTitle]) und [meta] (Wortzahl);
 * Tippen auf den Titel macht die Leiste zum Titelfeld, Weiter springt zurück in den Text ([onEditingDone] mit true).
 */
@Composable
fun EditorBarTitle(
    title: String,
    showTitle: Boolean,
    editing: Boolean,
    onTitleChange: (String) -> Unit,
    onStartEditing: () -> Unit,
    onEditingDone: (backToText: Boolean) -> Unit,
    focusRequester: FocusRequester,
    meta: @Composable () -> Unit
) {
    if (editing) {
        var hadFocus by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        PlainTitleField(
            title = title,
            onTitleChange = onTitleChange,
            keyboardActions = KeyboardActions(onNext = { onEditingDone(true) }),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged {
                    // Fokus woanders hin (Tipp in den Text, Tastatur zu): Leiste wird wieder Titelanzeige,
                    // ohne den Text neu zu fokussieren, sonst ginge die gerade geschlossene Tastatur wieder auf.
                    if (hadFocus && !it.isFocused) onEditingDone(false)
                    hadFocus = it.isFocused
                }
        )
    } else {
        // Schalter „Titelfeld“ aus und kein Titel: nur die Wortzahl, nichts zum Antippen.
        Column(modifier = if (showTitle) Modifier.clickable(onClick = onStartEditing) else Modifier) {
            if (showTitle) {
                Text(
                    text = title.ifEmpty { stringResource(R.string.title) },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (title.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            meta()
        }
    }
}

@Composable
private fun PlainTitleField(
    title: String,
    onTitleChange: (String) -> Unit,
    keyboardActions: KeyboardActions,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val placeholder = stringResource(R.string.title)
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant
    BasicTextField(
        value = title,
        onValueChange = onTitleChange,
        modifier = modifier,
        textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
        singleLine = true, // wie bisher: Enter springt weiter statt Zeilenumbruch
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Next
        ),
        keyboardActions = keyboardActions,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            Box {
                if (title.isEmpty()) Text(placeholder, style = style, color = placeholderColor)
                inner()
            }
        }
    )
}

/**
 * E5: Speichern-Knopf zeigt den Zustand: Haken, wenn alles gespeichert ist ([saved]), Diskette bei offenen
 * Änderungen oder einer neuen, nie gespeicherten Notiz. Tippen speichert in beiden Fällen sofort.
 * Im alten Design immer die Diskette.
 */
@Composable
fun SaveStateButton(saved: Boolean, onSave: () -> Unit) {
    IconButton(onClick = onSave) {
        Icon(
            imageVector = if (saved) Icons.Default.Done else Icons.Default.Save,
            contentDescription = stringResource(if (saved) R.string.editor_saved else R.string.save)
        )
    }
}

/**
 * E10 Stufe 1: Cursor an dieselbe Höhe wie der Tipp im Lesemodus, Scroll so, dass sie unter dem
 * Finger bleibt. Genau im getippten Absatz (Stufe 2) bräuchte Quellpositionen je Block in der Engine.
 */
internal suspend fun applyTapAnchor(
    anchor: TapAnchor,
    textFieldState: TextFieldState,
    scrollState: ScrollState,
    awaitLayout: suspend () -> TextLayoutResult
) {
    val layout = awaitLayout()
    val contentY = anchor.contentY.coerceAtMost(layout.size.height.toFloat())
    val offset = layout.getOffsetForPosition(Offset(anchor.x, contentY))
    textFieldState.edit { selection = TextRange(offset) }
    scrollState.scrollTo((contentY - anchor.viewportY).toInt().coerceIn(0, scrollState.maxValue))
}

/** E2: Inhalt ohne Rahmen, gleiche Ränder (SpacingSmall) und Schrift (bodyLarge) wie MarkdownPreview. */
@Composable
internal fun FramelessContentField(
    textFieldState: TextFieldState,
    modifier: Modifier,
    keyboardOptions: KeyboardOptions,
    scrollState: ScrollState,
    outputTransformation: MarkdownOutputTransformation,
    onTextLayout: (TextLayoutResult?) -> Unit
) {
    val placeholder = stringResource(R.string.content)
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant
    BasicTextField(
        state = textFieldState,
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        keyboardOptions = keyboardOptions,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        scrollState = scrollState,
        outputTransformation = outputTransformation,
        onTextLayout = { getResult -> onTextLayout(getResult()) },
        decorator = { innerTextField ->
            Box {
                if (textFieldState.text.isEmpty()) {
                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = placeholderColor)
                }
                innerTextField()
            }
        }
    )
}
