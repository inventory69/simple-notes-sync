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
 * Sync settings screen - Configurable Sync Triggers
 * v1.5.0: Jetpack Compose Settings Redesign
 * v1.6.0: Individual toggle for each sync trigger (onSave, onResume, WiFi-Connect, Periodic, Boot)
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
    
    // Check if server is configured
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
            
            // 🌟 v1.6.0: Offline Mode Warning if server not configured
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
            // SOFORT-SYNC Section
            // ═══════════════════════════════════════════════════════════════
            
            SettingsSectionHeader(text = stringResource(R.string.sync_section_instant))
            
            // onSave Trigger
            SettingsSwitch(
                title = stringResource(R.string.sync_trigger_on_save_title),
                subtitle = stringResource(R.string.sync_trigger_on_save_subtitle),
                checked = triggerOnSave,
                onCheckedChange = { viewModel.setTriggerOnSave(it) },
                icon = Icons.Default.Save,
                enabled = isServerConfigured
            )
            
            // onResume Trigger
            SettingsSwitch(
                title = stringResource(R.string.sync_trigger_on_resume_title),
                subtitle = stringResource(R.string.sync_trigger_on_resume_subtitle),
                checked = triggerOnResume,
                onCheckedChange = { viewModel.setTriggerOnResume(it) },
                icon = Icons.Default.PhonelinkRing,
                enabled = isServerConfigured
            )
            
            SettingsDivider()
            
            // ═══════════════════════════════════════════════════════════════
            // HINTERGRUND-SYNC Section
            // ═══════════════════════════════════════════════════════════════
            
            SettingsSectionHeader(text = stringResource(R.string.sync_section_background))
            
            // WiFi-Connect Trigger
            SettingsSwitch(
                title = stringResource(R.string.sync_trigger_wifi_connect_title),
                subtitle = stringResource(R.string.sync_trigger_wifi_connect_subtitle),
                checked = triggerWifiConnect,
                onCheckedChange = { viewModel.setTriggerWifiConnect(it) },
                icon = Icons.Default.Wifi,
                enabled = isServerConfigured
            )
            
            // Periodic Trigger
            SettingsSwitch(
                title = stringResource(R.string.sync_trigger_periodic_title),
                subtitle = stringResource(R.string.sync_trigger_periodic_subtitle),
                checked = triggerPeriodic,
                onCheckedChange = { viewModel.setTriggerPeriodic(it) },
                icon = Icons.Default.Schedule,
                enabled = isServerConfigured
            )
            
            // Periodic Interval Selection (only visible if periodic trigger is enabled)
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
            
            SettingsDivider()
            
            // ═══════════════════════════════════════════════════════════════
            // ADVANCED Section (Boot Sync)
            // ═══════════════════════════════════════════════════════════════
            
            SettingsSectionHeader(text = stringResource(R.string.sync_section_advanced))
            
            // Boot Trigger
            SettingsSwitch(
                title = stringResource(R.string.sync_trigger_boot_title),
                subtitle = stringResource(R.string.sync_trigger_boot_subtitle),
                checked = triggerBoot,
                onCheckedChange = { viewModel.setTriggerBoot(it) },
                icon = Icons.Default.SettingsInputAntenna,
                enabled = isServerConfigured
            )
            
            SettingsDivider()
            
            // Manual Sync Info
            val manualHintText = if (isServerConfigured) {
                stringResource(R.string.sync_manual_hint)
            } else {
                stringResource(R.string.sync_manual_hint_disabled)
            }
            
            SettingsInfoCard(
                text = manualHintText
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
