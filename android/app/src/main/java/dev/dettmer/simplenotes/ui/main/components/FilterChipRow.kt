package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.NoteFilter

/**
 * v1.9.0: FilterChip-Zeile + Suchfeld für die Notizliste.
 * v2.0.0: Sort-Button als gleichwertiger FilterChip, zentrierte Labels, responsive Layout.
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

    Column(modifier = modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sort chip — gleiche Optik wie Filter-Chips, zentriertes Icon
            FilterChip(
                modifier = Modifier.weight(1f),
                selected = false,
                onClick = {
                    focusManager.clearFocus()
                    onSortClick()
                },
                label = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = stringResource(R.string.sort_notes),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )

            FilterChip(
                modifier = Modifier.weight(1f),
                selected = currentFilter == NoteFilter.ALL,
                onClick = {
                    focusManager.clearFocus()
                    onFilterSelected(NoteFilter.ALL)
                },
                label = {
                    Text(
                        text = stringResource(R.string.filter_all),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )

            FilterChip(
                modifier = Modifier.weight(1f),
                selected = currentFilter == NoteFilter.TEXT_ONLY,
                onClick = {
                    focusManager.clearFocus()
                    onFilterSelected(NoteFilter.TEXT_ONLY)
                },
                label = {
                    Text(
                        text = stringResource(R.string.filter_text_only),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                leadingIcon = if (currentFilter == NoteFilter.TEXT_ONLY) {
                    { Icon(imageVector = Icons.AutoMirrored.Outlined.Notes, contentDescription = null) }
                } else {
                    null
                }
            )

            FilterChip(
                modifier = Modifier.weight(1f),
                selected = currentFilter == NoteFilter.CHECKLIST_ONLY,
                onClick = {
                    focusManager.clearFocus()
                    onFilterSelected(NoteFilter.CHECKLIST_ONLY)
                },
                label = {
                    Text(
                        text = stringResource(R.string.filter_checklist_only),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                leadingIcon = if (currentFilter == NoteFilter.CHECKLIST_ONLY) {
                    { Icon(imageVector = Icons.Outlined.Checklist, contentDescription = null) }
                } else {
                    null
                }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Suchfeld — volle Breite
        val interactionSource = remember { MutableInteractionSource() }
        val isFocused = interactionSource.collectIsFocusedAsState().value
        val chipShape = MaterialTheme.shapes.small
        val borderColor = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

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
                    border = BorderStroke(width = 1.dp, color = borderColor),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
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
                .fillMaxWidth()
                .height(36.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}
