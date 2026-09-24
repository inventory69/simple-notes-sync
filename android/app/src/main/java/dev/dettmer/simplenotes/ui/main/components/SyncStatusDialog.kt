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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
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
    val (icon, tint, title) = statusVisuals(summary)
    val lastSync = if (summary.lastSuccessAt > 0) {
        summary.lastSuccessAt.toReadableTime(LocalContext.current)
    } else {
        stringResource(R.string.sync_status_never)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(imageVector = icon, contentDescription = null, tint = tint) },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = title, textAlign = TextAlign.Center)
                Text(
                    text = stringResource(R.string.sync_status_last_sync, lastSync),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Dimensions.SpacingSmall)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMediumLarge)
            ) {
                summary.lastError?.let { ErrorCard(it, stale = summary.state == SyncStatusSummary.State.STALE) }
                if (summary.conflicts.isNotEmpty()) ConflictCard(summary.conflicts, onOpenNote)
                summary.exportProblems?.let { ExportCard(it, summary.markdownNotes, onOpenNote) }
                Actions(summary, isSyncing, onRetry, onOpenSettings)
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

/** Icon, Farbe und Titel des Dialogkopfs. Der Zustand selbst ist die Überschrift. */
@Composable
private fun statusVisuals(summary: SyncStatusSummary): Triple<ImageVector, Color, String> = when (summary.state) {
    SyncStatusSummary.State.STALE -> Triple(
        Icons.Outlined.SyncProblem,
        MaterialTheme.colorScheme.error,
        stringResource(R.string.sync_status_stale)
    )
    SyncStatusSummary.State.FAILED -> Triple(
        Icons.Outlined.SyncProblem,
        MaterialTheme.colorScheme.tertiary,
        stringResource(R.string.sync_status_cannot_sync)
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

/**
 * Der ganze Sync ist gescheitert. Der Titel ist die Fehlermeldung selbst, sie ist schon übersetzt.
 * Seit über einem Tag ohne Erfolg ([stale]) wird die Karte rötlich, der Text erbt dann onErrorContainer.
 */
@Composable
private fun ErrorCard(error: String, stale: Boolean) {
    ProblemCard(
        title = error.ifBlank { stringResource(R.string.sync_error_unknown) },
        container = if (stale) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        }
    ) {
        Text(
            text = stringResource(if (stale) R.string.sync_status_stale_hint else R.string.sync_status_notes_safe),
            style = MaterialTheme.typography.bodyMedium,
            color = if (stale) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimensions.SpacingSmall)
        )
    }
}

@Composable
private fun ConflictCard(conflicts: List<NoteRef>, onOpenNote: (String) -> Unit) {
    ProblemCard(
        icon = Icons.Default.Warning,
        iconTint = MaterialTheme.colorScheme.error,
        title = pluralStringResource(R.plurals.sync_status_conflicts, conflicts.size, conflicts.size)
    ) {
        conflicts.forEach { NoteLinkRow(it, onOpenNote) }
    }
}

/** Notizen sind synchron, aber MD-Kopien oder Bilder fehlen auf dem Server. */
@Composable
private fun ExportCard(problems: ExportProblems, markdownNotes: List<NoteRef>, onOpenNote: (String) -> Unit) {
    ProblemCard(
        icon = Icons.Outlined.WarningAmber,
        iconTint = MaterialTheme.colorScheme.tertiary,
        title = stringResource(R.string.notification_sync_export_title)
    ) {
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
    }
}

/** Karten informieren, hier wird gehandelt: Retry oben, Sprünge in die Einstellungen darunter. */
@Composable
private fun Actions(
    summary: SyncStatusSummary,
    isSyncing: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: (String) -> Unit
) {
    val export = summary.exportProblems
    val canRetry = summary.lastError != null || export != null
    if (!canRetry && summary.lastError == null) return
    Column(verticalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium)) {
        if (canRetry) RetryButton(isSyncing, onRetry)
        if (summary.lastError != null) {
            ActionButton(Icons.Outlined.Settings, stringResource(R.string.server_settings_title)) {
                onOpenSettings(SettingsRoute.Server.route)
            }
        }
        // Die MD-Schalter liegen in den Sync-Einstellungen; für Bilder gibt es dort nichts zu tun.
        if ((export?.markdownFailedCount ?: 0) > 0) {
            ActionButton(Icons.Outlined.Sync, stringResource(R.string.settings_sync)) {
                onOpenSettings(SettingsRoute.Sync.route)
            }
        }
        if (export != null) {
            ActionButton(Icons.Outlined.History, stringResource(R.string.activity_log_title)) {
                onOpenSettings(SettingsRoute.ActivityLog.route)
            }
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(Dimensions.SpacingMedium))
        Text(label)
    }
}

/** Ruhige Karte im Muster von [dev.dettmer.simplenotes.ui.editor.ConflictBanner]; nur das Icon trägt Farbe. */
@Composable
private fun ProblemCard(
    title: String,
    icon: ImageVector? = null,
    iconTint: Color = Color.Unspecified,
    // Eine Stufe über surfaceContainerHigh, dem Dialog-Hintergrund, sonst verschwände die Karte darin.
    container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    body: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimensions.SpacingMediumLarge),
        color = container
    ) {
        Column(modifier = Modifier.padding(Dimensions.SpacingLarge)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimensions.SpacingMedium)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null, // Titel daneben sagt dasselbe
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
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
        Text(stringResource(if (isSyncing) R.string.sync_status_syncing else R.string.sync_status_retry))
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
