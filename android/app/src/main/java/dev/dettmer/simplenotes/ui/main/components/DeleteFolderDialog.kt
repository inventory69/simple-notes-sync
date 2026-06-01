package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.dettmer.simplenotes.R

@Composable
fun DeleteFolderDialog(
    folderName: String,
    isNotEmpty: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(folderName) },
        text = {
            Text(
                if (isNotEmpty) {
                    stringResource(R.string.folder_delete_not_empty, folderName)
                } else {
                    stringResource(R.string.folder_delete_confirm, folderName)
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
