package dev.dettmer.simplenotes.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.dettmer.simplenotes.images.calculateInSampleSize
import dev.dettmer.simplenotes.images.downscaleIfNeeded
import dev.dettmer.simplenotes.markdown.MarkdownEngine
import dev.dettmer.simplenotes.markdown.MarkdownEngine.MarkdownBlock
import dev.dettmer.simplenotes.markdown.stripInlineFormatting
import dev.dettmer.simplenotes.storage.AssetStore
import dev.dettmer.simplenotes.utils.Logger
import java.io.File

private const val TAG = "WidgetMarkdownContent"
private const val WIDGET_MAX_MD_ITEMS = 50
private const val CODE_BLOCK_MAX_LINES = 10

/** Bitmap-Budget pro Widget-Render. Binder-Limit ist ~1 MB für die gesamte
 *  RemoteViews-Transaktion — die Hälfte bleibt für Layout und Text. Ein fester Bildzähler
 *  träfe die Grenze nicht: der Speicher hängt am Seitenverhältnis (ein quadratisches Bild
 *  kostet bei gleicher längster Kante das Zweieinhalbfache eines 16:6-Bildes). */
private const val WIDGET_IMAGE_BUDGET_BYTES = 512 * 1024

/** Decode-Ziel für Widget-Bilder (Mini-Canvas): längste Seite max. 256px, RGB_565. */
private const val WIDGET_IMAGE_MAX_DIM = 256

/** Höhe eines Bildes bei Größen-Preset 100 %. Der Default 50 % landet damit auf den bisherigen 110dp. */
private const val WIDGET_IMAGE_FULL_HEIGHT_DP = 220
private const val WIDGET_IMAGE_MIN_HEIGHT_DP = 24

/**
 * Größen-Preset (siehe `ImageActionsMenu`) → Bildhöhe im Widget. Glance kennt kein
 * `fillMaxWidth(fraction)` wie der Editor, und `LocalSize` liefert bei `SizeMode.Responsive`
 * nur den Breakpoint statt der echten Widget-Breite — deshalb skaliert die Höhe, die Breite
 * bleibt `fillMaxWidth` + `ContentScale.Fit`.
 * `coerceIn` fängt beliebige Token-Werte ab (`parseImageAlt` erlaubt 1–100 %).
 */
internal fun widgetImageHeightDp(sizePercent: Int): Int =
    (WIDGET_IMAGE_FULL_HEIGHT_DP * sizePercent / 100)
        .coerceIn(WIDGET_IMAGE_MIN_HEIGHT_DP, WIDGET_IMAGE_FULL_HEIGHT_DP)

private data class WidgetImage(val bitmap: Bitmap, val altText: String, val sizePercent: Int)

private sealed interface WidgetRenderItem {
    data class Heading(val level: Int, val text: String) : WidgetRenderItem

    data class Paragraph(val text: String) : WidgetRenderItem

    data class TaskItem(val text: String, val isChecked: Boolean) : WidgetRenderItem

    data class ListItem(val text: String) : WidgetRenderItem

    data class CodeLine(val text: String) : WidgetRenderItem

    /** Bilder einer Reihe (siehe [MarkdownEngine.imageRowSeparators]) — nebeneinander gerendert. */
    data class ImageRow(val images: List<WidgetImage>) : WidgetRenderItem

    data object Divider : WidgetRenderItem

    data object BlockSpacer : WidgetRenderItem
}

/**
 * Bounds-only Decode + `inSampleSize` auf max. [WIDGET_IMAGE_MAX_DIM]px, dann `RGB_565`
 * (halber Speicher ggü. ARGB_8888 — Mini-Canvas braucht keinen Alphakanal). Der
 * `downscaleIfNeeded`-Nachlauf ist Pflicht: `inSampleSize` springt nur in Zweierpotenzen und
 * ließe ein 1600×600-Bild sonst bei 800×300 (≈ 480 KB statt 49 KB) stehen. `null` bei
 * fehlendem/kaputtem Asset — Aufrufer fällt auf den Alt-Text-Platzhalter zurück.
 */
private fun decodeWidgetBitmap(file: File): Bitmap? {
    if (!file.exists()) return null
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, WIDGET_IMAGE_MAX_DIM)
        val decoded = BitmapFactory.decodeFile(
            file.path,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
        ) ?: return null
        decoded.downscaleIfNeeded(WIDGET_IMAGE_MAX_DIM)
    } catch (e: OutOfMemoryError) {
        Logger.w(TAG, "Widget image decode failed: ${e.message}")
        null
    }
}

