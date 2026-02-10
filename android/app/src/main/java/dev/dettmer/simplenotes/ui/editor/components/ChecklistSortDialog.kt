package dev.dettmer.simplenotes.ui.editor.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.ChecklistSortOption

/**
 * 🔀 v1.8.0: Dialog zur Auswahl der Checklist-Sortierung.
 *
 * Einmalige Sortier-Aktion (nicht persistiert).
 * User kann danach per Drag & Drop feinjustieren.
 *
 * ┌─────────────────────────────────┐
 * │       Sort Checklist            │
 * ├─────────────────────────────────┤
 * │  ( ) Manual                     │
 * │  ( ) A → Z                     │
 * │  ( ) Z → A                     │
 * │  (●) Unchecked first            │
 * │  ( ) Checked first              │
 * ├─────────────────────────────────┤
 * │               [Cancel] [Apply]  │
 * └─────────────────────────────────┘
 */
@Composable
fun ChecklistSortDialog(
    currentOption: ChecklistSortOption,  // 🔀 v1.8.0: Aktuelle Auswahl merken
    onOptionSelected: (ChecklistSortOption) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedOption by remember { mutableStateOf(currentOption) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.sort_checklist),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                ChecklistSortOption.entries.forEach { option ->
                    SortOptionRow(
                        label = stringResource(option.toStringRes()),
                        isSelected = selectedOption == option,
                        onClick = { selectedOption = option }
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onOptionSelected(selectedOption)
                }
            ) {
                Text(stringResource(R.string.apply))
            }
        }
    )
}

@Composable
private fun SortOptionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/**
 * Extension: ChecklistSortOption → String-Resource-ID
 */
fun ChecklistSortOption.toStringRes(): Int = when (this) {
    ChecklistSortOption.MANUAL -> R.string.sort_checklist_manual
    ChecklistSortOption.ALPHABETICAL_ASC -> R.string.sort_checklist_alpha_asc
    ChecklistSortOption.ALPHABETICAL_DESC -> R.string.sort_checklist_alpha_desc
    ChecklistSortOption.UNCHECKED_FIRST -> R.string.sort_checklist_unchecked_first
    ChecklistSortOption.CHECKED_FIRST -> R.string.sort_checklist_checked_first
}
