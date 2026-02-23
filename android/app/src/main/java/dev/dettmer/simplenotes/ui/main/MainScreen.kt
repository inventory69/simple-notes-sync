package dev.dettmer.simplenotes.ui.main

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
// FabPosition nicht mehr benötigt - FAB wird manuell platziert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.ui.main.components.SortDialog
import dev.dettmer.simplenotes.sync.SyncStateManager
import dev.dettmer.simplenotes.ui.main.components.DeleteConfirmationDialog
import dev.dettmer.simplenotes.ui.main.components.EmptyState
import dev.dettmer.simplenotes.ui.main.components.NoteTypeFAB
import dev.dettmer.simplenotes.ui.main.components.FilterChipRow
import dev.dettmer.simplenotes.ui.main.components.NotesList
import dev.dettmer.simplenotes.ui.main.components.NotesStaggeredGrid
import dev.dettmer.simplenotes.ui.main.components.SyncProgressBanner
import dev.dettmer.simplenotes.ui.main.components.SyncStatusLegendDialog
import kotlinx.coroutines.launch

private const val TIMESTAMP_UPDATE_INTERVAL_MS = 30_000L

/** 🆕 v1.9.0 (F13): Delay before scrolling to top after manual sync, giving Compose time to recompose with new data. */
private const val SYNC_SCROLL_DELAY_MS = 150L

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
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenNote: (String?) -> Unit,
    onOpenSettings: () -> Unit,
    onCreateNote: (NoteType) -> Unit
) {
    val notes by viewModel.sortedNotes.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val scrollToTop by viewModel.scrollToTop.collectAsState()
    // 🆕 v1.9.0 (F13): Scroll-to-top after manual sync
    val syncScrollToTop by viewModel.syncCompletedScrollToTop.collectAsState()
    
    // 🆕 v1.8.0: Einziges Banner-System
    val syncProgress by viewModel.syncProgress.collectAsState()
    
    // Multi-Select State
    val selectedNotes by viewModel.selectedNotes.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    
    // 🌟 v1.6.0: Reactive offline mode state
    val isOfflineMode by viewModel.isOfflineMode.collectAsState()
    
    // 🎨 v1.7.0: Display mode (list or grid)
    val displayMode by viewModel.displayMode.collectAsState()
    // 🆕 v1.9.0 (F05): Custom App Title
    val customAppTitle by viewModel.customAppTitle.collectAsState()
    
    // Delete confirmation dialog state
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    
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
    val focusManager = LocalFocusManager.current

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    // 🎨 v1.7.0: gridState für Staggered Grid Layout
    val gridState = rememberLazyStaggeredGridState()
    
    // ⏱️ Timestamp ticker - increments every 30 seconds to trigger recomposition of relative times
    var timestampTicker by remember { mutableStateOf(0L) }
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
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    data.onAction()
                }
            }
        }
    }
    
    // Phase 3: Scroll to top when new note created
    // 🎨 v1.7.0: Unterstützt beide Display-Modi (list & grid)
    LaunchedEffect(scrollToTop) {
        if (scrollToTop) {
            if (displayMode == "grid") {
                gridState.animateScrollToItem(0)
            } else {
                listState.animateScrollToItem(0)
            }
            viewModel.resetScrollToTop()
        }
    }

    // 🆕 v1.9.0 (F13): Scroll to top after manual sync completion
    LaunchedEffect(syncScrollToTop) {
        if (syncScrollToTop) {
            // Small delay to let the notes list recompose with new data
            kotlinx.coroutines.delay(SYNC_SCROLL_DELAY_MS)
            if (displayMode == "grid") {
                gridState.animateScrollToItem(0)
            } else {
                listState.animateScrollToItem(0)
            }
            viewModel.resetSyncCompletedScrollToTop()
        }
    }
    
    // v1.5.0 Hotfix: FAB manuell mit zIndex platzieren für garantierte Sichtbarkeit
    Scaffold(
        topBar = {
            // Animated switch between normal and selection TopBar
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                SelectionTopBar(
                    selectedCount = selectedNotes.size,
                    totalCount = notes.size,
                    onCloseSelection = { viewModel.clearSelection() },
                    onSelectAll = { viewModel.selectAllNotes() },
                    onDeleteSelected = { showBatchDeleteDialog = true }
                )
            }
            AnimatedVisibility(
                visible = !isSelectionMode,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                MainTopBar(
                    customTitle = customAppTitle,  // 🆕 v1.9.0 (F05)
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
        },
        // FAB wird manuell in Box platziert für korrekten z-Index
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                            searchQuery = searchQuery,
                            onSearchQueryChanged = { viewModel.setSearchQuery(it) },
                            onSortClick = { showSortDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Content: Empty state or notes list
                    if (notes.isEmpty()) {
                        EmptyState(modifier = Modifier.weight(1f))
                    } else {
                        // 🎨 v1.7.0: Switch between List and Grid based on display mode
                        if (displayMode == "grid") {
                            NotesStaggeredGrid(
                                notes = notes,
                                gridState = gridState,
                                showSyncStatus = viewModel.isServerConfigured(),
                                selectedNoteIds = selectedNotes,
                                isSelectionMode = isSelectionMode,
                                timestampTicker = timestampTicker,
                                modifier = Modifier.weight(1f),
                                onNoteClick = { note ->
                                    focusManager.clearFocus()
                                    if (isSelectionMode) {
                                        viewModel.toggleNoteSelection(note.id)
                                    } else {
                                        onOpenNote(note.id)
                                    }
                                },
                                onNoteLongClick = { note ->
                                    focusManager.clearFocus()
                                    viewModel.startSelectionMode(note.id)
                                }
                            )
                        } else {
                            NotesList(
                                notes = notes,
                                showSyncStatus = viewModel.isServerConfigured(),
                                selectedNotes = selectedNotes,
                                isSelectionMode = isSelectionMode,
                                timestampTicker = timestampTicker,
                                listState = listState,
                                modifier = Modifier.weight(1f),
                                onNoteClick = { note ->
                                    focusManager.clearFocus()
                                    onOpenNote(note.id)
                                },
                                onNoteLongPress = { note ->
                                    focusManager.clearFocus()
                                    viewModel.startSelectionMode(note.id)
                                },
                                onNoteSelectionToggle = { note ->
                                    viewModel.toggleNoteSelection(note.id)
                                }
                            )
                        }
                    }
                }
                
                // FAB als TOP-LAYER - nur anzeigen wenn nicht im Selection Mode
                AnimatedVisibility(
                    visible = !isSelectionMode,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .zIndex(Float.MAX_VALUE)
                ) {
                    NoteTypeFAB(
                        onCreateNote = onCreateNote
                    )
                }
            }
        }
        
        // Batch Delete Confirmation Dialog
        if (showBatchDeleteDialog) {
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    customTitle: String,  // 🆕 v1.9.0 (F05): Custom app title (empty = default)
    syncEnabled: Boolean,
    showSyncLegend: Boolean,  // 🆕 v1.8.0: Ob der Hilfe-Button sichtbar sein soll
    onSyncLegendClick: () -> Unit,  // 🆕 v1.8.0
    showFilterRow: Boolean,  // 🆕 v1.9.0 (F11): Filter row toggle state
    onFilterToggle: () -> Unit,  // 🆕 v1.9.0 (F11): Toggle filter row visibility
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
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        },
        actions = {
            // 🆕 v1.9.0 (F11): Filter row toggle button (replaces sort button)
            IconButton(onClick = onFilterToggle) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = stringResource(R.string.toggle_filter_row),
                    tint = if (showFilterRow) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            
            // 🆕 v1.8.0: Sync Status Legend Button (nur wenn Sync verfügbar)
            if (showSyncLegend) {
                IconButton(onClick = onSyncLegendClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                        contentDescription = stringResource(R.string.sync_legend_button)
                    )
                }
            }
            IconButton(
                onClick = onSyncClick,
                enabled = syncEnabled
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.action_sync)
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.action_settings)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

/**
 * Selection mode TopBar - shows selected count and actions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    totalCount: Int,
    onCloseSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit
) {
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
            // Select All button (only if not all selected)
            if (selectedCount < totalCount) {
                IconButton(onClick = onSelectAll) {
                    Icon(
                        imageVector = Icons.Default.SelectAll,
                        contentDescription = stringResource(R.string.action_select_all)
                    )
                }
            }
            // Delete button
            IconButton(
                onClick = onDeleteSelected,
                enabled = selectedCount > 0
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_delete_selected),
                    tint = if (selectedCount > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}
