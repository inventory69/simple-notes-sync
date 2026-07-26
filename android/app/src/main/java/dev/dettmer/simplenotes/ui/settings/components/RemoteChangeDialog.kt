package dev.dettmer.simplenotes.ui.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.settings.SettingsViewModel

/**
 * 🆕 v2.11.0: Bestätigungsdialog beim Wechsel des WebDAV-Sync-Ordners.
 * 🆕 v2.12.0: Deckt zusätzlich den Server-Wechsel ab (Remote-Ziel = Ordner + Server),
 * Titel/Message sind `kind`-abhängig.
 * Ausgelöst per Zurück-Intercept auf ServerSettingsScreen (kein Save-Button).
 * M3 ModalBottomSheet, Aufbau analog zu ExcludeFolderSyncSheet.kt / DeleteSelectionDialog
 * (3 gleichwertige gestapelte Aktionen statt Standard-AlertDialog-Buttons).
 * Spacing: rohe .dp-Literale statt Dimensions.*, um exakt mit diesem Muster konsistent zu bleiben.
 */
// Abbau: TECH_DEBT_ROADMAP.md §4 (Bestand, keinem Refactoring-Slice zugeordnet)
@Suppress("CyclomaticComplexMethod", "LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteChangeDialog(
    prompt: SettingsViewModel.FolderChangePrompt,
    inProgress: Boolean,
    onMigrate: () -> Unit,
    onSwitch: () -> Unit,
    onCancel: () -> Unit
) {
    var showLocalOnlyConfirm by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 🔧 v2.11.0: Verhindert Wegwischen/Predictive-Back, solange der Restore noch läuft
    // (Race Condition: onBack() darf erst nach dem Completion-Signal aus dem ViewModel feuern).
    LaunchedEffect(inProgress) { if (inProgress) sheetState.show() }

    val title = when (prompt.kind) {
        SettingsViewModel.RemoteChangeKind.FOLDER -> stringResource(R.string.folder_change_dialog_title)
        SettingsViewModel.RemoteChangeKind.SERVER -> stringResource(R.string.server_change_dialog_title)
        SettingsViewModel.RemoteChangeKind.BOTH -> stringResource(R.string.remote_change_dialog_title)
    }
    val message = when (prompt.kind) {
        SettingsViewModel.RemoteChangeKind.FOLDER -> stringResource(
            R.string.folder_change_dialog_message,
            prompt.oldLabel,
            prompt.newLabel
        )
        SettingsViewModel.RemoteChangeKind.SERVER -> stringResource(
            R.string.server_change_dialog_message,
            prompt.oldLabel,
            prompt.newLabel
        )
        SettingsViewModel.RemoteChangeKind.BOTH -> stringResource(R.string.remote_change_dialog_message)
    }

    ModalBottomSheet(
        onDismissRequest = { if (!inProgress) onCancel() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (prompt.unsyncedCount > 0 && !inProgress) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.folder_change_dialog_data_loss_warning,
                        prompt.unsyncedCount
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (inProgress) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.folder_change_in_progress),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                // "Nicht mitnehmen" (switch/REPLACE): lokal destruktiv → starker, fehlerfarbener
                // Button oben, Position/Styling analog ExcludeFolderSyncSheet.
                Button(
                    onClick = { if (prompt.localOnlyCount > 0) showLocalOnlyConfirm = true else onSwitch() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.folder_change_action_switch))
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                // "Notizen mitnehmen" (migrate): sichere Standard-Aktion, kein Datenverlust.
                OutlinedButton(onClick = onMigrate, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = Icons.Outlined.CloudSync,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.folder_change_action_migrate))
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }

    if (showLocalOnlyConfirm && !inProgress) {
        AlertDialog(
            onDismissRequest = { showLocalOnlyConfirm = false },
            title = { Text(stringResource(R.string.folder_change_local_only_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.folder_change_local_only_confirm_message,
                        prompt.localOnlyCount
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLocalOnlyConfirm = false
                    onSwitch()
                }) {
                    Text(
                        text = stringResource(R.string.folder_change_local_only_confirm_button),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLocalOnlyConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
