package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.ui.theme.Dimensions
import dev.dettmer.simplenotes.utils.FolderNameValidator

/**
 * 🆕 v2.8.0 (Local-Only Folders): [showLocalOnlyOption] blendet eine Checkbox ein, mit der
 * der Ordner direkt beim Anlegen vom Sync ausgeschlossen wird (nur sinnvoll wenn ein
 * Server konfiguriert ist).
 */
@Composable
fun CreateFolderDialog(
    onConfirm: (name: String, localOnly: Boolean) -> Unit,
    onDismiss: () -> Unit,
    showLocalOnlyOption: Boolean = false
) {
    var text by remember { mutableStateOf("") }
    var localOnly by remember { mutableStateOf(false) }
    val valid = FolderNameValidator.isValid(text)
    val showError = text.isNotEmpty() && !valid
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_create_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    isError = showError,
                    label = { Text(stringResource(R.string.folder_create_hint)) },
                    modifier = Modifier.focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                if (showError) {
                    Text(
                        text = stringResource(R.string.folder_name_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (showLocalOnlyOption) {
                    Spacer(Modifier.height(Dimensions.SpacingMedium))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { localOnly = !localOnly }
                    ) {
                        Checkbox(checked = localOnly, onCheckedChange = { localOnly = it })
                        Spacer(Modifier.width(Dimensions.SpacingMedium))
                        Text(
                            text = stringResource(R.string.folder_create_local_only),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(text.trim(), localOnly) }) {
                Text(stringResource(R.string.folder_create_action))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
