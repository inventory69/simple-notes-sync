package dev.dettmer.simplenotes.ui.settings.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
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
 * Sync settings screen (Auto-Sync toggle and interval selection)
 * v1.5.0: Jetpack Compose Settings Redesign
 */
@Composable
fun SyncSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val autoSyncEnabled by viewModel.autoSyncEnabled.collectAsState()
    val syncInterval by viewModel.syncInterval.collectAsState()
    
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
            
            // Auto-Sync Info
            SettingsInfoCard(
                text = stringResource(R.string.sync_auto_sync_info)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Auto-Sync Toggle
            SettingsSwitch(
                title = stringResource(R.string.sync_auto_sync_enabled),
                checked = autoSyncEnabled,
                onCheckedChange = { viewModel.setAutoSync(it) },
                icon = Icons.Default.Sync
            )
            
            SettingsDivider()
            
            // Sync Interval Section
            SettingsSectionHeader(text = stringResource(R.string.sync_interval_section))
            
            SettingsInfoCard(
                text = stringResource(R.string.sync_interval_info)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Interval Radio Group
            val intervalOptions = listOf(
                RadioOption(
                    value = 15L,
                    title = stringResource(R.string.sync_interval_15min_title),
                    subtitle = stringResource(R.string.sync_interval_15min_subtitle)
                ),
                RadioOption(
                    value = 30L,
                    title = stringResource(R.string.sync_interval_30min_title),
                    subtitle = stringResource(R.string.sync_interval_30min_subtitle)
                ),
                RadioOption(
                    value = 60L,
                    title = stringResource(R.string.sync_interval_60min_title),
                    subtitle = stringResource(R.string.sync_interval_60min_subtitle)
                )
            )
            
            SettingsRadioGroup(
                options = intervalOptions,
                selectedValue = syncInterval,
                onValueSelected = { viewModel.setSyncInterval(it) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
