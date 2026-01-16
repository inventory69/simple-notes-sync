package dev.dettmer.simplenotes.ui.settings.screens

import android.app.Activity
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.settings.components.RadioOption
import dev.dettmer.simplenotes.ui.settings.components.SettingsInfoCard
import dev.dettmer.simplenotes.ui.settings.components.SettingsRadioGroup
import dev.dettmer.simplenotes.ui.settings.components.SettingsScaffold

/**
 * Language selection settings screen
 * v1.5.0: Internationalization feature
 * 
 * Uses Android's Per-App Language API (Android 13+) with AppCompat fallback
 */
@Composable
fun LanguageSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    // Get current app locale - fresh value each time (no remember, always reads current state)
    val currentLocale = AppCompatDelegate.getApplicationLocales()
    val currentLanguageCode = if (currentLocale.isEmpty) {
        "" // System default
    } else {
        currentLocale.get(0)?.language ?: ""
    }
    
    var selectedLanguage by remember(currentLanguageCode) { mutableStateOf(currentLanguageCode) }
    
    // Language options
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
            
            // Info card
            SettingsInfoCard(
                text = stringResource(R.string.language_info)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Language selection radio group
            SettingsRadioGroup(
                options = languageOptions,
                selectedValue = selectedLanguage,
                onValueSelected = { newLanguage ->
                    if (newLanguage != selectedLanguage) {
                        selectedLanguage = newLanguage
                        setAppLanguage(newLanguage, context as Activity)
                    }
                }
            )
        }
    }
}

/**
 * Set app language using AppCompatDelegate
 * Works on Android 13+ natively, falls back to AppCompat on older versions
 */
private fun setAppLanguage(languageCode: String, activity: Activity) {
    val localeList = if (languageCode.isEmpty()) {
        LocaleListCompat.getEmptyLocaleList()
    } else {
        LocaleListCompat.forLanguageTags(languageCode)
    }
    
    AppCompatDelegate.setApplicationLocales(localeList)
    
    // Restart the activity to apply the change
    // On Android 13+ the system handles this automatically for some apps,
    // but we need to recreate to ensure our Compose UI recomposes with new locale
    activity.recreate()
}
