package dev.dettmer.simplenotes.ui.settings.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.settings.components.RadioOption
import dev.dettmer.simplenotes.ui.settings.components.SettingsHint
import dev.dettmer.simplenotes.ui.settings.components.SettingsRadioGroup
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold
import dev.dettmer.simplenotes.ui.settings.components.SettingsSectionCard

/**
 * Language selection settings screen
 * v1.5.0: Internationalization feature
 *
 * Uses Android's Per-App Language API (Android 13+) with AppCompat fallback
 */
@Composable
fun LanguageSettingsScreen(onBack: () -> Unit) {
    // Get current app locale - fresh value each time (no remember, always reads current state)
    val currentLocale = AppCompatDelegate.getApplicationLocales()
    val currentLanguageCode = if (currentLocale.isEmpty) {
        "" // System default
    } else {
        currentLocale.get(0)?.language.orEmpty()
    }

    var selectedLanguage by remember(currentLanguageCode) { mutableStateOf(currentLanguageCode) }

    // Language options. Fallback-Picker fuer Android 12 und aelter - ab Android 13
    // oeffnet SettingsMainScreen stattdessen den System-Picker aus locales_config.xml.
    // Diese Liste muss synchron zu locales_config.xml gehalten werden.
    val languageOptions = listOf(
        RadioOption(
            value = "",
            title = stringResource(R.string.language_system_default),
            subtitle = null
        ),
        RadioOption(
            value = "en",
            title = stringResource(R.string.language_english),
            subtitle = "English"
        ),
        RadioOption(
            value = "de",
            title = stringResource(R.string.language_german),
            subtitle = "German"
        ),
        RadioOption(
            value = "es",
            title = stringResource(R.string.language_spanish),
            subtitle = "Spanish"
        ),
        RadioOption(
            value = "fr",
            title = stringResource(R.string.language_french),
            subtitle = "French"
        ),
        RadioOption(
            value = "in",
            title = stringResource(R.string.language_indonesian),
            subtitle = "Indonesian"
        ),
        RadioOption(
            value = "it",
            title = stringResource(R.string.language_italian),
            subtitle = "Italian"
        ),
        RadioOption(
            value = "nb",
            title = stringResource(R.string.language_norwegian),
            subtitle = "Norwegian Bokmål"
        ),
        RadioOption(
            value = "ru",
            title = stringResource(R.string.language_russian),
            subtitle = "Russian"
        ),
        RadioOption(
            value = "tr",
            title = stringResource(R.string.language_turkish),
            subtitle = "Turkish"
        ),
        RadioOption(
            value = "uk",
            title = stringResource(R.string.language_ukrainian),
            subtitle = "Ukrainian"
        ),
        RadioOption(
            value = "zh",
            title = stringResource(R.string.language_chinese_simplified),
            subtitle = "Chinese (Simplified)"
        )
    )

    SettingsScaffold(
        title = stringResource(R.string.language_settings_title),
        onBack = onBack
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SettingsSectionCard(title = stringResource(R.string.settings_language)) {
                SettingsRadioGroup(
                    options = languageOptions,
                    selectedValue = selectedLanguage,
                    onValueSelected = { newLanguage ->
                        if (newLanguage != selectedLanguage) {
                            selectedLanguage = newLanguage
                            setAppLanguage(newLanguage)
                        }
                    }
                )

                SettingsHint(text = stringResource(R.string.language_info))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Set app language using AppCompatDelegate
 * v1.8.0: Smooth language change without activity recreate
 *
 * ComposeSettingsActivity handles locale changes via android:configChanges="locale"
 * in AndroidManifest.xml, preventing full activity recreate and eliminating flicker.
 * Compose automatically recomposes when the configuration changes.
 */
private fun setAppLanguage(languageCode: String) {
    val localeList = if (languageCode.isEmpty()) {
        LocaleListCompat.getEmptyLocaleList()
    } else {
        LocaleListCompat.forLanguageTags(languageCode)
    }

    // Sets the app locale - triggers onConfigurationChanged() instead of recreate()
    AppCompatDelegate.setApplicationLocales(localeList)
}
