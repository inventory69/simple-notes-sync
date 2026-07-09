package dev.dettmer.simplenotes.ui.settings.screens

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.editor.Preview
import dev.dettmer.simplenotes.ui.editor.Strategy
import dev.dettmer.simplenotes.ui.editor.calendarParsingStrategy
import dev.dettmer.simplenotes.ui.editor.computePreview
import dev.dettmer.simplenotes.ui.editor.setCalendarParsingStrategy
import dev.dettmer.simplenotes.ui.settings.components.RadioOption
import dev.dettmer.simplenotes.ui.settings.components.SettingsDivider
import dev.dettmer.simplenotes.ui.settings.components.SettingsInfoCard
import dev.dettmer.simplenotes.ui.settings.components.SettingsRadioGroup
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold
import dev.dettmer.simplenotes.ui.settings.components.SettingsSectionHeader
import dev.dettmer.simplenotes.utils.Constants

@Composable
fun CalendarParsingExperimentScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE) }
    val exampleText = stringResource(R.string.calendar_experiment_input_placeholder)
    var inputText by remember { mutableStateOf(exampleText) }
    var selectedStrategy by remember { mutableStateOf(prefs.calendarParsingStrategy()) }
    val preview = remember(inputText, selectedStrategy) { computePreview(inputText, selectedStrategy) }

    SettingsScaffold(
        title = stringResource(R.string.calendar_experiment_title),
        onBack = onBack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SettingsInfoCard(text = stringResource(R.string.calendar_experiment_hint))

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text(stringResource(R.string.calendar_experiment_input_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            SettingsDivider()

            SettingsRadioGroup(
                title = stringResource(R.string.calendar_experiment_strategy_section),
                options = listOf(
                    RadioOption(
                        Strategy.RAW,
                        stringResource(R.string.calendar_experiment_strategy_raw),
                        stringResource(R.string.calendar_experiment_strategy_raw_subtitle)
                    ),
                    RadioOption(
                        Strategy.POSITIONAL,
                        stringResource(R.string.calendar_experiment_strategy_positional),
                        stringResource(R.string.calendar_experiment_strategy_positional_subtitle)
                    ),
                    RadioOption(
                        Strategy.PHONE_REGEX,
                        stringResource(R.string.calendar_experiment_strategy_phone_regex),
                        stringResource(R.string.calendar_experiment_strategy_phone_regex_subtitle)
                    ),
                    RadioOption(
                        Strategy.LABEL_PREFIX,
                        stringResource(R.string.calendar_experiment_strategy_label_prefix),
                        stringResource(R.string.calendar_experiment_strategy_label_prefix_subtitle)
                    ),
                    RadioOption(
                        Strategy.PHONE_EMAIL_REGEX,
                        stringResource(R.string.calendar_experiment_strategy_phone_email_regex),
                        stringResource(R.string.calendar_experiment_strategy_phone_email_regex_subtitle)
                    )
                ),
                selectedValue = selectedStrategy,
                onValueSelected = {
                    selectedStrategy = it
                    prefs.setCalendarParsingStrategy(it)
                }
            )

            SettingsDivider()

            SettingsSectionHeader(text = stringResource(R.string.calendar_experiment_preview_section))
            PreviewCard(preview)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PreviewCard(preview: Preview) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            PreviewRow(stringResource(R.string.calendar_experiment_preview_title), preview.title, TAG_PREVIEW_TITLE)
            Spacer(modifier = Modifier.height(8.dp))
            PreviewRow(stringResource(R.string.calendar_experiment_preview_location), preview.location, TAG_PREVIEW_LOCATION)
            Spacer(modifier = Modifier.height(8.dp))
            PreviewRow(
                stringResource(R.string.calendar_experiment_preview_description),
                preview.description,
                TAG_PREVIEW_DESCRIPTION
            )
            Spacer(modifier = Modifier.height(8.dp))
            PreviewRow(
                stringResource(R.string.calendar_experiment_preview_attendees),
                preview.attendees,
                TAG_PREVIEW_ATTENDEES
            )
        }
    }
}

// Test tags (read by CalendarParsingExperimentScreenInstrumentedTest) — the raw input text and the
// parsed preview otherwise share substrings, so text-based matchers alone can't tell them apart.
internal const val TAG_PREVIEW_TITLE = "calendar_experiment_preview_title"
internal const val TAG_PREVIEW_LOCATION = "calendar_experiment_preview_location"
internal const val TAG_PREVIEW_DESCRIPTION = "calendar_experiment_preview_description"
internal const val TAG_PREVIEW_ATTENDEES = "calendar_experiment_preview_attendees"

@Composable
private fun PreviewRow(label: String, value: String, testTag: String) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag(testTag))
}
