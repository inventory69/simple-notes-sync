package dev.dettmer.simplenotes.ui.settings.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.dettmer.simplenotes.BuildConfig
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.settings.SettingsRoute
import dev.dettmer.simplenotes.ui.settings.SettingsViewModel
import dev.dettmer.simplenotes.ui.settings.components.SettingsCard
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold

/**
 * Main Settings overview screen with clickable group cards
 * v1.5.0: Jetpack Compose Settings Redesign
 */
@Suppress("MagicNumber") // Color hex values
@Composable
fun SettingsMainScreen(
    viewModel: SettingsViewModel,
    onNavigate: (SettingsRoute) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val serverUrl by viewModel.serverUrl.collectAsState()
    val serverStatus by viewModel.serverStatus.collectAsState()
    val autoSyncEnabled by viewModel.autoSyncEnabled.collectAsState()
    val syncInterval by viewModel.syncInterval.collectAsState()
    val markdownAutoSync by viewModel.markdownAutoSync.collectAsState()
    val fileLoggingEnabled by viewModel.fileLoggingEnabled.collectAsState()
    val developerOptionsUnlocked by viewModel.developerOptionsUnlocked.collectAsState() // 🔧 v1.11.0

    // 🌟 v1.6.0: Collect offline mode and trigger states
    val offlineMode by viewModel.offlineMode.collectAsState()
    val triggerOnSave by viewModel.triggerOnSave.collectAsState()
    val triggerOnResume by viewModel.triggerOnResume.collectAsState()
    val triggerWifiConnect by viewModel.triggerWifiConnect.collectAsState()
    val triggerPeriodic by viewModel.triggerPeriodic.collectAsState()
    val triggerBoot by viewModel.triggerBoot.collectAsState()

    // 🆕 v1.12.0: Notification state for settings overview summary
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val notificationsErrorsOnly by viewModel.notificationsErrorsOnly.collectAsState()

    // Check server status on first load
    LaunchedEffect(Unit) {
        viewModel.checkServerStatus()
    }

    // Get current language for display (no remember - always fresh value after activity recreate)
    val locales = AppCompatDelegate.getApplicationLocales()
    val currentLanguageName = if (locales.isEmpty) {
        null // System default
    } else {
        locales[0]?.displayLanguage?.replaceFirstChar { it.uppercase() }
    }
    val systemDefaultText = stringResource(R.string.language_system_default)
    val languageSubtitle = currentLanguageName ?: systemDefaultText

    SettingsScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Language Settings
            item {
                SettingsCard(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.settings_language),
                    subtitle = languageSubtitle,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                                        data = "package:${context.packageName}".toUri()
                                    }
                                )
                            } catch (_: ActivityNotFoundException) {
                                onNavigate(SettingsRoute.Language)
                            }
                        } else {
                            onNavigate(SettingsRoute.Language)
                        }
                    }
                )
            }

            // 🎨 v1.7.0: Display Settings
            item {
                val displayMode by viewModel.displayMode.collectAsState()
                val autosaveEnabled by viewModel.autosaveEnabled.collectAsState()
                val displayModeLabel = when (displayMode) {
                    "grid" -> stringResource(R.string.display_mode_grid)
                    else -> stringResource(R.string.display_mode_list)
                }
                val autosaveLabel = if (autosaveEnabled) {
                    stringResource(R.string.settings_subtitle_autosave_on)
                } else {
                    stringResource(R.string.settings_subtitle_autosave_off)
                }
                val displaySubtitle = "$displayModeLabel · $autosaveLabel"

                SettingsCard(
                    icon = Icons.Default.GridView,
                    title = stringResource(R.string.display_settings_title),
                    subtitle = displaySubtitle,
                    onClick = { onNavigate(SettingsRoute.Display) }
                )
            }

            // Server-Einstellungen
            item {
                // 🌟 v1.6.0: Check if server is configured (host is not empty)
                val isConfigured = serverUrl.isNotEmpty()

                SettingsCard(
                    icon = Icons.Default.Cloud,
                    title = stringResource(R.string.settings_server),
                    subtitle = if (!offlineMode && isConfigured) serverUrl else null,
                    statusText = when {
                        offlineMode ->
                            stringResource(R.string.settings_server_status_offline_mode)
                        serverStatus is SettingsViewModel.ServerStatus.OfflineMode ->
                            stringResource(R.string.settings_server_status_offline_mode)
                        serverStatus is SettingsViewModel.ServerStatus.Reachable ->
                            stringResource(R.string.settings_server_status_reachable)
                        serverStatus is SettingsViewModel.ServerStatus.Unreachable ->
                            stringResource(R.string.settings_server_status_unreachable)
                        serverStatus is SettingsViewModel.ServerStatus.Checking ->
                            stringResource(R.string.settings_server_status_checking)
                        serverStatus is SettingsViewModel.ServerStatus.NotConfigured ->
                            stringResource(R.string.settings_server_status_offline_mode)
                        else -> null
                    },
                    statusColor = when {
                        offlineMode -> MaterialTheme.colorScheme.tertiary
                        serverStatus is SettingsViewModel.ServerStatus.OfflineMode ->
                            MaterialTheme.colorScheme.tertiary
                        serverStatus is SettingsViewModel.ServerStatus.Reachable ->
                            ServerReachableColor
                        serverStatus is SettingsViewModel.ServerStatus.Unreachable ->
                            ServerUnreachableColor
                        serverStatus is SettingsViewModel.ServerStatus.NotConfigured ->
                            MaterialTheme.colorScheme.tertiary
                        else -> Color.Gray
                    },
                    onClick = { onNavigate(SettingsRoute.Server) }
                )
            }

            // Sync-Einstellungen
            item {
                // 🌟 v1.6.0: Build dynamic subtitle based on active triggers
                val isServerConfigured = viewModel.isServerConfigured()
                val activeTriggersCount = listOf(
                    triggerOnSave,
                    triggerOnResume,
                    triggerWifiConnect,
                    triggerPeriodic,
                    triggerBoot
                ).count { it }

                // 🆕 v1.12.0: Notification status text
                val notificationStatus = when {
                    !notificationsEnabled -> stringResource(R.string.settings_sync_notifications_disabled)
                    notificationsErrorsOnly -> stringResource(R.string.settings_sync_notifications_errors_only)
                    else -> stringResource(R.string.settings_sync_notifications_enabled)
                }

                // 🌟 v1.6.0 Fix: Use statusText for offline mode (consistent with Server card)
                // 🆕 v1.12.0: Append notification status to subtitle
                val syncSubtitle = if (isServerConfigured) {
                    val triggerText = if (activeTriggersCount == 0) {
                        stringResource(R.string.settings_sync_manual_only)
                    } else {
                        stringResource(R.string.settings_sync_triggers_active, activeTriggersCount)
                    }
                    "$triggerText · $notificationStatus"
                } else {
                    null
                }

                SettingsCard(
                    icon = Icons.Default.Sync,
                    title = stringResource(R.string.settings_sync),
                    subtitle = syncSubtitle,
                    statusText = if (!isServerConfigured) stringResource(R.string.settings_sync_offline_mode) else null,
                    statusColor = if (!isServerConfigured) MaterialTheme.colorScheme.tertiary else Color.Gray,
                    onClick = { onNavigate(SettingsRoute.Sync) }
                )
            }

            // Markdown-Integration
            item {
                // 🌟 v1.6.0 Fix: Use statusText for offline mode (consistent with Server card)
                val isServerConfiguredForMarkdown = viewModel.isServerConfigured()

                SettingsCard(
                    icon = Icons.Default.Description,
                    title = stringResource(R.string.settings_markdown),
                    subtitle = if (isServerConfiguredForMarkdown) {
                        if (markdownAutoSync) {
                            stringResource(R.string.settings_markdown_auto_on)
                        } else {
                            stringResource(R.string.settings_markdown_auto_off)
                        }
                    } else {
                        null
                    },
                    statusText = if (!isServerConfiguredForMarkdown) {
                        stringResource(
                            R.string.settings_sync_offline_mode
                        )
                    } else {
                        null
                    },
                    statusColor = if (!isServerConfiguredForMarkdown) MaterialTheme.colorScheme.tertiary else Color.Gray,
                    onClick = { onNavigate(SettingsRoute.Markdown) }
                )
            }

            // Backup & Wiederherstellung
            item {
                SettingsCard(
                    icon = Icons.Default.Backup,
                    title = stringResource(R.string.settings_backup),
                    subtitle = stringResource(R.string.settings_backup_subtitle),
                    onClick = { onNavigate(SettingsRoute.Backup) }
                )
            }

            // 🆕 Issue #21: Notizen importieren
            item {
                SettingsCard(
                    icon = Icons.Default.FileOpen,
                    title = stringResource(R.string.settings_import_title),
                    subtitle = stringResource(R.string.settings_import_subtitle),
                    onClick = { onNavigate(SettingsRoute.Import) }
                )
            }

            // Über diese App
            item {
                SettingsCard(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.settings_about),
                    subtitle = stringResource(
                        R.string.about_version,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE
                    ),
                    onClick = { onNavigate(SettingsRoute.About) }
                )
            }

            // 🔧 v1.11.0: Debug & Diagnose — nur sichtbar nach Easter-Egg-Freischaltung
            if (developerOptionsUnlocked) {
                item {
                    SettingsCard(
                        icon = Icons.Default.BugReport,
                        title = stringResource(R.string.settings_debug),
                        subtitle = if (fileLoggingEnabled) {
                            stringResource(R.string.settings_debug_logging_on)
                        } else {
                            stringResource(R.string.settings_debug_logging_off)
                        },
                        onClick = { onNavigate(SettingsRoute.Debug) }
                    )
                }
            }
        }
    }
}
