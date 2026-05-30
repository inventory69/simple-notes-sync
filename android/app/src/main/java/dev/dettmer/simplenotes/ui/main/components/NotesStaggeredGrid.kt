package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.Folder
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
    onNoteLongClick: (Note) -> Unit,
    folders: List<Folder> = emptyList(),                    // 🆕 v2.7.0 (Folders): List<Folder>
    folderNoteCounts: Map<String, Int> = emptyMap(),
    selectedFolders: Set<String> = emptySet(),              // 🆕 v2.7.0 (Folders): Auswahl
    onFolderClick: (String) -> Unit = {},
    onFolderLongPress: (String) -> Unit = {},
    onFolderSelectionToggle: (String) -> Unit = {}          // 🆕 v2.7.0 (Folders)
) {
    val pinnedNotes = notes.filter { it.isPinned == true }
    val unpinnedNotes = notes.filter { it.isPinned != true }
    // 🆕 v2.7.0 (Folders): Reihenfolge Pinned → Folders → Notes
    val showNotesHeader = unpinnedNotes.isNotEmpty() && (pinnedNotes.isNotEmpty() || folders.isNotEmpty())

    LazyVerticalStaggeredGrid(
        columns = if (adaptiveScaling) {
            StaggeredGridCells.Adaptive(150.dp) // v2.0.0: 150dp keeps 2-col on large font scaling (was 180dp)
        } else {
            StaggeredGridCells.Fixed(manualColumns)
        },
        modifier = modifier.fillMaxSize(),
        state = gridState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp
    ) {
        if (pinnedNotes.isNotEmpty()) {
            item(
                key = "header_pinned",
                contentType = "SectionHeader",
                span = StaggeredGridItemSpan.FullLine,
            ) {
                SectionHeaderText(stringResource(R.string.section_pinned))
            }
            item(key = "pinned_notes_body", contentType = "PinnedSection", span = StaggeredGridItemSpan.FullLine) {
                PinnedNotesGrid(
                    notes = pinnedNotes,
                    showSyncStatus = showSyncStatus,
                    selectedNoteIds = selectedNoteIds,
                    isSelectionMode = isSelectionMode,
                    timestampTicker = timestampTicker,
                    onNoteClick = onNoteClick,
                    onNoteLongClick = onNoteLongClick
                )
            }
        }

        if (folders.isNotEmpty()) {
            item(key = "header_folders", contentType = "SectionHeader", span = StaggeredGridItemSpan.FullLine) {
                SectionHeaderText(stringResource(R.string.folder_section_header))
            }
            items(items = folders, key = { "folder_${it.name}" }, contentType = { "FolderCardGrid" }) { folder ->
                FolderCardGrid(
                    name = folder.name,
                    count = folderNoteCounts[folder.name] ?: 0,
                    color = folder.color,
                    isSelected = folder.name in selectedFolders,
                    isSelectionMode = isSelectionMode, // 🆕 v2.7.0 (Folders)
                    onClick = { if (isSelectionMode) onFolderSelectionToggle(folder.name) else onFolderClick(folder.name) },
                    onLongClick = { onFolderLongPress(folder.name) }
                )
            }
        }

        if (showNotesHeader) {
            item(
                key = "header_notes",
                contentType = "SectionHeader",
                span = StaggeredGridItemSpan.FullLine,
            ) {
                SectionHeaderText(stringResource(R.string.section_notes))
            }
        }

        items(
            items = unpinnedNotes,
            key = { it.id },
            contentType = { "NoteCardGrid" }
            // 🎨 v1.7.0: KEIN span mehr - alle Items sind SingleLane (halbe Breite)
        ) { note ->
            NoteCardGrid(
                note = note,
                showSyncStatus = showSyncStatus,
                isSelected = selectedNoteIds.contains(note.id),
                isSelectionMode = isSelectionMode,
                timestampTicker = timestampTicker,
                onClick = { onNoteClick(note) },
                onLongClick = { onNoteLongClick(note) }
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun PinnedNotesGrid(
    notes: List<Note>,
    showSyncStatus: Boolean,
    selectedNoteIds: Set<String>,
    isSelectionMode: Boolean,
    timestampTicker: Long,
    onNoteClick: (Note) -> Unit,
    onNoteLongClick: (Note) -> Unit
) {
    val leftNotes = notes.filterIndexed { i, _ -> i % 2 == 0 }
    val rightNotes = notes.filterIndexed { i, _ -> i % 2 == 1 }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            leftNotes.forEach { note ->
                NoteCardGrid(
                    note = note,
                    showSyncStatus = showSyncStatus,
                    isSelected = selectedNoteIds.contains(note.id),
                    isSelectionMode = isSelectionMode,
                    timestampTicker = timestampTicker,
                    onClick = { onNoteClick(note) },
                    onLongClick = { onNoteLongClick(note) }
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rightNotes.forEach { note ->
                NoteCardGrid(
                    note = note,
                    showSyncStatus = showSyncStatus,
                    isSelected = selectedNoteIds.contains(note.id),
                    isSelectionMode = isSelectionMode,
                    timestampTicker = timestampTicker,
                    onClick = { onNoteClick(note) },
                    onLongClick = { onNoteLongClick(note) }
                )
            }
        }
    }
}
