package dev.dettmer.simplenotes.ui.main.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.Folder
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.ui.theme.Dimensions

/**
 * Notes list - v1.5.0 with Multi-Select Support
 *
 * ULTRA SIMPLE + SELECTION:
 * - Selection state passed through as parameters
 * - Tap behavior changes based on selection mode
 * - ⏱️ timestampTicker triggers recomposition for relative time updates
 * - 🔧 Perf: pinned/unpinned split is remember(notes)-cached (matches NotesStaggeredGrid) so the
 *   30s timestampTicker recomposition doesn't re-filter the full note list every tick
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
    folders: List<Folder> = emptyList(), // 🆕 v2.7.0 (Folders): List<Folder>
    folderNoteCounts: Map<String, Int> = emptyMap(),
    selectedFolders: Set<String> = emptySet(), // 🆕 v2.7.0 (Folders): Auswahl
    localOnlyFolderNames: Set<String> = emptySet(), // 🆕 v2.8.0 (Local-Only Folders)
    onFolderClick: (String) -> Unit = {},
    onFolderLongPress: (String) -> Unit = {},
    onFolderSelectionToggle: (String) -> Unit = {}, // 🆕 v2.7.0 (Folders)
    onNoteClick: (Note) -> Unit,
    onNoteLongPress: (Note) -> Unit,
    onNoteSelectionToggle: (Note) -> Unit = {},
    collapsedSections: Set<String> = emptySet(), // 🆕 collapsible sections
    onToggleSection: (String) -> Unit = {}, // 🆕 collapsible sections
    sectionOrder: List<String> = listOf(SECTION_PINNED, SECTION_FOLDERS, SECTION_NOTES), // 🆕 section reordering
    onMoveSection: (from: String, to: String?) -> Unit = { _, _ -> } // 🆕 section reordering
) {
    val pinnedNotes = remember(notes) { notes.filter { it.isPinned == true } }
    val unpinnedNotes = remember(notes) { notes.filter { it.isPinned != true } }
    // 🆕 v2.7.0 (Folders): Reihenfolge Pinned → Folders → Notes
    val showNotesHeader = unpinnedNotes.isNotEmpty() && (pinnedNotes.isNotEmpty() || folders.isNotEmpty())

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

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
    ) {
        for (section in sectionOrder) {
            when (section) {
                SECTION_PINNED -> {
                    if (pinnedNotes.isNotEmpty()) {
                        item(key = "header_pinned", contentType = "SectionHeader") {
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
                            items(items = pinnedNotes, key = { it.id }, contentType = { "PinnedNoteCard" }) { note ->
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
                }

                SECTION_FOLDERS -> {
                    if (folders.isNotEmpty()) {
                        item(key = "header_folders", contentType = "SectionHeader") {
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
                            items(items = folders, key = { "folder_${it.name}" }, contentType = { "FolderCard" }) { folder ->
                                FolderCardList(
                                    name = folder.name,
                                    count = folderNoteCounts[folder.name] ?: 0,
                                    color = folder.color,
                                    isSelected = folder.name in selectedFolders,
                                    isSelectionMode = isSelectionMode, // 🆕 v2.7.0 (Folders)
                                    isLocalOnly = folder.name in localOnlyFolderNames, // 🆕 v2.8.0 (Local-Only Folders)
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
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
                        item(key = "header_notes", contentType = "SectionHeader") {
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
                        items(items = unpinnedNotes, key = { it.id }, contentType = { "NoteCard" }) { note ->
                            NoteCard(
                                note = note,
                                showSyncStatus = showSyncStatus,
                                isSelected = note.id in selectedNotes,
                                isSelectionMode = isSelectionMode,
                                timestampTicker = timestampTicker,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                onClick = { if (isSelectionMode) onNoteSelectionToggle(note) else onNoteClick(note) },
                                onLongClick = { onNoteLongPress(note) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// 🆕 collapsible sections: shared section keys (List & Grid, persisted via MainViewModel)
internal const val SECTION_PINNED = "pinned"
internal const val SECTION_FOLDERS = "folders"
internal const val SECTION_NOTES = "notes"

/** 🆕 v2.7.0 (Folders): Sektions-Header mit Collapse-Toggle (List & Grid).
 *  🆕 Section reordering: Long-press öffnet ein Kontextmenü mit Move up/down (Tap deckt Collapse/Expand
 *  bereits ab, daher kein redundanter Menüeintrag dafür).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SectionHeaderText(
    text: String,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {}
) {
    var showReorderMenu by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimensions.SpacingSmall, bottom = Dimensions.SpacingXSmall)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).fillMaxWidth()
        )
        Box(modifier = Modifier.align(Alignment.CenterEnd)) {
            Box(
                modifier = Modifier
                    .size(Dimensions.IconSizeMedium)
                    .combinedClickable(
                        onClick = onToggleCollapse,
                        onLongClick = { if (canMoveUp || canMoveDown) showReorderMenu = true }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                    contentDescription = stringResource(
                        if (collapsed) R.string.action_expand_section else R.string.action_collapse_section,
                        text
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimensions.IconSizeSmall)
                )
            }
            DropdownMenu(expanded = showReorderMenu, onDismissRequest = { showReorderMenu = false }) {
                if (canMoveUp) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_move_section_up)) },
                        onClick = {
                            onMoveUp()
                            showReorderMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) }
                    )
                }
                if (canMoveDown) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_move_section_down)) },
                        onClick = {
                            onMoveDown()
                            showReorderMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) }
                    )
                }
            }
        }
    }
}