// Abbau: TECH_DEBT_ROADMAP.md §4 (Bestand, keinem Refactoring-Slice zugeordnet)
@Suppress("CyclomaticComplexMethod")
private fun flattenToRenderItems(
    blocks: List<MarkdownBlock>,
    maxItems: Int,
    loadImage: (String) -> Bitmap? = { null }
): List<WidgetRenderItem> {
    val result = mutableListOf<WidgetRenderItem>()
    var bytesUsed = 0
    val separators = MarkdownEngine.imageRowSeparators(blocks)
    blocks.forEachIndexed { blockIdx, block ->
        if (result.size >= maxItems) return result
        // " " = Bild derselben Reihe wie der Vorgänger: kein Spacer, es wandert unten in die Reihe.
        val continuesRow = separators[blockIdx] == " "
        if (blockIdx > 0 && !continuesRow) result.add(WidgetRenderItem.BlockSpacer)
        when (block) {
            is MarkdownBlock.Heading -> {
                result.add(WidgetRenderItem.Heading(block.level, stripInlineFormatting(block.text)))
            }
            is MarkdownBlock.Paragraph -> {
                block.text.split("\n").forEach { line ->
                    if (result.size < maxItems) {
                        result.add(WidgetRenderItem.Paragraph(line))
                    }
                }
            }
            is MarkdownBlock.TaskList -> {
                block.items.forEach { item ->
                    if (result.size < maxItems) {
                        result.add(
                            WidgetRenderItem.TaskItem(
                                text = item.text,
                                isChecked = item.isChecked
                            )
                        )
                    }
                }
            }
            is MarkdownBlock.UnorderedList -> {
                block.items.forEach { itemText ->
                    if (result.size < maxItems) {
                        result.add(WidgetRenderItem.ListItem(itemText))
                    }
                }
            }
            is MarkdownBlock.CodeBlock -> {
                block.code.split("\n").take(CODE_BLOCK_MAX_LINES).forEach { line ->
                    if (result.size < maxItems) {
                        result.add(WidgetRenderItem.CodeLine(line))
                    }
                }
            }
            // 🆕 v2.16.0 (Issue #140): Im Widget bewusst übersprungen — der Platz dort ist knapp
            // und eine gewollte Lücke wäre eine verlorene Textzeile.
            is MarkdownBlock.BlankLines -> Unit

            MarkdownBlock.HorizontalRule -> {
                result.add(WidgetRenderItem.Divider)
            }
            // 🆕 Bild-Attachments v2: echte Bilder bis [WIDGET_IMAGE_BUDGET_BYTES] voll ist,
            // Rest/Decode-Fail → Alt-Text. Größe steuert die Höhe (siehe [widgetImageHeightDp]),
            // Ausrichtung wird im Widget ignoriert (Mini-Canvas) — Bild bleibt zentriert.
            // ponytail: ein Platzhalter jenseits des Budgets bricht die ImageRow auf und landet
            // als eigene Zeile darunter. Sichtbar nur noch im Decode-Fehler-Fall; erst zusammenlegen,
            // wenn das in der Praxis auffällt.
            is MarkdownBlock.Image -> {
                val bitmap = if (bytesUsed < WIDGET_IMAGE_BUDGET_BYTES) loadImage(block.assetName) else null
                val previousRow = result.lastOrNull() as? WidgetRenderItem.ImageRow
                if (bitmap != null) {
                    bytesUsed += bitmap.allocationByteCount
                    val image = WidgetImage(bitmap, block.altText, block.sizePercent)
                    if (continuesRow && previousRow != null) {
                        result[result.lastIndex] = previousRow.copy(images = previousRow.images + image)
                    } else {
                        result.add(WidgetRenderItem.ImageRow(listOf(image)))
                    }
                } else {
                    result.add(WidgetRenderItem.Paragraph("🖼 ${block.altText}".trim()))
                }
            }
        }
    }
    return result
}

