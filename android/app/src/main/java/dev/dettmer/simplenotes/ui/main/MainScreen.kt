package dev.dettmer.simplenotes.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteFilter
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.sync.SyncStateManager
import dev.dettmer.simplenotes.ui.main.components.CreateFolderDialog
import dev.dettmer.simplenotes.ui.main.components.DeleteConfirmationDialog
import dev.dettmer.simplenotes.ui.main.components.DeleteSelectionDialog
import dev.dettmer.simplenotes.ui.main.components.EmptyState
import dev.dettmer.simplenotes.ui.main.components.FilterChipRow
import dev.dettmer.simplenotes.ui.main.components.MoveToFolderSheet
import dev.dettmer.simplenotes.ui.main.components.NoteColorPickerSheet
import dev.dettmer.simplenotes.ui.main.components.RenameFolderDialog
import dev.dettmer.simplenotes.ui.main.components.NoteTypeFAB
import dev.dettmer.simplenotes.ui.main.components.NotesList
import dev.dettmer.simplenotes.ui.main.components.NotesStaggeredGrid
import dev.dettmer.simplenotes.ui.main.components.SortDialog
import dev.dettmer.simplenotes.ui.main.components.SyncProgressBanner
import dev.dettmer.simplenotes.ui.main.components.SyncStatusLegendDialog
import kotlinx.coroutines.launch

private const val TIMESTAMP_UPDATE_INTERVAL_MS = 30_000L

/** 🆕 v1.9.0 (F13): Delay before scrolling to top after manual sync, giving Compose time to recompose with new data. */
private const val SYNC_SCROLL_DELAY_MS = 150L

/** 🆕 v2.7.0 (Folders): Dauer/Versatz der Ordner-Navigations-Animation (analog shared_axis_x, 200 ms). */
private const val FOLDER_ANIM_DURATION_MS = 200
private const val FOLDER_SLIDE_FRACTION = 0.3f

/** 🆕 v2.7.0 (Folders): Responsive Selection-TopBar — Icon-Breite & reservierte Breite (Nav + Titel). */
private const val SELECTION_ICON_WIDTH_DP = 48
private const val SELECTION_TITLE_RESERVE_DP = 160

