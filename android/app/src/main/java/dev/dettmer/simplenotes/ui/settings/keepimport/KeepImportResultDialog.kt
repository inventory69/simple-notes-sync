package dev.dettmer.simplenotes.ui.settings.keepimport

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.noteimport.keep.KeepImportSummary
import dev.dettmer.simplenotes.ui.theme.Dimensions

/**
 * v2.5.0 — Result-Dialog (Analyseplan §4.2 [5]).
 *
 * Zeigt alle Counter; Klick auf "Details anzeigen" expandiert die
 * `errors`-Liste inline.
 */
@Composable
fun KeepImportResultDialog(
    summary: KeepImportSummary,
    onDismiss: () -> Unit
) {
    var showErrors by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.keep_import_result_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.keep_import_result_imported, summary.imported))
                Text(stringResource(R.string.keep_import_result_replaced, summary.replaced))
                Text(stringResource(R.string.keep_import_result_skipped, summary.skipped))
                Text(stringResource(R.string.keep_import_result_failed, summary.failed))
                if (summary.notesWithDroppedAttachments > 0) {
                    Spacer(Modifier.height(Dimensions.SpacingMedium))
                    Text(
                        stringResource(
                            R.string.keep_import_result_attachments_dropped,
                            summary.notesWithDroppedAttachments
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(Modifier.height(Dimensions.SpacingMedium))
                Text(stringResource(R.string.keep_import_result_labels, summary.labelsImported))
                if (summary.sharedNotesImported > 0) {
                    Text(
                        stringResource(
                            R.string.keep_import_result_shared,
                            summary.sharedNotesImported
                        )
                    )
                }
                if (summary.notesWithColor > 0) {
                    Text(stringResource(R.string.keep_import_result_color, summary.notesWithColor))
                }
                if (summary.notesWithPin > 0) {
                    Text(stringResource(R.string.keep_import_result_pinned, summary.notesWithPin))
                }

                if (summary.errors.isNotEmpty()) {
                    Spacer(Modifier.height(Dimensions.SpacingLarge))
                    TextButton(onClick = { showErrors = !showErrors }) {
                        Text(
                            if (showErrors) {
                                stringResource(R.string.keep_import_result_hide_errors)
                            } else {
                                stringResource(R.string.keep_import_result_show_errors)
                            }
                        )
                    }
                    if (showErrors) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            summary.errors.forEach { err ->
                                Text(
                                    "• ${err.sourceName}: ${err.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.keep_import_result_button_close))
            }
        }
    )
}
