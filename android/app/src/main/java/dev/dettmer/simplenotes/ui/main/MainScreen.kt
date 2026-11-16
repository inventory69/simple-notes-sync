package dev.dettmer.simplenotes.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.Folder
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.models.SortDirection
import dev.dettmer.simplenotes.models.SortOption
import dev.dettmer.simplenotes.sync.SyncStateManager
import dev.dettmer.simplenotes.ui.main.components.CreateFolderDialog
import dev.dettmer.simplenotes.ui.main.components.DeleteSelectionDialog
import dev.dettmer.simplenotes.ui.main.components.ExcludeFolderSyncSheet
import dev.dettmer.simplenotes.ui.main.components.FilterChipRow
import dev.dettmer.simplenotes.ui.main.components.MoveToFolderSheet
import dev.dettmer.simplenotes.ui.main.components.NoteColorPickerSheet
import dev.dettmer.simplenotes.ui.main.components.NoteTypeFAB
import dev.dettmer.simplenotes.ui.main.components.RenameFolderDialog
import dev.dettmer.simplenotes.ui.main.components.SearchTopBar
import dev.dettmer.simplenotes.ui.main.components.SortDialog
import dev.dettmer.simplenotes.ui.main.components.SyncCloudButton
import dev.dettmer.simplenotes.ui.main.components.SyncProgressBanner
import dev.dettmer.simplenotes.ui.main.components.SyncStatusDialog
import dev.dettmer.simplenotes.ui.theme.LocalNewDesign
import dev.dettmer.simplenotes.utils.ActivityLog
import kotlinx.coroutines.launch

private const val TIMESTAMP_UPDATE_INTERVAL_MS = 30_000L

// UI-Beta: Deckkraft der Übersicht, solange das Suchfeld aktiv und noch leer ist
private const val SEARCH_DIM_ALPHA = 0.45f

/**
 * Main screen displaying the notes list
 * v1.5.0: Jetpack Compose MainActivity Redesign
 *
 * Performance optimized with proper state handling:
 * - LazyListState for scroll control
 * - Scaffold FAB slot for proper z-ordering
 * - Scroll-to-top on new note
 */
