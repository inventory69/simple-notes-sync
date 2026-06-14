package dev.dettmer.simplenotes.ui.settings.keepimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.settings.components.SettingsButton
import dev.dettmer.simplenotes.ui.settings.components.SettingsInfoCard
import dev.dettmer.simplenotes.ui.settings.components.SettingsSectionHeader
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

    Column(modifier = modifier.fillMaxWidth()) {
        SettingsSectionHeader(text = stringResource(R.string.keep_import_section_title))
        Spacer(modifier = Modifier.height(8.dp))
        SettingsInfoCard(text = stringResource(R.string.keep_import_section_description))
        Spacer(modifier = Modifier.height(8.dp))
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
