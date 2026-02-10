package dev.dettmer.simplenotes.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 🆕 v1.8.0: Dezenter Gradient-Overlay der anzeigt, dass mehr Text
 * vorhanden ist als aktuell sichtbar.
 *
 * Features:
 * - Top gradient: surface → transparent (zeigt Text oberhalb)
 * - Bottom gradient: transparent → surface (zeigt Text unterhalb)
 * - Höhe: 24dp für subtilen, aber erkennbaren Effekt
 * - Material You kompatibel: nutzt dynamische surface-Farbe
 * - Dark Mode Support: automatisch durch MaterialTheme
 *
 * Verwendet in: ChecklistItemRow für lange Texteinträge
 *
 * @param isTopGradient true = Gradient von surface→transparent (oben), false = transparent→surface (unten)
 */
@Composable
fun OverflowGradient(
    modifier: Modifier = Modifier,
    isTopGradient: Boolean = false
) {
    val surfaceColor = MaterialTheme.colorScheme.surface

    val gradientColors = if (isTopGradient) {
        // Oben: surface → transparent (zeigt dass Text OBERHALB existiert)
        listOf(
            surfaceColor.copy(alpha = 0.95f),
            surfaceColor.copy(alpha = 0.7f),
            Color.Transparent
        )
    } else {
        // Unten: transparent → surface (zeigt dass Text UNTERHALB existiert)
        listOf(
            Color.Transparent,
            surfaceColor.copy(alpha = 0.7f),
            surfaceColor.copy(alpha = 0.95f)
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(GRADIENT_HEIGHT)
            .background(
                brush = Brush.verticalGradient(colors = gradientColors)
            )
    )
}

private val GRADIENT_HEIGHT = 24.dp
