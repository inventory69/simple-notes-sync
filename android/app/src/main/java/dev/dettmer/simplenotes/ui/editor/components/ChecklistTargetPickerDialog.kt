package dev.dettmer.simplenotes.ui.editor.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.Note

/**
 * 🆕 v2.2.0: Dialog zur Auswahl einer Ziel-Checkliste für "In andere Checkliste kopieren".
 *
 * Zeigt eine scrollbare Liste aller anderen Checklisten-Notizen.
 * User tippt auf eine Notiz → onSelect(noteId) wird aufgerufen.
 *
 * Falls keine anderen Checklisten vorhanden sind, wird ein Hinweis-Text angezeigt
 * und nur ein "OK"-Button zum Schließen.
 *
 * Design-Pattern: Identisch mit ChecklistSortDialog und SyncStatusLegendDialog:
 * AlertDialog + scrollbare Column + TextButton im confirmButton-Slot.
 *
 * @param checklists Liste aller anderen Checklisten-Notizen (kann leer sein)
 * @param onSelect Callback wenn User eine Checkliste auswählt — erhält die Note-ID
 * @param onDismiss Callback zum Schließen des Dialogs ohne Auswahl
 */
@Composable
fun ChecklistTargetPickerDialog(
    checklists: List<Note>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.checklist_copy_to_checklist_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            if (checklists.isEmpty()) {
                Text(
                    text = stringResource(R.string.checklist_copy_to_checklist_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    checklists.forEach { note ->
                        ChecklistTargetRow(
                            title = note.title.ifBlank {
                                stringResource(R.string.checklist_copy_to_checklist_untitled)
                            },
                            itemCount = note.checklistItems?.size ?: 0,
                            onClick = { onSelect(note.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(
                        if (checklists.isEmpty()) R.string.ok else R.string.cancel
                    )
                )
            }
        }
    )
}

/**
 * Einzelne Zeile im Auswahl-Dialog: Icon + Notiz-Titel + Item-Count.
 *
 * Stil orientiert sich an SortOptionRow (ChecklistSortDialog) und
 * LegendRow (SyncStatusLegendDialog): Icon links, Text rechts, clickable Row.
 */
@Composable
private fun ChecklistTargetRow(
    title: String,
    itemCount: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Checklist,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.checklist_copy_to_checklist_item_count, itemCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
