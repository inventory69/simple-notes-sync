package dev.dettmer.simplenotes.widget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.background
import androidx.glance.layout.Alignment
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
import dev.dettmer.simplenotes.markdown.MarkdownEngine
import dev.dettmer.simplenotes.markdown.MarkdownEngine.MarkdownBlock
import dev.dettmer.simplenotes.markdown.stripInlineFormatting
import dev.dettmer.simplenotes.storage.AssetStore
import dev.dettmer.simplenotes.utils.Logger
import java.io.File

private const val TAG = "WidgetMarkdownContent"
private const val WIDGET_MAX_MD_ITEMS = 50
private const val CODE_BLOCK_MAX_LINES = 10

/** Max. Anzahl Bilder pro Widget-Render — Bitmap-Speicher im Binder-Transaktions-Limit halten. */
private const val WIDGET_MAX_IMAGES = 3

/** Decode-Ziel für Widget-Bilder (Mini-Canvas): längste Seite max. 256px, RGB_565. */
private const val WIDGET_IMAGE_MAX_DIM = 256

private sealed interface WidgetRenderItem {
    data class Heading(val level: Int, val text: String) : WidgetRenderItem

    data class Paragraph(val text: String) : WidgetRenderItem

    data class TaskItem(val text: String, val isChecked: Boolean) : WidgetRenderItem

    data class ListItem(val text: String) : WidgetRenderItem

    data class CodeLine(val text: String) : WidgetRenderItem

    data class Image(val bitmap: Bitmap, val altText: String) : WidgetRenderItem

    data object Divider : WidgetRenderItem

    data object BlockSpacer : WidgetRenderItem
}

/**
 * Bounds-only Decode + `inSampleSize`-Loop auf max. [WIDGET_IMAGE_MAX_DIM]px, dann `RGB_565`
 * (halber Speicher ggü. ARGB_8888 — Mini-Canvas braucht keinen Alphakanal). `null` bei
 * fehlendem/kaputtem Asset — Aufrufer fällt auf den Alt-Text-Platzhalter zurück.
 */
private fun decodeWidgetBitmap(file: File): Bitmap? {
    if (!file.exists()) return null
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= WIDGET_IMAGE_MAX_DIM &&
            bounds.outHeight / (sampleSize * 2) >= WIDGET_IMAGE_MAX_DIM
        ) {
            sampleSize *= 2
        }

        BitmapFactory.decodeFile(
            file.path,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
        )
    } catch (e: OutOfMemoryError) {
        Logger.w(TAG, "Widget image decode failed: ${e.message}")
        null
    }
}

private fun flattenToRenderItems(
    blocks: List<MarkdownBlock>,
    maxItems: Int,
    loadImage: (String) -> Bitmap? = { null }
): List<WidgetRenderItem> {
    val result = mutableListOf<WidgetRenderItem>()
    var imagesUsed = 0
    blocks.forEachIndexed { blockIdx, block ->
        if (result.size >= maxItems) return result
        if (blockIdx > 0) result.add(WidgetRenderItem.BlockSpacer)
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
            MarkdownBlock.HorizontalRule -> {
                result.add(WidgetRenderItem.Divider)
            }
            // 🆕 Bild-Attachments v2: bis zu WIDGET_MAX_IMAGES echte Bilder, Rest/Decode-Fail → Alt-Text.
            // Ausrichtung/Größe werden im Widget ignoriert (Mini-Canvas).
            is MarkdownBlock.Image -> {
                val bitmap = if (imagesUsed < WIDGET_MAX_IMAGES) loadImage(block.assetName) else null
                if (bitmap != null) {
                    imagesUsed++
                    result.add(WidgetRenderItem.Image(bitmap, block.altText))
                } else {
                    result.add(WidgetRenderItem.Paragraph("🖼 ${block.altText}".trim()))
                }
            }
        }
    }
    return result
}

@Composable
internal fun WidgetMarkdownView(content: String, fontSizeScale: Float = 1.0f) {
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

                is WidgetRenderItem.Image -> {
                    Image(
                        provider = ImageProvider(item.bitmap),
                        contentDescription = item.altText,
                        contentScale = ContentScale.Fit,
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .padding(bottom = 4.dp)
                    )
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
