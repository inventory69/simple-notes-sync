package dev.dettmer.simplenotes.ui.editor

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.TransferableContent
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.markdown.HtmlToMarkdown
import dev.dettmer.simplenotes.markdown.ImageAlign
import dev.dettmer.simplenotes.markdown.MarkdownEngine
import dev.dettmer.simplenotes.markdown.MarkdownOutputTransformation
import dev.dettmer.simplenotes.markdown.TapAnchor
import dev.dettmer.simplenotes.markdown.buildImageAlt
import dev.dettmer.simplenotes.markdown.computeImageRewrite
import dev.dettmer.simplenotes.markdown.toSourceOffset
import dev.dettmer.simplenotes.ui.editor.components.FramelessContentField
import dev.dettmer.simplenotes.ui.editor.components.applyTapAnchor
import dev.dettmer.simplenotes.ui.theme.Dimensions
import dev.dettmer.simplenotes.ui.theme.LocalNewDesign
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Text-Editor: Eingabefeld, Bild-Picker, Einfügen, Aufhaken von Markdown-Aufgaben. Aus NoteEditorScreen.kt ausgelagert.

// 🆕 v2.12.0: Obergrenze Multi-Bild-Picker (Beta-Feedback: mehrere Bilder auf einmal)
private const val MAX_PICKER_IMAGES = 10

private const val CHECKBOX_TAP_PREFIX_COLS = 5

/**
 * 🆕 Bild-Attachments: Photo-Picker-Launcher — ausgelagert, damit die Verzweigung
 * (uri null-check, attachImage-Ergebnis) nicht in NoteEditorScreens Cyclomatic Complexity zählt.
 */
@Composable
internal fun rememberImagePickerLauncher(
    viewModel: NoteEditorViewModel,
    textFieldState: TextFieldState,
    scope: CoroutineScope
) = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_PICKER_IMAGES)
) { uris ->
    if (uris.isNotEmpty()) {
        scope.launch { attachAndInsertImages(viewModel, textFieldState, uris) }
    }
}

/**
 * 🆕 v2.12.0: Hängt [uris] sequenziell als Assets an (Kompression + Store via
 * attachImage) und fügt je ein `![](.assets/…)` auf eigener Zeile ein.
 * Gemeinsamer Pfad für Multi-Picker und Clipboard-Bild-Paste.
 */
internal suspend fun attachAndInsertImages(
    viewModel: NoteEditorViewModel,
    textFieldState: TextFieldState,
    uris: List<Uri>
) {
    uris.forEach { uri ->
        viewModel.attachImage(uri)?.let { assetName ->
            insertImageMarkdownOnOwnLine(textFieldState, assetName, viewModel.defaultImageSizePercent)
        }
    }
}

/**
 * Fügt `![|sizePercent](.assets/…)` mit führendem Umbruch ein, wenn der Cursor nicht am
 * Zeilenanfang steht — bei mehreren Bildern landet so jeder Link auf einer eigenen Zeile
 * (Renderer erwartet Bild-Links als eigene Zeile).
 */
private fun insertImageMarkdownOnOwnLine(state: TextFieldState, assetName: String, sizePercent: Int) {
    state.edit {
        val start = selection.min
        val atLineStart = start == 0 || asCharSequence()[start - 1] == '\n'
        val alt = buildImageAlt("", sizePercent, ImageAlign.CENTER)
        val link = (if (atLineStart) "" else "\n") + "![$alt](.assets/$assetName)"
        insert(start, link)
        selection = TextRange(start + link.length)
    }
}

/**
 * 🆕 Bild-Attachments v2: Schreibt eine Größe/Ausrichtung-Auswahl aus dem Long-Press-Menü
 * zurück in die Markdown-Source. [computeImageRewrite] liefert `null` bei Asset-Mismatch/
 * Out-of-range-Ordinal (Text hat sich geändert) — dann ist es ein stiller No-op.
 */
