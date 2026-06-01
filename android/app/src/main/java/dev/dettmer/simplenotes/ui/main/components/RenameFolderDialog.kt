package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.utils.FolderNameValidator

/** 🆕 v2.7.0 (Folders): Umbenennen-Dialog für einen Ordner. */
@Composable
fun RenameFolderDialog(
    currentName: String,
    existingNames: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentName) }
    val trimmed = text.trim()
    val isUnchanged = trimmed.equals(currentName, ignoreCase = true)
    val isAlreadyTaken = !isUnchanged && existingNames.any { it.equals(trimmed, ignoreCase = true) }
    val valid = FolderNameValidator.isValid(trimmed) && !isUnchanged && !isAlreadyTaken
    val showError = trimmed.isNotEmpty() && (!FolderNameValidator.isValid(trimmed) || isAlreadyTaken)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_rename_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    isError = showError,
                    label = { Text(stringResource(R.string.folder_create_hint)) }
                )
                if (showError) {
                    Text(
                        text = if (isAlreadyTaken) {
                            stringResource(R.string.folder_name_invalid)
                        } else {
                            stringResource(R.string.folder_name_invalid)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(trimmed) }) {
                Text(stringResource(R.string.folder_rename_action))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
