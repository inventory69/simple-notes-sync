package dev.dettmer.simplenotes.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.sync.SyncStateManager
import dev.dettmer.simplenotes.ui.main.components.DeleteConfirmationDialog
import dev.dettmer.simplenotes.ui.main.components.EmptyState
import dev.dettmer.simplenotes.ui.main.components.NoteTypeFAB
import dev.dettmer.simplenotes.ui.main.components.NotesList
import dev.dettmer.simplenotes.ui.main.components.SyncStatusBanner
import kotlinx.coroutines.launch

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
    val notes by viewModel.notes.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val scrollToTop by viewModel.scrollToTop.collectAsState()
    
    // Multi-Select State
    val selectedNotes by viewModel.selectedNotes.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    
    // Delete confirmation dialog state
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    // Compute isSyncing once
    val isSyncing = syncState == SyncStateManager.SyncState.SYNCING
    
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
    LaunchedEffect(scrollToTop) {
        if (scrollToTop) {
            listState.animateScrollToItem(0)
            viewModel.resetScrollToTop()
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
                    syncEnabled = !isSyncing,
                    onSyncClick = { viewModel.triggerManualSync("toolbar") },
                    onSettingsClick = onOpenSettings
                )
            }
        },
        // FAB wird manuell in Box platziert für korrekten z-Index
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        // PullToRefreshBox wraps the content with pull-to-refresh capability
        PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = { viewModel.triggerManualSync("pullToRefresh") },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Main content column
                Column(modifier = Modifier.fillMaxSize()) {
                    // Sync Status Banner (not affected by pull-to-refresh)
                    SyncStatusBanner(
                        syncState = syncState,
                        message = syncMessage
                    )
                    
                    // Content: Empty state or notes list
                    if (notes.isEmpty()) {
                        EmptyState(modifier = Modifier.weight(1f))
                    } else {
                        NotesList(
                            notes = notes,
                            showSyncStatus = viewModel.isServerConfigured(),
                            selectedNotes = selectedNotes,
                            isSelectionMode = isSelectionMode,
                            listState = listState,
                            modifier = Modifier.weight(1f),
                            onNoteClick = { note -> onOpenNote(note.id) },
                            onNoteLongPress = { note -> 
                                // Long-press starts selection mode
                                viewModel.startSelectionMode(note.id)
                            },
                            onNoteSelectionToggle = { note ->
                                viewModel.toggleNoteSelection(note.id)
                            }
                        )
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopBar(
    syncEnabled: Boolean,
    onSyncClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.main_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        actions = {
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
