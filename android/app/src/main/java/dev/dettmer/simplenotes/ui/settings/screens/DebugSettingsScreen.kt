package dev.dettmer.simplenotes.ui.settings.screens

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.dettmer.simplenotes.BuildConfig
import dev.dettmer.simplenotes.ui.settings.SettingsViewModel
import dev.dettmer.simplenotes.ui.settings.components.SettingsButton
import dev.dettmer.simplenotes.ui.settings.components.SettingsDangerButton
import dev.dettmer.simplenotes.ui.settings.components.SettingsDivider
import dev.dettmer.simplenotes.ui.settings.components.SettingsInfoCard
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold
import dev.dettmer.simplenotes.ui.settings.components.SettingsSectionHeader
import dev.dettmer.simplenotes.ui.settings.components.SettingsSwitch

/**
 * Debug and diagnostics settings screen
 * v1.5.0: Jetpack Compose Settings Redesign
 */
@Composable
fun DebugSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val fileLoggingEnabled by viewModel.fileLoggingEnabled.collectAsState()
    
    var showClearLogsDialog by remember { mutableStateOf(false) }
    
    SettingsScaffold(
        title = "Debug & Diagnose",
        onBack = onBack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // File Logging Toggle
            SettingsSwitch(
                title = "Datei-Logging",
                subtitle = "Sync-Logs in Datei speichern",
                checked = fileLoggingEnabled,
                onCheckedChange = { viewModel.setFileLogging(it) },
                icon = Icons.AutoMirrored.Filled.Notes
            )
            
            // Privacy Info
            SettingsInfoCard(
                text = "🔒 Datenschutz: Logs werden nur lokal auf deinem Gerät gespeichert " +
                    "und niemals an externe Server gesendet. Die Logs enthalten " +
                    "Sync-Aktivitäten zur Fehlerdiagnose. Du kannst sie jederzeit löschen " +
                    "oder exportieren."
            )
            
            SettingsDivider()
            
            SettingsSectionHeader(text = "Log-Aktionen")
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Export Logs Button
            SettingsButton(
                text = "📤 Logs exportieren & teilen",
                onClick = {
                    val logFile = viewModel.getLogFile()
                    if (logFile != null && logFile.exists() && logFile.length() > 0L) {
                        val logUri = FileProvider.getUriForFile(
                            context,
                            "${BuildConfig.APPLICATION_ID}.fileprovider",
                            logFile
                        )
                        
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, logUri)
                            putExtra(Intent.EXTRA_SUBJECT, "SimpleNotes Sync Logs")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        
                        context.startActivity(Intent.createChooser(shareIntent, "Logs teilen via..."))
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Clear Logs Button
            SettingsDangerButton(
                text = "🗑️ Logs löschen",
                onClick = { showClearLogsDialog = true },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    
    // Clear Logs Confirmation Dialog
    if (showClearLogsDialog) {
        AlertDialog(
            onDismissRequest = { showClearLogsDialog = false },
            title = { Text("Logs löschen?") },
            text = {
                Text("Alle gespeicherten Sync-Logs werden unwiderruflich gelöscht.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearLogsDialog = false
                        viewModel.clearLogs()
                    }
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLogsDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}
