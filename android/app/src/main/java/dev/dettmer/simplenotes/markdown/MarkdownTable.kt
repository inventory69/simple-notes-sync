package dev.dettmer.simplenotes.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.markdown.MarkdownEngine.ColumnAlign
import dev.dettmer.simplenotes.markdown.MarkdownEngine.MarkdownBlock
import dev.dettmer.simplenotes.ui.theme.Dimensions

/** Untergrenze beim Herunterskalieren zu breiter Tabellen — darunter wird horizontal gescrollt. */
private val TABLE_MIN_COLUMN_WIDTH = 56.dp

/** Horizontales Zellpadding; steckt auch in der gemessenen Spaltenbreite. */
private val TABLE_CELL_PADDING = Dimensions.SpacingMedium

/**
 * 🆕 GFM-Tabellen: echtes Spaltenlayout statt roher Pipe-Zeilen (wie `marked` auf dem Desktop).
 *
 * Spaltenbreiten kommen aus dem gemessenen Zellinhalt: passt die Summe in den Viewport, wird
 * proportional auf die volle Breite aufgezogen (Pendant zu `width: 100%` im Desktop-CSS), sonst
 * proportional heruntergerechnet — bis zur [TABLE_MIN_COLUMN_WIDTH], ab der horizontal gescrollt
 * wird statt die Spalten unlesbar zu quetschen.
 */
@Composable
internal fun TableBlock(table: MarkdownBlock.Table, bodyStyle: TextStyle) {
    if (table.header.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val available = maxWidth
        val widths = remember(table, bodyStyle, available) {
            tableColumnWidths(table, measurer, bodyStyle, density, available)
        }
        val rows = remember(table) { listOf(table.header) + table.rows }
        val shape = MaterialTheme.shapes.small
        Column(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .width(widths.fold(0.dp) { sum, width -> sum + width })
                // clip NACH border: der Rahmen bleibt voll sichtbar, aber die rechteckige
                // Kopfzeilen-Fläche wird auf den Eckradius beschnitten — sonst lugt sie in den
                // oberen Ecken unter dem Rahmen hervor.
                .border(1.dp, gridColor, shape)
                .clip(shape)
        ) {
            rows.forEachIndexed { rowIndex, row ->
                // IntrinsicSize.Min: alle Zellen der Zeile sind so hoch wie die höchste, damit
                // die Zellränder auch dann fluchten, wenn eine Zelle umbricht.
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    widths.indices.forEach { colIndex ->
                        TableCell(
                            text = row.getOrElse(colIndex) { "" },
                            style = bodyStyle,
                            align = table.alignments.getOrElse(colIndex) { ColumnAlign.LEFT },
                            width = widths[colIndex],
                            isHeader = rowIndex == 0,
                            gridColor = gridColor,
                            drawRight = colIndex < widths.lastIndex,
                            drawBottom = rowIndex < rows.lastIndex
                        )
                    }
                }
            }
        }
    }
}

/**
 * Eine Tabellenzelle. Das Gitter zeichnet jede Zelle selbst — nur rechte und untere Kante, außer
 * in letzter Spalte/Zeile — sonst lägen an jeder inneren Kante zwei Linien übereinander.
 */
@Composable
private fun TableCell(
    text: String,
    style: TextStyle,
    align: ColumnAlign,
    width: Dp,
    isHeader: Boolean,
    gridColor: Color,
    drawRight: Boolean,
    drawBottom: Boolean
) {
    val headerBackground = MaterialTheme.colorScheme.surfaceVariant
    Text(
        text = parseInlineFormatting(text),
        style = if (isHeader) style.copy(fontWeight = FontWeight.SemiBold) else style,
        color = if (isHeader) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        textAlign = when (align) {
            ColumnAlign.LEFT -> TextAlign.Start
            ColumnAlign.CENTER -> TextAlign.Center
            ColumnAlign.RIGHT -> TextAlign.End
        },
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .then(if (isHeader) Modifier.background(headerBackground) else Modifier)
            .drawBehind {
                val stroke = 1.dp.toPx()
                if (drawRight) {
                    val x = size.width - stroke / 2
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), stroke)
                }
                if (drawBottom) {
                    val y = size.height - stroke / 2
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), stroke)
                }
            }
            .padding(horizontal = TABLE_CELL_PADDING, vertical = Dimensions.SpacingSmall)
    )
}

/**
 * Natürliche Spaltenbreite = breiteste einzeilige Zelle + Padding, danach proportional auf
 * [available] skaliert. Inline-Marker werden vor dem Messen entfernt, sonst zählte `**fett**`
 * vier Zeichen zu viel.
 */
private fun tableColumnWidths(
    table: MarkdownBlock.Table,
    measurer: TextMeasurer,
    style: TextStyle,
    density: Density,
    available: Dp
): List<Dp> {
    val rows = listOf(table.header) + table.rows
    val natural = List(table.header.size) { col ->
        val widest = rows.maxOf { row ->
            val cell = row.getOrElse(col) { "" }
            if (cell.isEmpty()) {
                0
            } else {
                measurer.measure(cell.let(::stripInlineFormatting), style, softWrap = false, maxLines = 1).size.width
            }
        }
        with(density) { widest.toDp() } + TABLE_CELL_PADDING * 2
    }
    val total = natural.reduce { sum, width -> sum + width }
    val scale = available / total
    return when {
        scale >= 1f -> natural.map { it * scale }
        else -> natural.map { (it * scale).coerceAtLeast(TABLE_MIN_COLUMN_WIDTH) }
    }
}
