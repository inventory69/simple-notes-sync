package dev.dettmer.simplenotes.ui.settings.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.settings.SettingsViewModel
import dev.dettmer.simplenotes.ui.settings.components.SettingsButton
import dev.dettmer.simplenotes.ui.settings.components.SettingsDivider
import dev.dettmer.simplenotes.ui.settings.components.SettingsInfoCard
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold
import dev.dettmer.simplenotes.ui.settings.components.SettingsSwitch

/**
 * Markdown Desktop integration settings screen
 * v1.5.0: Jetpack Compose Settings Redesign
 */
@Composable
fun MarkdownSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val markdownAutoSync by viewModel.markdownAutoSync.collectAsState()
    val exportProgress by viewModel.markdownExportProgress.collectAsState()
    
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
                        text = if (progress.isComplete) {
                            stringResource(R.string.markdown_export_complete)
                        } else {
                            stringResource(R.string.markdown_export_progress, progress.current, progress.total)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    LinearProgressIndicator(
                        progress = { 
                            if (progress.total > 0) {
                                progress.current.toFloat() / progress.total.toFloat()
                            } else 0f
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
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
            
            // Markdown Auto-Sync Toggle
            SettingsSwitch(
                title = stringResource(R.string.markdown_auto_sync_title),
                subtitle = stringResource(R.string.markdown_auto_sync_subtitle),
                checked = markdownAutoSync,
                onCheckedChange = { viewModel.setMarkdownAutoSync(it) },
                icon = Icons.Default.Description
            )
            
            // Manual sync button (only visible when auto-sync is off)
            if (!markdownAutoSync) {
                SettingsDivider()
                
                SettingsInfoCard(
                    text = stringResource(R.string.markdown_manual_sync_info)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                SettingsButton(
                    text = stringResource(R.string.markdown_manual_sync_button),
                    onClick = { viewModel.performManualMarkdownSync() },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
