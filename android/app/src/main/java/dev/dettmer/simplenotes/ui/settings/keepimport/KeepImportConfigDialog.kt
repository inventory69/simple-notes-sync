package dev.dettmer.simplenotes.ui.settings.keepimport

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.noteimport.keep.conflict.ConflictStrategy
import dev.dettmer.simplenotes.noteimport.keep.zip.KeepPreScanResult
import dev.dettmer.simplenotes.ui.theme.Dimensions

private const val MB_DIVISOR = 1024L * 1024L
private const val LARGE_ZIP_WARN_MB = 200L

/**
 * v2.5.0 — Configuring-Dialog (Analyseplan §4.2 [3]).
 *
 * Zeigt Pre-Scan-Vorschau (oder Spinner während Scan), Switches für
 * Archived/Trashed, RadioGroup für Konflikt-Strategie, drei InfoTexts.
 */
@Composable
fun KeepImportConfigDialog(
    state: KeepImportUiState.Configuring,
    onConfirm: (KeepImportOptionsHolder) -> Unit,
    onDismiss: () -> Unit,
) {
    var includeArchived by remember { mutableStateOf(false) }
    var includeTrashed by remember { mutableStateOf(false) }
    var conflictStrategy by remember { mutableStateOf(ConflictStrategy.ALWAYS_CREATE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.keep_import_config_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                // Pre-Scan-Vorschau
                if (state.scanning || state.preScan == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(Dimensions.SpacingMedium))
                        Text(
                            stringResource(R.string.keep_import_config_scanning),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    PreScanSummary(state.preScan)
                }

                Spacer(Modifier.height(Dimensions.SpacingLarge))

                // Switches
                SwitchRow(
                    label = stringResource(R.string.keep_import_config_include_archived),
                    checked = includeArchived,
                    onCheckedChange = { includeArchived = it },
                )
                SwitchRow(
                    label = stringResource(R.string.keep_import_config_include_trashed),
                    checked = includeTrashed,
                    onCheckedChange = { includeTrashed = it },
                )

                Spacer(Modifier.height(Dimensions.SpacingLarge))

                // RadioGroup
                Text(
                    stringResource(R.string.keep_import_config_conflict_label),
                    style = MaterialTheme.typography.titleSmall,
                )
                ConflictRadio(
                    label = stringResource(R.string.keep_import_config_conflict_always_create),
                    selected = conflictStrategy == ConflictStrategy.ALWAYS_CREATE,
                    onSelect = { conflictStrategy = ConflictStrategy.ALWAYS_CREATE },
                )
                ConflictRadio(
                    label = stringResource(R.string.keep_import_config_conflict_skip),
                    selected = conflictStrategy == ConflictStrategy.SKIP,
                    onSelect = { conflictStrategy = ConflictStrategy.SKIP },
                )
                ConflictRadio(
                    label = stringResource(R.string.keep_import_config_conflict_replace),
                    selected = conflictStrategy == ConflictStrategy.REPLACE,
                    onSelect = { conflictStrategy = ConflictStrategy.REPLACE },
                )

                Spacer(Modifier.height(Dimensions.SpacingLarge))

                // InfoTexts
                Text(
                    stringResource(R.string.keep_import_config_info_labels),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(Dimensions.SpacingMedium))
                Text(
                    stringResource(R.string.keep_import_config_info_attachments),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(Dimensions.SpacingMedium))
                Text(
                    stringResource(R.string.keep_import_config_info_color_pin),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        KeepImportOptionsHolder(
                            includeArchived = includeArchived,
                            includeTrashed = includeTrashed,
                            conflictStrategy = conflictStrategy,
                        )
                    )
                },
                enabled = !state.scanning && state.preScan != null,
            ) {
                Text(stringResource(R.string.keep_import_config_button_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.keep_import_config_button_cancel))
            }
        },
    )
}

@Composable
private fun PreScanSummary(scan: KeepPreScanResult) {
    val sizeMb = scan.sizeBytes / MB_DIVISOR
    Column {
        Text(
            stringResource(R.string.keep_import_prescan_header),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(Dimensions.SpacingMedium))
        Text(stringResource(R.string.keep_import_prescan_active, scan.activeCount))
        Text(stringResource(R.string.keep_import_prescan_archived, scan.archivedCount))
        Text(stringResource(R.string.keep_import_prescan_trashed, scan.trashedCount))
        Text(stringResource(R.string.keep_import_prescan_labels, scan.labelCount))
        Text(stringResource(R.string.keep_import_prescan_shared, scan.sharedCount))
        Text(stringResource(R.string.keep_import_prescan_attachments, scan.notesWithAttachments))
        if (sizeMb > LARGE_ZIP_WARN_MB) {
            Spacer(Modifier.height(Dimensions.SpacingMedium))
            Text(
                stringResource(R.string.keep_import_prescan_large_warning, sizeMb),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimensions.SpacingMedium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ConflictRadio(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = Dimensions.SpacingMedium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.size(Dimensions.SpacingMedium))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
