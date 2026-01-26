package dev.dettmer.simplenotes.ui.settings.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.settings.SettingsViewModel
import dev.dettmer.simplenotes.ui.settings.components.RadioOption
import dev.dettmer.simplenotes.ui.settings.components.SettingsInfoCard
import dev.dettmer.simplenotes.ui.settings.components.SettingsRadioGroup
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold
import dev.dettmer.simplenotes.ui.settings.components.SettingsSectionHeader

/**
 * 🎨 v1.7.0: Display Settings Screen
 * 
 * Allows switching between List and Grid view modes.
 */
@Composable
fun DisplaySettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val displayMode by viewModel.displayMode.collectAsState()
    
    SettingsScaffold(
        title = stringResource(R.string.display_settings_title),
        onBack = onBack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            SettingsSectionHeader(text = stringResource(R.string.display_mode_title))
            
            SettingsRadioGroup(
                options = listOf(
                    RadioOption(
                        value = "list",
                        title = stringResource(R.string.display_mode_list),
                        subtitle = null
                    ),
                    RadioOption(
                        value = "grid",
                        title = stringResource(R.string.display_mode_grid),
                        subtitle = null
                    )
                ),
                selectedValue = displayMode,
                onValueSelected = { viewModel.setDisplayMode(it) }
            )
            
            SettingsInfoCard(
                text = stringResource(R.string.display_mode_info)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