/**
 * Main screen displaying the notes list
 * v1.5.0: Jetpack Compose MainActivity Redesign
 *
 * Performance optimized with proper state handling:
 * - LazyListState for scroll control
 * - Scaffold FAB slot for proper z-ordering
 * - Scroll-to-top on new note
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod") // 🔧 v2.5.0: color picker state + sheet push over limit
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenNote: (String?) -> Unit,
    onOpenSettings: () -> Unit,
    onCreateNote: (NoteType, String?) -> Unit
) {
    // 🆕 v2.7.0 (Folders): ordner-unabhängige Liste; jede Pane filtert selbst nach ihrem folderKey.
    val notes by viewModel.sortedNotesUnfoldered.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val scrollToTop by viewModel.scrollToTop.collectAsState()
    // 🆕 v1.9.0 (F13): Scroll-to-top after manual sync
    val syncScrollToTop by viewModel.syncCompletedScrollToTop.collectAsState()

    // 🆕 v1.8.0: Einziges Banner-System
    val syncProgress by viewModel.syncProgress.collectAsState()

    // Multi-Select State
    val selectedNotes by viewModel.selectedNotes.collectAsState()
    val selectedFolders by viewModel.selectedFolders.collectAsState() // 🆕 v2.7.0 (Folders)
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()

    // 🆕 v2.7.0 (Folders): folder state
    val currentFolder by viewModel.currentFolder.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val folderNoteCounts by viewModel.folderNoteCounts.collectAsState()

    // Back press handler for selection mode
    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }
    BackHandler(enabled = !isSelectionMode && currentFolder != null) {
        viewModel.goToRoot()
    }

    // 🌟 v1.6.0: Reactive offline mode state
    val isOfflineMode by viewModel.isOfflineMode.collectAsState()
    val isServerConfigured by viewModel.isServerConfigured.collectAsState()

    // 🎨 v1.7.0: Display mode (list or grid)
    val displayMode by viewModel.displayMode.collectAsState()
    // 🆕 v2.1.0 (F46): Grid column control
    val gridAdaptiveScaling by viewModel.gridAdaptiveScaling.collectAsState()
    val gridManualColumns by viewModel.gridManualColumns.collectAsState()
    // 🆕 v1.9.0 (F05): Custom App Title
    val customAppTitle by viewModel.customAppTitle.collectAsState()

    // Delete confirmation dialog state
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    // 🆕 v2.5.0: Bulk color-picker dialog
    var showBatchColorPicker by remember { mutableStateOf(false) }
    // 🆕 v2.7.0 (Folders): folder dialogs
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showMoveSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    // 🆕 v1.8.0: Sync status legend dialog
    var showSyncLegend by remember { mutableStateOf(false) }

    // 🔀 v1.8.0: Sort dialog state
    var showSortDialog by remember { mutableStateOf(false) }
    // 🆕 v1.9.0 (F11): Filter row visibility toggle (default: hidden)
    var showFilterRow by remember { mutableStateOf(false) }
    val sortOption by viewModel.sortOption.collectAsState()
    val sortDirection by viewModel.sortDirection.collectAsState()
    // 🆕 v1.9.0 (F06): Note filter state
    val noteFilter by viewModel.noteFilter.collectAsState()
    // 🆕 v1.9.0 (F10): Search query state
    val searchQuery by viewModel.searchQuery.collectAsState()
    // 🆕 v2.5.0: Farbfilter-State
    val colorFilter by viewModel.colorFilter.collectAsState()
    val availableColors by viewModel.availableColors.collectAsState()
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

    // 🌟 v1.6.0: Reactive sync availability (recomposes when offline mode changes)
    // Note: isOfflineMode is updated via StateFlow from MainViewModel.refreshOfflineModeState()
    // which is called in ComposeMainActivity.onResume() when returning from Settings
    val hasServerConfig = viewModel.hasServerConfig()
    val isSyncAvailable = !isOfflineMode && hasServerConfig
    val canSync = isSyncAvailable && !isSyncing

    // Handle snackbar events from ViewModel
    LaunchedEffect(Unit) {
        viewModel.showSnackbar.collect { data ->
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = data.message,
                    actionLabel = data.actionLabel,
                    duration = if (data.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short
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
                    SelectionTopBar(
                        selectedNoteCount = selectedNotes.size,
                        selectedFolderCount = selectedFolders.size,
                        totalCount = notes.size + (if (currentFolder == null) folders.size else 0),
                        allSelectedPinned = notes.filter { it.id in selectedNotes }.all { it.isPinned == true },
                        onCloseSelection = { viewModel.clearSelection() },
                        onSelectAll = { viewModel.selectAll() },
                        onTogglePinSelected = { viewModel.togglePinForSelected() },
                        onColorClick = { showBatchColorPicker = true },
                        onMoveClick = { showMoveSheet = true },
                        onRename = { showRenameDialog = true },
                        onDeleteSelected = { showBatchDeleteDialog = true }
                    )
                }
                AnimatedVisibility(
                    visible = !isSelectionMode,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    if (currentFolder != null) {
                        FolderTopBar(
                            folderName = currentFolder!!,
                            onBack = { viewModel.goToRoot() },
                            syncEnabled = canSync,
                            showSyncLegend = isSyncAvailable,
                            onSyncLegendClick = { showSyncLegend = true },
                            showFilterRow = showFilterRow,
                            onFilterToggle = { showFilterRow = !showFilterRow },
                            onSyncClick = { viewModel.triggerManualSync("toolbar") },
                            onSettingsClick = onOpenSettings
                        )
                    } else {
                        MainTopBar(
                            customTitle = customAppTitle, // 🆕 v1.9.0 (F05)
                            syncEnabled = canSync,
                            showSyncLegend = isSyncAvailable,
                            onSyncLegendClick = { showSyncLegend = true },
                            // 🆕 v1.9.0 (F11): Sort button replaced by filter row toggle
                            showFilterRow = showFilterRow,
                            onFilterToggle = { showFilterRow = !showFilterRow },
                            onSyncClick = { viewModel.triggerManualSync("toolbar") },
                            onSettingsClick = onOpenSettings
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
                onRefresh = { if (isSyncAvailable) viewModel.triggerManualSync("pullToRefresh") },
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
                                onFilterSelected = { viewModel.setNoteFilter(it) },
                                currentColorFilter = colorFilter,                           // 🆕 v2.5.0
                                onColorFilterSelected = { viewModel.setColorFilter(it) },   // 🆕 v2.5.0
                                availableColors = availableColors,                          // 🆕 v2.5.0
                                searchQuery = searchQuery,
                                onSearchQueryChanged = { viewModel.setSearchQuery(it) },
                                onSortClick = { showSortDialog = true },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // 🆕 v2.7.0 (Folders): Ordner-Navigation mit Shared-Axis-Animation (wie Notiz öffnen).
                        AnimatedContent(
                            targetState = currentFolder,
                            transitionSpec = { folderNavTransition(forward = targetState != null) },
                            label = "folderNav",
                            modifier = Modifier.weight(1f)
                        ) { folderKey ->
                            NotesPane(
                                folderKey = folderKey,
                                isActive = folderKey == currentFolder,
                                notes = notes,
                                displayMode = displayMode,
                                folders = folders,
                                folderNoteCounts = folderNoteCounts,
                                isServerConfigured = isServerConfigured,
                                selectedNotes = selectedNotes,
                                selectedFolders = selectedFolders,
                                isSelectionMode = isSelectionMode,
                                timestampTicker = timestampTicker,
                                gridAdaptiveScaling = gridAdaptiveScaling,
                                gridManualColumns = gridManualColumns,
                                scrollToTop = scrollToTop,
                                syncScrollToTop = syncScrollToTop,
                                noteFilter = noteFilter,
                                colorFilter = colorFilter,
                                onResetScrollToTop = { viewModel.resetScrollToTop() },
                                onResetSyncScrollToTop = { viewModel.resetSyncCompletedScrollToTop() },
                                onEnterFolder = { viewModel.enterFolder(it) },
                                onFolderLongPress = { viewModel.startSelectionWithFolder(it) },
                                onFolderSelectionToggle = { viewModel.toggleFolderSelection(it) },
                                onOpenNote = { onOpenNote(it) },
                                onStartSelection = { viewModel.startSelectionMode(it) },
                                onToggleSelection = { viewModel.toggleNoteSelection(it) },
                                focusManager = focusManager
                            )
                        }
                    }

                    // FAB ist jetzt außerhalb des Scaffolds als Fullscreen-Overlay — siehe unten
                }
            }
            if (showBatchDeleteDialog) {
                if (selectedFolders.isNotEmpty()) {
                    // 🆕 v2.7.0 (Folders): gemischte Auswahl (Notizen + Ordner)
                    val hasNonEmptyFolders = selectedFolders.any { (folderNoteCounts[it] ?: 0) > 0 }
                    DeleteSelectionDialog(
                        noteCount = selectedNotes.size,
                        folderCount = selectedFolders.size,
                        hasNonEmptyFolders = hasNonEmptyFolders,
                        isOfflineMode = isOfflineMode,
                        onDismiss = { showBatchDeleteDialog = false },
                        onDeleteLocal = { keep ->
                            viewModel.deleteSelection(deleteFromServer = false, keepContainedNotes = keep)
                            showBatchDeleteDialog = false
                        },
                        onDeleteEverywhere = { keep ->
                            viewModel.deleteSelection(deleteFromServer = true, keepContainedNotes = keep)
                            showBatchDeleteDialog = false
                        }
                    )
                } else {
                    DeleteConfirmationDialog(
                        noteCount = selectedNotes.size,
                        isOfflineMode = isOfflineMode,
                        onDismiss = { showBatchDeleteDialog = false },
                        onDeleteLocal = {
                            viewModel.deleteSelectedNotes(deleteFromServer = false)
                            showBatchDeleteDialog = false
                        },
                        onDeleteEverywhere = {
                            viewModel.deleteSelectedNotes(deleteFromServer = true)
                            showBatchDeleteDialog = false
                        }
                    )
                }
            }

            // 🆕 v1.8.0: Sync Status Legend Dialog
            if (showSyncLegend) {
                SyncStatusLegendDialog(
                    onDismiss = { showSyncLegend = false }
                )
            }

            // 🔀 v1.8.0: Sort Dialog
            if (showSortDialog) {
                SortDialog(
                    currentOption = sortOption,
                    currentDirection = sortDirection,
                    onOptionSelected = { option ->
                        viewModel.setSortOption(option)
                    },
                    onDirectionToggled = {
                        viewModel.toggleSortDirection()
                    },
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
                onDismiss = { showBatchColorPicker = false },
            )
        }

        // 🆕 v2.7.0 (Folders): Folder dialogs/sheet
        if (showCreateFolderDialog) {
            CreateFolderDialog(
                onConfirm = { name -> viewModel.createFolder(name); showCreateFolderDialog = false },
                onDismiss = { showCreateFolderDialog = false }
            )
        }
        if (showMoveSheet) {
            MoveToFolderSheet(
                folders = folders,
                currentFolder = currentFolder,
                onMoveToRoot = { viewModel.moveSelectedNotesTo(null); showMoveSheet = false },
                onMoveToFolder = { f -> viewModel.moveSelectedNotesTo(f); showMoveSheet = false },
                onCreateFolder = { name -> viewModel.createFolder(name) },
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
                        viewModel.renameFolder(renameTarget, newName)
                        showRenameDialog = false
                    },
                    onDismiss = { showRenameDialog = false }
                )
            } else {
                showRenameDialog = false
            }
        }

        // 🆕 v1.11.0: FAB als Fullscreen-Overlay ÜBER dem Scaffold — Scrim deckt Statusbar ab
        AnimatedVisibility(
            visible = !isSelectionMode,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    customTitle: String, // 🆕 v1.9.0 (F05): Custom app title (empty = default)
    syncEnabled: Boolean,
    showSyncLegend: Boolean, // 🆕 v1.8.0: Ob der Hilfe-Button sichtbar sein soll
    onSyncLegendClick: () -> Unit, // 🆕 v1.8.0
    showFilterRow: Boolean, // 🆕 v1.9.0 (F11): Filter row toggle state
    onFilterToggle: () -> Unit, // 🆕 v1.9.0 (F11): Toggle filter row visibility
    onSyncClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                // 🆕 v1.9.0 (F05): Use custom title if set, otherwise default
                text = customTitle.ifBlank { stringResource(R.string.main_title) },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        actions = {
            TopBarActions(
                syncEnabled = syncEnabled,
                showSyncLegend = showSyncLegend,
                onSyncLegendClick = onSyncLegendClick,
                showFilterRow = showFilterRow,
                onFilterToggle = onFilterToggle,
                onSyncClick = onSyncClick,
                onSettingsClick = onSettingsClick
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

/** 🆕 v2.7.0 (Folders): Action-Icons, geteilt von MainTopBar und FolderTopBar. Läuft im actions-RowScope. */
@Composable
private fun TopBarActions(
    syncEnabled: Boolean,
    showSyncLegend: Boolean,
    onSyncLegendClick: () -> Unit,
    showFilterRow: Boolean,
    onFilterToggle: () -> Unit,
    onSyncClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    IconButton(onClick = onFilterToggle) {
        Icon(
            imageVector = Icons.Outlined.Tune,
            contentDescription = stringResource(R.string.toggle_filter_row),
            tint = if (showFilterRow) MaterialTheme.colorScheme.primary else LocalContentColor.current
        )
    }
    if (showSyncLegend) {
        IconButton(onClick = onSyncLegendClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = stringResource(R.string.sync_legend_button)
            )
        }
    }
    IconButton(onClick = onSyncClick, enabled = syncEnabled) {
        Icon(imageVector = Icons.Default.Refresh, contentDescription = stringResource(R.string.action_sync))
    }
    IconButton(onClick = onSettingsClick) {
        Icon(imageVector = Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
    }
}

