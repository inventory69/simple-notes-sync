package dev.dettmer.simplenotes.ui.settings.screens

import android.content.ActivityNotFoundException
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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.dettmer.simplenotes.BuildConfig
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.settings.SettingsViewModel
import dev.dettmer.simplenotes.ui.settings.components.SettingsButton
import dev.dettmer.simplenotes.ui.settings.components.SettingsDangerButton
import dev.dettmer.simplenotes.ui.settings.components.SettingsDivider
import dev.dettmer.simplenotes.ui.settings.components.SettingsInfoCard
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold
import dev.dettmer.simplenotes.ui.settings.components.SettingsSectionHeader
import dev.dettmer.simplenotes.ui.settings.components.SettingsSwitch
import dev.dettmer.simplenotes.utils.Logger
import dev.dettmer.simplenotes.utils.SyncDebugLogger

private const val TAG = "DebugSettingsScreen"

/**
 * Debug and diagnostics settings screen
 * v1.5.0: Jetpack Compose Settings Redesign
 */
@Composable
fun DebugSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val fileLoggingEnabled by viewModel.fileLoggingEnabled.collectAsState()
    val syncDebugLoggingEnabled by viewModel.syncDebugLoggingEnabled.collectAsState()

    var showClearLogsDialog by remember { mutableStateOf(false) }
    var showDisableAfterExportDialog by remember { mutableStateOf(false) }
    // True after export while waiting for the share sheet to be dismissed
    var pendingDisableDialog by remember { mutableStateOf(false) }

    // Show the disable-after-export dialog only once the screen resumes (share sheet closed)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && pendingDisableDialog) {
                pendingDisableDialog = false
                showDisableAfterExportDialog = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsScaffold(
        title = stringResource(R.string.debug_settings_title),
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
                title = stringResource(R.string.debug_file_logging_title),
                subtitle = stringResource(R.string.debug_file_logging_subtitle),
                checked = fileLoggingEnabled,
                onCheckedChange = { viewModel.setFileLogging(it) },
                icon = Icons.AutoMirrored.Filled.Notes
            )

            // 🆕 v2.2.0: Persistent sync debug log toggle
            SettingsSwitch(
                title = stringResource(R.string.debug_sync_debug_logging_title),
                subtitle = stringResource(R.string.debug_sync_debug_logging_subtitle),
                checked = syncDebugLoggingEnabled,
                onCheckedChange = { viewModel.setSyncDebugLogging(it) },
                icon = Icons.Filled.BugReport
            )

            // Privacy Info
            SettingsInfoCard(
                text = stringResource(R.string.debug_privacy_info)
            )

            SettingsDivider()

            SettingsSectionHeader(text = stringResource(R.string.debug_log_actions_section))

            Spacer(modifier = Modifier.height(8.dp))

            // Export Logs Button
            val logsSubject = stringResource(R.string.debug_logs_subject)
            val logsShareVia = stringResource(R.string.debug_logs_share_via)
            val exportEmptyMsg = stringResource(R.string.debug_export_empty_message)
            val exportFailedMsg = stringResource(R.string.debug_export_failed_toast)
            val exportPreparingMsg = stringResource(R.string.debug_export_preparing)

            SettingsButton(
                text = stringResource(R.string.debug_export_logs),
                onClick = {
                    // Collect all non-empty log files (regular log + sync debug log)
                    val logFiles = listOfNotNull(
                        viewModel.getLogFile()?.takeIf { it.exists() && it.length() > 0L },
                        SyncDebugLogger.getLogFile(context)?.takeIf { it.exists() && it.length() > 0L }
                    )
                    if (logFiles.isEmpty()) {
                        viewModel.showSnackbar(exportEmptyMsg)
                        return@SettingsButton
                    }
                    viewModel.showSnackbar(exportPreparingMsg)
                    val uris = ArrayList(logFiles.map { file ->
                        FileProvider.getUriForFile(
                            context,
                            "${BuildConfig.APPLICATION_ID}.fileprovider",
                            file
                        )
                    })
                    val shareIntent = if (uris.size == 1) {
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uris[0])
                            putExtra(Intent.EXTRA_SUBJECT, logsSubject)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    } else {
                        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                            type = "text/plain"
                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                            putExtra(Intent.EXTRA_SUBJECT, logsSubject)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    }
                    try {
                        context.startActivity(Intent.createChooser(shareIntent, logsShareVia))
                        if (fileLoggingEnabled) pendingDisableDialog = true
                    } catch (e: ActivityNotFoundException) {
                        Logger.w(TAG, "No app available to handle share intent for logs: ${e.message}")
                        viewModel.showSnackbar(exportFailedMsg)
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Clear Logs Button
            SettingsDangerButton(
                text = stringResource(R.string.debug_delete_logs),
                onClick = { showClearLogsDialog = true },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            SettingsDivider()

            // v1.8.0: Test Mode Section
            SettingsSectionHeader(text = stringResource(R.string.debug_test_section))

            Spacer(modifier = Modifier.height(8.dp))

            // Info about test mode
            SettingsInfoCard(
                text = stringResource(R.string.debug_reset_changelog_desc)
            )

            val changelogResetToast = stringResource(R.string.debug_changelog_reset)

            SettingsButton(
                text = stringResource(R.string.debug_reset_changelog),
                onClick = {
                    viewModel.resetChangelogVersion()
                    viewModel.showSnackbar(changelogResetToast)
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Clear Logs Confirmation Dialog
    if (showClearLogsDialog) {
        AlertDialog(
            onDismissRequest = { showClearLogsDialog = false },
            title = { Text(stringResource(R.string.debug_delete_logs_title)) },
            text = {
                Text(stringResource(R.string.debug_delete_logs_message))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearLogsDialog = false
                        viewModel.clearLogs()
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLogsDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // Disable Logging After Export Dialog
    if (showDisableAfterExportDialog) {
        AlertDialog(
            onDismissRequest = { showDisableAfterExportDialog = false },
            title = { Text(stringResource(R.string.debug_after_export_title)) },
            text = { Text(stringResource(R.string.debug_after_export_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisableAfterExportDialog = false
                        viewModel.setFileLogging(false)
                    }
                ) {
                    Text(stringResource(R.string.debug_after_export_disable))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableAfterExportDialog = false }) {
                    Text(stringResource(R.string.debug_after_export_keep))
                }
            }
        )
    }
}
