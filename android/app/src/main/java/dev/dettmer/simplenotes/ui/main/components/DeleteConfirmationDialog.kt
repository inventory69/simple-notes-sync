package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
        "Notiz löschen?"
    } else {
        "$noteCount Notizen löschen?"
    }
    
    val message = if (noteCount == 1) {
        "Wie möchtest du diese Notiz löschen?"
    } else {
        "Wie möchtest du diese $noteCount Notizen löschen?"
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
                    Text("Überall löschen (auch Server)")
                }
                
                // Delete local only
                TextButton(
                    onClick = onDeleteLocal,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Nur lokal löschen")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Cancel button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Abbrechen")
                }
            }
        },
        dismissButton = null // All buttons in confirmButton column
    )
}