/**
 * Selection mode TopBar - shows selected count and actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList") // 🆕 v2.7.0: Rename-Param erhöht Count über 10
@Composable
private fun SelectionTopBar(
    selectedNoteCount: Int,
    selectedFolderCount: Int,  // 🆕 v2.7.0 (Folders)
    totalCount: Int,
    allSelectedPinned: Boolean,
    onCloseSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onTogglePinSelected: () -> Unit,
    onColorClick: () -> Unit,
    onMoveClick: () -> Unit = {},
    onRename: () -> Unit = {},         // 🆕 v2.7.0 (Folders)
    onDeleteSelected: () -> Unit
) {
    val selectedCount = selectedNoteCount + selectedFolderCount
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onCloseSelection) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_close_selection)
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.selection_count, selectedCount),
                style = MaterialTheme.typography.titleLarge
            )
        },
        actions = {
            SelectionActions(
                selectedNoteCount = selectedNoteCount,
                selectedFolderCount = selectedFolderCount,
                totalCount = totalCount,
                allSelectedPinned = allSelectedPinned,
                onSelectAll = onSelectAll,
                onTogglePin = onTogglePinSelected,
                onColorClick = onColorClick,
                onMoveClick = onMoveClick,
                onRename = onRename,
                onDeleteSelected = onDeleteSelected
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderTopBar(
    folderName: String,
    onBack: () -> Unit,
    syncEnabled: Boolean,
    showSyncLegend: Boolean,
    onSyncLegendClick: () -> Unit,
    showFilterRow: Boolean,
    onFilterToggle: () -> Unit,
    onSyncClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
        },
        title = {
            Text(
                text = folderName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        actions = {
            TopBarActions(
                syncEnabled = syncEnabled,
                showSyncLegend = showSyncLegend,
                onSyncLegendClick = onSyncLegendClick,
                showFilterRow = showFilterRow,
                onFilterToggle = onFilterToggle,
                onSyncClick = onSyncClick,
                onSettingsClick = onSettingsClick
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

/** 🆕 v2.7.0 (Folders): Shared-Axis-X Transition zwischen Root und Ordner (wie Notiz-Öffnen-Animation). */
private fun folderNavTransition(forward: Boolean): ContentTransform {
    val slide: FiniteAnimationSpec<IntOffset> = tween(FOLDER_ANIM_DURATION_MS, easing = FastOutSlowInEasing)
    val fade: FiniteAnimationSpec<Float> = tween(FOLDER_ANIM_DURATION_MS, easing = FastOutSlowInEasing)
    return if (forward) {
        ContentTransform(
            targetContentEnter = slideInHorizontally(slide) { w -> (w * FOLDER_SLIDE_FRACTION).toInt() } + fadeIn(fade),
            initialContentExit = slideOutHorizontally(slide) { w -> -(w * FOLDER_SLIDE_FRACTION).toInt() } + fadeOut(fade),
            sizeTransform = SizeTransform(clip = false)
        )
    } else {
        ContentTransform(
            targetContentEnter = slideInHorizontally(slide) { w -> -(w * FOLDER_SLIDE_FRACTION).toInt() } + fadeIn(fade),
            initialContentExit = slideOutHorizontally(slide) { w -> (w * FOLDER_SLIDE_FRACTION).toInt() } + fadeOut(fade),
            sizeTransform = SizeTransform(clip = false)
        )
    }
}

