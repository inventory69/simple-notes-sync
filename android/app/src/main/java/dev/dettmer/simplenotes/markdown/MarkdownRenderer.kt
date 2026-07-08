package dev.dettmer.simplenotes.markdown

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
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
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.images.orientationSwapsAxes
import dev.dettmer.simplenotes.images.readExifOrientation
import dev.dettmer.simplenotes.markdown.MarkdownEngine.MarkdownBlock
import dev.dettmer.simplenotes.storage.AssetStore
import dev.dettmer.simplenotes.ui.theme.Dimensions
import dev.dettmer.simplenotes.utils.truncate
import java.io.File

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
    compactHeaders: Boolean = false,
    onImageTokensChange: ((image: MarkdownBlock.Image, sizePercent: Int, align: ImageAlign, altText: String) -> Unit)? = null
) {
    val bodyStyle = if (compactHeaders) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge
    // Fullscreen-Viewer + Long-Press-Menü sind self-contained: kein Wiring in den Consumern nötig.
    var viewerAsset by remember { mutableStateOf<String?>(null) }
    var menuTarget by remember { mutableStateOf<MarkdownBlock.Image?>(null) }
    var infoAsset by remember { mutableStateOf<String?>(null) }

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
                        ParagraphBlock(
                            block.text,
                            bodyStyle,
                            startOrdinal = block.startOrdinal,
                            onImageTap = { viewerAsset = it },
                            onImageLongPress = onImageTokensChange?.let { { image -> menuTarget = image } }
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

                    is MarkdownBlock.Image -> {
                        ImageBlock(
                            image = block,
                            onTap = { viewerAsset = block.assetName },
                            onLongPress = onImageTokensChange?.let { { menuTarget = block } }
                        )
                        Spacer(modifier = Modifier.height(Dimensions.SpacingMediumLarge))
                    }
                }
            }
        }
    }

    val menuImage = menuTarget
    if (menuImage != null && onImageTokensChange != null) {
        val context = LocalContext.current
        val assetFile = remember(context, menuImage.assetName) { AssetStore(context).getAssetFile(menuImage.assetName) }
        ImageActionsMenu(
            assetFile = assetFile,
            currentSize = menuImage.sizePercent,
            currentAlign = menuImage.align,
            currentAlt = menuImage.altText,
            onSelect = { size, align, altText ->
                onImageTokensChange(menuImage, size, align, altText)
                menuTarget = menuImage.copy(sizePercent = size, align = align, altText = altText)
            },
            onInfoClick = { infoAsset = menuImage.assetName },
            onDismiss = { menuTarget = null }
        )
    }

    val infoAssetName = infoAsset
    if (infoAssetName != null) {
        val context = LocalContext.current
        val assetFile = remember(context, infoAssetName) { AssetStore(context).getAssetFile(infoAssetName) }
        ImageInfoDialog(assetFile = assetFile, onDismiss = { infoAsset = null })
    }

    val viewerAssetName = viewerAsset
    if (viewerAssetName != null) {
        ImageViewerDialog(assetName = viewerAssetName, onDismiss = { viewerAsset = null })
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

private fun ImageAlign.toBoxAlignment(): Alignment = when (this) {
    ImageAlign.LEFT -> Alignment.CenterStart
    ImageAlign.CENTER -> Alignment.Center
    ImageAlign.RIGHT -> Alignment.CenterEnd
    // Die Engine kann INLINE auf einem Block-Image nicht produzieren — defensiver Fallback.
    ImageAlign.INLINE -> Alignment.Center
}

/**
 * 🆕 Bild-Attachments: Rendert einen Block-Bild-Link. Fehlt die Asset-Datei (Notiz vor
 * Asset da, manuell getippter Link, noch nicht gesyncte Desktop-Assets) oder schlägt das
 * Decoding fehl, wird ein Platzhalter mit Alt-Text gezeigt statt eines leeren Bereichs
 * (unverändert: volle Breite, kein Menü/Tap).
 *
 * [onLongPress] ist `null`, wenn der Preview-Consumer kein `onImageTokensChange` übergeben
 * hat (z.B. Changelog/PDF-Vorschau) — das Menü ist dann aus.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ImageBlock(image: MarkdownBlock.Image, onTap: () -> Unit, onLongPress: (() -> Unit)?) {
    val context = LocalContext.current
    val assetFile = remember(context, image.assetName) { AssetStore(context).getAssetFile(image.assetName) }
    var loadFailed by remember(image.assetName) { mutableStateOf(false) }
    val aspect = remember(assetFile) { decodeAspectRatio(assetFile) }

    if (!assetFile.exists() || loadFailed || aspect == null) {
        ImagePlaceholder(image.altText)
        return
    }

    val fraction = image.sizePercent / 100f

    // DisableSelection zwingend: Preview liegt in SelectionContainer, Long-Press würde sonst
    // Textselektion starten statt das Menü zu öffnen.
    // key() erzwingt eine frische AsyncImage-Instanz (und damit einen neuen Coil-Request) bei
    // Size-Änderung — Coil3s Input.equals() vergleicht sonst die SizeResolver-Instanz statt der
    // aufgelösten Größe und redecoded nicht, das Bild wird nur pixelig hochskaliert.
    DisableSelection {
        key(image.assetName, image.sizePercent) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = image.align.toBoxAlignment()) {
                AsyncImage(
                    model = assetFile,
                    contentDescription = image.altText.ifBlank { null },
                    contentScale = ContentScale.Fit,
                    onError = { loadFailed = true },
                    // ponytail: Höhe wird deterministisch aus der Bounds-only-decodierten Aspect-Ratio
                    // abgeleitet statt aus coil3s (asynchron gelieferter, unter unbounded-height-
                    // verticalScroll gecachter) Intrinsic-Size — die wächst sonst bei Fraction-Änderung
                    // nicht mit. Falls extreme Panoramen mal stören, harte Obergrenze nachrüsten.
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .aspectRatio(aspect)
                        .combinedClickable(onClick = onTap, onLongClick = onLongPress)
                )
            }
        }
    }
}

@Composable
private fun ImagePlaceholder(altText: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(Dimensions.SpacingMediumLarge)
    ) {
        Icon(
            imageVector = Icons.Outlined.BrokenImage,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(Dimensions.SpacingMedium))
        Text(
            text = altText.ifBlank { stringResource(R.string.markdown_image_missing) },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
        appendFormattedMatch(match, linkColor, codeBackground, codeColor)
        pos = match.range.last + 1
    }
    if (pos < text.length) append(text.substring(pos))
}

/**
 * Wendet die Formatierung eines einzelnen [INLINE_COMBINED_REGEX]-Matches auf den Builder an.
 * Geteilt zwischen [parseInlineFormattingWithColors] (Headings/Listen/Card-Preview — Bilder
 * werden dort nur als "🖼 Alt-Text" angezeigt) und [ParagraphBlock] (das für Bild-Matches
 * stattdessen sein eigenes InlineTextContent mit echtem Bild einsetzt, siehe dort).
 */
private fun AnnotatedString.Builder.appendFormattedMatch(
    match: MatchResult,
    linkColor: Color,
    codeBackground: Color,
    codeColor: Color
) {
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

        match.groups[INLINE_GROUP_IMAGE_ASSET] != null -> {
            // 🖼-Präfix wie beim Block-Bild/InlineTextContent-Fallback — sonst verschwindet ein
            // Inline-Bild ohne Alt-Text hier spurlos (leerer String statt sichtbarem Platzhalter).
            append("🖼 ${parseImageAlt(match.groupValues[INLINE_GROUP_IMAGE_ALT]).cleanAlt}".trim())
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
}

private const val INLINE_IMAGE_HEIGHT_EM = 2f
private const val INLINE_IMAGE_MAX_WIDTH_EM = 8f

/** Bounds-only Decode (kein Full-Bitmap) — billig genug für synchrones `remember`. */
private fun decodeAspectRatio(file: File): Float? {
    if (!file.exists()) return null
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    // EXIF-Orientation wird beim Anzeigen (Coil) angewendet, die rohen Bounds hier nicht —
    // bei 90°/270°-Rotation muss die Aspect-Ratio seitenvertauscht berechnet werden, sonst
    // bekommt die Box das falsche Seitenverhältnis und das Bild wird letterboxed.
    val (w, h) = if (orientationSwapsAxes(readExifOrientation(file))) {
        options.outHeight to options.outWidth
    } else {
        options.outWidth to options.outHeight
    }
    return w.toFloat() / h.toFloat()
}

/**
 * Paragraph-Rendering. Enthält der Text keinen Bild-Link, identisch zu vorher (reiner
 * [parseInlineFormatting]-Pfad). Sonst wird der Text selbst annotiert: jeder Inline-Bild-Match
 * (`|inline`-Token oder Größen-Token — beide werden hier ignoriert, nur die Alt-Tokens zählen)
 * bekommt einen [InlineTextContent]-Platzhalter mit dem echten Bild statt nur Clean-Alt-Text;
 * ein fehlendes/undecodierbares Asset fällt auf `"🖼 cleanAlt"` als Plaintext zurück.
 */
@Composable
private fun ParagraphBlock(
    text: String,
    bodyStyle: TextStyle,
    startOrdinal: Int,
    onImageTap: (String) -> Unit,
    onImageLongPress: ((MarkdownBlock.Image) -> Unit)?
) {
    if (!MarkdownEngine.IMAGE_REGEX.containsMatchIn(text)) {
        Text(
            text = parseInlineFormatting(text),
            style = bodyStyle,
            color = MaterialTheme.colorScheme.onSurface
        )
        return
    }

    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val codeColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Ein remember(text) statt remember(assetName) pro Bild: vermeidet das Compose-Footgun von
    // remember()-Aufrufen in variabel langen Schleifen, bleibt aber genauso "billig" (Bounds-only).
    val aspects = remember(context, text) {
        MarkdownEngine.IMAGE_REGEX.findAll(text).associate { match ->
            val assetName = match.groupValues[2]
            assetName to decodeAspectRatio(AssetStore(context).getAssetFile(assetName))
        }
    }

    val (annotated, inlineContent) = remember(context, text, linkColor, codeBackground, codeColor, aspects) {
        buildParagraphInlineContent(
            text,
            linkColor,
            codeBackground,
            codeColor,
            context,
            aspects,
            startOrdinal,
            onImageTap,
            onImageLongPress
        )
    }

    Text(
        text = annotated,
        style = bodyStyle,
        color = MaterialTheme.colorScheme.onSurface,
        inlineContent = inlineContent
    )
}

@OptIn(ExperimentalFoundationApi::class)
private fun buildParagraphInlineContent(
    text: String,
    linkColor: Color,
    codeBackground: Color,
    codeColor: Color,
    context: Context,
    aspects: Map<String, Float?>,
    startOrdinal: Int,
    onImageTap: (String) -> Unit,
    onImageLongPress: ((MarkdownBlock.Image) -> Unit)?
): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val inlineContent = mutableMapOf<String, InlineTextContent>()
    var imageIndex = 0
    var imageOrdinalOffset = 0
    val annotated = buildAnnotatedString {
        var pos = 0
        for (match in INLINE_COMBINED_REGEX.findAll(text)) {
            if (match.range.first > pos) {
                append(text.substring(pos, match.range.first))
            }
            if (match.groups[INLINE_GROUP_IMAGE_ASSET] != null) {
                val assetName = match.groupValues[INLINE_GROUP_IMAGE_ASSET]
                val altInfo = parseImageAlt(match.groupValues[INLINE_GROUP_IMAGE_ALT])
                val cleanAlt = altInfo.cleanAlt
                // Jeder IMAGE_REGEX-Match im Paragraph zählt, unabhängig davon ob er später ein
                // InlineTextContent bekommt — muss exakt den findAll-Index treffen, den
                // MarkdownEngine.parse() für [startOrdinal] verwendet hat.
                val ordinal = startOrdinal + imageOrdinalOffset++
                val aspect = aspects[assetName]
                if (aspect != null) {
                    val key = "img:${imageIndex++}"
                    val assetFile = AssetStore(context).getAssetFile(assetName)
                    val sizeFactor = altInfo.sizePercent / 100f
                    val effectiveHeightEm = INLINE_IMAGE_HEIGHT_EM * sizeFactor
                    val effectiveMaxWidthEm = INLINE_IMAGE_MAX_WIDTH_EM * sizeFactor
                    inlineContent[key] = InlineTextContent(
                        Placeholder(
                            width = (effectiveHeightEm * aspect).coerceAtMost(effectiveMaxWidthEm).em,
                            height = effectiveHeightEm.em,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                        )
                    ) {
                        // key() erzwingt einen frischen Coil-Request bei Size-Änderung, s. ImageBlock.
                        key(assetName, altInfo.sizePercent) {
                            AsyncImage(
                                model = assetFile,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .combinedClickable(
                                        onClick = { onImageTap(assetName) },
                                        onLongClick = onImageLongPress?.let {
                                            {
                                                it(
                                                    MarkdownBlock.Image(
                                                        altText = cleanAlt,
                                                        assetName = assetName,
                                                        sizePercent = altInfo.sizePercent,
                                                        align = ImageAlign.INLINE,
                                                        ordinal = ordinal
                                                    )
                                                )
                                            }
                                        }
                                    )
                            )
                        }
                    }
                    // appendInlineContent wirft bei leerem Alt-Text — neu eingefügte Bilder haben
                    // leeren Alt (![](.assets/x)), daher Fallback-Platzhalter statt Crash.
                    appendInlineContent(key, cleanAlt.ifEmpty { "�" })
                } else {
                    append("🖼 $cleanAlt".trim())
                }
            } else {
                appendFormattedMatch(match, linkColor, codeBackground, codeColor)
            }
            pos = match.range.last + 1
        }
        if (pos < text.length) append(text.substring(pos))
    }
    return annotated to inlineContent
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

            is MarkdownBlock.Image -> append("🖼 ${block.altText}".trim())
        }
    }
}

