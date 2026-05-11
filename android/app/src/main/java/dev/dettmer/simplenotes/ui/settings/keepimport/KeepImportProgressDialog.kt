package dev.dettmer.simplenotes.ui.settings.keepimport

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.noteimport.keep.KeepImportProgress
import dev.dettmer.simplenotes.ui.theme.Dimensions

/**
 * v2.5.0 — Running-Dialog (Analyseplan §4.2 [4]).
 * Nicht über System-Back schließbar — User muss explizit Cancel drücken.
 */
@Composable
fun KeepImportProgressDialog(
    progress: KeepImportProgress,
    cancellable: Boolean,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* Nicht-dismissable, User MUSS Cancel drücken. */ },
        title = { Text(stringResource(R.string.keep_import_progress_title)) },
        text = {
            Column {
                val ratio = if (progress.total > 0) {
                    progress.processed.toFloat() / progress.total.toFloat()
                } else 0f
                LinearProgressIndicator(
                    progress = { ratio.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Dimensions.SpacingMedium))
                Text(
                    stringResource(
                        R.string.keep_import_progress_status,
                        progress.processed,
                        progress.total,
                        progress.currentName,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel, enabled = cancellable) {
                Text(stringResource(R.string.keep_import_progress_button_cancel))
            }
        },
    )
}
