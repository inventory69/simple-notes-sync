package dev.dettmer.simplenotes.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.ChecklistSortOption
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.ui.editor.components.CheckedItemsSeparator
import dev.dettmer.simplenotes.ui.editor.components.ChecklistItemRow
import dev.dettmer.simplenotes.ui.editor.components.ChecklistSortDialog
import dev.dettmer.simplenotes.ui.main.components.DeleteConfirmationDialog
import kotlinx.coroutines.delay
import dev.dettmer.simplenotes.utils.showToast
import kotlin.math.roundToInt

private const val LAYOUT_DELAY_MS = 100L
private const val ITEM_CORNER_RADIUS_DP = 8
private const val DRAGGING_ITEM_Z_INDEX = 10f

/**
 * Main Composable for the Note Editor screen.
 * 
 * v1.5.0: Jetpack Compose NoteEditor Redesign
 * - Supports both TEXT and CHECKLIST notes
 * - Drag & Drop reordering for checklist items
 * - Auto-keyboard focus for new items
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    viewModel: NoteEditorViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val checklistItems by viewModel.checklistItems.collectAsState()
    
    // 🌟 v1.6.0: Offline mode state
    val isOfflineMode by viewModel.isOfflineMode.collectAsState()
    
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showChecklistSortDialog by remember { mutableStateOf(false) }  // 🔀 v1.8.0
    val lastChecklistSortOption by viewModel.lastChecklistSortOption.collectAsState()  // 🔀 v1.8.0
    var focusNewItemId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    // Strings for toast messages (avoid LocalContextGetResourceValueCall lint)
    val msgNoteIsEmpty = stringResource(R.string.note_is_empty)
    val msgNoteSaved = stringResource(R.string.note_saved)
    val msgNoteDeleted = stringResource(R.string.note_deleted)
    
    // v1.5.0: Auto-keyboard support
    val keyboardController = LocalSoftwareKeyboardController.current
    val titleFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    
    // v1.5.0: Auto-focus and show keyboard
    LaunchedEffect(uiState.isNewNote, uiState.noteType) {
        delay(LAYOUT_DELAY_MS) // Wait for layout
        when {
            uiState.isNewNote -> {
                // New note: focus title
                titleFocusRequester.requestFocus()
                keyboardController?.show()
            }
            !uiState.isNewNote && uiState.noteType == NoteType.TEXT -> {
                // Editing text note: focus content
                contentFocusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    }
    
    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is NoteEditorEvent.ShowToast -> {
                    val message = when (event.message) {
                        ToastMessage.NOTE_IS_EMPTY -> msgNoteIsEmpty
                        ToastMessage.NOTE_SAVED -> msgNoteSaved
                        ToastMessage.NOTE_DELETED -> msgNoteDeleted
                    }
                    context.showToast(message)
                }
                is NoteEditorEvent.NavigateBack -> onNavigateBack()
                is NoteEditorEvent.ShowDeleteConfirmation -> showDeleteDialog = true
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (uiState.toolbarTitle) {
                            ToolbarTitle.NEW_NOTE -> stringResource(R.string.new_note)
                            ToolbarTitle.EDIT_NOTE -> stringResource(R.string.edit_note)
                            ToolbarTitle.NEW_CHECKLIST -> stringResource(R.string.new_checklist)
                            ToolbarTitle.EDIT_CHECKLIST -> stringResource(R.string.edit_checklist)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    // Delete button (only for existing notes)
                    if (viewModel.canDelete()) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete)
                            )
                        }
                    }
                    
                    // Save button
                    IconButton(onClick = { viewModel.saveNote() }) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = stringResource(R.string.save)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = Modifier.imePadding()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Title Input (for both types)
            OutlinedTextField(
                value = uiState.title,
                onValueChange = { viewModel.updateTitle(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocusRequester),
                label = { Text(stringResource(R.string.title)) },
                singleLine = false,
                maxLines = 2,
                shape = RoundedCornerShape(16.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            when (uiState.noteType) {
                NoteType.TEXT -> {
                    // Content Input for TEXT notes
                    TextNoteContent(
                        content = uiState.content,
                        onContentChange = { viewModel.updateContent(it) },
                        focusRequester = contentFocusRequester,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
                
                NoteType.CHECKLIST -> {
                    // Checklist Editor
                    ChecklistEditor(
                        items = checklistItems,
                        scope = scope,
                        focusNewItemId = focusNewItemId,
                        currentSortOption = lastChecklistSortOption,  // 🔀 v1.8.0
                        onTextChange = { id, text -> viewModel.updateChecklistItemText(id, text) },
                        onCheckedChange = { id, checked -> viewModel.updateChecklistItemChecked(id, checked) },
                        onDelete = { id -> viewModel.deleteChecklistItem(id) },
                        onAddNewItemAfter = { id -> 
                            val newId = viewModel.addChecklistItemAfter(id)
                            focusNewItemId = newId
                        },
                        onAddItemAtEnd = {
                            val newId = viewModel.addChecklistItemAtEnd()
                            focusNewItemId = newId
                        },
                        onMove = { from, to -> viewModel.moveChecklistItem(from, to) },
                        onFocusHandled = { focusNewItemId = null },
                        onSortClick = { showChecklistSortDialog = true },  // 🔀 v1.8.0
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
    
    // Delete Confirmation Dialog - v1.5.0: Use shared component with server/local options
    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            noteCount = 1,
            isOfflineMode = isOfflineMode,
            onDismiss = { showDeleteDialog = false },
            onDeleteLocal = {
                showDeleteDialog = false
                viewModel.deleteNote(deleteOnServer = false)
            },
            onDeleteEverywhere = {
                showDeleteDialog = false
                viewModel.deleteNote(deleteOnServer = true)
            }
        )
    }
    
    // 🔀 v1.8.0: Checklist Sort Dialog
    if (showChecklistSortDialog) {
        ChecklistSortDialog(
            currentOption = lastChecklistSortOption,
            onOptionSelected = { option ->
                viewModel.sortChecklistItems(option)
                showChecklistSortDialog = false
            },
            onDismiss = { showChecklistSortDialog = false }
        )
    }
}

@Composable
private fun TextNoteContent(
    content: String,
    onContentChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    // v1.5.0: Use TextFieldValue to control cursor position
    // Track if initial cursor position has been set (only set to end once on first load)
    var initialCursorSet by remember { mutableStateOf(false) }
    
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(
            text = content,
            selection = TextRange(content.length)
        ))
    }
    
    // Set initial cursor position only once when content first loads
    LaunchedEffect(Unit) {
        if (!initialCursorSet && content.isNotEmpty()) {
            textFieldValue = TextFieldValue(
                text = content,
                selection = TextRange(content.length)
            )
            initialCursorSet = true
        }
    }
    
    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            textFieldValue = newValue
            onContentChange(newValue.text)
        },
        modifier = modifier.focusRequester(focusRequester),
        label = { Text(stringResource(R.string.content)) },
        shape = RoundedCornerShape(16.dp)
    )
}

@Suppress("LongParameterList") // Compose functions commonly have many callback parameters
@Composable
private fun ChecklistEditor(
    items: List<ChecklistItemState>,
    scope: kotlinx.coroutines.CoroutineScope,
    focusNewItemId: String?,
    currentSortOption: ChecklistSortOption,  // 🔀 v1.8.0: Aktuelle Sortierung
    onTextChange: (String, String) -> Unit,
    onCheckedChange: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onAddNewItemAfter: (String) -> Unit,
    onAddItemAtEnd: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onFocusHandled: () -> Unit,
    onSortClick: () -> Unit,  // 🔀 v1.8.0
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val dragDropState = rememberDragDropListState(
        lazyListState = listState,
        scope = scope,
        onMove = onMove
    )

    // 🆕 v1.8.0 (IMPL_017 + IMPL_020): Separator nur bei MANUAL und UNCHECKED_FIRST anzeigen
    val uncheckedCount = items.count { !it.isChecked }
    val checkedCount = items.count { it.isChecked }
    val shouldShowSeparator = currentSortOption == ChecklistSortOption.MANUAL || 
                              currentSortOption == ChecklistSortOption.UNCHECKED_FIRST
    val showSeparator = shouldShowSeparator && uncheckedCount > 0 && checkedCount > 0

    Column(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> item.id }
            ) { index, item ->
                // 🆕 v1.8.0 (IMPL_017): Separator vor dem ersten Checked-Item
                if (showSeparator && index == uncheckedCount) {
                    CheckedItemsSeparator(checkedCount = checkedCount)
                }

                val isDragging = dragDropState.draggingItemIndex == index
                val elevation by animateDpAsState(
                    targetValue = if (isDragging) 8.dp else 0.dp,
                    label = "elevation"
                )

                val shouldFocus = item.id == focusNewItemId

                // v1.5.0: Clear focus request after handling
                LaunchedEffect(shouldFocus) {
                    if (shouldFocus) {
                        onFocusHandled()
                    }
                }

                // 🆕 v1.8.0 (IMPL_017): AnimatedVisibility für sanfte Übergänge
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    ChecklistItemRow(
                        item = item,
                        onTextChange = { onTextChange(item.id, it) },
                        onCheckedChange = { onCheckedChange(item.id, it) },
                        onDelete = { onDelete(item.id) },
                        onAddNewItem = { onAddNewItemAfter(item.id) },
                        requestFocus = shouldFocus,
                        // 🆕 v1.8.0: IMPL_023 - Drag state übergeben
                        isDragging = isDragging,
                        // 🆕 v1.8.0: IMPL_023 - Gradient während Drag ausblenden
                        isAnyItemDragging = dragDropState.draggingItemIndex != null,
                        // 🆕 v1.8.0: IMPL_023 - Drag nur auf Handle
                        dragModifier = Modifier.dragContainer(dragDropState, index),
                        modifier = Modifier
                            .animateItem()  // 🆕 v1.8.0 (IMPL_017): LazyColumn Item-Animation
                            .offset {
                                IntOffset(
                                    0,
                                    if (isDragging) dragDropState.draggingItemOffset.roundToInt() else 0
                                )
                            }
                            // 🆕 v1.8.0: IMPL_023 - Gedraggtes Item liegt über anderen
                            .zIndex(if (isDragging) DRAGGING_ITEM_Z_INDEX else 0f)
                            .shadow(elevation, shape = RoundedCornerShape(ITEM_CORNER_RADIUS_DP.dp))
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(ITEM_CORNER_RADIUS_DP.dp)
                            )
                    )
                }
            }
        }

        // 🔀 v1.8.0: Add Item Button + Sort Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            TextButton(onClick = onAddItemAtEnd) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(stringResource(R.string.add_item))
            }
            
            IconButton(onClick = onSortClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Sort,
                    contentDescription = stringResource(R.string.sort_checklist),
                    modifier = androidx.compose.ui.Modifier.padding(4.dp)
                )
            }
        }
    }
}

// v1.5.0: Local DeleteConfirmationDialog removed - now using shared component from ui/main/components/
