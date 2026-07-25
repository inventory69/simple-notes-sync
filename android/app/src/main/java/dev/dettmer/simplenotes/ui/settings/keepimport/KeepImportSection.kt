package dev.dettmer.simplenotes.ui.settings.keepimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.settings.components.SettingsButton
import dev.dettmer.simplenotes.ui.settings.components.SettingsHint
import dev.dettmer.simplenotes.ui.settings.components.SettingsSectionCard
import dev.dettmer.simplenotes.utils.Logger

private const val TAG = "KeepImportSection"

/**
 * v2.5.0 — Einstiegs-Section für den Keep-Import (Analyseplan §4.1).
 * Wird in Commit #13 in `ImportSettingsScreen` eingebettet.
 *
 * SAF-Launcher öffnet `application/zip`-Picker; bei Auswahl → [onZipPicked].
 */
@Composable
fun KeepImportSection(
    onZipPicked: (android.net.Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) onZipPicked(uri)
    }

    SettingsSectionCard(title = stringResource(R.string.keep_import_section_title), modifier = modifier) {
        SettingsHint(text = stringResource(R.string.keep_import_section_description))
        SettingsButton(
            text = stringResource(R.string.keep_import_section_button_pick_zip),
            onClick = {
                // MIME-Type "application/zip" mit Fallback "*/*" — manche
                // SAF-Provider liefern keinen ZIP-MIME, daher liberal:
                try {
                    launcher.launch("application/zip")
                } catch (e: Exception) {
                    Logger.d(TAG, "ZIP MIME launch failed, retrying with */*: ${e.message}")
                    launcher.launch("*/*")
                }
            },
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
