package dev.dettmer.simplenotes.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SyncDisabled
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.Folder
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteFilter
import dev.dettmer.simplenotes.models.SortDirection
import dev.dettmer.simplenotes.models.SortOption
import dev.dettmer.simplenotes.ui.main.components.EmptyState
import dev.dettmer.simplenotes.ui.main.components.NotesList
import dev.dettmer.simplenotes.ui.main.components.NotesStaggeredGrid
import dev.dettmer.simplenotes.ui.theme.NotePreviewLength

// Eine Notiz-/Ordner-Ansicht und die Auswahl-Aktionen, aus MainScreen.kt ausgelagert.

/** 🆕 v1.9.0 (F13): Delay before scrolling to top after manual sync, giving Compose time to recompose with new data. */
private const val SYNC_SCROLL_DELAY_MS = 150L

/**
 * Grid-Top-Settle-Guard: LazyVerticalStaggeredGrid scrollt beim ersten Laden mit FullLine-Items
 * (Pinned-Header/-Body) spontan ein Item nach unten, wodurch der "Angeheftet"-Header oben aus dem
 * Viewport rutscht. Direkt nach dem ersten Erscheinen des Inhalts halten wir das Grid für ein
 * kurzes Fenster auf Index 0, bis sich das Layout gesetzt hat.
 */
private const val GRID_TOP_SETTLE_FRAMES = 20
private const val GRID_TOP_SETTLE_INTERVAL_MS = 30L

/** 🆕 v2.7.0 (Folders): Responsive Selection-TopBar — Icon-Breite & reservierte Breite (Nav + Titel). */
private const val SELECTION_ICON_WIDTH_DP = 48
private const val SELECTION_TITLE_RESERVE_DP = 160

/**
 * 🆕 v2.7.0 (Folders): Eine Notiz-/Ordner-Ansicht (Root oder ein Ordner). Eigener Scroll-State pro
 * AnimatedContent-Slot → Ordnerwechsel startet oben; Zurück-zur-Root zeigt wieder die Ordner.
 * Nur die aktive Pane (isActive) konsumiert die One-Shot-Scroll-Flags.
 *
 * `notes` ist die ordner-unabhängige Liste; jede Pane filtert UND sortiert sich selbst anhand
 * ihres eigenen `folderKey` (via `sortAndPin`), damit die abgehende Pane während der Animation
 * ihren eigenen (korrekten) Inhalt in ihrer eigenen Reihenfolge behält — kein Flackern.
 */