// Abbau: TECH_DEBT_ROADMAP.md §4 (Bestand, keinem Refactoring-Slice zugeordnet)
@Suppress("CyclomaticComplexMethod", "LongMethod")
@Composable
internal fun WidgetMarkdownView(
    content: String,
    fontSizeScale: Float = 1.0f,
    /**
     * Tap-Aktion für den Textbereich. Muss an jeder **Zeile** hängen: die `LazyColumn` wird zu
     * einer `ListView`, und die verschluckt Taps, bevor ein `clickable` am umgebenden `Box`
     * feuert. Gleiches Muster wie `NotesListWidgetContent.NoteCard`.
     */
    onItemClick: Action? = null
) {
    val context = LocalContext.current
    val renderItems = flattenToRenderItems(
        blocks = MarkdownEngine.parse(content),
        maxItems = WIDGET_MAX_MD_ITEMS,
        // provideContent läuft auf einem Glance-SessionWorker-Thread, nicht dem Main-Thread —
        // synchrones Datei-IO hier ist sicher (Precedent: NoteWidget.kt).
        loadImage = { AssetStore(context).getAssetFile(it).let(::decodeWidgetBitmap) }
    )

    LazyColumn(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp)
    ) {
        items(renderItems.size) { index ->
            val itemModifier = GlanceModifier.fillMaxWidth()
                .let { if (onItemClick != null) it.clickable(onItemClick) else it }
            Box(modifier = itemModifier) {
                when (val item = renderItems[index]) {
                    is WidgetRenderItem.Heading -> {
                        val fontSize = when (item.level) {
                            1 -> (18 * fontSizeScale).sp
                            2 -> (16 * fontSizeScale).sp
                            else -> (15 * fontSizeScale).sp
                        }
                        Text(
                            text = item.text,
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurface,
                                fontSize = fontSize,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = GlanceModifier.padding(bottom = 2.dp)
                        )
                    }

                    is WidgetRenderItem.Paragraph -> {
                        if (item.text.isBlank()) {
                            Spacer(modifier = GlanceModifier.height(4.dp))
                        } else {
                            WidgetInlineText(
                                text = item.text,
                                fontSize = 14f * fontSizeScale,
                                maxLines = 5,
                                modifier = GlanceModifier.padding(bottom = 4.dp)
                            )
                        }
                    }

                    is WidgetRenderItem.TaskItem -> {
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (item.isChecked) "☑" else "☐",
                                style = TextStyle(
                                    color = if (item.isChecked) {
                                        GlanceTheme.colors.outline
                                    } else {
                                        GlanceTheme.colors.onSurface
                                    },
                                    fontSize = (14 * fontSizeScale).sp
                                )
                            )
                            Spacer(modifier = GlanceModifier.width(6.dp))
                            WidgetInlineText(
                                text = item.text,
                                fontSize = 14f * fontSizeScale,
                                maxLines = 2,
                                dimmed = item.isChecked,
                                addStrikethrough = item.isChecked,
                                modifier = GlanceModifier.defaultWeight()
                            )
                        }
                    }

                    is WidgetRenderItem.ListItem -> {
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "•",
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurface,
                                    fontSize = (14 * fontSizeScale).sp
                                ),
                                modifier = GlanceModifier.width(20.dp)
                            )
                            WidgetInlineText(
                                text = item.text,
                                fontSize = 14f * fontSizeScale,
                                maxLines = 3,
                                modifier = GlanceModifier.defaultWeight()
                            )
                        }
                    }

                    is WidgetRenderItem.CodeLine -> {
                        Text(
                            text = item.text.ifEmpty { " " },
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = (12 * fontSizeScale).sp
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier.padding(start = 8.dp, bottom = 1.dp)
                        )
                    }

                    is WidgetRenderItem.ImageRow -> {
                        // ponytail: gleich breite Slots — Glance kennt kein gewichtetes weight(), die
                        // Prozentgröße steuert im Widget nur die Höhe (s. widgetImageHeightDp).
                        Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)) {
                            item.images.forEach { image ->
                                Image(
                                    provider = ImageProvider(image.bitmap),
                                    contentDescription = image.altText,
                                    contentScale = ContentScale.Fit,
                                    modifier = GlanceModifier
                                        .defaultWeight()
                                        .height(widgetImageHeightDp(image.sizePercent).dp)
                                        .padding(horizontal = 2.dp)
                                )
                            }
                        }
                    }

                    WidgetRenderItem.Divider -> {
                        Column(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(GlanceTheme.colors.outline)
                            ) {}
                        }
                    }

                    WidgetRenderItem.BlockSpacer -> {
                        Spacer(modifier = GlanceModifier.height(6.dp))
                    }
                }
            }
        }
    }
}
