package dev.dettmer.simplenotes.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val DISABLED_ALPHA = 0.38f
private const val DENSE_FROM = 5
private val DENSE_PADDING = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
private const val LIGHT_SWATCH_LUMINANCE = 0.6f

/**
 * S2: Auswahlgruppe in voller Breite, verbunden statt einzelner Chips in einer FlowRow, die je nach
 * Textlänge anders umbricht. Lange Beschriftungen schrumpfen (Übersetzungen), statt umzubrechen.
 * Für alle Auswahlen der Einstellungen (Design, Schriftgröße, Ansicht, Spalten, Vorschaulänge,
 * Bildgröße, Sperrzeit).
 */
@Composable
fun <T> SettingsSegmentedRow(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (T) -> Boolean = { true },
    label: @Composable (T) -> Unit
) {
    val dense = options.size >= DENSE_FROM
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                enabled = enabled(option),
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {}, // kein Haken: er nähme den schmalen Segmenten den Platz für den Text
                // Ab fünf Segmenten kleinere, für alle gleiche Schrift und weniger Rand, damit lange
                // Übersetzungen („Extra long“) passen, ohne dass jedes Segment eine andere Größe hat.
                contentPadding = if (dense) DENSE_PADDING else SegmentedButtonDefaults.ContentPadding,
                label = {
                    ProvideTextStyle(if (dense) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge) {
                        label(option)
                    }
                }
            )
        }
    }
}

private val TRAILING_PARENTHETICAL = Regex("""^(.*?)\s*[(（](.*?)[)）]\s*$""")

/**
 * „System (follow device)“ → („System“, „follow device“). Auch vollbreite Klammern („系统（跟随设备）“),
 * die einige Übersetzungen nutzen. Ohne Klammer: (Text, null).
 */
internal fun splitParenthetical(text: String): Pair<String, String?> {
    val match = TRAILING_PARENTHETICAL.find(text) ?: return text.trim() to null
    return match.groupValues[1].trim() to match.groupValues[2].trim().takeIf { it.isNotEmpty() }
}

/** Einzeilige Segment-Beschriftung; was dann noch nicht passt, endet mit Punkten statt umzubrechen. */
@Composable
fun SegmentLabel(text: String) {
    Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
}

/** S2: Farbschemata als Farbpunkte mit Namen, gleich breit verteilt; der gewählte trägt einen Haken. */
@Composable
fun <T> SettingsColorDots(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    color: (T) -> Color,
    name: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    enabled: (T) -> Boolean = { true },
    // Füllung des Punkts (z. B. Verlauf für „Dynamisch“); [color] bleibt für die Hakenfarbe maßgeblich.
    brush: (T) -> Brush = { SolidColor(color(it)) }
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val isEnabled = enabled(option)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (isEnabled) 1f else DISABLED_ALPHA)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(enabled = isEnabled, role = Role.RadioButton) { onSelect(option) }
                    .padding(vertical = 6.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .then(
                            if (isSelected) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape).padding(4.dp)
                            } else {
                                Modifier
                            }
                        )
                        .clip(CircleShape)
                        .background(brush(option))
                ) {
                    if (isSelected) {
                        val swatch = color(option)
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = if (swatch.luminance() > LIGHT_SWATCH_LUMINANCE) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = name(option),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = MaterialTheme.typography.labelSmall.fontSize),
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}