// Abbau: TECH_DEBT_ROADMAP.md Slice 5
@Suppress("LongParameterList", "CyclomaticComplexMethod", "LongMethod") // viele UI-State-Parameter
@Composable
internal fun NotesPane(
    folderKey: String?,
    isActive: Boolean,
    notes: List<Note>,
    sortOption: SortOption, // 🔧 aktive Sortierung — nur Remember-Key, damit die aktive Pane sofort reagiert
    sortDirection: SortDirection, // 🔧 s.o.
    sortAndPin: (List<Note>, String?) -> List<Note>, // 🔧 sortiert+pinnt anhand des EIGENEN folderKey
    sortFoldersFn: (List<Folder>, String?) -> List<Folder>, // 🔧 dito für die Ordner-Section
    displayMode: String,
    folders: List<Folder>,
    folderNoteCounts: Map<String, Int>,
    isServerConfigured: Boolean,
    selectedNotes: Set<String>,
    selectedFolders: Set<String>, // 🆕 v2.7.0 (Folders)
    localOnlyFolderNames: Set<String> = emptySet(), // 🆕 v2.8.0 (Local-Only Folders)
    isSelectionMode: Boolean,
    timestampTicker: Long,
    gridAdaptiveScaling: Boolean,
    gridManualColumns: Int,
    notePreviewLength: NotePreviewLength,
    showNoteTimestamp: Boolean,
    showNoteTypeIcon: Boolean,
    hideCheckedInPreview: Boolean,
    collapsedSectionsForFolder: (String?) -> Set<String>, // 🔧 aktiver Ordner: reaktiv, sonst gespeicherter Satz
    sectionOrder: List<String>, // 🆕 section reordering
    scrollToTop: Boolean,
    syncScrollToTop: Boolean,
    noteFilter: NoteFilter,
    colorFilter: String?,
    showArchived: Boolean = false, // 🆕 v2.11.0 (Archive)
    searchActive: Boolean = false, // 🆕 v2.16.0 (#141): Suche geht über alle Ordner
    onResetScrollToTop: () -> Unit,
    onResetSyncScrollToTop: () -> Unit,
    onEnterFolder: (String) -> Unit,
    onFolderLongPress: (String) -> Unit,
    onFolderSelectionToggle: (String) -> Unit, // 🆕 v2.7.0 (Folders)
    onToggleSection: (String) -> Unit, // 🆕 collapsible sections
    onMoveSection: (String, String?) -> Unit, // 🆕 section reordering
    onOpenNote: (String) -> Unit,
    onStartSelection: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    focusManager: FocusManager
) {
    // Scroll-State bewusst NICHT saveable (kein rememberSaveable): Position bleibt innerhalb
    // der lebenden Komposition erhalten (Notiz öffnen & zurück, Background→Foreground, da der
    // Editor eine eigene Activity ist und MainActivity nur gestoppt, nicht zerstört wird),
    // wird aber NICHT in den savedInstanceState-Bundle geschrieben. Dadurch startet ein
    // Kaltstart (Prozess-Tod) immer ganz oben mit sichtbarem "Angeheftet"-Header.
    val listState = remember(folderKey) { LazyListState() }
    val gridState = remember(folderKey) { LazyStaggeredGridState() }
    // Ordner nur in der Root-Ansicht — und nicht während einer Suche, die ohnehin flach über alles geht
    val foldersForPane = remember(folders, folderKey, showArchived, searchActive, sortOption, sortDirection) {
        if (folderKey == null && !showArchived && !searchActive) sortFoldersFn(folders, folderKey) else emptyList()
    }
    // 🆕 v2.7.0 (Folders): Notizen dieses Slots — eigener folderKey, nicht der gerade aktive Ordner.
    // 🆕 v2.11.0 (Archive): Archiv-Ansicht ist eine flache Liste über alle Ordner.
    // 🆕 v2.16.0 (#141): Suche ebenso — sonst zeigt die Root-Ansicht nur Root-Treffer und
    // verschweigt jeden Treffer in einem Ordner, ohne das irgendwo anzuzeigen.
    // ponytail: sortiert den (kleinen) Ordner-Ausschnitt synchron auf dem Main-Thread — Wechsel auf
    // Dispatchers.Default nur nötig, falls ein einzelner Ordner je Tausende Notizen enthält.
    val paneNotes = remember(notes, folderKey, showArchived, searchActive, sortOption, sortDirection) {
        val filtered =
            if (showArchived || searchActive) notes else notes.filter { it.folderName == folderKey }
        sortAndPin(filtered, folderKey)
    }
    // 🔧 Fix Flash aufgeklappter Sections: aktive Pane liest reaktiv (Live-Toggle), die
    // verschwindende Pane hält ihren eigenen gespeicherten Zustand fest, unabhängig vom Ordner,
    // der die reaktive StateFlow während der Animation bereits überschrieben hat.
    val savedCollapsedSections = remember(folderKey) { collapsedSectionsForFolder(folderKey) }
    val paneCollapsedSections = if (isActive) collapsedSectionsForFolder(folderKey) else savedCollapsedSections

    // Grid-Top-Settle-Guard: das Staggered-Grid scrollt beim ersten Laden spontan ein Item
    // nach unten (Foundation-Quirk mit FullLine-Items) → Pinned-Header verschwindet. Direkt
    // nach Erscheinen des Inhalts kurz auf Index 0 halten, bis das Layout sich gesetzt hat.
    // Läuft nur einmal pro Komposition (Key = folderKey + "hat Inhalt"), stört spätere
    // In-Session-Scrollposition nicht.
    LaunchedEffect(folderKey, paneNotes.isEmpty()) {
        if (isActive && displayMode == "grid" && paneNotes.isNotEmpty()) {
            repeat(GRID_TOP_SETTLE_FRAMES) {
                if (gridState.firstVisibleItemIndex != 0 || gridState.firstVisibleItemScrollOffset != 0) {
                    gridState.scrollToItem(0)
                }
                kotlinx.coroutines.delay(GRID_TOP_SETTLE_INTERVAL_MS)
            }
        }
    }

    LaunchedEffect(scrollToTop) {
        if (isActive && scrollToTop) {
            if (displayMode == "grid") gridState.animateScrollToItem(0) else listState.animateScrollToItem(0)
            onResetScrollToTop()
        }
    }
    LaunchedEffect(syncScrollToTop) {
        if (isActive && syncScrollToTop) {
            kotlinx.coroutines.delay(SYNC_SCROLL_DELAY_MS)
            if (displayMode == "grid") gridState.animateScrollToItem(0) else listState.animateScrollToItem(0)
            onResetSyncScrollToTop()
        }
    }
    var filterSettled by remember { mutableStateOf(false) }
    LaunchedEffect(noteFilter, colorFilter, showArchived) {
        if (!filterSettled) {
            filterSettled = true
        } else {
            gridState.scrollToItem(0)
            listState.scrollToItem(0)
        }
    }

    // 🆕 section reordering: ein Reorder ist eine strukturelle Aktion. Ohne Eingriff verankert
    // das Lazy-Layout den Scroll am Key des ersten sichtbaren Items und "scrollt mit" dem
    // verschobenen Header — sichtbarer Zwei-Frame-Sprung (Anchoring, dann Korrektur). requestScrollToItem
    // registriert Index 0 als Ziel für die NÄCHSTE Messung (die der sectionOrder-Wechsel auslöst),
    // überstimmt das Key-Anchoring im selben Pass → kein Zwischenframe, kein Sprung. Synchron VOR
    // dem State-Wechsel gesetzt, deshalb hier gewrappt statt in einem nachgelagerten LaunchedEffect.
    val onMoveSectionScrolled: (String, String?) -> Unit = { from, to ->
        if (to != null && isActive) {
            if (displayMode == "grid") gridState.requestScrollToItem(0) else listState.requestScrollToItem(0)
        }
        onMoveSection(from, to)
    }

    if (paneNotes.isEmpty() && foldersForPane.isEmpty()) {
        when {
            // 🆕 v2.16.0 (#141): „Erste Notiz anlegen“ ist bei einer ergebnislosen Suche die falsche
            // Auskunft — es gibt Notizen, sie passen nur nicht. Der Text sagt außerdem, dass die
            // Suche wirklich überall war, sonst sucht der Nutzer weiter in anderen Ordnern.
            searchActive -> EmptyState(
                modifier = Modifier.fillMaxSize(),
                title = stringResource(R.string.search_empty_state_title),
                message = stringResource(R.string.search_empty_state_message)
            )

            showArchived -> EmptyState(
                modifier = Modifier.fillMaxSize(),
                title = stringResource(R.string.archive_empty_state_title),
                message = stringResource(R.string.archive_empty_state_message)
            )

            else -> EmptyState(modifier = Modifier.fillMaxSize())
        }
    } else if (displayMode == "grid") {
        NotesStaggeredGrid(
            notes = paneNotes,
            gridState = gridState,
            adaptiveScaling = gridAdaptiveScaling,
            manualColumns = gridManualColumns,
            showSyncStatus = isServerConfigured,
            selectedNoteIds = selectedNotes,
            isSelectionMode = isSelectionMode,
            timestampTicker = timestampTicker,
            previewLength = notePreviewLength,
            showTimestamp = showNoteTimestamp,
            showTypeIcon = showNoteTypeIcon,
            hideCheckedInPreview = hideCheckedInPreview,
            showFolderLabels = searchActive, // 🆕 v2.16.0 (#141)
            modifier = Modifier.fillMaxSize(),
            onNoteClick = { note ->
                focusManager.clearFocus()
                if (isSelectionMode) onToggleSelection(note.id) else onOpenNote(note.id)
            },
            onNoteLongClick = { note ->
                focusManager.clearFocus()
                onStartSelection(note.id)
            },
            folders = foldersForPane,
            folderNoteCounts = folderNoteCounts,
            selectedFolders = selectedFolders,
            localOnlyFolderNames = localOnlyFolderNames,
            onFolderClick = { if (isSelectionMode) onFolderSelectionToggle(it) else onEnterFolder(it) },
            onFolderLongPress = onFolderLongPress,
            onFolderSelectionToggle = onFolderSelectionToggle,
            collapsedSections = paneCollapsedSections,
            onToggleSection = onToggleSection,
            sectionOrder = sectionOrder,
            onMoveSection = onMoveSectionScrolled
        )
    } else {
        NotesList(
            notes = paneNotes,
            showSyncStatus = isServerConfigured,
            selectedNotes = selectedNotes,
            isSelectionMode = isSelectionMode,
            timestampTicker = timestampTicker,
            previewLength = notePreviewLength,
            showTimestamp = showNoteTimestamp,
            showTypeIcon = showNoteTypeIcon,
            hideCheckedInPreview = hideCheckedInPreview,
            showFolderLabels = searchActive, // 🆕 v2.16.0 (#141)
            listState = listState,
            modifier = Modifier.fillMaxSize(),
            folders = foldersForPane,
            folderNoteCounts = folderNoteCounts,
            selectedFolders = selectedFolders,
            localOnlyFolderNames = localOnlyFolderNames,
            onFolderClick = { if (isSelectionMode) onFolderSelectionToggle(it) else onEnterFolder(it) },
            onFolderLongPress = onFolderLongPress,
            onFolderSelectionToggle = onFolderSelectionToggle,
            onNoteClick = { note ->
                focusManager.clearFocus()
                onOpenNote(note.id)
            },
            onNoteLongPress = { note ->
                focusManager.clearFocus()
                onStartSelection(note.id)
            },
            onNoteSelectionToggle = { note -> onToggleSelection(note.id) },
            collapsedSections = paneCollapsedSections,
            onToggleSection = onToggleSection,
            sectionOrder = sectionOrder,
            onMoveSection = onMoveSectionScrolled
        )
    }
}

