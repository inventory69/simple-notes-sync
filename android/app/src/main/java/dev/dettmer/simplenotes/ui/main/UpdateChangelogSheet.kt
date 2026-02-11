package dev.dettmer.simplenotes.ui.main

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.BuildConfig
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.launch

/**
 * v1.8.0: Post-Update Changelog Bottom Sheet
 * 
 * Shows a subtle changelog on first launch after an update.
 * - Reads changelog from raw resources (supports DE/EN)
 * - Only shows once per versionCode (stored in SharedPreferences)
 * - Uses Material 3 ModalBottomSheet with built-in slide-up animation
 * - Dismissable via button or swipe-down
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateChangelogSheet() {
    val context = LocalContext.current
    val prefs = remember {
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    val currentVersionCode = BuildConfig.VERSION_CODE
    val lastShownVersion = prefs.getInt(Constants.KEY_LAST_SHOWN_CHANGELOG_VERSION, 0)
    
    // Only show if this is a new version
    var showSheet by remember { mutableStateOf(currentVersionCode > lastShownVersion) }
    
    if (!showSheet) return
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    
    // Load changelog text based on current locale
    val changelogText = remember {
        loadChangelog(context)
    }
    
    ModalBottomSheet(
        onDismissRequest = {
            showSheet = false
            prefs.edit()
                .putInt(Constants.KEY_LAST_SHOWN_CHANGELOG_VERSION, currentVersionCode)
                .apply()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = stringResource(R.string.update_changelog_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Changelog content with clickable links
            val annotatedText = buildAnnotatedString {
                val lines = changelogText.split("\n")
                lines.forEachIndexed { index, line ->
                    if (line.startsWith("http://") || line.startsWith("https://")) {
                        // Make URLs clickable
                        withLink(
                            LinkAnnotation.Url(
                                url = line.trim(),
                                styles = androidx.compose.ui.text.TextLinkStyles(
                                    style = SpanStyle(
                                        color = MaterialTheme.colorScheme.primary,
                                        textDecoration = TextDecoration.Underline
                                    )
                                )
                            )
                        ) {
                            append(line)
                        }
                    } else {
                        append(line)
                    }
                    if (index < lines.size - 1) append("\n")
                }
            }
            
            Text(
                text = annotatedText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Dismiss button
            Button(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        showSheet = false
                        prefs.edit()
                            .putInt(Constants.KEY_LAST_SHOWN_CHANGELOG_VERSION, currentVersionCode)
                            .apply()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                Text(stringResource(R.string.update_changelog_dismiss))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Load changelog text from assets based on current app locale and versionCode.
 * Changelogs are copied from /fastlane/metadata/android/{locale}/changelogs/{versionCode}.txt
 * at build time, providing a single source of truth for F-Droid and in-app display.
 * Falls back to English if the localized version is not available.
 */
private fun loadChangelog(context: Context): String {
    val currentLocale = AppCompatDelegate.getApplicationLocales()
    val languageCode = if (currentLocale.isEmpty) {
        // System default — check system locale
        java.util.Locale.getDefault().language
    } else {
        currentLocale.get(0)?.language ?: "en"
    }
    
    // Map language code to F-Droid locale directory
    val localeDir = when (languageCode) {
        "de" -> "de-DE"
        else -> "en-US"
    }
    
    val versionCode = BuildConfig.VERSION_CODE
    val changelogPath = "changelogs/$localeDir/$versionCode.txt"
    
    return try {
        context.assets.open(changelogPath)
            .bufferedReader()
            .use { it.readText() }
    } catch (e: Exception) {
        Logger.e("UpdateChangelogSheet", "Failed to load changelog for locale: $localeDir", e)
        // Fallback to English
        try {
            context.assets.open("changelogs/en-US/$versionCode.txt")
                .bufferedReader()
                .use { it.readText() }
        } catch (e2: Exception) {
            Logger.e("UpdateChangelogSheet", "Failed to load English fallback changelog", e2)
            "v${BuildConfig.VERSION_NAME}"
        }
    }
}
