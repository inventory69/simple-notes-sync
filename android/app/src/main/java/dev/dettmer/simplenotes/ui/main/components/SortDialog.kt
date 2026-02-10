package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.SortDirection
import dev.dettmer.simplenotes.models.SortOption

/**
 * 🔀 v1.8.0: Dialog zur Auswahl der Sortierung für die Notizliste.
 * 
 * Zeigt RadioButtons für die Sortieroption und einen Toggle für die Richtung.
 *
 * ┌─────────────────────────────────┐
 * │         Sort Notes              │
 * ├─────────────────────────────────┤
 * │  (●) Last modified         ↓↑  │
 * │  ( ) Date created               │
 * │  ( ) Name                       │
 * │  ( ) Type                       │
 * ├─────────────────────────────────┤
 * │                        [Close]  │
 * └─────────────────────────────────┘
 */
@Composable
fun SortDialog(
    currentOption: SortOption,
    currentDirection: SortDirection,
    onOptionSelected: (SortOption) -> Unit,
    onDirectionToggled: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.sort_notes),
                    style = MaterialTheme.typography.headlineSmall
                )
                
                // Direction Toggle Button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(onClick = onDirectionToggled) {
                        Icon(
                            imageVector = if (currentDirection == SortDirection.DESCENDING) {
                                Icons.Default.ArrowDownward
                            } else {
                                Icons.Default.ArrowUpward
                            },
                            contentDescription = stringResource(
                                if (currentDirection == SortDirection.DESCENDING) {
                                    R.string.sort_descending
                                } else {
                                    R.string.sort_ascending
                                }
                            ),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = stringResource(
                            if (currentDirection == SortDirection.DESCENDING) {
                                R.string.sort_descending
                            } else {
                                R.string.sort_ascending
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                
                SortOption.entries.forEach { option ->
                    SortOptionRow(
                        label = stringResource(option.toStringRes()),
                        isSelected = currentOption == option,
                        onClick = { onOptionSelected(option) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
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
 * Extension: SortOption → String-Resource-ID
 */
fun SortOption.toStringRes(): Int = when (this) {
    SortOption.UPDATED_AT -> R.string.sort_by_updated
    SortOption.CREATED_AT -> R.string.sort_by_created
    SortOption.TITLE -> R.string.sort_by_name
    SortOption.NOTE_TYPE -> R.string.sort_by_type
}
