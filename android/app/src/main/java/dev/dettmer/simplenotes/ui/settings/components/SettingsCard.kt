package dev.dettmer.simplenotes.ui.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val GROUP_OUTER_RADIUS = 18.dp
private val GROUP_INNER_RADIUS = 4.dp

/**
 * Clickable Settings entry with icon, title, subtitle and optional status
 * v1.5.0: Jetpack Compose Settings Redesign
 * S1: Einträge einer Gruppe stehen als eine Karte untereinander, getrennt durch eine Linie in
 * Hintergrundfarbe; Symbole ohne Kreis in Akzentfarbe.
 */
@Composable
fun SettingsCard(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    statusText: String? = null,
    statusColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    groupPosition: GroupPosition = GroupPosition.SINGLE,
    onClick: () -> Unit
) {
    val top = if (groupPosition == GroupPosition.SINGLE || groupPosition == GroupPosition.FIRST) {
        GROUP_OUTER_RADIUS
    } else {
        GROUP_INNER_RADIUS
    }
    val bottom = if (groupPosition == GroupPosition.SINGLE || groupPosition == GroupPosition.LAST) {
        GROUP_OUTER_RADIUS
    } else {
        GROUP_INNER_RADIUS
    }
    val shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                // 2 dp Fuge in Hintergrundfarbe = Trennlinie innerhalb der Gruppe
                top = if (groupPosition == GroupPosition.SINGLE || groupPosition == GroupPosition.FIRST) 4.dp else 2.dp,
                bottom = if (groupPosition == GroupPosition.SINGLE || groupPosition == GroupPosition.LAST) 4.dp else 0.dp
            )
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(18.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (statusText != null) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )
                }
            }

            // Arrow
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