private data class SelectionAction(
    val icon: ImageVector,
    val label: String,
    val keepPriority: Int,
    val enabled: Boolean,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

/** 🆕 v2.7.0 (Folders): zeigt so viele Action-Icons wie passen, Rest ins ⋮-Overflow-Menü. */
// Abbau: TECH_DEBT_ROADMAP.md Slice 5
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList", "CyclomaticComplexMethod", "LongMethod")
@Composable
internal fun SelectionActions(
    selectedNoteCount: Int,
    selectedFolderCount: Int, // 🆕 v2.7.0 (Folders)
    totalCount: Int,
    allSelectedPinned: Boolean,
    isSelectedFolderLocalOnly: Boolean = false, // 🆕 v2.8.0 (Local-Only Folders)
    isArchiveView: Boolean = false, // 🆕 v2.11.0 (Archive)
    onSelectAll: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit = {}, // 🆕 v2.11.0 (Archive)
    onColorClick: () -> Unit,
    onMoveClick: () -> Unit,
    onRename: () -> Unit, // 🆕 v2.7.0 (Folders)
    onToggleLocalOnly: () -> Unit, // 🆕 v2.8.0 (Local-Only Folders)
    onDeleteSelected: () -> Unit
) {
    val selectedCount = selectedNoteCount + selectedFolderCount
    val anySelected = selectedCount > 0
    val selectAllLabel = stringResource(R.string.action_select_all)
    val pinLabel = stringResource(R.string.action_toggle_pin)
    val archiveLabel = stringResource(
        if (isArchiveView) R.string.action_unarchive else R.string.action_archive
    )
    val colorLabel = stringResource(R.string.action_set_note_color)
    val moveLabel = stringResource(R.string.action_move_to_folder)
    val renameLabel = stringResource(R.string.action_rename_folder)
    val localOnlyLabel = stringResource(
        if (isSelectedFolderLocalOnly) R.string.folder_include_in_sync else R.string.folder_exclude_from_sync
    )
    val deleteLabel = stringResource(R.string.action_delete_selected)

    val actions = buildList {
        if (selectedCount < totalCount) {
            add(SelectionAction(Icons.Default.SelectAll, selectAllLabel, keepPriority = 1, enabled = true, onClick = onSelectAll))
        }
        // Pin: nur wenn Notizen ausgewählt sind
        if (selectedNoteCount > 0) {
            val pinIcon = if (allSelectedPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin
            add(SelectionAction(pinIcon, pinLabel, keepPriority = 4, enabled = true, onClick = onTogglePin))
        }
        // 🆕 v2.11.0 (Archive): nur wenn Notizen ausgewählt sind. LOW keepPriority (0) →
        // wandert auf schmalen Screens als Erstes ins ⋮-Overflow (Leiste nicht überfüllen).
        if (selectedNoteCount > 0) {
            val archiveIcon = if (isArchiveView) Icons.Outlined.Unarchive else Icons.Outlined.Archive
            add(SelectionAction(archiveIcon, archiveLabel, keepPriority = 0, enabled = true, onClick = onToggleArchive))
        }
        // Color: für Notizen und Ordner
        add(SelectionAction(Icons.Outlined.Palette, colorLabel, keepPriority = 2, enabled = anySelected, onClick = onColorClick))
        // Move: nur wenn Notizen ausgewählt sind
        if (selectedNoteCount > 0) {
            add(SelectionAction(Icons.Filled.Folder, moveLabel, keepPriority = 3, enabled = true, onClick = onMoveClick))
        }
        // Rename: genau 1 Ordner, 0 Notizen
        if (selectedFolderCount == 1 && selectedNoteCount == 0) {
            add(SelectionAction(Icons.Default.Edit, renameLabel, keepPriority = 2, enabled = true, onClick = onRename))
        }
        // Local-only toggle: mind. 1 Ordner, 0 Notizen (Mehrfachauswahl erlaubt)
        if (selectedFolderCount >= 1 && selectedNoteCount == 0) {
            val icon = if (isSelectedFolderLocalOnly) Icons.Outlined.Sync else Icons.Outlined.SyncDisabled
            add(SelectionAction(icon, localOnlyLabel, keepPriority = 3, enabled = true, onClick = onToggleLocalOnly))
        }
        add(
            SelectionAction(
                Icons.Default.Delete,
                deleteLabel,
                keepPriority = 5,
                enabled = anySelected,
                isDestructive = true,
                onClick = onDeleteSelected
            )
        )
    }

    // Verfügbare Icon-Slots aus der Bildschirmbreite ableiten (recomposed bei Rotation).
    val screenWidthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value.toInt() }
    val budget = (screenWidthDp - SELECTION_TITLE_RESERVE_DP).coerceAtLeast(SELECTION_ICON_WIDTH_DP)
    val maxIcons = (budget / SELECTION_ICON_WIDTH_DP).coerceAtLeast(1)

    val (visible, overflow) = if (actions.size <= maxIcons) {
        actions to emptyList()
    } else {
        val visibleCount = (maxIcons - 1).coerceAtLeast(1) // ein Slot fürs ⋮-Menü
        val keepIdx = actions.indices.sortedByDescending { actions[it].keepPriority }.take(visibleCount).toSet()
        actions.filterIndexed { i, _ -> i in keepIdx } to actions.filterIndexed { i, _ -> i !in keepIdx }
    }

    visible.forEach { a ->
        IconButton(onClick = a.onClick, enabled = a.enabled) {
            Icon(
                imageVector = a.icon,
                contentDescription = a.label,
                tint = if (a.isDestructive && a.enabled) MaterialTheme.colorScheme.error else LocalContentColor.current
            )
        }
    }
    if (overflow.isNotEmpty()) {
        var expanded by remember { mutableStateOf(false) }
        IconButton(onClick = { expanded = true }) {
            Icon(imageVector = Icons.Default.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            overflow.forEach { a ->
                DropdownMenuItem(
                    text = { Text(a.label) },
                    enabled = a.enabled,
                    leadingIcon = { Icon(a.icon, contentDescription = null) },
                    onClick = {
                        expanded = false
                        a.onClick()
                    }
                )
            }
        }
    }
}
