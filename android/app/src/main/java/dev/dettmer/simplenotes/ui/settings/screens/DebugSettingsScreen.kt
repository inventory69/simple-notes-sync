package dev.dettmer.simplenotes.ui.settings.screens

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.dettmer.simplenotes.BuildConfig
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.settings.SettingsRoute
import dev.dettmer.simplenotes.ui.settings.SettingsViewModel
import dev.dettmer.simplenotes.ui.settings.components.SettingsButton
import dev.dettmer.simplenotes.ui.settings.components.SettingsDangerButton
import dev.dettmer.simplenotes.ui.settings.components.SettingsHint
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold
import dev.dettmer.simplenotes.ui.settings.components.SettingsSectionCard
import dev.dettmer.simplenotes.ui.settings.components.SettingsSwitch
import dev.dettmer.simplenotes.utils.Logger
import java.io.File
import kotlinx.coroutines.launch

private const val TAG = "DebugSettingsScreen"

/** Nur in Beta-Builds sichtbar. Steht ohnehin öffentlich in den F-Droid-Metadaten. */
private const val MAINTAINER_EMAIL = "admin@dettmer.dev"

/**
 * 🆕 v2.14.0: Teilen-Intent für die bereits anonymisierten Logdateien.
 *
 * [recipient] != null hängt nur die Empfängeradresse an. Die Einschränkung auf Mail-Apps
 * passiert nicht hier, sondern über [resolveMailTargets] — ein `mailto:`-Selector wäre der
 * naheliegende Weg, scheitert aber am MIME-Typ (siehe dort).
 */
