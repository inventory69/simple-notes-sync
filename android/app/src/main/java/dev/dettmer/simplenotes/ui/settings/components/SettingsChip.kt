package dev.dettmer.simplenotes.ui.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Selectable chip: bordered [Surface] with a centered, optional [preview] slot above the
 * label. Shared visual language for all settings chip rows (theme, color scheme, display
 * mode, grid columns, note preview length, font size, app-lock grace period).
 */
@Composable
fun SettingsChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    preview: @Composable (contentColor: Color) -> Unit = {}
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val chipAlpha = if (enabled) 1f else 0.38f

    Surface(
        onClick = onClick,
        modifier = modifier.alpha(chipAlpha),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(width = if (selected) 2.dp else 1.dp, color = borderColor),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            preview(contentColor)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = contentColor)
        }
    }
}

/** FlowRow wrapper shared by all chip selectors: 16dp outer padding, 12dp gap in both axes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsChipRow(modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}
