package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R

/**
 * 🆕 v1.8.0: Dialog showing the sync status icon legend
 * 
 * Displays all 5 SyncStatus values with their icons, colors, 
 * and descriptions. Helps users understand what each icon means.
 */
@Composable
fun SyncStatusLegendDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.sync_legend_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Optional: Kurze Einleitung
                Text(
                    text = stringResource(R.string.sync_legend_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                HorizontalDivider()
                
                // ☁️✓ SYNCED
                LegendRow(
                    icon = Icons.Outlined.CloudDone,
                    tint = MaterialTheme.colorScheme.primary,
                    label = stringResource(R.string.sync_legend_synced_label),
                    description = stringResource(R.string.sync_legend_synced_desc)
                )
                
                // ☁️↻ PENDING
                LegendRow(
                    icon = Icons.Outlined.CloudSync,
                    tint = MaterialTheme.colorScheme.outline,
                    label = stringResource(R.string.sync_legend_pending_label),
                    description = stringResource(R.string.sync_legend_pending_desc)
                )
                
                // ⚠️ CONFLICT
                LegendRow(
                    icon = Icons.Default.Warning,
                    tint = MaterialTheme.colorScheme.error,
                    label = stringResource(R.string.sync_legend_conflict_label),
                    description = stringResource(R.string.sync_legend_conflict_desc)
                )
                
                // ☁️✗ LOCAL_ONLY
                LegendRow(
                    icon = Icons.Outlined.CloudOff,
                    tint = MaterialTheme.colorScheme.outline,
                    label = stringResource(R.string.sync_legend_local_only_label),
                    description = stringResource(R.string.sync_legend_local_only_desc)
                )
                
                // ☁️✗ DELETED_ON_SERVER
                LegendRow(
                    icon = Icons.Outlined.CloudOff,
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    label = stringResource(R.string.sync_legend_deleted_label),
                    description = stringResource(R.string.sync_legend_deleted_desc)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}

/**
 * Single row in the sync status legend
 * Shows icon + label + description
 */
@Composable
private fun LegendRow(
    icon: ImageVector,
    tint: Color,
    label: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,  // Dekorativ, Label reicht
            tint = tint,
            modifier = Modifier
                .size(20.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
