package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R

/**
 * 🆕 v2.7.0 (Folders): Delete-Dialog für gemischte Auswahl (Notizen + Ordner).
 *
 * 🆕 v2.9.0 (Trash): vereinfacht — „Nur lokal löschen" und die Offline-Logik entfallen, da Löschen
 * jetzt „in den Papierkorb verschieben" bedeutet. Ein einziger Bestätigungs-Button; bei nicht-leeren
 * Ordnern bietet der Toggle weiterhin „Notizen behalten (nach Root)".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteSelectionDialog(
    noteCount: Int,
    folderCount: Int,
    hasNonEmptyFolders: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (keepContainedNotes: Boolean) -> Unit
) {
    var keepNotes by remember { mutableStateOf(true) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.folder_delete_selected_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when {
                    noteCount > 0 -> stringResource(R.string.delete_selection_message)
                    folderCount == 1 -> stringResource(R.string.delete_folder_message)
                    else -> stringResource(R.string.delete_folders_message, folderCount)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (hasNonEmptyFolders) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { keepNotes = !keepNotes }
                ) {
                    Checkbox(checked = keepNotes, onCheckedChange = { keepNotes = it })
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.folder_delete_keep_notes),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { onConfirm(keepNotes) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.delete))
            }

            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}