// Abbau: TECH_DEBT_ROADMAP.md Slice 5
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod", "CyclomaticComplexMethod") // 🔧 v2.5.0: color picker state + sheet push over limit
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenNote: (String?) -> Unit,
    onOpenSettings: (String?) -> Unit, // 🆕 v2.19.0: Startseite der Einstellungen (null = Übersicht)
    onCreateNote: (NoteType, String?) -> Unit
) {
    // 🆕 v2.7.0 (Folders): ordner-unabhängige Liste; jede Pane filtert selbst nach ihrem folderKey.
    val notes by viewModel.sortedNotesUnfoldered.collectAsState()
    val syncState by viewModel.sync.syncState.collectAsState()
    val scrollToTop by viewModel.scrollToTop.collectAsState()
    // 🆕 v1.9.0 (F13): Scroll-to-top after manual sync
    val syncScrollToTop by viewModel.sync.syncCompletedScrollToTop.collectAsState()

    // 🆕 v1.8.0: Einziges Banner-System
    val syncProgress by viewModel.sync.syncProgress.collectAsState()

    // Multi-Select State
    val selectedNotes by viewModel.selection.selectedNotes.collectAsState()
    val selectedFolders by viewModel.selection.selectedFolders.collectAsState() // 🆕 v2.7.0 (Folders)
    val isSelectionMode by viewModel.selection.isSelectionMode.collectAsState()

    // 🆕 v2.7.0 (Folders): folder state
    val currentFolder by viewModel.folders.currentFolder.collectAsState()
    val folders by viewModel.folders.list.collectAsState()
    val folderNoteCounts by viewModel.folderNoteCounts.collectAsState()
    val localOnlyFolderNames by viewModel.folders.localOnlyFolderNames.collectAsState() // 🆕 v2.8.0 (Local-Only Folders)

    // Back press handler for selection mode
    BackHandler(enabled = isSelectionMode) {
        viewModel.selection.clearSelection()
    }
    BackHandler(enabled = !isSelectionMode && currentFolder != null) {
        viewModel.folders.goToRoot()
    }

    val isServerConfigured by viewModel.isServerConfigured.collectAsState()
    val syncStatusSummary by viewModel.sync.syncStatusSummary.collectAsState() // 🆕 v2.19.0
    val newDesign = LocalNewDesign.current // UI-Beta: Übersicht (U1-U7)
    val showSyncStatus by viewModel.sync.showSyncStatus.collectAsState() // 🆕 v2.19.0

    // 🎨 v1.7.0: Display mode (list or grid)
    val displayMode by viewModel.display.displayMode.collectAsState()
    // 🆕 v2.1.0 (F46): Grid column control
    val gridAdaptiveScaling by viewModel.display.gridAdaptiveScaling.collectAsState()
    val gridManualColumns by viewModel.display.gridManualColumns.collectAsState()
    // 🆕 v2.11.0: Note preview length preset (List + Grid)
    val notePreviewLength by viewModel.display.notePreviewLength.collectAsState()
    // 🆕 Issue #100: Zeitstempel/Icon auf Notizkarten ausblendbar
    val showNoteTimestamp by viewModel.display.showNoteTimestamp.collectAsState()
    val showNoteTypeIcon by viewModel.display.showNoteTypeIcon.collectAsState()
    val hideCheckedInPreview by viewModel.display.hideCheckedInPreview.collectAsState()
    // 🆕 collapsible sections (Pinned / Folders / Notes)
    val collapsedSections by viewModel.display.collapsedSections.collectAsState()
    // 🆕 section reordering
    val sectionOrder by viewModel.display.sectionOrder.collectAsState()
    // 🆕 v1.9.0 (F05): Custom App Title
    val customAppTitle by viewModel.display.customAppTitle.collectAsState()

    // Delete confirmation dialog state
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    // 🆕 v2.5.0: Bulk color-picker dialog
    var showBatchColorPicker by remember { mutableStateOf(false) }
    // 🆕 v2.7.0 (Folders): folder dialogs
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showMoveSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    // 🆕 v2.8.0 (Local-Only Folders): Auswahl "Server behalten / entfernen" beim Ausschließen
    var showExcludeSyncSheet by remember { mutableStateOf(false) }

    // 🔀 v1.8.0: Sort dialog state
    var showSortDialog by remember { mutableStateOf(false) }
    // 🆕 v1.9.0 (F11): Filter row visibility toggle (default: hidden)
    var showFilterRow by remember { mutableStateOf(false) }
    val sortOption by viewModel.query.sortOption.collectAsState()
    val sortDirection by viewModel.query.sortDirection.collectAsState()
    // 🆕 v1.9.0 (F06): Note filter state
    val noteFilter by viewModel.query.noteFilter.collectAsState()
    // 🆕 v1.9.0 (F10): Search query state
    val searchQuery by viewModel.query.searchQuery.collectAsState()
    // UI-Beta: Tippen ins Suchfeld lässt die Übersicht zurücktreten, bis der erste Buchstabe filtert.
    var searchFieldActive by remember { mutableStateOf(false) }
    val paneAlpha by animateFloatAsState(
        targetValue = if (newDesign && searchFieldActive && searchQuery.isEmpty()) SEARCH_DIM_ALPHA else 1f,
        label = "searchDim"
    )
    // 🆕 v2.16.0 (#141): Suche läuft → ordnerübergreifende, flache Trefferliste (siehe NotesPane)
    val searchActive by viewModel.searchActive.collectAsState()
    // 🆕 v2.5.0: Farbfilter-State
    val colorFilter by viewModel.query.colorFilter.collectAsState()
    val availableColors by viewModel.availableColors.collectAsState()
    val showArchived by viewModel.query.showArchived.collectAsState() // 🆕 v2.11.0 (Archive)
    val focusManager = LocalFocusManager.current

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // ⏱️ Timestamp ticker - increments every 30 seconds to trigger recomposition of relative times
    var timestampTicker by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(TIMESTAMP_UPDATE_INTERVAL_MS)
            timestampTicker = System.currentTimeMillis()
        }
    }

    // Compute isSyncing once
    val isSyncing = syncState == SyncStateManager.SyncState.SYNCING

    val isSyncAvailable = isServerConfigured
    val canSync = isSyncAvailable && !isSyncing

    // Handle snackbar events from ViewModel
    LaunchedEffect(Unit) {
        viewModel.showSnackbar.collect { data ->
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = data.message,
                    actionLabel = data.actionLabel,
                    duration = if (data.actionLabel != null || data.longDuration) {
                        SnackbarDuration.Long
                    } else {
                        SnackbarDuration.Short
                    }
                )
                if (result == SnackbarResult.ActionPerformed) {
                    data.onAction?.invoke()
                }
            }
        }
    }

    // v1.5.0 Hotfix: FAB manuell mit zIndex platzieren für garantierte Sichtbarkeit
    // 🆕 v1.11.0: Äußere Box — ermöglicht NoteTypeFAB als Fullscreen-Overlay über dem Scaffold
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                // Animated switch between normal and selection TopBar
                AnimatedVisibility(
                    visible = isSelectionMode,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    // 🆕 v2.8.0 (Local-Only Folders): alle selektierten Ordner bereits ausgeschlossen?
                    val selectedAllLocalOnly = selectedFolders.isNotEmpty() &&
                        selectedFolders.all { it in localOnlyFolderNames }
                    SelectionTopBar(
                        selectedNoteCount = selectedNotes.size,
                        selectedFolderCount = selectedFolders.size,
                        totalCount = notes.size +
                            (if (currentFolder == null && !showArchived && !searchActive) folders.size else 0),
                        allSelectedPinned = notes.filter { it.id in selectedNotes }.all { it.isPinned == true },
                        isSelectedFolderLocalOnly = selectedAllLocalOnly,
                        isArchiveView = showArchived, // 🆕 v2.11.0 (Archive)
                        onCloseSelection = { viewModel.selection.clearSelection() },
                        onSelectAll = { viewModel.selection.selectAll() },
                        onTogglePinSelected = { viewModel.togglePinForSelected() },
                        onToggleArchiveSelected = { viewModel.toggleArchiveForSelected() }, // 🆕 v2.11.0 (Archive)
                        onColorClick = { showBatchColorPicker = true },
                        onMoveClick = { showMoveSheet = true },
                        onRename = { showRenameDialog = true },
                        onToggleLocalOnly = {
                            when {
                                selectedAllLocalOnly -> viewModel.folders.includeFoldersInSync(selectedFolders)
                                // Server konfiguriert → User entscheidet über die Server-Kopien
                                isServerConfigured -> showExcludeSyncSheet = true
                                else -> viewModel.folders.excludeFoldersFromSync(selectedFolders, removeFromServer = false)
                            }
                        },
                        // 🆕 v2.9.0 (Trash): reine Notiz-Auswahl wandert direkt in den Papierkorb
                        // (kein Bestätigungs-Sheet); nur bei Ordnern im Spiel erscheint der Dialog.
                        onDeleteSelected = {
                            if (selectedFolders.isNotEmpty()) {
                                showBatchDeleteDialog = true
                            } else {
                                viewModel.trash.moveSelectedToTrash()
                            }
                        }
                    )
                }
                AnimatedVisibility(
                    visible = !isSelectionMode,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    if (newDesign) {
                        // UI-Beta (U5/U6): Suchfeld oben, App-Titel bzw. Ordnername als Ruhetext.
                        SearchTopBar(
                            placeholder = currentFolder ?: customAppTitle.ifBlank { stringResource(R.string.main_title) },
                            searchQuery = searchQuery,
                            onSearchQueryChanged = { viewModel.query.setSearchQuery(it) },
                            showFilterRow = showFilterRow,
                            onFilterToggle = { showFilterRow = !showFilterRow },
                            onBack = if (currentFolder != null) ({ viewModel.folders.goToRoot() }) else null,
                            onSettingsClick = { onOpenSettings(null) },
                            onSearchingChange = { searchFieldActive = it },
                            syncAction = {
                                SyncCloudButton(
                                    state = syncStatusSummary.state,
                                    // Auch der stille Sync beim Öffnen: der Ring zeigt, dass gerade abgeglichen wird.
                                    isSyncing = isSyncing || syncState == SyncStateManager.SyncState.SYNCING_SILENT,
                                    badgeCount = syncStatusSummary.badgeCount,
                                    onClick = if (isSyncAvailable) ({ viewModel.sync.openSyncStatus() }) else null
                                )
                            }
                        )
                    } else if (currentFolder != null) {
                        FolderTopBar(
                            folderName = currentFolder!!,
                            onBack = { viewModel.folders.goToRoot() },
                            syncEnabled = canSync,
                            onSyncLegendClick = if (isSyncAvailable) ({ viewModel.sync.openSyncStatus() }) else null,
                            syncLegendBadgeCount = syncStatusSummary.badgeCount, // 🆕 v2.19.0
                            showFilterRow = showFilterRow,
                            onFilterToggle = { showFilterRow = !showFilterRow },
                            onSyncClick = { viewModel.sync.triggerManualSync(ActivityLog.Trigger.TOOLBAR) },
                            onSettingsClick = { onOpenSettings(null) }
                        )
                    } else {
                        MainTopBar(
                            customTitle = customAppTitle, // 🆕 v1.9.0 (F05)
                            syncEnabled = canSync,
                            onSyncLegendClick = if (isSyncAvailable) ({ viewModel.sync.openSyncStatus() }) else null,
                            syncLegendBadgeCount = syncStatusSummary.badgeCount, // 🆕 v2.19.0
                            // 🆕 v1.9.0 (F11): Sort button replaced by filter row toggle
                            showFilterRow = showFilterRow,
                            onFilterToggle = { showFilterRow = !showFilterRow },
                            onSyncClick = { viewModel.sync.triggerManualSync(ActivityLog.Trigger.TOOLBAR) },
                            onSettingsClick = { onOpenSettings(null) }
                        )
                    }
                }
            },
            // FAB liegt als Fullscreen-Overlay außerhalb des Scaffolds (siehe NoteTypeFAB-Block
            // weiter unten). Das Scaffold kennt den FAB nicht und kann die Snackbar nicht
            // automatisch darüber anheben. 72.dp = 56.dp (Material Standard-FAB-Höhe) +
            // 16.dp (Column bottom padding in NoteTypeFAB).
            // Kein navigationBarsPadding() hier — das Scaffold konsumiert die Navbar-Insets
            // für seinen Layout-Bereich; innerhalb des snackbarHost-Slots wäre der Modifier
            // ein No-Op und würde die Snackbar fälschlicherweise doppelt anheben.
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .padding(bottom = 72.dp)
                )
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { paddingValues ->
            // 🌟 v1.6.0: PullToRefreshBox only enabled when sync available
            PullToRefreshBox(
                isRefreshing = isSyncing,
                onRefresh = { if (isSyncAvailable) viewModel.sync.triggerManualSync(ActivityLog.Trigger.PULL_REFRESH) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Main content column
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 🆕 v1.8.0: Einziges Sync Banner (Progress + Ergebnis)
                        SyncProgressBanner(
                            progress = syncProgress,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 🆕 v1.9.0 (F06): Filter Chip Row
                        // 🆕 v1.9.0 (F10): + Inline search field
                        // 🆕 v1.9.0 (F11): + Sort chip + toggle visibility
                        AnimatedVisibility(
                            visible = showFilterRow,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            FilterChipRow(
                                currentFilter = noteFilter,
                                onFilterSelected = { viewModel.query.setNoteFilter(it) },
                                currentColorFilter = colorFilter, // 🆕 v2.5.0
                                onColorFilterSelected = { viewModel.query.setColorFilter(it) }, // 🆕 v2.5.0
                                availableColors = availableColors, // 🆕 v2.5.0
                                archiveActive = showArchived, // 🆕 v2.11.0 (Archive)
                                onArchiveToggle = { viewModel.setShowArchived(!showArchived) }, // 🆕 v2.11.0 (Archive)
                                searchQuery = searchQuery,
                                onSearchQueryChanged = { viewModel.query.setSearchQuery(it) },
                                onSortClick = { showSortDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                showSearchField = !newDesign
                            )
                        }

                        // 🆕 v2.7.0 (Folders): Ordner-Navigation mit Shared-Axis-Animation (wie Notiz öffnen).
                        // 🔧 Fix Flash beim Ordnerwechsel: jede Pane sortiert sich anhand IHRES EIGENEN
                        // folderKey selbst (statt der einen globalen, aktiven Sortierung zu vertrauen) —
                        // sonst übernimmt die gerade verschwindende Pane während der Animation kurz die
                        // Sortierung des neuen Ordners. Aktiver Ordner nutzt die reaktiven StateFlows
                        // (live-Update bei Sortierdialog); jeder andere Ordner liest seine eigene
                        // gespeicherte Einstellung.
                        val sortSettingsForPane: (String?) -> Pair<SortOption, SortDirection> = { folderKey ->
                            if (folderKey == currentFolder) {
                                sortOption to sortDirection
                            } else {
                                viewModel.query.sortSettingsFor(folderKey)
                            }
                        }
                        val sortAndPinForFolder: (List<Note>, String?) -> List<Note> = { list, folderKey ->
                            val (option, direction) = sortSettingsForPane(folderKey)
                            val sorted = sortNotes(list, option, direction)
                            sorted.filter { it.isPinned == true } + sorted.filter { it.isPinned != true }
                        }
                        // Ordner-Section folgt derselben Sortierung wie die Notizen der Pane.
                        val sortFoldersForFolder: (List<Folder>, String?) -> List<Folder> = { list, folderKey ->
                            val (option, direction) = sortSettingsForPane(folderKey)
                            sortFolders(list, option, direction)
                        }
                        // 🔧 Fix Flash aufgeklappter Sections: analog sortAndPinForFolder — aktiver Ordner
                        // nutzt die reaktive StateFlow (Live-Toggle), jeder andere seinen eigenen
                        // gespeicherten Zustand, damit die verschwindende Pane während der Animation
                        // stabil bleibt.
                        val collapsedSectionsForFolder: (String?) -> Set<String> = { folderKey ->
                            if (folderKey == currentFolder) collapsedSections else viewModel.display.collapsedSectionsFor(folderKey)
                        }
                        AnimatedContent(
                            targetState = currentFolder,
                            transitionSpec = { folderNavTransition(forward = targetState != null) },
                            label = "folderNav",
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer { alpha = paneAlpha }
                        ) { folderKey ->
                            NotesPane(
                                folderKey = folderKey,
                                isActive = folderKey == currentFolder,
                                notes = notes,
                                sortOption = sortOption,
                                sortDirection = sortDirection,
                                sortAndPin = sortAndPinForFolder,
                                sortFoldersFn = sortFoldersForFolder,
                                displayMode = displayMode,
                                folders = folders,
                                folderNoteCounts = folderNoteCounts,
                                isServerConfigured = isServerConfigured,
                                selectedNotes = selectedNotes,
                                selectedFolders = selectedFolders,
                                localOnlyFolderNames = localOnlyFolderNames, // 🆕 v2.8.0 (Local-Only Folders)
                                isSelectionMode = isSelectionMode,
                                timestampTicker = timestampTicker,
                                gridAdaptiveScaling = gridAdaptiveScaling,
                                gridManualColumns = gridManualColumns,
                                notePreviewLength = notePreviewLength,
                                showNoteTimestamp = showNoteTimestamp,
                                showNoteTypeIcon = showNoteTypeIcon,
                                hideCheckedInPreview = hideCheckedInPreview,
                                collapsedSectionsForFolder = collapsedSectionsForFolder,
                                sectionOrder = sectionOrder,
                                scrollToTop = scrollToTop,
                                syncScrollToTop = syncScrollToTop,
                                noteFilter = noteFilter,
                                colorFilter = colorFilter,
                                showArchived = showArchived, // 🆕 v2.11.0 (Archive)
                                searchActive = searchActive, // 🆕 v2.16.0 (#141)
                                onResetScrollToTop = { viewModel.resetScrollToTop() },
                                onResetSyncScrollToTop = { viewModel.sync.resetSyncCompletedScrollToTop() },
                                onEnterFolder = { viewModel.folders.enterFolder(it) },
                                onFolderLongPress = { viewModel.selection.startSelectionWithFolder(it) },
                                onFolderSelectionToggle = { viewModel.selection.toggleFolderSelection(it) },
                                onToggleSection = { viewModel.display.toggleSectionCollapsed(it) },
                                onMoveSection = { from, to -> viewModel.display.swapSections(from, to) },
                                onOpenNote = { onOpenNote(it) },
                                onStartSelection = { viewModel.selection.startSelectionMode(it) },
                                onToggleSelection = { viewModel.selection.toggleNoteSelection(it) },
                                focusManager = focusManager
                            )
                        }
                    }

                    // FAB ist jetzt außerhalb des Scaffolds als Fullscreen-Overlay — siehe unten
                }
            }
            if (showBatchDeleteDialog) {
                // 🆕 v2.9.0 (Trash): nur noch der Ordner-Branch; Notizen-only löscht direkt (siehe oben).
                val hasNonEmptyFolders = selectedFolders.any { (folderNoteCounts[it] ?: 0) > 0 }
                DeleteSelectionDialog(
                    noteCount = selectedNotes.size,
                    folderCount = selectedFolders.size,
                    hasNonEmptyFolders = hasNonEmptyFolders,
                    onDismiss = { showBatchDeleteDialog = false },
                    onConfirm = { keep ->
                        viewModel.trash.deleteSelection(keepContainedNotes = keep)
                        showBatchDeleteDialog = false
                    }
                )
            }

            // 🆕 v2.19.0: Sync-Status-Dialog (vorher nur Symbol-Legende)
            if (showSyncStatus) {
                SyncStatusDialog(
                    summary = syncStatusSummary,
                    // Auch still: ein Retry während eines stillen Syncs promotet ihn nur.
                    isSyncing = syncState == SyncStateManager.SyncState.SYNCING ||
                        syncState == SyncStateManager.SyncState.SYNCING_SILENT,
                    // ponytail: zählt als Toolbar-Sync (beides „vom Nutzer"); eigener Trigger, falls die Rohdaten das trennen müssen
                    onRetry = { viewModel.sync.triggerManualSync(ActivityLog.Trigger.TOOLBAR) },
                    onOpenNote = { id ->
                        viewModel.sync.closeSyncStatus()
                        onOpenNote(id)
                    },
                    onOpenSettings = { route ->
                        viewModel.sync.closeSyncStatus()
                        onOpenSettings(route)
                    },
                    onDismiss = { viewModel.sync.closeSyncStatus() }
                )
            }

            // 🔀 v1.8.0: Sort Dialog
            if (showSortDialog) {
                SortDialog(
                    currentOption = sortOption,
                    currentDirection = sortDirection,
                    onOptionSelected = { option ->
                        viewModel.query.setSortOption(option)
                    },
                    onDirectionToggled = {
                        viewModel.query.toggleSortDirection()
                    },
                    onResetToDefault = { viewModel.query.resetSortToDefault() },
                    onDismiss = { showSortDialog = false }
                )
            }
        } // end Scaffold

        // 🆕 v2.5.0: Einheitliche Farbe der Selektion für den Batch-ColorPicker:
        // 1 Notiz oder alle Notizen gleiche Farbe → diese Farbe anzeigen
        // Gemischte Farben oder leere Selektion → null (kein Highlight)
        val selectedDisplayColor: String? by remember {
            derivedStateOf {
                val noteColors = notes.filter { it.id in selectedNotes }.map { it.color }
                val folderColors = folders.filter { it.name in selectedFolders }.map { it.color }
                (noteColors + folderColors).distinct().singleOrNull()
            }
        }

        // 🆕 v2.5.0: Bulk colour picker — shown as overlay above Scaffold
        if (showBatchColorPicker) {
            NoteColorPickerSheet(
                currentColor = selectedDisplayColor,
                onColorSelected = { hex ->
                    viewModel.setColorForSelected(hex)
                    showBatchColorPicker = false
                },
                onDismiss = { showBatchColorPicker = false }
            )
        }

        // 🆕 v2.7.0 (Folders): Folder dialogs/sheet
        if (showCreateFolderDialog) {
            CreateFolderDialog(
                showLocalOnlyOption = isServerConfigured, // 🆕 v2.8.0 (Local-Only Folders)
                onConfirm = { name, localOnly ->
                    viewModel.folders.createFolder(name, localOnly)
                    showCreateFolderDialog = false
                },
                onDismiss = { showCreateFolderDialog = false }
            )
        }
        // 🆕 v2.8.0 (Local-Only Folders): Auswahl was mit den Server-Kopien passieren soll
        if (showExcludeSyncSheet) {
            ExcludeFolderSyncSheet(
                onRemoveFromServer = {
                    viewModel.folders.excludeFoldersFromSync(selectedFolders, removeFromServer = true)
                    showExcludeSyncSheet = false
                },
                onKeepOnServer = {
                    viewModel.folders.excludeFoldersFromSync(selectedFolders, removeFromServer = false)
                    showExcludeSyncSheet = false
                },
                onDismiss = { showExcludeSyncSheet = false }
            )
        }
        if (showMoveSheet) {
            MoveToFolderSheet(
                folders = folders,
                currentFolder = currentFolder,
                onMoveToRoot = {
                    viewModel.folders.moveSelectedNotesTo(null)
                    showMoveSheet = false
                },
                onMoveToFolder = { f ->
                    viewModel.folders.moveSelectedNotesTo(f)
                    showMoveSheet = false
                },
                onCreateFolder = { name -> viewModel.folders.createFolder(name) },
                onDismiss = { showMoveSheet = false }
            )
        }
        // 🆕 v2.7.0 (Folders): Rename-Dialog für genau 1 selektierten Ordner
        if (showRenameDialog) {
            val renameTarget = selectedFolders.singleOrNull()
            if (renameTarget != null) {
                RenameFolderDialog(
                    currentName = renameTarget,
                    existingNames = folders.map { it.name },
                    onConfirm = { newName ->
                        viewModel.folders.renameFolder(renameTarget, newName)
                        showRenameDialog = false
                    },
                    onDismiss = { showRenameDialog = false }
                )
            } else {
                showRenameDialog = false
            }
        }

        // 🆕 v1.11.0: FAB als Fullscreen-Overlay ÜBER dem Scaffold — Scrim deckt Statusbar ab
        // 🆕 v2.11.0 (Archive): FAB im Archiv ausgeblendet (Archiv legt keine neuen Notizen an).
        AnimatedVisibility(
            visible = !isSelectionMode && !showArchived,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxSize()
                .zIndex(Float.MAX_VALUE)
        ) {
            NoteTypeFAB(
                showCreateFolder = currentFolder == null,
                onCreateNote = { type -> onCreateNote(type, currentFolder) },
                onCreateFolder = { showCreateFolderDialog = true }
            )
        }
    } // end outer Box
}