// 🔧 Perf: Karten zeigen ohnehin nur maxLines = 3-4. Android's StaticLayout muss aber den
// KOMPLETTEN String zeilenumbrechen, bevor er auf maxLines kürzt — bei sehr langem Content
// (z.B. importierter Text ohne Zeilenumbrüche) blockiert das den Main-Thread für Sekunden
// und löst eine ANR beim Scrollen aus. Daher vor jeder Messung hart kürzen.
internal const val NOTE_PREVIEW_CHAR_LIMIT = 500

@Composable
internal fun noteCardMarkdownPreview(content: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val codeColor = MaterialTheme.colorScheme.onSurfaceVariant
    val truncated = content.truncate(NOTE_PREVIEW_CHAR_LIMIT)
    return remember(truncated, linkColor, codeBackground, codeColor) {
        val blocks = MarkdownEngine.parse(truncated)
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
 *  9 = image alt (raw, mit Tokens) (![…](.assets/…))
 * 10 = image asset name
 *
 * Die Bild-Alternative steht bewusst am Ende: an der Position eines `!` scheitern alle
 * vorherigen Alternativen (auch die Link-Alternative — die beginnt mit `[`, nicht `!`), sodass
 * erst dort die Bild-Alternative greift und den kompletten `![…](…)`-Span konsumiert. Das
 * verhindert, dass ein späterer findAll-Versuch das innere `[alt](url)` fälschlich als Link matcht.
 */
internal val INLINE_COMBINED_REGEX = Regex(
    """\*\*(.+?)\*\*|__(.+?)__|~~(.+?)~~|\*(.+?)\*|(?<![A-Za-z0-9])_(.+?)_(?![A-Za-z0-9])|""" +
        """`([^`]+)`|https?://[^\s<>"')\]!]+|\[([^\]]+)\]\(([^)]+)\)|""" +
        """!\[([^\]]*)]\(\.assets/([A-Za-z0-9][A-Za-z0-9._-]*)\)"""
)

internal const val INLINE_GROUP_STRIKETHROUGH = 3
internal const val INLINE_GROUP_ITALIC_ASTERISK = 4
internal const val INLINE_GROUP_ITALIC_UNDERSCORE = 5
internal const val INLINE_GROUP_INLINE_CODE = 6
internal const val INLINE_GROUP_LINK_TEXT = 7
internal const val INLINE_GROUP_LINK_URL = 8
internal const val INLINE_GROUP_IMAGE_ALT = 9
internal const val INLINE_GROUP_IMAGE_ASSET = 10

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
            match.groups[INLINE_GROUP_IMAGE_ASSET] != null ->
                parseImageAlt(match.groupValues[INLINE_GROUP_IMAGE_ALT]).cleanAlt
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

            match.groups[INLINE_GROUP_IMAGE_ASSET] != null ->
                append(parseImageAlt(match.groupValues[INLINE_GROUP_IMAGE_ALT]).cleanAlt.escapeHtml())

            else -> append(match.value.escapeHtml()) // bare URL — keep as plain text
        }
        pos = match.range.last + 1
    }
    if (pos < text.length) append(text.substring(pos).escapeHtml())
}