private fun buildLogShareIntent(
    context: Context,
    files: List<File>,
    subject: String,
    body: String?,
    recipient: String?
): Intent {
    val uris = ArrayList(
        files.map {
            FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", it)
        }
    )
    val single = uris.size == 1
    return Intent(if (single) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
        type = "text/plain"
        if (single) {
            putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        // Ohne ClipData hängt das Leserecht auf die content://-URIs daran, dass der
        // System-Chooser sie aus EXTRA_STREAM nachträgt. Beim Direktstart in eine Mail-App
        // gibt es keinen Chooser, die Anhänge kämen ohne Leserecht an.
        clipData = ClipData.newRawUri(null, uris[0]).apply {
            uris.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        putExtra(Intent.EXTRA_SUBJECT, subject)
        body?.let { putExtra(Intent.EXTRA_TEXT, it) }
        recipient?.let { putExtra(Intent.EXTRA_EMAIL, arrayOf(it)) }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

/**
 * 🆕 v2.14.0: Packages, die sowohl `mailto:` beantworten als auch [share] samt Anhang annehmen.
 *
 * Der offensichtliche Weg wäre ein `mailto:`-Selector auf [share]. Der scheitert daran, dass
 * das Framework zwar den Selector auflöst, den resolvedType aber aus dem Basis-Intent zieht:
 * Mail-Apps registrieren `SENDTO`/`mailto` ohne mimeType, und ein Filter ohne Typ matcht nicht
 * gegen `text/plain`. Ohne Typ wiederum verwirft die Mail-App die Anhänge. Beides zusammen geht
 * nur über einen expliziten Package-Start.
 *
 * Der Schnitt beider Abfragen ist nötig, weil `mailto:` auch von Nicht-Mail-Apps beansprucht
 * wird (PayPal etwa) — die würden am Anhang scheitern.
 */
private fun resolveMailTargets(context: Context, share: Intent): List<String> {
    val pm = context.packageManager
    val mailtoPackages = pm
        .queryIntentActivities(Intent(Intent.ACTION_SENDTO, "mailto:".toUri()), 0)
        .mapTo(mutableSetOf()) { it.activityInfo.packageName }
    return pm.queryIntentActivities(share, 0)
        .map { it.activityInfo.packageName }
        .filter { it in mailtoPackages }
        .distinct()
}

/**
 * Debug and diagnostics settings screen
 * v1.5.0: Jetpack Compose Settings Redesign
 */
// Abbau: TECH_DEBT_ROADMAP.md §4 (Bestand, keinem Refactoring-Slice zugeordnet)
@Suppress("LongMethod")
@Composable
fun DebugSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit, onNavigate: (SettingsRoute) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val fileLoggingEnabled by viewModel.fileLoggingEnabled.collectAsState()
    val syncDebugLoggingEnabled by viewModel.syncDebugLoggingEnabled.collectAsState()

    var showClearLogsDialog by remember { mutableStateOf(false) }
    var showDisableAfterExportDialog by remember { mutableStateOf(false) }
    // True after export while waiting for the share sheet to be dismissed
    var pendingDisableDialog by remember { mutableStateOf(false) }
    var showClearETagCacheDialog by remember { mutableStateOf(false) }

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

            SettingsSectionCard(title = stringResource(R.string.debug_logging_section)) {
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

                SettingsHint(text = stringResource(R.string.debug_privacy_info))
            }

            SettingsSectionCard(title = stringResource(R.string.debug_log_actions_section)) {
                // Export Logs Button
                val logsSubject = stringResource(R.string.debug_logs_subject)
                val logsShareVia = stringResource(R.string.debug_logs_share_via)
                val exportEmptyMsg = stringResource(R.string.debug_export_empty_message)
                val exportFailedMsg = stringResource(R.string.debug_export_failed_toast)
                val exportPreparingMsg = stringResource(R.string.debug_export_preparing)

                SettingsButton(
                    text = stringResource(R.string.debug_export_logs),
                    onClick = {
                        // 🆕 v2.14.0: Geteilt werden anonymisierte Kopien, nie die Originale —
                        // das Lesen/Schreiben läuft im ViewModel auf Dispatchers.IO.
                        scope.launch {
                            viewModel.showSnackbar(exportPreparingMsg)
                            val logFiles = viewModel.prepareLogsForSharing()
                            if (logFiles.isEmpty()) {
                                viewModel.showSnackbar(exportEmptyMsg)
                                return@launch
                            }
                            val shareIntent = buildLogShareIntent(
                                context, logFiles, logsSubject, body = null, recipient = null
                            )
                            try {
                                context.startActivity(Intent.createChooser(shareIntent, logsShareVia))
                                if (fileLoggingEnabled) pendingDisableDialog = true
                            } catch (e: ActivityNotFoundException) {
                                Logger.w(TAG, "No app available to handle share intent for logs: ${e.message}")
                                viewModel.showSnackbar(exportFailedMsg)
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // 🆕 v2.14.0: Direktweg für Beta-Tester. In Release-Builds bewusst nicht sichtbar
                // — dort führt der Weg über ein Issue, sonst kommen Logs ohne Kontext an.
                if (BuildConfig.BETA_BUILD) {
                    Spacer(modifier = Modifier.height(8.dp))

                    val mailSubject = stringResource(
                        R.string.debug_logs_mail_subject,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                        BuildConfig.GIT_HASH
                    )
                    val mailBody = stringResource(
                        R.string.debug_logs_mail_body,
                        "${Build.MANUFACTURER} ${Build.MODEL}",
                        Build.VERSION.RELEASE,
                        Build.VERSION.SDK_INT
                    )
                    val noMailAppMsg = stringResource(R.string.debug_send_no_mail_app)

                    SettingsButton(
                        text = stringResource(R.string.debug_send_to_developer),
                        onClick = {
                            scope.launch {
                                viewModel.showSnackbar(exportPreparingMsg)
                                val logFiles = viewModel.prepareLogsForSharing()
                                if (logFiles.isEmpty()) {
                                    viewModel.showSnackbar(exportEmptyMsg)
                                    return@launch
                                }
                                // Bewusst ohne pendingDisableDialog — wer gerade einen Fehler
                                // meldet, soll das Logging anlassen.
                                val share = buildLogShareIntent(
                                    context, logFiles, mailSubject, mailBody, MAINTAINER_EMAIL
                                )
                                val targets = resolveMailTargets(context, share)
                                    .map { Intent(share).setPackage(it) }
                                if (targets.isEmpty()) {
                                    Logger.w(TAG, "No mail app available for log hand-off")
                                    viewModel.showSnackbar(noMailAppMsg)
                                    return@launch
                                }
                                // Chooser über das erste Ziel, der Rest als initial intents —
                                // so bleibt die Liste auf Mail-Apps beschränkt.
                                val picker = Intent.createChooser(targets.first(), logsShareVia)
                                if (targets.size > 1) {
                                    picker.putExtra(
                                        Intent.EXTRA_INITIAL_INTENTS,
                                        targets.drop(1).toTypedArray()
                                    )
                                }
                                try {
                                    context.startActivity(picker)
                                } catch (e: ActivityNotFoundException) {
                                    Logger.w(TAG, "Mail app vanished before hand-off: ${e.message}")
                                    viewModel.showSnackbar(noMailAppMsg)
                                }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Clear Logs Button
                SettingsDangerButton(
                    text = stringResource(R.string.debug_delete_logs),
                    onClick = { showClearLogsDialog = true },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // v1.8.0: Test Mode Section
            SettingsSectionCard(title = stringResource(R.string.debug_test_section)) {
                SettingsHint(text = stringResource(R.string.debug_reset_changelog_desc))

                val changelogResetToast = stringResource(R.string.debug_changelog_reset)

                SettingsButton(
                    text = stringResource(R.string.debug_reset_changelog),
                    onClick = {
                        viewModel.resetChangelogVersion()
                        viewModel.showSnackbar(changelogResetToast)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            SettingsSectionCard(title = stringResource(R.string.debug_sync_section)) {
                SettingsHint(text = stringResource(R.string.debug_clear_etag_cache_subtitle))

                SettingsButton(
                    text = stringResource(R.string.debug_clear_etag_cache_title),
                    onClick = { showClearETagCacheDialog = true },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            SettingsSectionCard(title = stringResource(R.string.debug_calendar_experiment_section)) {
                SettingsButton(
                    text = stringResource(R.string.calendar_experiment_open),
                    onClick = { onNavigate(SettingsRoute.CalendarParsingExperiment) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

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

    if (showClearETagCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearETagCacheDialog = false },
            title = { Text(stringResource(R.string.debug_clear_etag_cache_dialog_title)) },
            text = { Text(stringResource(R.string.debug_clear_etag_cache_dialog_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearETagCacheDialog = false
                        viewModel.clearETagCache()
                    }
                ) {
                    Text(stringResource(R.string.debug_clear_etag_cache_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearETagCacheDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
