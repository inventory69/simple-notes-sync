package dev.dettmer.simplenotes.ui.settings.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhonelinkRing
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.settings.SettingsViewModel
import dev.dettmer.simplenotes.ui.settings.components.RadioOption
import dev.dettmer.simplenotes.ui.settings.components.SettingsDivider
import dev.dettmer.simplenotes.ui.settings.components.SettingsInfoCard
import dev.dettmer.simplenotes.ui.settings.components.SettingsRadioGroup
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold
import dev.dettmer.simplenotes.ui.settings.components.SettingsSectionHeader
import dev.dettmer.simplenotes.ui.settings.components.SettingsSwitch

/**
 * Sync settings screen — Restructured for v1.8.0
 * 
 * Two clear sections:
 * 1. Sync Triggers (all 5 triggers grouped logically)
 * 2. Network & Performance (WiFi-only + Parallel Downloads)
 */
@Composable
fun SyncSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToServerSettings: () -> Unit
) {
    // Collect all trigger states
    val triggerOnSave by viewModel.triggerOnSave.collectAsState()
    val triggerOnResume by viewModel.triggerOnResume.collectAsState()
    val triggerWifiConnect by viewModel.triggerWifiConnect.collectAsState()
    val triggerPeriodic by viewModel.triggerPeriodic.collectAsState()
    val triggerBoot by viewModel.triggerBoot.collectAsState()
    val syncInterval by viewModel.syncInterval.collectAsState()

    val maxParallelConnections by viewModel.maxParallelConnections.collectAsState()
    val wifiOnlySync by viewModel.wifiOnlySync.collectAsState()
    
    val isServerConfigured = viewModel.isServerConfigured()
    
    SettingsScaffold(
        title = stringResource(R.string.sync_settings_title),
        onBack = onBack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // ── Offline Mode Warning ──
            if (!isServerConfigured) {
                SettingsInfoCard(
                    text = stringResource(R.string.sync_offline_mode_message),
                    isWarning = true
                )
                
                Button(
                    onClick = onNavigateToServerSettings,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(stringResource(R.string.sync_offline_mode_button))
                }
                
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // ═══════════════════════════════════════════════════════════════
            // SECTION 1: SYNC TRIGGERS
            // ═══════════════════════════════════════════════════════════════
            
            SettingsSectionHeader(text = stringResource(R.string.sync_section_triggers))
            
            // ── Sofort-Sync ──
            SettingsSectionHeader(text = stringResource(R.string.sync_section_instant))
            
            SettingsSwitch(
                title = stringResource(R.string.sync_trigger_on_save_title),
                subtitle = stringResource(R.string.sync_trigger_on_save_subtitle),
                checked = triggerOnSave,
                onCheckedChange = { viewModel.setTriggerOnSave(it) },
                icon = Icons.Default.Save,
                enabled = isServerConfigured
            )
            
            SettingsSwitch(
                title = stringResource(R.string.sync_trigger_on_resume_title),
                subtitle = stringResource(R.string.sync_trigger_on_resume_subtitle),
                checked = triggerOnResume,
                onCheckedChange = { viewModel.setTriggerOnResume(it) },
                icon = Icons.Default.PhonelinkRing,
                enabled = isServerConfigured
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // ── Hintergrund-Sync ──
            SettingsSectionHeader(text = stringResource(R.string.sync_section_background))
            
            SettingsSwitch(
                title = stringResource(R.string.sync_trigger_wifi_connect_title),
                subtitle = stringResource(R.string.sync_trigger_wifi_connect_subtitle),
                checked = triggerWifiConnect,
                onCheckedChange = { viewModel.setTriggerWifiConnect(it) },
                icon = Icons.Default.Wifi,
                enabled = isServerConfigured
            )
            
            SettingsSwitch(
                title = stringResource(R.string.sync_trigger_periodic_title),
                subtitle = stringResource(R.string.sync_trigger_periodic_subtitle),
                checked = triggerPeriodic,
                onCheckedChange = { viewModel.setTriggerPeriodic(it) },
                icon = Icons.Default.Schedule,
                enabled = isServerConfigured
            )
            
            // Interval-Auswahl (nur sichtbar wenn Periodic aktiv)
            if (triggerPeriodic && isServerConfigured) {
                Spacer(modifier = Modifier.height(8.dp))
                
                val intervalOptions = listOf(
                    RadioOption(
                        value = 15L,
                        title = stringResource(R.string.sync_interval_15min_title),
                        subtitle = null
                    ),
                    RadioOption(
                        value = 30L,
                        title = stringResource(R.string.sync_interval_30min_title),
                        subtitle = null
                    ),
                    RadioOption(
                        value = 60L,
                        title = stringResource(R.string.sync_interval_60min_title),
                        subtitle = null
                    )
                )
                
                SettingsRadioGroup(
                    options = intervalOptions,
                    selectedValue = syncInterval,
                    onValueSelected = { viewModel.setSyncInterval(it) }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            SettingsSwitch(
                title = stringResource(R.string.sync_trigger_boot_title),
                subtitle = stringResource(R.string.sync_trigger_boot_subtitle),
                checked = triggerBoot,
                onCheckedChange = { viewModel.setTriggerBoot(it) },
                icon = Icons.Default.SettingsInputAntenna,
                enabled = isServerConfigured
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // ── Info Card ──
            val manualHintText = if (isServerConfigured) {
                stringResource(R.string.sync_manual_hint)
            } else {
                stringResource(R.string.sync_manual_hint_disabled)
            }
            
            SettingsInfoCard(
                text = manualHintText
            )
            
            SettingsDivider()
            
            // ═══════════════════════════════════════════════════════════════
            // SECTION 2: NETZWERK & PERFORMANCE
            // ═══════════════════════════════════════════════════════════════
            
            SettingsSectionHeader(text = stringResource(R.string.sync_section_network_performance))
            
            // WiFi-Only Toggle
            SettingsSwitch(
                title = stringResource(R.string.sync_wifi_only_title),
                subtitle = stringResource(R.string.sync_wifi_only_subtitle),
                checked = wifiOnlySync,
                onCheckedChange = { viewModel.setWifiOnlySync(it) },
                icon = Icons.Default.Wifi,
                enabled = isServerConfigured
            )
            
            if (wifiOnlySync && isServerConfigured) {
                SettingsInfoCard(
                    text = stringResource(R.string.sync_wifi_only_hint)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            // 🔧 v1.9.0: Unified parallel connections (downloads + uploads)
            val parallelOptions = listOf(
                RadioOption(
                    value = 1,
                    title = "1 ${stringResource(R.string.sync_parallel_connections_unit)}",
                    subtitle = stringResource(R.string.sync_parallel_connections_desc_1)
                ),
                RadioOption(
                    value = 3,
                    title = "3 ${stringResource(R.string.sync_parallel_connections_unit)}",
                    subtitle = stringResource(R.string.sync_parallel_connections_desc_3)
                ),
                RadioOption(
                    value = 5,
                    title = "5 ${stringResource(R.string.sync_parallel_connections_unit)}",
                    subtitle = stringResource(R.string.sync_parallel_connections_desc_5)
                )
            )

            SettingsRadioGroup(
                title = stringResource(R.string.sync_parallel_connections_title),
                options = parallelOptions,
                selectedValue = maxParallelConnections,
                onValueSelected = { viewModel.setMaxParallelConnections(it) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
