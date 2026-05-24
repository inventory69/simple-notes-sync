package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.ui.theme.Dimensions

/**
 * Notes list - v1.5.0 with Multi-Select Support
 *
 * ULTRA SIMPLE + SELECTION:
 * - NO remember() anywhere
 * - NO caching tricks
 * - Selection state passed through as parameters
 * - Tap behavior changes based on selection mode
 * - ⏱️ timestampTicker triggers recomposition for relative time updates
 */
@Suppress("LongParameterList") // Composable with many UI state parameters
@Composable
fun NotesList(
    notes: List<Note>,
    showSyncStatus: Boolean,
    modifier: Modifier = Modifier,
    selectedNotes: Set<String> = emptySet(),
    isSelectionMode: Boolean = false,
    timestampTicker: Long = 0L,
    listState: LazyListState = rememberLazyListState(),
    onNoteClick: (Note) -> Unit,
    onNoteLongPress: (Note) -> Unit,
    onNoteSelectionToggle: (Note) -> Unit = {}
) {
    val pinnedNotes = notes.filter { it.isPinned == true }
    val unpinnedNotes = notes.filter { it.isPinned != true }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
    ) {
        if (pinnedNotes.isNotEmpty()) {
            item(key = "header_pinned", contentType = "SectionHeader") {
                Text(
                    text = stringResource(R.string.section_pinned),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = Dimensions.SpacingMedium,
                            bottom = Dimensions.SpacingSmall,
                        )
                )
            }
            items(
                items = pinnedNotes,
                key = { it.id },
                contentType = { "NoteCard" }
            ) { note ->
                NoteCard(
                    note = note,
                    showSyncStatus = showSyncStatus,
                    isSelected = note.id in selectedNotes,
                    isSelectionMode = isSelectionMode,
                    timestampTicker = timestampTicker,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    onClick = {
                        if (isSelectionMode) onNoteSelectionToggle(note) else onNoteClick(note)
                    },
                    onLongClick = { onNoteLongPress(note) }
                )
            }
            item(key = "header_notes", contentType = "SectionHeader") {
                Text(
                    text = stringResource(R.string.section_notes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = Dimensions.SpacingMedium,
                            bottom = Dimensions.SpacingSmall,
                        )
                )
            }
        }
        items(
            items = unpinnedNotes,
            key = { it.id },
            contentType = { "NoteCard" }
        ) { note ->
            NoteCard(
                note = note,
                showSyncStatus = showSyncStatus,
                isSelected = note.id in selectedNotes,
                isSelectionMode = isSelectionMode,
                timestampTicker = timestampTicker,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                onClick = {
                    if (isSelectionMode) onNoteSelectionToggle(note) else onNoteClick(note)
                },
                onLongClick = { onNoteLongPress(note) }
            )
        }
    }
}