internal fun applyImageTokenRewrite(
    textFieldState: TextFieldState,
    viewModel: NoteEditorViewModel,
    image: MarkdownEngine.MarkdownBlock.Image,
    sizePercent: Int,
    align: ImageAlign,
    altText: String
) {
    val rewrite = computeImageRewrite(
        textFieldState.text.toString(),
        image.ordinal,
        image.assetName,
        sizePercent,
        align,
        cleanAlt = altText
    ) ?: return
    val (range, replacement) = rewrite
    textFieldState.edit { replace(range.first, range.last + 1, replacement) }
    viewModel.updateContent(textFieldState.text.toString())
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TextNoteContent(
    textFieldState: TextFieldState,
    onContentChange: (String) -> Unit,
    focusRequester: FocusRequester,
    outputTransformation: MarkdownOutputTransformation,
    onPasteImages: (List<Uri>) -> Unit, // 🆕 v2.12.0
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
    tapAnchor: TapAnchor? = null, // UI-Beta (E10): Tippstelle aus dem Lesemodus
    onTapAnchorApplied: () -> Unit = {}
) {
    // 🆕 v1.8.2 (IMPL_07): Migration zu TextFieldState-API für scrollState-Unterstützung
    // v1.9.0 (F07): TextFieldState now provided from parent for toolbar access
    // UI-Beta (E2): im neuen Design ohne Rahmen, Text an derselben Stelle wie im Lesemodus
    val frameless = LocalNewDesign.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope() // 🆕 v2.12.0: async HTML-Konvertierung

    // Focus-State tracken für Auto-Scroll bei Tastaturöffnung
    var isFocused by remember { mutableStateOf(false) }

    // Layout-Result für Tap-to-Toggle (Task-Checkboxes)
    val textLayoutRef = remember { mutableStateOf<TextLayoutResult?>(null) }

    // Text-Änderungen an ViewModel propagieren
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .drop(1) // 🆕 v1.9.0: skip initial emission — snapshotFlow always emits current
            // value on first collect, but that's hydration, not a user edit
            .collect { newText ->
                onContentChange(newText)
            }
    }

    // E10: Cursor und Scroll an die Stelle, die im Lesemodus angetippt wurde.
    var anchored by remember { mutableStateOf(false) }
    LaunchedEffect(tapAnchor) {
        val anchor = tapAnchor ?: return@LaunchedEffect
        applyTapAnchor(anchor, textFieldState, scrollState) { snapshotFlow { textLayoutRef.value }.filterNotNull().first() }
        anchored = true
        onTapAnchorApplied()
    }

    // 🆕 v1.8.2 (IMPL_07): Auto-Scroll zum Ende wenn Fokus erhalten (Tastatur öffnet sich)
    // Delay gibt dem Layout Zeit, sich nach imePadding-Resize zu stabilisieren
    LaunchedEffect(isFocused) {
        if (isFocused && !anchored && tapAnchor == null) {
            delay(LAYOUT_DELAY_MS)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    val context = LocalContext.current

    val fieldModifier = modifier
        .focusRequester(focusRequester)
        .onFocusChanged { focusState ->
            isFocused = focusState.isFocused
            onFocusChanged(focusState.isFocused)
        }
        .contentReceiver { transferable ->
            handlePastedContent(transferable, context, textFieldState, scope, onPasteImages)
        }
    val checkboxTapModifier = Modifier.pointerInput(textFieldState) {
        awaitPointerEventScope {
            while (true) {
                toggleTaskCheckbox(
                    awaitPointerEvent(PointerEventPass.Initial),
                    textLayoutRef.value,
                    textFieldState,
                    scrollOffsetY = if (frameless) scrollState.value else 0
                )
            }
        }
    }
    // 🆕 v1.8.2: Auto-Großschreibung für Satzanfänge im Inhalt
    val keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)

    if (frameless) {
        FramelessContentField(
            textFieldState = textFieldState,
            modifier = fieldModifier
                .padding(horizontal = Dimensions.SpacingSmall)
                .then(checkboxTapModifier),
            keyboardOptions = keyboardOptions,
            scrollState = scrollState,
            outputTransformation = outputTransformation,
            onTextLayout = { textLayoutRef.value = it }
        )
        return
    }

    OutlinedTextField(
        state = textFieldState,
        modifier = fieldModifier.then(checkboxTapModifier),
        label = { Text(stringResource(R.string.content)) },
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(16.dp),
        // 🆕 v1.8.2 (IMPL_07): Externer ScrollState für programmatisches Auto-Scroll
        scrollState = scrollState,
        outputTransformation = outputTransformation,
        onTextLayout = { getResult -> textLayoutRef.value = getResult() }
    )
}

/**
 * 🆕 v2.12.0: Paste-Handler für das Content-Feld. Bilder zuerst („Bild kopieren"
 * in Chrome legt Uri **und** ein <img>-HTML-Fragment in die ClipData — das Bild ist
 * das, was der Nutzer will), dann HTML→Markdown. Rückgabe `null` = konsumiert,
 * sonst geht [transferable] den normalen Paste-Weg.
 *
 * HTML-Konvertierung läuft zweistufig: Rohtext synchron (eigener Undo-Eintrag),
 * Markdown-Ersetzung async off-main (zweiter Undo-Eintrag → direktes Undo stellt
 * den Rohtext wieder her).
 */
@OptIn(ExperimentalFoundationApi::class)
private fun handlePastedContent(
    transferable: TransferableContent,
    context: Context,
    textFieldState: TextFieldState,
    scope: CoroutineScope,
    onPasteImages: (List<Uri>) -> Unit
): TransferableContent? {
    val clipData = transferable.clipEntry.clipData
    val imageUris = (0 until clipData.itemCount)
        .mapNotNull { clipData.getItemAt(it).uri }
        .filter { context.contentResolver.getType(it)?.startsWith("image/") == true }
    if (imageUris.isNotEmpty()) {
        onPasteImages(imageUris)
        return null
    }

    val html = transferable.clipEntry.firstHtmlText()
    if (html == null || !HtmlToMarkdown.hasRichContent(html)) {
        return transferable // nicht zuständig → normaler Paste
    }
    // ponytail: gegen die style-gestrippte Länge messen — Chrome inlined computed
    // styles, die den Clip weit über den Cap blähen; convert() bekommt trotzdem das
    // Original-HTML (off-main, Größe egal dort), damit style-Attribut-Fett/Kursiv bleibt.
    val measured = HtmlToMarkdown.stripStyleAttributes(html).length
    if (measured > HtmlToMarkdown.MAX_HTML_LENGTH) {
        Logger.d("HtmlPaste", "over cap: raw=${html.length} stripped=$measured → plaintext fallback")
        return transferable // nicht zuständig → normaler Paste
    }

    val plain = clipData.getItemAt(0).coerceToText(context).toString()
    val sel = textFieldState.selection
    val start = sel.min
    // Stufe 1 (synchron): Rohtext einfügen
    textFieldState.edit {
        if (!sel.collapsed) delete(sel.min, sel.max)
        insert(start, plain)
        selection = TextRange(start + plain.length)
    }
    // Stufe 2 (async, off-main): zu Markdown ersetzen
    scope.launch {
        val markdown = withContext(Dispatchers.Default) { HtmlToMarkdown.convert(html, plain) }
        if (markdown == plain) Logger.d("HtmlPaste", "convert() returned fallback: raw=${html.length}")
        val end = start + plain.length
        // Guard: nur ersetzen, wenn der Rohtext dort noch unverändert steht
        // (Nutzer könnte während der Konvertierung getippt haben)
        if (markdown != plain &&
            textFieldState.text.length >= end &&
            textFieldState.text.subSequence(start, end).toString() == plain
        ) {
            textFieldState.edit {
                replace(start, end, markdown)
                selection = TextRange(start + markdown.length)
            }
        }
    }
    return null // konsumiert
}

/** First non-blank `text/html` payload across all clip items, or null. */
private fun ClipEntry.firstHtmlText(): String? {
    val data = clipData
    for (i in 0 until data.itemCount) {
        val html = data.getItemAt(i).htmlText
        if (!html.isNullOrBlank()) return html
    }
    return null
}

/**
 * Tipp auf `[ ]`/`[x]` am Zeilenanfang schaltet die Aufgabe um. [scrollOffsetY]: ohne Rahmen (UI-Beta)
 * liegt der Tipp im Koordinatenraum des Textes, nur der Scroll fehlt; mit Rahmen 0 wie bisher.
 */
private fun toggleTaskCheckbox(event: PointerEvent, layout: TextLayoutResult?, textFieldState: TextFieldState, scrollOffsetY: Int) {
    if (event.type != PointerEventType.Press) return
    val rawPos = event.changes.firstOrNull()?.position ?: return
    val tapPos = rawPos.copy(y = rawPos.y + scrollOffsetY)
    if (layout == null) return
    val rawText = textFieldState.text.toString()
    val charOff = toSourceOffset(rawText, layout.getOffsetForPosition(tapPos))
    val lineStartOff = rawText.lastIndexOf('\n', (charOff - 1).coerceAtLeast(0))
        .let { if (it < 0) 0 else it + 1 }
    if (charOff - lineStartOff > CHECKBOX_TAP_PREFIX_COLS) return
    val lineEndOff = rawText.indexOf('\n', lineStartOff).let { if (it < 0) rawText.length else it }
    val rawLine = rawText.substring(lineStartOff, lineEndOff)
    val taskMatch = MarkdownEngine.TASK_LIST_REGEX.matchEntire(rawLine) ?: return
    val isChecked = taskMatch.groupValues[1].equals("x", ignoreCase = true)
    // Aus dem Match statt fixem Offset: der Marker darf eingerückt sein, "*"/"+" heißen,
    // und "- []" hat gar kein Zeichen zwischen den Klammern (replace über Länge 0 = Insert).
    val open = lineStartOff + rawLine.indexOf('[')
    val close = lineStartOff + rawLine.indexOf(']')
    textFieldState.edit {
        replace(open + 1, close, if (isChecked) " " else "x")
    }
}
