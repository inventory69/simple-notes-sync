package dev.dettmer.simplenotes.ui.settings.screens

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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
@Composable
fun SettingsMainScreen(
    viewModel: SettingsViewModel,
    onNavigate: (SettingsRoute) -> Unit,
    onBack: () -> Unit
) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val serverStatus by viewModel.serverStatus.collectAsState()
    val autoSyncEnabled by viewModel.autoSyncEnabled.collectAsState()
    val syncInterval by viewModel.syncInterval.collectAsState()
    val markdownAutoSync by viewModel.markdownAutoSync.collectAsState()
    val fileLoggingEnabled by viewModel.fileLoggingEnabled.collectAsState()
    
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
                    onClick = { onNavigate(SettingsRoute.Language) }
                )
            }
            
            // Server-Einstellungen
            item {
                // v1.5.0 Fix: Nur Prefix-URLs gelten als "nicht konfiguriert"
                val isConfigured = serverUrl.isNotEmpty() && 
                    serverUrl != "http://" && 
                    serverUrl != "https://"
                
                SettingsCard(
                    icon = Icons.Default.Cloud,
                    title = stringResource(R.string.settings_server),
                    subtitle = if (isConfigured) serverUrl else null,
                    statusText = when (serverStatus) {
                        is SettingsViewModel.ServerStatus.Reachable -> stringResource(R.string.settings_server_status_reachable)
                        is SettingsViewModel.ServerStatus.Unreachable -> stringResource(R.string.settings_server_status_unreachable)
                        is SettingsViewModel.ServerStatus.Checking -> stringResource(R.string.settings_server_status_checking)
                        is SettingsViewModel.ServerStatus.NotConfigured -> stringResource(R.string.settings_server_status_not_configured)
                        else -> null
                    },
                    statusColor = when (serverStatus) {
                        is SettingsViewModel.ServerStatus.Reachable -> Color(0xFF4CAF50)
                        is SettingsViewModel.ServerStatus.Unreachable -> Color(0xFFF44336)
                        is SettingsViewModel.ServerStatus.NotConfigured -> Color(0xFFFF9800)
                        else -> Color.Gray
                    },
                    onClick = { onNavigate(SettingsRoute.Server) }
                )
            }
            
            // Sync-Einstellungen
            item {
                val intervalText = when (syncInterval) {
                    15L -> stringResource(R.string.settings_interval_15min)
                    60L -> stringResource(R.string.settings_interval_60min)
                    else -> stringResource(R.string.settings_interval_30min)
                }
                SettingsCard(
                    icon = Icons.Default.Sync,
                    title = stringResource(R.string.settings_sync),
                    subtitle = if (autoSyncEnabled) {
                        stringResource(R.string.settings_sync_auto_on, intervalText)
                    } else {
                        stringResource(R.string.settings_sync_auto_off)
                    },
                    onClick = { onNavigate(SettingsRoute.Sync) }
                )
            }
            
            // Markdown-Integration
            item {
                SettingsCard(
                    icon = Icons.Default.Description,
                    title = stringResource(R.string.settings_markdown),
                    subtitle = if (markdownAutoSync) {
                        stringResource(R.string.settings_markdown_auto_on)
                    } else {
                        stringResource(R.string.settings_markdown_auto_off)
                    },
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
            
            // Über diese App
            item {
                SettingsCard(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.settings_about),
                    subtitle = stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    onClick = { onNavigate(SettingsRoute.About) }
                )
            }
            
            // Debug & Diagnose
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
