package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.sync.ExportProblems
import dev.dettmer.simplenotes.sync.NoteRef
import dev.dettmer.simplenotes.sync.SyncStatusSummary
import dev.dettmer.simplenotes.ui.settings.SettingsRoute
import dev.dettmer.simplenotes.ui.theme.Dimensions
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.toReadableTime

/**
 * 🆕 v1.8.0: Legende der Sync-Symbole
 * 🆕 v2.19.0: Sync-Status-Dialog. Zeigt, was hängt und warum, und bietet den nächsten Schritt an.
 * Die Legende bleibt als aufklappbarer Abschnitt, offen, solange nichts klemmt.
 */
@Composable
fun SyncStatusDialog(
    summary: SyncStatusSummary,
    isSyncing: Boolean,
    onRetry: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenSettings: (String) -> Unit,
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
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMediumLarge)
            ) {
                StatusHeader(summary)
                summary.lastError?.let { ErrorCard(it, onOpenSettings) }
                if (summary.conflicts.isNotEmpty()) ConflictCard(summary.conflicts, onOpenNote)
                summary.exportProblems?.let { ExportCard(it, summary.markdownNotes, onOpenNote, onOpenSettings) }
                if (summary.state == SyncStatusSummary.State.FAILED || summary.exportProblems != null) {
                    RetryButton(isSyncing, onRetry)
                }
                LegendSection(
                    initiallyExpanded = summary.state == SyncStatusSummary.State.OK ||
                        summary.state == SyncStatusSummary.State.PENDING
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

/** Eine Zeile Zustand, darunter der letzte erfolgreiche Sync. */
@Composable
private fun StatusHeader(summary: SyncStatusSummary) {
    val (icon, tint, text) = when (summary.state) {
        SyncStatusSummary.State.FAILED -> Triple(
            Icons.Outlined.ErrorOutline,
            MaterialTheme.colorScheme.error,
            stringResource(R.string.sync_status_error)
        )
        SyncStatusSummary.State.ATTENTION -> Triple(
            Icons.Outlined.WarningAmber,
            MaterialTheme.colorScheme.tertiary,
            stringResource(R.string.sync_status_attention)
        )
        SyncStatusSummary.State.PENDING -> Triple(
            Icons.Outlined.CloudSync,
            MaterialTheme.colorScheme.outline,
            pluralStringResource(R.plurals.sync_status_pending_changes, summary.pendingCount, summary.pendingCount)
        )
        SyncStatusSummary.State.OK -> Triple(
            Icons.Outlined.CloudDone,
            MaterialTheme.colorScheme.primary,
            stringResource(R.string.sync_status_synced)
        )
    }
    val lastSync = if (summary.lastSuccessAt > 0) {
        summary.lastSuccessAt.toReadableTime(LocalContext.current)
    } else {
        stringResource(R.string.sync_status_never)
    }
    Row(verticalAlignment = Alignment.Top) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(Dimensions.SpacingMediumLarge))
        Column {
            Text(text = text, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.sync_status_last_sync, lastSync),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Der ganze Sync ist gescheitert. Der Titel ist die Fehlermeldung selbst, sie ist schon übersetzt. */
@Composable
private fun ErrorCard(error: String, onOpenSettings: (String) -> Unit) {
    ProblemCard(
        icon = Icons.Outlined.ErrorOutline,
        title = error.ifBlank { stringResource(R.string.sync_status_error) },
        container = MaterialTheme.colorScheme.errorContainer,
        content = MaterialTheme.colorScheme.onErrorContainer
    ) {
        TextButton(onClick = { onOpenSettings(SettingsRoute.Server.route) }) {
            Text(stringResource(R.string.settings_title))
        }
    }
}

@Composable
private fun ConflictCard(conflicts: List<NoteRef>, onOpenNote: (String) -> Unit) {
    ProblemCard(
        icon = Icons.Default.Warning,
        title = pluralStringResource(R.plurals.sync_status_conflicts, conflicts.size, conflicts.size)
    ) {
        conflicts.forEach { NoteLinkRow(it, onOpenNote) }
    }
}

/** Notizen sind synchron, aber MD-Kopien oder Bilder fehlen auf dem Server. */
@Composable
private fun ExportCard(
    problems: ExportProblems,
    markdownNotes: List<NoteRef>,
    onOpenNote: (String) -> Unit,
    onOpenSettings: (String) -> Unit
) {
    ProblemCard(icon = Icons.Outlined.WarningAmber, title = stringResource(R.string.notification_sync_export_title)) {
        val mdCount = problems.markdownFailedIds.size
        if (mdCount > 0) CardLine(pluralStringResource(R.plurals.sync_markdown_failed_count, mdCount, mdCount))
        markdownNotes.forEach { NoteLinkRow(it, onOpenNote) }
        if (problems.markdownImportFailed) CardLine(stringResource(R.string.sync_markdown_import_failed))
        val assets = problems.assetsFailed
        if (assets > 0) CardLine(pluralStringResource(R.plurals.sync_assets_failed_count, assets, assets))
        // Gleicher Grund für MD und Bilder (z. B. beide 409) steht nur einmal da.
        listOfNotNull(problems.markdownReason, problems.assetsReason).distinct().forEach {
            CardLine(stringResource(R.string.sync_status_reason, it))
        }
        Text(
            text = stringResource(R.string.sync_status_notes_in_sync),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = Dimensions.SpacingSmall)
        )
        Row {
            TextButton(onClick = { onOpenSettings(SettingsRoute.ActivityLog.route) }) {
                Text(stringResource(R.string.activity_log_title))
            }
            // Die MD-Schalter liegen in den Sync-Einstellungen; für Bilder gibt es dort nichts zu tun.
            if (problems.markdownFailedCount > 0) {
                TextButton(onClick = { onOpenSettings(SettingsRoute.Sync.route) }) {
                    Text(stringResource(R.string.settings_title))
                }
            }
        }
    }
}

/** Karte im Muster von [dev.dettmer.simplenotes.ui.editor.ConflictBanner]. */
@Composable
private fun ProblemCard(
    icon: ImageVector,
    title: String,
    // Nicht surfaceContainerHigh, das ist der Dialog-Hintergrund, die Karte verschwände darin.
    container: Color = MaterialTheme.colorScheme.tertiaryContainer,
    content: Color = MaterialTheme.colorScheme.onTertiaryContainer,
    body: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.SpacingMediumLarge),
        color = container,
        contentColor = content
    ) {
        Column(modifier = Modifier.padding(Dimensions.SpacingLarge)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null, // Titel daneben sagt dasselbe
                    modifier = Modifier.size(20.dp)
                )
                Text(text = title, style = MaterialTheme.typography.titleSmall)
            }
            body()
        }
    }
}

