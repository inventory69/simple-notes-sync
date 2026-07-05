package dev.dettmer.simplenotes.ui.settings.screens

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.settings.SettingsViewModel
import dev.dettmer.simplenotes.ui.settings.components.SettingsButton
import dev.dettmer.simplenotes.ui.settings.components.SettingsDivider
import dev.dettmer.simplenotes.ui.settings.components.SettingsInfoCard
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold
import dev.dettmer.simplenotes.ui.settings.components.SettingsSwitch
import kotlinx.coroutines.launch

/**
 * Markdown Desktop integration settings screen
 * v1.5.0: Jetpack Compose Settings Redesign
 */
@Composable
fun MarkdownSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val markdownAutoSync by viewModel.markdownAutoSync.collectAsState()
    val exportProgress by viewModel.markdownExportProgress.collectAsState()

    val isServerConfigured by viewModel.isServerConfigured.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val syncFolderName by viewModel.syncFolderName.collectAsState()

    // v1.5.0 Fix: Progress Dialog for initial export
    exportProgress?.let { progress ->
        AlertDialog(
            onDismissRequest = { /* Not dismissable */ },
            title = { Text(stringResource(R.string.markdown_dialog_title)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = when {
                            progress.isChecking -> stringResource(R.string.markdown_checking_server)
                            progress.isComplete -> stringResource(R.string.markdown_export_complete)
                            else -> stringResource(R.string.markdown_export_progress, progress.current, progress.total)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // 🆕 v1.10.0: Indeterminate während Server-Check, determinate beim Export
                    if (progress.isChecking) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = {
                                if (progress.total > 0) {
                                    progress.current.toFloat() / progress.total.toFloat()
                                } else {
                                    0f
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = { /* No button - auto dismiss */ }
        )
    }

    SettingsScaffold(
        title = stringResource(R.string.markdown_settings_title),
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
                text = stringResource(R.string.markdown_info)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Used MD folder (only meaningful once a server is connected)
            if (isServerConfigured) {
                val folderPath = "$serverUrl/$syncFolderName-md"
                val clipboard = LocalClipboard.current
                val scope = rememberCoroutineScope()

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.markdown_folder_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = folderPath,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", folderPath)))
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.markdown_folder_copy_cd)
                            )
                        }
                    }
                }
            }

            // Markdown Auto-Sync Toggle
            // 🌟 v1.6.0: Disabled when offline mode active
            SettingsSwitch(
                title = stringResource(R.string.markdown_auto_sync_title),
                subtitle = if (!isServerConfigured) {
                    stringResource(R.string.settings_sync_offline_mode)
                } else {
                    stringResource(R.string.markdown_auto_sync_subtitle)
                },
                checked = markdownAutoSync,
                onCheckedChange = { viewModel.setMarkdownAutoSync(it) },
                icon = Icons.Default.Description,
                enabled = isServerConfigured
            )

            // Manual sync button (only visible when auto-sync is off)
            // 🌟 v1.6.0: Also disabled in offline mode
            if (!markdownAutoSync) {
                SettingsDivider()

                SettingsInfoCard(
                    text = stringResource(R.string.markdown_manual_sync_info)
                )

                Spacer(modifier = Modifier.height(8.dp))

                SettingsButton(
                    text = stringResource(R.string.markdown_manual_sync_button),
                    onClick = { viewModel.performManualMarkdownSync() },
                    enabled = isServerConfigured,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // 🌟 v1.6.0: Show hint when offline
                if (!isServerConfigured) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_sync_offline_mode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