/**
 * 🆕 v2.7.0 (Folders): Eine Notiz-/Ordner-Ansicht (Root oder ein Ordner). Eigener Scroll-State pro
 * AnimatedContent-Slot → Ordnerwechsel startet oben; Zurück-zur-Root zeigt wieder die Ordner.
 * Nur die aktive Pane (isActive) konsumiert die One-Shot-Scroll-Flags.
 *
 * `notes` ist die ordner-unabhängige Liste; jede Pane filtert selbst nach ihrem `folderKey`, damit
 * die abgehende Pane während der Animation ihren eigenen (korrekten) Inhalt behält — kein Flackern.
 */
@Suppress("LongParameterList") // viele UI-State-Parameter
@Composable
private fun NotesPane(
    folderKey: String?,
    isActive: Boolean,
    notes: List<Note>,
    displayMode: String,
    folders: List<dev.dettmer.simplenotes.models.Folder>,
    folderNoteCounts: Map<String, Int>,
    isServerConfigured: Boolean,
    selectedNotes: Set<String>,
    selectedFolders: Set<String>,                    // 🆕 v2.7.0 (Folders)
    isSelectionMode: Boolean,
    timestampTicker: Long,
    gridAdaptiveScaling: Boolean,
    gridManualColumns: Int,
    scrollToTop: Boolean,
    syncScrollToTop: Boolean,
    noteFilter: NoteFilter,
    colorFilter: String?,
    onResetScrollToTop: () -> Unit,
    onResetSyncScrollToTop: () -> Unit,
    onEnterFolder: (String) -> Unit,
    onFolderLongPress: (String) -> Unit,
    onFolderSelectionToggle: (String) -> Unit,       // 🆕 v2.7.0 (Folders)
    onOpenNote: (String) -> Unit,
    onStartSelection: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    focusManager: FocusManager
) {
    val listState = rememberLazyListState()
    val gridState = rememberLazyStaggeredGridState()
    val foldersForPane = if (folderKey == null) folders else emptyList() // Ordner nur in der Root-Ansicht
    // 🆕 v2.7.0 (Folders): Notizen dieses Slots — eigener folderKey, nicht der gerade aktive Ordner.
    val paneNotes = remember(notes, folderKey) { notes.filter { it.folderName == folderKey } }

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
    LaunchedEffect(noteFilter, colorFilter) {
        if (!filterSettled) {
            filterSettled = true
        } else {
            gridState.scrollToItem(0)
            listState.scrollToItem(0)
        }
    }

    if (paneNotes.isEmpty() && foldersForPane.isEmpty()) {
        EmptyState(modifier = Modifier.fillMaxSize())
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
            onFolderClick = { if (isSelectionMode) onFolderSelectionToggle(it) else onEnterFolder(it) },
            onFolderLongPress = onFolderLongPress,
            onFolderSelectionToggle = onFolderSelectionToggle
        )
    } else {
        NotesList(
            notes = paneNotes,
            showSyncStatus = isServerConfigured,
            selectedNotes = selectedNotes,
            isSelectionMode = isSelectionMode,
            timestampTicker = timestampTicker,
            listState = listState,
            modifier = Modifier.fillMaxSize(),
            folders = foldersForPane,
            folderNoteCounts = folderNoteCounts,
            selectedFolders = selectedFolders,
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
            onNoteSelectionToggle = { note -> onToggleSelection(note.id) }
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
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
private fun SelectionActions(
    selectedNoteCount: Int,
    selectedFolderCount: Int,  // 🆕 v2.7.0 (Folders)
    totalCount: Int,
    allSelectedPinned: Boolean,
    onSelectAll: () -> Unit,
    onTogglePin: () -> Unit,
    onColorClick: () -> Unit,
    onMoveClick: () -> Unit,
    onRename: () -> Unit,      // 🆕 v2.7.0 (Folders)
    onDeleteSelected: () -> Unit
) {
    val selectedCount = selectedNoteCount + selectedFolderCount
    val anySelected = selectedCount > 0
    val selectAllLabel = stringResource(R.string.action_select_all)
    val pinLabel = stringResource(R.string.action_toggle_pin)
    val colorLabel = stringResource(R.string.action_set_note_color)
    val moveLabel = stringResource(R.string.action_move_to_folder)
    val renameLabel = stringResource(R.string.action_rename_folder)
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
        add(SelectionAction(Icons.Default.Delete, deleteLabel, keepPriority = 5, enabled = anySelected,
            isDestructive = true, onClick = onDeleteSelected))
    }

    // Verfügbare Icon-Slots aus der Bildschirmbreite ableiten (recomposed bei Rotation).
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
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
                    onClick = { expanded = false; a.onClick() }
                )
            }
        }
    }
}
