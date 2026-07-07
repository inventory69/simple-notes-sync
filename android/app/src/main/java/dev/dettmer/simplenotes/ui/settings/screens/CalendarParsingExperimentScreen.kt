package dev.dettmer.simplenotes.ui.settings.screens

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.settings.components.RadioOption
import dev.dettmer.simplenotes.ui.settings.components.SettingsDivider
import dev.dettmer.simplenotes.ui.settings.components.SettingsInfoCard
import dev.dettmer.simplenotes.ui.settings.components.SettingsRadioGroup
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold
import dev.dettmer.simplenotes.ui.settings.components.SettingsSectionHeader

// internal (not private): unit-tested directly from CalendarParsingExperimentScreenTest
internal enum class Strategy { RAW, POSITIONAL, PHONE_REGEX, LABEL_PREFIX, PHONE_EMAIL_REGEX }

internal data class Preview(val title: String, val location: String, val description: String, val attendees: String = "")

private val PHONE_REGEX = Regex("""\b\d[\d\s/-]{5,}\d\b""")
private val EMAIL_REGEX = Regex("""[\w.+-]+@[\w-]+\.[A-Za-z]{2,}""")

private fun segments(text: String) = text.split("/").map { it.trim() }.filter { it.isNotBlank() }

internal fun computePreview(text: String, strategy: Strategy): Preview = when (strategy) {
    Strategy.RAW -> Preview(title = text, location = "", description = "")

    Strategy.POSITIONAL -> {
        val parts = segments(text)
        Preview(
            title = parts.getOrElse(0) { "" },
            location = parts.getOrElse(1) { "" },
            description = parts.drop(2).joinToString("\n")
        )
    }

    Strategy.PHONE_REGEX -> {
        val phone = PHONE_REGEX.find(text)?.value?.trim()
        val cleaned = phone?.let { text.replace(it, "") } ?: text
        val parts = segments(cleaned)
        val descLines = parts.drop(2).let { if (phone != null) listOf("Tel: $phone") + it else it }
        Preview(
            title = parts.getOrElse(0) { "" },
            location = parts.getOrElse(1) { "" },
            description = descLines.joinToString("\n")
        )
    }

    Strategy.LABEL_PREFIX -> {
        var title = ""
        var location = ""
        var attendees = ""
        val description = mutableListOf<String>()
        segments(text).forEach { segment ->
            val prefix = segment.substringBefore(':', missingDelimiterValue = "").lowercase()
            val value = if (prefix.isNotEmpty()) segment.substringAfter(':').trim() else segment
            when (prefix) {
                "t", "tel", "telefon" -> description.add("Tel: $value")
                "a", "adresse", "str" -> location = value
                "n", "name", "titel" -> title = value
                "e", "email", "gast" -> attendees = value
                else -> description.add(segment)
            }
        }
        Preview(title = title, location = location, description = description.joinToString("\n"), attendees = attendees)
    }

    Strategy.PHONE_EMAIL_REGEX -> {
        val phone = PHONE_REGEX.find(text)?.value?.trim()
        val email = EMAIL_REGEX.find(text)?.value?.trim()
        var cleaned = text
        phone?.let { cleaned = cleaned.replace(it, "") }
        email?.let { cleaned = cleaned.replace(it, "") }
        val parts = segments(cleaned)
        val descLines = parts.drop(2).let { if (phone != null) listOf("Tel: $phone") + it else it }
        Preview(
            title = parts.getOrElse(0) { "" },
            location = parts.getOrElse(1) { "" },
            description = descLines.joinToString("\n"),
            attendees = email ?: ""
        )
    }
}

/**
 * Wegwerf-Experiment für Kalender-Parsing-Strategien, siehe DEVELOPMENT_CONSTRAINTS.
 * Wird durch die finale Lösung ersetzt, sobald eine Strategie ausgewählt ist.
 */
@Composable
fun CalendarParsingExperimentScreen(onBack: () -> Unit) {
    val exampleText = stringResource(R.string.calendar_experiment_input_placeholder)
    var inputText by remember { mutableStateOf(exampleText) }
    var selectedStrategy by remember { mutableStateOf(Strategy.RAW) }
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
                onValueSelected = { selectedStrategy = it }
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
