package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.NoteFilter

/**
 * 🆕 v1.9.0 (F06): Horizontale FilterChip-Reihe für die Notizliste.
 * 🆕 v1.9.0 (F10): Inline-Suchfeld am rechten Ende der Reihe.
 * 🆕 v1.9.0 (F11): Sort-Button als erstes Element (links), icon-only.
 *
 * ┌──────────────────────────────────────────────────────────────────────────────┐
 * │  [↕]  [All]  [📝 Text]  [☑ Checklists]            [ 🔍 Search...     ✕ ] │
 * └──────────────────────────────────────────────────────────────────────────────┘
 */
@Composable
fun FilterChipRow(
    currentFilter: NoteFilter,
    onFilterSelected: (NoteFilter) -> Unit,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onSortClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Row(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 🆕 v1.9.0 (F11): Sort button (icon-only) — opens sort dialog
        IconButton(onClick = { focusManager.clearFocus(); onSortClick() }) {
            Icon(
                imageVector = Icons.Default.SwapVert,
                contentDescription = stringResource(R.string.sort_notes)
            )
        }

        // "All" chip — no icon, just text
        FilterChip(
            selected = currentFilter == NoteFilter.ALL,
            onClick = { focusManager.clearFocus(); onFilterSelected(NoteFilter.ALL) },
            label = { Text(stringResource(R.string.filter_all)) }
        )

        // "Text" chip — with document icon when selected
        FilterChip(
            selected = currentFilter == NoteFilter.TEXT_ONLY,
            onClick = { focusManager.clearFocus(); onFilterSelected(NoteFilter.TEXT_ONLY) },
            label = { Text(stringResource(R.string.filter_text_only)) },
            leadingIcon = if (currentFilter == NoteFilter.TEXT_ONLY) {
                {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Notes,
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
            onClick = { focusManager.clearFocus(); onFilterSelected(NoteFilter.CHECKLIST_ONLY) },
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

        // 🆕 v1.9.0 (F10): Compact inline search field — matches FilterChip height (32dp)
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused = interactionSource.collectIsFocusedAsState().value
        val chipShape = MaterialTheme.shapes.small
        val borderColor = if (isFocused) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        }

        BasicTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            singleLine = true,
            textStyle = MaterialTheme.typography.labelLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Surface(
                    shape = chipShape,
                    border = BorderStroke(
                        width = 1.dp,
                        color = borderColor
                    ),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search_notes_placeholder),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.search_clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { onSearchQueryChanged("") }
                            )
                        }
                    }
                }
            },
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
        )
    }
}
