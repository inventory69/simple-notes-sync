package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.sync.ExportProblems
import dev.dettmer.simplenotes.ui.theme.Dimensions
import dev.dettmer.simplenotes.utils.Constants

/**
 * 🆕 v1.8.0: Dialog showing the sync status icon legend
 *
 * Displays 4 visible SyncStatus values with their icons, colors,
 * and descriptions, plus a trash hint footnote.
 */
@Composable
fun SyncStatusLegendDialog(exportProblems: ExportProblems?, onDismiss: () -> Unit) {
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
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 🆕 v2.19.0: verschwindet nach dem nächsten fehlerfreien Sync von selbst
                exportProblems?.let { LastSyncCard(it) }

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

                HorizontalDivider()

                Text(
                    text = stringResource(R.string.sync_legend_trash_hint, Constants.TRASH_RETENTION_DAYS),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

/** 🆕 v2.19.0: Export-Probleme des letzten Syncs, Muster wie [dev.dettmer.simplenotes.ui.editor.ConflictBanner]. */
@Composable
private fun LastSyncCard(problems: ExportProblems) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.SpacingMediumLarge),
        // Nicht surfaceContainerHigh — das ist der Dialog-Hintergrund, die Karte verschwände darin.
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Column(modifier = Modifier.padding(Dimensions.SpacingLarge)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium)
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null, // Titel daneben sagt dasselbe
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(R.string.sync_legend_last_sync_title),
                    style = MaterialTheme.typography.titleSmall
                )
            }
            val lines = buildList {
                val mdCount = problems.markdownFailedIds.size
                if (mdCount > 0) add(pluralStringResource(R.plurals.sync_markdown_failed_count, mdCount, mdCount))
                if (problems.markdownImportFailed) add(stringResource(R.string.sync_markdown_import_failed))
                val assets = problems.assetsFailed
                if (assets > 0) add(pluralStringResource(R.plurals.sync_assets_failed_count, assets, assets))
                // Gleicher Grund für MD und Bilder (z. B. beide 409) steht nur einmal da.
                listOfNotNull(problems.markdownReason, problems.assetsReason).distinct().forEach {
                    add(stringResource(R.string.sync_status_reason, it))
                }
            }
            lines.forEach {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = Dimensions.SpacingSmall)
                )
            }
            Text(
                text = stringResource(R.string.sync_status_notes_in_sync),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = Dimensions.SpacingSmall)
            )
        }
    }
}

/**
 * Single row in the sync status legend
 * Shows icon + label + description
 */
@Composable
private fun LegendRow(icon: ImageVector, tint: Color, label: String, description: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null, // Dekorativ, Label reicht
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
