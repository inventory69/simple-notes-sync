package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.NoteFilter

/**
 * 🆕 v1.9.0 (F06): Horizontale FilterChip-Reihe für die Notizliste.
 *
 * Zeigt Filter-Chips für "All", "Text", "Checklists".
 * Der aktive Filter ist visuell hervorgehoben (filled chip).
 *
 * ┌──────────────────────────────────────────┐
 * │  [All]  [📝 Text]  [☑ Checklists]       │
 * └──────────────────────────────────────────┘
 */
@Composable
fun FilterChipRow(
    currentFilter: NoteFilter,
    onFilterSelected: (NoteFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // "All" chip — no icon, just text
        FilterChip(
            selected = currentFilter == NoteFilter.ALL,
            onClick = { onFilterSelected(NoteFilter.ALL) },
            label = { Text(stringResource(R.string.filter_all)) }
        )

        // "Text" chip — with document icon when selected
        FilterChip(
            selected = currentFilter == NoteFilter.TEXT_ONLY,
            onClick = { onFilterSelected(NoteFilter.TEXT_ONLY) },
            label = { Text(stringResource(R.string.filter_text_only)) },
            leadingIcon = if (currentFilter == NoteFilter.TEXT_ONLY) {
                {
                    Icon(
                        imageVector = Icons.Outlined.Notes,
                        contentDescription = null
                    )
                }
            } else {
                null
            }
        )

        // "Checklists" chip — with checklist icon when selected
        FilterChip(
            selected = currentFilter == NoteFilter.CHECKLIST_ONLY,
            onClick = { onFilterSelected(NoteFilter.CHECKLIST_ONLY) },
            label = { Text(stringResource(R.string.filter_checklist_only)) },
            leadingIcon = if (currentFilter == NoteFilter.CHECKLIST_ONLY) {
                {
                    Icon(
                        imageVector = Icons.Outlined.Checklist,
                        contentDescription = null
                    )
                }
            } else {
                null
            }
        )
    }
}
