package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.models.Note

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
    selectedNotes: Set<String> = emptySet(),
    isSelectionMode: Boolean = false,
    timestampTicker: Long = 0L,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onNoteClick: (Note) -> Unit,
    onNoteLongPress: (Note) -> Unit,
    onNoteSelectionToggle: (Note) -> Unit = {}
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
    ) {
        items(
            items = notes,
            key = { it.id },
            contentType = { "NoteCard" }
        ) { note ->
            val isSelected = note.id in selectedNotes
            
            NoteCard(
                note = note,
                showSyncStatus = showSyncStatus,
                isSelected = isSelected,
                isSelectionMode = isSelectionMode,
                timestampTicker = timestampTicker,
                // 🎨 v1.7.0: Padding hier in Liste (nicht in Card selbst)
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                onClick = {
                    if (isSelectionMode) {
                        // In selection mode, tap toggles selection
                        onNoteSelectionToggle(note)
                    } else {
                        // Normal mode, open note
                        onNoteClick(note)
                    }
                },
                onLongClick = { onNoteLongPress(note) }
            )
        }
    }
}