@Composable
private fun CardLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = Dimensions.SpacingSmall)
    )
}

/** Antippen schließt den Dialog und öffnet die Notiz im Editor. */
@Composable
private fun NoteLinkRow(note: NoteRef, onOpenNote: (String) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimensions.MinTouchTarget)
            .clickable { onOpenNote(note.id) }
            .padding(start = Dimensions.SpacingMedium)
    ) {
        Text(
            text = note.title.ifBlank { stringResource(R.string.untitled) },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
private fun RetryButton(isSyncing: Boolean, onRetry: () -> Unit) {
    FilledTonalButton(onClick = onRetry, enabled = !isSyncing, modifier = Modifier.fillMaxWidth()) {
        if (isSyncing) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(Dimensions.SpacingMedium))
        Text(stringResource(R.string.sync_status_retry))
    }
}

/** Die bisherige Legende. Offen, solange nichts klemmt. Sonst stünden die Probleme unter einer Wand aus Erklärungen. */
@Composable
private fun LegendSection(initiallyExpanded: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMediumLarge)) {
        HorizontalDivider()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimensions.MinTouchTarget)
                .clickable { expanded = !expanded }
        ) {
            Text(
                text = stringResource(R.string.sync_legend_expand),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null
            )
        }
        if (expanded) LegendContent()
    }
}

@Composable
private fun LegendContent() {
    Text(
        text = stringResource(R.string.sync_legend_description),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    LegendRow(
        icon = Icons.Outlined.CloudDone,
        tint = MaterialTheme.colorScheme.primary,
        label = stringResource(R.string.sync_legend_synced_label),
        description = stringResource(R.string.sync_legend_synced_desc)
    )
    LegendRow(
        icon = Icons.Outlined.CloudSync,
        tint = MaterialTheme.colorScheme.outline,
        label = stringResource(R.string.sync_legend_pending_label),
        description = stringResource(R.string.sync_legend_pending_desc)
    )
    LegendRow(
        icon = Icons.Default.Warning,
        tint = MaterialTheme.colorScheme.error,
        label = stringResource(R.string.sync_legend_conflict_label),
        description = stringResource(R.string.sync_legend_conflict_desc)
    )
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
