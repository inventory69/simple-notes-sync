package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.Folder
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.ui.theme.NotePreviewLength
import kotlin.math.max

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
    previewLength: NotePreviewLength = NotePreviewLength.STANDARD,
    showTimestamp: Boolean = true,
    showTypeIcon: Boolean = true,
    onNoteClick: (Note) -> Unit,
    onNoteLongClick: (Note) -> Unit,
    folders: List<Folder> = emptyList(), // 🆕 v2.7.0 (Folders): List<Folder>
    folderNoteCounts: Map<String, Int> = emptyMap(),
    selectedFolders: Set<String> = emptySet(), // 🆕 v2.7.0 (Folders): Auswahl
    localOnlyFolderNames: Set<String> = emptySet(), // 🆕 v2.8.0 (Local-Only Folders)
    onFolderClick: (String) -> Unit = {},
    onFolderLongPress: (String) -> Unit = {},
    onFolderSelectionToggle: (String) -> Unit = {}, // 🆕 v2.7.0 (Folders)
    collapsedSections: Set<String> = emptySet(), // 🆕 collapsible sections
    onToggleSection: (String) -> Unit = {}, // 🆕 collapsible sections
    sectionOrder: List<String> = listOf(SECTION_PINNED, SECTION_FOLDERS, SECTION_NOTES), // 🆕 section reordering
    onMoveSection: (from: String, to: String?) -> Unit = { _, _ -> } // 🆕 section reordering
) {
    val pinnedNotes = remember(notes) { notes.filter { it.isPinned == true } }
    val unpinnedNotes = remember(notes) { notes.filter { it.isPinned != true } }
    // 🆕 v2.7.0 (Folders): Reihenfolge Pinned → Folders → Notes
    val showNotesHeader = remember(notes, folders) { unpinnedNotes.isNotEmpty() && (pinnedNotes.isNotEmpty() || folders.isNotEmpty()) }

    // 🆕 section reordering: a section only counts for move-up/down adjacency if its header is
    // actually rendered this frame (mirrors each section's existing visibility rule).
    val headerVisible = remember(pinnedNotes, folders, showNotesHeader) {
        mapOf(
            SECTION_PINNED to pinnedNotes.isNotEmpty(),
            SECTION_FOLDERS to folders.isNotEmpty(),
            SECTION_NOTES to showNotesHeader
        )
    }
    val visibleHeaderOrder = remember(sectionOrder, headerVisible) {
        sectionOrder.filter { headerVisible[it] == true }
    }

    fun adjacentSection(section: String, delta: Int): String? {
        val idx = visibleHeaderOrder.indexOf(section)
        if (idx == -1) return null
        return visibleHeaderOrder.getOrNull(idx + delta)
    }

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
        for (section in sectionOrder) {
            when (section) {
                SECTION_PINNED -> {
                    if (pinnedNotes.isNotEmpty()) {
                        item(
                            key = "header_pinned",
                            contentType = "SectionHeader",
                            span = StaggeredGridItemSpan.FullLine
                        ) {
                            SectionHeaderText(
                                text = stringResource(R.string.section_pinned),
                                collapsed = SECTION_PINNED in collapsedSections,
                                onToggleCollapse = { onToggleSection(SECTION_PINNED) },
                                canMoveUp = adjacentSection(SECTION_PINNED, -1) != null,
                                canMoveDown = adjacentSection(SECTION_PINNED, +1) != null,
                                onMoveUp = { onMoveSection(SECTION_PINNED, adjacentSection(SECTION_PINNED, -1)) },
                                onMoveDown = { onMoveSection(SECTION_PINNED, adjacentSection(SECTION_PINNED, +1)) }
                            )
                        }
                        if (SECTION_PINNED !in collapsedSections) {
                            item(
                                key = "pinned_notes_body",
                                contentType = "PinnedSection",
                                span = StaggeredGridItemSpan.FullLine
                            ) {
                                PinnedNotesGrid(
                                    notes = pinnedNotes,
                                    adaptiveScaling = adaptiveScaling,
                                    manualColumns = manualColumns,
                                    showSyncStatus = showSyncStatus,
                                    selectedNoteIds = selectedNoteIds,
                                    isSelectionMode = isSelectionMode,
                                    timestampTicker = timestampTicker,
                                    previewLength = previewLength,
                                    showTimestamp = showTimestamp,
                                    showTypeIcon = showTypeIcon,
                                    onNoteClick = onNoteClick,
                                    onNoteLongClick = onNoteLongClick
                                )
                            }
                        }
                    }
                }

                SECTION_FOLDERS -> {
                    if (folders.isNotEmpty()) {
                        item(
                            key = "header_folders",
                            contentType = "SectionHeader",
                            span = StaggeredGridItemSpan.FullLine
                        ) {
                            SectionHeaderText(
                                text = stringResource(R.string.folder_section_header),
                                collapsed = SECTION_FOLDERS in collapsedSections,
                                onToggleCollapse = { onToggleSection(SECTION_FOLDERS) },
                                canMoveUp = adjacentSection(SECTION_FOLDERS, -1) != null,
                                canMoveDown = adjacentSection(SECTION_FOLDERS, +1) != null,
                                onMoveUp = { onMoveSection(SECTION_FOLDERS, adjacentSection(SECTION_FOLDERS, -1)) },
                                onMoveDown = { onMoveSection(SECTION_FOLDERS, adjacentSection(SECTION_FOLDERS, +1)) }
                            )
                        }
                        if (SECTION_FOLDERS !in collapsedSections) {
                            items(
                                items = folders,
                                key = { "folder_${it.name}" },
                                contentType = { "FolderCardGrid" }
                            ) { folder ->
                                FolderCardGrid(
                                    name = folder.name,
                                    count = folderNoteCounts[folder.name] ?: 0,
                                    color = folder.color,
                                    isSelected = folder.name in selectedFolders,
                                    isSelectionMode = isSelectionMode, // 🆕 v2.7.0 (Folders)
                                    isLocalOnly = folder.name in localOnlyFolderNames, // 🆕 v2.8.0 (Local-Only Folders)
                                    onClick = {
                                        if (isSelectionMode) {
                                            onFolderSelectionToggle(folder.name)
                                        } else {
                                            onFolderClick(folder.name)
                                        }
                                    },
                                    onLongClick = { onFolderLongPress(folder.name) }
                                )
                            }
                        }
                    }
                }

                SECTION_NOTES -> {
                    if (showNotesHeader) {
                        item(
                            key = "header_notes",
                            contentType = "SectionHeader",
                            span = StaggeredGridItemSpan.FullLine
                        ) {
                            SectionHeaderText(
                                text = stringResource(R.string.section_notes),
                                collapsed = SECTION_NOTES in collapsedSections,
                                onToggleCollapse = { onToggleSection(SECTION_NOTES) },
                                canMoveUp = adjacentSection(SECTION_NOTES, -1) != null,
                                canMoveDown = adjacentSection(SECTION_NOTES, +1) != null,
                                onMoveUp = { onMoveSection(SECTION_NOTES, adjacentSection(SECTION_NOTES, -1)) },
                                onMoveDown = { onMoveSection(SECTION_NOTES, adjacentSection(SECTION_NOTES, +1)) }
                            )
                        }
                    }
                    if (SECTION_NOTES !in collapsedSections) {
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
                                previewLength = previewLength,
                                showTimestamp = showTimestamp,
                                showTypeIcon = showTypeIcon,
                                onClick = { onNoteClick(note) },
                                onLongClick = { onNoteLongClick(note) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun PinnedNotesGrid(
    notes: List<Note>,
    adaptiveScaling: Boolean,
    manualColumns: Int,
    showSyncStatus: Boolean,
    selectedNoteIds: Set<String>,
    isSelectionMode: Boolean,
    timestampTicker: Long,
    previewLength: NotePreviewLength,
    showTimestamp: Boolean,
    showTypeIcon: Boolean,
    onNoteClick: (Note) -> Unit,
    onNoteLongClick: (Note) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columnCount = if (adaptiveScaling) max(1, (maxWidth / 150.dp).toInt()) else manualColumns
        val columnedNotes = remember(notes, columnCount) {
            (0 until columnCount).map { col -> notes.filterIndexed { i, _ -> i % columnCount == col } }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            columnedNotes.forEach { columnNotes ->
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    columnNotes.forEach { note ->
                        NoteCardGrid(
                            note = note,
                            showSyncStatus = showSyncStatus,
                            isSelected = selectedNoteIds.contains(note.id),
                            isSelectionMode = isSelectionMode,
                            timestampTicker = timestampTicker,
                            previewLength = previewLength,
                            showTimestamp = showTimestamp,
                            showTypeIcon = showTypeIcon,
                            onClick = { onNoteClick(note) },
                            onLongClick = { onNoteLongClick(note) }
                        )
                    }
                }
            }
        }
    }
}
