package dev.dettmer.simplenotes.ui.settings.screens

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
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.BuildConfig
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
    
    SettingsScaffold(
        title = "Einstellungen",
        onBack = onBack
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // Server-Einstellungen
            item {
                // v1.5.0 Fix: Nur Prefix-URLs gelten als "nicht konfiguriert"
                val isConfigured = serverUrl.isNotEmpty() && 
                    serverUrl != "http://" && 
                    serverUrl != "https://"
                
                SettingsCard(
                    icon = Icons.Default.Cloud,
                    title = "Server-Einstellungen",
                    subtitle = if (isConfigured) serverUrl else null,
                    statusText = when (serverStatus) {
                        is SettingsViewModel.ServerStatus.Reachable -> "✅ Erreichbar"
                        is SettingsViewModel.ServerStatus.Unreachable -> "❌ Nicht erreichbar"
                        is SettingsViewModel.ServerStatus.Checking -> "🔍 Prüfe..."
                        is SettingsViewModel.ServerStatus.NotConfigured -> "⚠️ Nicht konfiguriert"
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
                    15L -> "15 Min"
                    60L -> "60 Min"
                    else -> "30 Min"
                }
                SettingsCard(
                    icon = Icons.Default.Sync,
                    title = "Sync-Einstellungen",
                    subtitle = if (autoSyncEnabled) "Auto-Sync: An • $intervalText" else "Auto-Sync: Aus",
                    onClick = { onNavigate(SettingsRoute.Sync) }
                )
            }
            
            // Markdown-Integration
            item {
                SettingsCard(
                    icon = Icons.Default.Description,
                    title = "Markdown Desktop-Integration",
                    subtitle = if (markdownAutoSync) "Auto-Sync: An" else "Auto-Sync: Aus",
                    onClick = { onNavigate(SettingsRoute.Markdown) }
                )
            }
            
            // Backup & Wiederherstellung
            item {
                SettingsCard(
                    icon = Icons.Default.Backup,
                    title = "Backup & Wiederherstellung",
                    subtitle = "Lokales oder Server-Backup",
                    onClick = { onNavigate(SettingsRoute.Backup) }
                )
            }
            
            // Über diese App
            item {
                SettingsCard(
                    icon = Icons.Default.Info,
                    title = "Über diese App",
                    subtitle = "Version ${BuildConfig.VERSION_NAME}",
                    onClick = { onNavigate(SettingsRoute.About) }
                )
            }
            
            // Debug & Diagnose
            item {
                SettingsCard(
                    icon = Icons.Default.BugReport,
                    title = "Debug & Diagnose",
                    subtitle = if (fileLoggingEnabled) "Logging: An" else "Logging: Aus",
                    onClick = { onNavigate(SettingsRoute.Debug) }
                )
            }
        }
    }
}
