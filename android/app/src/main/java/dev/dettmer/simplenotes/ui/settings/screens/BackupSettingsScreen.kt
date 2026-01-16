package dev.dettmer.simplenotes.ui.settings.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.backup.RestoreMode
import dev.dettmer.simplenotes.ui.settings.SettingsViewModel
import dev.dettmer.simplenotes.ui.settings.components.RadioOption
import dev.dettmer.simplenotes.ui.settings.components.SettingsButton
import dev.dettmer.simplenotes.ui.settings.components.SettingsDivider
import dev.dettmer.simplenotes.ui.settings.components.SettingsInfoCard
import dev.dettmer.simplenotes.ui.settings.components.SettingsOutlinedButton
import dev.dettmer.simplenotes.ui.settings.components.SettingsRadioGroup
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold
import dev.dettmer.simplenotes.ui.settings.components.SettingsSectionHeader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup and restore settings screen
 * v1.5.0: Jetpack Compose Settings Redesign
 */
@Composable
fun BackupSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val isBackupInProgress by viewModel.isBackupInProgress.collectAsState()
    
    // Restore dialog state
    var showRestoreDialog by remember { mutableStateOf(false) }
    var restoreSource by remember { mutableStateOf<RestoreSource>(RestoreSource.LocalFile) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var selectedRestoreMode by remember { mutableStateOf(RestoreMode.MERGE) }
    
    // File picker launchers
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.createBackup(it) }
    }
    
    val restoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            pendingRestoreUri = it
            restoreSource = RestoreSource.LocalFile
            showRestoreDialog = true
        }
    }
    
    SettingsScaffold(
        title = stringResource(R.string.backup_settings_title),
        onBack = onBack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // Info Card
            SettingsInfoCard(
                text = stringResource(R.string.backup_auto_info)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Local Backup Section
            SettingsSectionHeader(text = stringResource(R.string.backup_local_section))
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingsButton(
                text = stringResource(R.string.backup_create),
                onClick = {
                    val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US)
                        .format(Date())
                    val filename = "simplenotes_backup_$timestamp.json"
                    createBackupLauncher.launch(filename)
                },
                isLoading = isBackupInProgress,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingsOutlinedButton(
                text = stringResource(R.string.backup_restore_file),
                onClick = {
                    restoreFileLauncher.launch(arrayOf("application/json"))
                },
                isLoading = isBackupInProgress,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            SettingsDivider()
            
            // Server Backup Section
            SettingsSectionHeader(text = stringResource(R.string.backup_server_section))
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingsOutlinedButton(
                text = stringResource(R.string.backup_restore_server),
                onClick = {
                    restoreSource = RestoreSource.Server
                    showRestoreDialog = true
                },
                isLoading = isBackupInProgress,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    
    // Restore Mode Dialog
    if (showRestoreDialog) {
        RestoreModeDialog(
            source = restoreSource,
            selectedMode = selectedRestoreMode,
            onModeSelected = { selectedRestoreMode = it },
            onConfirm = {
                showRestoreDialog = false
                when (restoreSource) {
                    RestoreSource.LocalFile -> {
                        pendingRestoreUri?.let { uri ->
                            viewModel.restoreFromFile(uri, selectedRestoreMode)
                        }
                    }
                    RestoreSource.Server -> {
                        viewModel.restoreFromServer(selectedRestoreMode)
                    }
                }
            },
            onDismiss = {
                showRestoreDialog = false
                pendingRestoreUri = null
            }
        )
    }
}

/**
 * Restore source enum
 */
private enum class RestoreSource {
    LocalFile,
    Server
}

/**
 * Dialog for selecting restore mode
 */
@Composable
private fun RestoreModeDialog(
    source: RestoreSource,
    selectedMode: RestoreMode,
    onModeSelected: (RestoreMode) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val sourceText = when (source) {
        RestoreSource.LocalFile -> stringResource(R.string.backup_restore_source_file)
        RestoreSource.Server -> stringResource(R.string.backup_restore_source_server)
    }
    
    val modeOptions = listOf(
        RadioOption(
            value = RestoreMode.MERGE,
            title = stringResource(R.string.backup_mode_merge_title),
            subtitle = stringResource(R.string.backup_mode_merge_subtitle)
        ),
        RadioOption(
            value = RestoreMode.REPLACE,
            title = stringResource(R.string.backup_mode_replace_title),
            subtitle = stringResource(R.string.backup_mode_replace_subtitle)
        ),
        RadioOption(
            value = RestoreMode.OVERWRITE_DUPLICATES,
            title = stringResource(R.string.backup_mode_overwrite_title),
            subtitle = stringResource(R.string.backup_mode_overwrite_subtitle)
        )
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_restore_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.backup_restore_source, sourceText),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = stringResource(R.string.backup_restore_mode_label),
                    style = MaterialTheme.typography.labelLarge
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                SettingsRadioGroup(
                    options = modeOptions,
                    selectedValue = selectedMode,
                    onValueSelected = onModeSelected
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = stringResource(R.string.backup_restore_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.backup_restore_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
