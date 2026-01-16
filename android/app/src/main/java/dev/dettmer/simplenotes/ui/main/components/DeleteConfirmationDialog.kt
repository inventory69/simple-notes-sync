package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R

/**
 * Delete confirmation dialog with server/local options
 * v1.5.0: Multi-Select Feature
 */
@Composable
fun DeleteConfirmationDialog(
    noteCount: Int = 1,
    onDismiss: () -> Unit,
    onDeleteLocal: () -> Unit,
    onDeleteEverywhere: () -> Unit
) {
    val title = if (noteCount == 1) {
        stringResource(R.string.delete_note_title)
    } else {
        stringResource(R.string.delete_notes_title, noteCount)
    }
    
    val message = if (noteCount == 1) {
        stringResource(R.string.delete_note_message)
    } else {
        stringResource(R.string.delete_notes_message, noteCount)
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Delete everywhere (server + local) - primary action
                TextButton(
                    onClick = onDeleteEverywhere,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.delete_everywhere))
                }
                
                // Delete local only
                TextButton(
                    onClick = onDeleteLocal,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.delete_local_only))
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Cancel button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
        dismissButton = null // All buttons in confirmButton column
    )
}
