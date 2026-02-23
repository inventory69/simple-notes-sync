package dev.dettmer.simplenotes.ui.settings.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import dev.dettmer.simplenotes.ui.settings.components.SettingsInfoCard
import dev.dettmer.simplenotes.ui.settings.components.SettingsRadioGroup
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold
import dev.dettmer.simplenotes.ui.settings.components.SettingsSectionHeader
import dev.dettmer.simplenotes.utils.Constants

/**
 * 🎨 v1.7.0: Display Settings Screen
 * 🆕 v1.9.0 (F05): Added Custom App Title section
 *
 * Allows switching between List and Grid view modes,
 * and setting a custom app title for the main screen.
 */
@Composable
fun DisplaySettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val displayMode by viewModel.displayMode.collectAsState()
    val customAppTitle by viewModel.customAppTitle.collectAsState()

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

            // ── Display Mode Section ──
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

            Spacer(modifier = Modifier.height(24.dp))

            // ── 🆕 v1.9.0 (F05): Custom App Title Section ──
            SettingsSectionHeader(text = stringResource(R.string.custom_app_title_section))

            OutlinedTextField(
                value = customAppTitle,
                onValueChange = { newValue ->
                    if (newValue.length <= Constants.MAX_CUSTOM_APP_TITLE_LENGTH) {
                        viewModel.setCustomAppTitle(newValue)
                    }
                },
                label = { Text(stringResource(R.string.custom_app_title_label)) },
                placeholder = { Text(stringResource(R.string.custom_app_title_placeholder)) },
                singleLine = true,
                supportingText = {
                    Text(
                        text = stringResource(
                            R.string.custom_app_title_char_count,
                            customAppTitle.length,
                            Constants.MAX_CUSTOM_APP_TITLE_LENGTH
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (customAppTitle.length >= Constants.MAX_CUSTOM_APP_TITLE_LENGTH) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            SettingsInfoCard(
                text = stringResource(R.string.custom_app_title_info)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
