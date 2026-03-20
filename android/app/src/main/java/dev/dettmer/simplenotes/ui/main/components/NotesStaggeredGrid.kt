package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.models.Note

/**
 * 🎨 v1.7.0: Staggered Grid Layout - OPTIMIERT
 *
 * Pinterest-style Grid:
 * - ALLE Items als SingleLane (halbe Breite)
 * - Dynamische Höhe basierend auf NoteSize (LARGE=6 Zeilen, SMALL=3 Zeilen)
 * - Keine Lücken mehr durch FullLine-Items
 * - Selection mode support
 * - Efficient LazyVerticalStaggeredGrid
 * - ⏱️ timestampTicker triggers recomposition for relative time updates
 */
@Suppress("LongParameterList") // 🔧 v2.1.0 (F46): Compose grid needs adaptiveScaling + manualColumns
@Composable
fun NotesStaggeredGrid(
    notes: List<Note>,
    gridState: LazyStaggeredGridState,
    adaptiveScaling: Boolean,
    manualColumns: Int,
    showSyncStatus: Boolean,
    selectedNoteIds: Set<String>,
    isSelectionMode: Boolean,
    modifier: Modifier = Modifier,
    timestampTicker: Long = 0L,
    onNoteClick: (Note) -> Unit,
    onNoteLongClick: (Note) -> Unit
) {
    LazyVerticalStaggeredGrid(
        columns = if (adaptiveScaling) {
            StaggeredGridCells.Adaptive(150.dp) // v2.0.0: 150dp keeps 2-col on large font scaling (was 180dp)
        } else {
            StaggeredGridCells.Fixed(manualColumns)
        },
        modifier = modifier.fillMaxSize(),
        state = gridState,
        // 🎨 v1.7.0: Konsistente Abstände - 16dp horizontal wie Liste, mehr Platz für FAB
        contentPadding = PaddingValues(
            start = 16.dp, // Wie Liste, war 8dp
            end = 16.dp,
            top = 8.dp,
            bottom = 80.dp // Mehr Platz für FAB, war 16dp
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp), // War 8dp
        verticalItemSpacing = 12.dp // War Constants.GRID_SPACING_DP (8dp)
    ) {
        items(
            items = notes,
            key = { it.id },
            contentType = { "NoteCardGrid" }
            // 🎨 v1.7.0: KEIN span mehr - alle Items sind SingleLane (halbe Breite)
        ) { note ->
            val isSelected = selectedNoteIds.contains(note.id)

            // 🎉 Einheitliche Card für alle Größen - dynamische maxLines intern
            NoteCardGrid(
                note = note,
                showSyncStatus = showSyncStatus,
                isSelected = isSelected,
                isSelectionMode = isSelectionMode,
                timestampTicker = timestampTicker,
                onClick = { onNoteClick(note) },
                onLongClick = { onNoteLongClick(note) }
            )
        }
    }
}
