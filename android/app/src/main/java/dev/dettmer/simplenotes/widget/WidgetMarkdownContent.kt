package dev.dettmer.simplenotes.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
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

private const val WIDGET_MAX_MD_ITEMS = 50
private const val CODE_BLOCK_MAX_LINES = 10

private sealed interface WidgetRenderItem {
    data class Heading(val level: Int, val text: String) : WidgetRenderItem

    data class Paragraph(val text: String) : WidgetRenderItem

    data class TaskItem(val text: String, val isChecked: Boolean) : WidgetRenderItem

    data class ListItem(val text: String) : WidgetRenderItem

    data class CodeLine(val text: String) : WidgetRenderItem

    data object Divider : WidgetRenderItem

    data object BlockSpacer : WidgetRenderItem
}

private fun flattenToRenderItems(
    blocks: List<MarkdownBlock>,
    maxItems: Int
): List<WidgetRenderItem> {
    val result = mutableListOf<WidgetRenderItem>()
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
        }
    }
    return result
}

@Composable
internal fun WidgetMarkdownView(content: String, fontSizeScale: Float = 1.0f) {
    val renderItems = flattenToRenderItems(
        blocks = MarkdownEngine.parse(content),
        maxItems = WIDGET_MAX_MD_ITEMS
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
