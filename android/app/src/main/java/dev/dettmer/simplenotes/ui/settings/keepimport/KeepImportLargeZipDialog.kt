package dev.dettmer.simplenotes.ui.settings.keepimport

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.noteimport.keep.zip.KeepPreScanResult

private const val MB_DIVISOR = 1024L * 1024L

/**
 * v2.5.0 — 200 MB-Confirmation (Analyseplan §4.2 [3a], §7.2 weiches Limit).
 */
@Composable
fun KeepImportLargeZipDialog(
    preScan: KeepPreScanResult,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val sizeMb = preScan.sizeBytes / MB_DIVISOR
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.keep_import_large_zip_title)) },
        text = { Text(stringResource(R.string.keep_import_large_zip_body, sizeMb)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.keep_import_large_zip_button_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.keep_import_large_zip_button_cancel))
            }
        }
    )
}
