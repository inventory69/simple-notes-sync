package dev.dettmer.simplenotes.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility

import dev.dettmer.simplenotes.markdown.MarkdownEngine
import dev.dettmer.simplenotes.markdown.MarkdownPreview
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import dev.dettmer.simplenotes.ui.editor.components.MarkdownToolbar
import dev.dettmer.simplenotes.ui.main.components.DeleteConfirmationDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.drop
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.showToast
import kotlin.math.roundToInt

private const val LAYOUT_DELAY_MS = 100L
private const val AUTO_SCROLL_DELAY_MS = 50L
private const val ITEM_CORNER_RADIUS_DP = 8
private const val DRAGGING_ITEM_Z_INDEX = 10f
private val DRAGGING_ELEVATION_DP = 8.dp

/**
 * Main Composable for the Note Editor screen.
 * 
 * v1.5.0: Jetpack Compose NoteEditor Redesign
 * - Supports both TEXT and CHECKLIST notes
 * - Drag & Drop reordering for checklist items
 * - Auto-keyboard focus for new items
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
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
    // 🆕 v1.9.0 (F07): Markdown Preview toggle (only for TEXT notes)
    var isPreviewMode by remember { mutableStateOf(false) }
    var showChecklistSortDialog by remember { mutableStateOf(false) }  // 🔀 v1.8.0
    val lastChecklistSortOption by viewModel.lastChecklistSortOption.collectAsState()  // 🔀 v1.8.0
    val autosaveIndicatorVisible by viewModel.autosaveIndicatorVisible.collectAsState()  // 🆕 v1.9.0
    val canUndo by viewModel.canUndo.collectAsState()  // 🆕 v1.10.0
    val canRedo by viewModel.canRedo.collectAsState()  // 🆕 v1.10.0
    var showOverflowMenu by remember { mutableStateOf(false) }  // 🆕 v1.10.0-Papa
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

    // 🆕 v1.9.0 (F07): Lifted TextFieldState for toolbar access
    val textFieldState = rememberTextFieldState(initialText = uiState.content)

    // Cursor ans Ende setzen wenn Content geladen wird (einmalig)
    LaunchedEffect(Unit) {
        if (uiState.content.isNotEmpty()) {
            textFieldState.edit { placeCursorAtEnd() }
        }
    }

    // 🆕 v1.9.0 (F07): Reset preview mode if note type changes to CHECKLIST
    LaunchedEffect(uiState.noteType) {
        if (uiState.noteType == NoteType.CHECKLIST) {
            isPreviewMode = false
        }
    }

    // 🆕 v1.9.0 (F07): Auto-show keyboard when switching from preview → edit
    LaunchedEffect(isPreviewMode) {
        if (!isPreviewMode && uiState.noteType == NoteType.TEXT && !uiState.isNewNote) {
            delay(LAYOUT_DELAY_MS)
            contentFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

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
                is NoteEditorEvent.RestoreContent -> {  // 🆕 v1.10.0: Undo/Redo
                    textFieldState.edit {
                        replace(0, length, event.content)
                        placeCursorAtEnd()
                    }
                }
                // 🆕 v1.10.0-P2: handled by Activity (deletion forwarded to MainViewModel)
                is NoteEditorEvent.NoteDeleteRequested -> Unit
                // 🆕 v1.10.0-Papa: handled by Activity
                is NoteEditorEvent.OpenCalendar -> Unit
                is NoteEditorEvent.ShareAsText -> Unit
                is NoteEditorEvent.ShareAsPdf -> Unit
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    NoteEditorToolbarTitle(
                        toolbarTitle = uiState.toolbarTitle,
                        autosaveIndicatorVisible = autosaveIndicatorVisible
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
                    // 🆕 v1.10.0: Undo/Redo buttons
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = canUndo
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = stringResource(R.string.editor_undo)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = canRedo
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = stringResource(R.string.editor_redo)
                        )
                    }

                    // 🆕 v1.9.0 (F07): Markdown Preview Toggle (only for TEXT notes)
                    if (uiState.noteType == NoteType.TEXT) {
                        IconButton(onClick = { isPreviewMode = !isPreviewMode }) {
                            Icon(
                                imageVector = if (isPreviewMode) {
                                    Icons.Outlined.Edit
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = stringResource(R.string.editor_toggle_preview)
                            )
                        }
                    }

                    // Delete button (only for existing notes) — moved to overflow menu

                    // Save button
                    IconButton(onClick = { viewModel.saveNote() }) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = stringResource(R.string.save)
                        )
                    }

                    // 🆕 v1.10.0-Papa: Overflow menu (Calendar, Share, PDF, Delete)
                    // 🆕 v1.10.0-P2: Box ensures menu anchors to the ⋮ button,
                    // not to the full actions block (fixes position inconsistency
                    // between text and checklist note types)
                    Box {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.share_overflow_menu)
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        shape = MaterialTheme.shapes.large,  // 🆕 v1.10.0-P2: Rounded corners
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,  // 🆕 v1.10.0-P2
                        shadowElevation = 6.dp,  // 🆕 v1.10.0-P2
                        tonalElevation = 2.dp  // 🆕 v1.10.0-P2
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_to_calendar)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                showOverflowMenu = false
                                viewModel.openInCalendar()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_as_text)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Share,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                showOverflowMenu = false
                                viewModel.shareAsText()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_as_pdf)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.PictureAsPdf,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = {
                                showOverflowMenu = false
                                viewModel.shareAsPdf()
                            }
                        )
                        if (viewModel.canDelete()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.delete),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                    } // Box
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
                singleLine = true,  // 🆕 v1.8.2 (IMPL_09): Enter navigiert statt Newline
                // 🆕 v1.8.2: Auto-Großschreibung für Wortanfänge im Titel
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next  // 🆕 v1.8.2 (IMPL_09): Weiter-Taste
                ),
                // 🆕 v1.8.2 (IMPL_09): Nach Enter/Next → ins passende Feld springen
                keyboardActions = KeyboardActions(
                    onNext = {
                        when (uiState.noteType) {
                            NoteType.TEXT -> {
                                // Text-Notiz: Fokus direkt ins Content-Feld
                                contentFocusRequester.requestFocus()
                            }
                            NoteType.CHECKLIST -> {
                                // Checkliste: Fokus auf erstes Item
                                val firstItemId = checklistItems.firstOrNull()?.id
                                if (firstItemId != null) {
                                    focusNewItemId = firstItemId
                                }
                            }
                        }
                    }
                ),
                shape = RoundedCornerShape(16.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            when (uiState.noteType) {
                NoteType.TEXT -> {
                    if (isPreviewMode) {
                        // 🆕 v1.9.0 (F07): Markdown rendered preview
                        val blocks = remember(uiState.content) {
                            MarkdownEngine.parse(uiState.content)
                        }
                        MarkdownPreview(
                            blocks = blocks,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    } else {
                        // Content Input for TEXT notes
                        TextNoteContent(
                            textFieldState = textFieldState,
                            onContentChange = { viewModel.updateContent(it) },
                            focusRequester = contentFocusRequester,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )

                        // 🆕 v1.9.0 (F07): Markdown formatting toolbar below content
                        MarkdownToolbar(
                            textFieldState = textFieldState
                        )
                    }
                }
                
                NoteType.CHECKLIST -> {
                    // Checklist Editor
                    ChecklistEditor(
                        items = checklistItems,
                        scope = scope,
                        focusNewItemId = focusNewItemId,
                        currentSortOption = lastChecklistSortOption,  // 🔀 v1.8.0
                        checklistScrollAction = viewModel.checklistScrollAction,  // 🆕 v1.9.0 (F14)
                        onTextChange = { id, text -> viewModel.updateChecklistItemText(id, text) },
                        onCheckedChange = { id, checked ->
                            viewModel.updateChecklistItemChecked(id, checked)
                        },
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
    textFieldState: TextFieldState,
    onContentChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    // 🆕 v1.8.2 (IMPL_07): Migration zu TextFieldState-API für scrollState-Unterstützung
    // v1.9.0 (F07): TextFieldState now provided from parent for toolbar access
    val scrollState = rememberScrollState()
    
    // Focus-State tracken für Auto-Scroll bei Tastaturöffnung
    var isFocused by remember { mutableStateOf(false) }
    
    // Text-Änderungen an ViewModel propagieren
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .drop(1)  // 🆕 v1.9.0: skip initial emission — snapshotFlow always emits current
                      // value on first collect, but that's hydration, not a user edit
            .collect { newText ->
                onContentChange(newText)
            }
    }
    
    // 🆕 v1.8.2 (IMPL_07): Auto-Scroll zum Ende wenn Fokus erhalten (Tastatur öffnet sich)
    // Delay gibt dem Layout Zeit, sich nach imePadding-Resize zu stabilisieren
    LaunchedEffect(isFocused) {
        if (isFocused) {
            delay(LAYOUT_DELAY_MS)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    
    OutlinedTextField(
        state = textFieldState,
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                isFocused = focusState.isFocused
            },
        label = { Text(stringResource(R.string.content)) },
        // 🆕 v1.8.2: Auto-Großschreibung für Satzanfänge im Inhalt
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences
        ),
        shape = RoundedCornerShape(16.dp),
        // 🆕 v1.8.2 (IMPL_07): Externer ScrollState für programmatisches Auto-Scroll
        scrollState = scrollState
    )
}

/**
 * 🆕 v1.8.1 IMPL_14: Extrahiertes Composable für ein einzelnes draggbares Checklist-Item.
 * Entkoppelt von der Separator-Logik — wiederverwendbar für unchecked und checked Items.
 */
@Suppress("LongParameterList") // Compose callbacks — cannot be reduced without wrapper class
@Composable
private fun LazyItemScope.DraggableChecklistItem(
    item: ChecklistItemState,
    visualIndex: Int,
    dragDropState: DragDropListState,
    focusNewItemId: String?,
    onTextChange: (String, String) -> Unit,
    onCheckedChange: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onAddNewItemAfter: (String) -> Unit,
    onFocusHandled: () -> Unit,
    onHeightChanged: () -> Unit,  // 🆕 v1.8.1 (IMPL_05)
) {
    // 🆕 v1.8.2 (IMPL_11): Drag nur visuell anzeigen wenn tatsächlich bestätigt.
    // Verhindert Glitch beim schnellen Scrollen (kurzzeitiges onDragStart ohne onDrag).
    val isDragging = dragDropState.draggingItemIndex == visualIndex && dragDropState.isDragConfirmed
    val elevation by animateDpAsState(
        targetValue = if (isDragging) DRAGGING_ELEVATION_DP else 0.dp,
        label = "elevation"
    )

    val shouldFocus = item.id == focusNewItemId

    LaunchedEffect(shouldFocus) {
        if (shouldFocus) {
            onFocusHandled()
        }
    }

    ChecklistItemRow(
        item = item,
        onTextChange = { onTextChange(item.id, it) },
        onCheckedChange = { onCheckedChange(item.id, it) },
        onDelete = { onDelete(item.id) },
        onAddNewItem = { onAddNewItemAfter(item.id) },
        requestFocus = shouldFocus,
        isDragging = isDragging,
        isAnyItemDragging = dragDropState.draggingItemIndex != null,
        dragModifier = Modifier.dragContainer(dragDropState, visualIndex),
        onHeightChanged = onHeightChanged,  // 🆕 v1.8.1 (IMPL_05)
        modifier = Modifier
            // 🆕 v1.8.2 (IMPL_11): animateItem() NUR während bestätigtem Drag anwenden.
            // Vorher: animateItem() auf ALLEN Items permanent → Fade-In/Out-Animationen
            // verursachten visuelles Flackern bei langen Items beim schnellen Scrollen.
            // Jetzt: Nur placement-Animation für nicht-gedraggte Items während Reorder.
            .then(
                if (dragDropState.isDragConfirmed && !isDragging)
                    Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                else
                    Modifier
            )
            .offset {
                IntOffset(
                    0,
                    if (isDragging) dragDropState.draggingItemOffset.roundToInt() else 0
                )
            }
            .zIndex(if (isDragging) DRAGGING_ITEM_Z_INDEX else 0f)
            .shadow(elevation, shape = RoundedCornerShape(ITEM_CORNER_RADIUS_DP.dp))
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(ITEM_CORNER_RADIUS_DP.dp)
            )
    )
}

@Suppress("LongParameterList") // Compose functions commonly have many callback parameters
@Composable
private fun ChecklistEditor(
    items: List<ChecklistItemState>,
    scope: kotlinx.coroutines.CoroutineScope,
    focusNewItemId: String?,
    currentSortOption: ChecklistSortOption,  // 🔀 v1.8.0: Aktuelle Sortierung
    checklistScrollAction: SharedFlow<NoteEditorViewModel.ChecklistScrollAction>,  // 🆕 v1.9.0 (F14): Scroll action on check/un-check
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

    // 🆕 v1.8.1 (IMPL_05): Auto-Scroll bei Zeilenumbruch
    var scrollToItemIndex by remember { mutableStateOf<Int?>(null) }

    // 🆕 v1.8.2 (IMPL_10): Kontrollierter Scroll zum neuen Item (verhindert Sprung ans Ende)
    var scrollToNewItemIndex by remember { mutableStateOf<Int?>(null) }

    // 🆕 v1.10.0-P2: Minimal scroll — only ensure new item is visible, don't force to top
    LaunchedEffect(scrollToNewItemIndex) {
        scrollToNewItemIndex?.let { index ->
            // Wait one frame for the new item to be laid out
            delay(Constants.CHECKLIST_SCROLL_LAYOUT_DELAY_MS)
            val layoutInfo = listState.layoutInfo
            val viewportEnd = layoutInfo.viewportEndOffset
            val viewportStart = layoutInfo.viewportStartOffset
            val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == index }
            if (itemInfo != null) {
                val itemBottom = itemInfo.offset + itemInfo.size
                val itemTop = itemInfo.offset
                when {
                    itemBottom > viewportEnd ->
                        // Partially below viewport — scroll just enough to show it
                        listState.animateScrollBy((itemBottom - viewportEnd).toFloat())
                    itemTop < viewportStart ->
                        // Partially above viewport — scroll up just enough
                        listState.animateScrollBy((itemTop - viewportStart).toFloat())
                    // else: fully visible → no scroll needed
                }
            } else {
                // Item not yet visible (far away) — fallback: scroll to it
                listState.animateScrollToItem(index)
            }
            scrollToNewItemIndex = null
        }
    }

    // 🆕 v1.9.0 (F14): Scroll action handler for check/un-check
    LaunchedEffect(Unit) {
        checklistScrollAction.collect { action ->
            when (action) {
                is NoteEditorViewModel.ChecklistScrollAction.ScrollToTop -> {
                    // Un-check → scroll smoothly to the very top of the list
                    listState.animateScrollToItem(index = 0, scrollOffset = 0)
                }
                is NoteEditorViewModel.ChecklistScrollAction.NoScroll -> {
                    // Check → intentionally do nothing.
                    // LazyColumn uses stable keys (item.id), so Compose preserves
                    // the scroll position naturally during recomposition.
                }
            }
        }
    }

    // 🆕 v1.8.2 (IMPL_10): Berechne Visual-Index für neues Item bei focusNewItemId
    LaunchedEffect(focusNewItemId) {
        focusNewItemId?.let { itemId ->
            val dataIndex = items.indexOfFirst { it.id == itemId }
            if (dataIndex >= 0) {
                val hasSeparator = currentSortOption == ChecklistSortOption.MANUAL ||
                    currentSortOption == ChecklistSortOption.UNCHECKED_FIRST
                val unchecked = items.count { !it.isChecked }
                val visualIndex = if (hasSeparator && dataIndex >= unchecked) {
                    dataIndex + 1  // +1 für Separator
                } else {
                    dataIndex
                }
                scrollToNewItemIndex = visualIndex
            }
        }
    }

    // 🆕 v1.8.0 (IMPL_017 + IMPL_020): Separator nur bei MANUAL und UNCHECKED_FIRST anzeigen
    val uncheckedCount = items.count { !it.isChecked }
    val checkedCount = items.count { it.isChecked }
    val shouldShowSeparator = currentSortOption == ChecklistSortOption.MANUAL || 
                              currentSortOption == ChecklistSortOption.UNCHECKED_FIRST
    val showSeparator = shouldShowSeparator && (
        (uncheckedCount > 0 && checkedCount > 0) ||
        // 🆕 v1.8.2 (IMPL_26): Separator während Drag beibehalten wenn er vorher sichtbar war.
        // Wenn das letzte Item einer Seite über den Separator gezogen wird, wird ein Count 0.
        // Ohne diesen Guard verschwindet der Separator → visualItemCount ändert sich →
        // draggingItemIndex zeigt auf falschen Slot → Drag bricht ab.
        // dragDropState.separatorVisualIndex hat noch den Wert der VORHERIGEN Composition
        // (SideEffect läuft erst nach Composition) → >= 0 = Separator war vorher sichtbar.
        (dragDropState.draggingItemIndex != null && dragDropState.separatorVisualIndex >= 0)
    )

    Column(modifier = modifier) {
        // 🆕 v1.8.1 IMPL_14: Separator-Position für DragDropState aktualisieren
        // 🆕 v1.8.2 (IMPL_26): SideEffect statt LaunchedEffect — synchron nach Composition,
        // damit separatorVisualIndex sofort aktuell ist für den nächsten onDrag-Event
        val separatorVisualIndex = if (showSeparator) uncheckedCount else -1
        SideEffect {
            dragDropState.separatorVisualIndex = separatorVisualIndex
        }

        // 🆕 v1.8.1 + v1.8.2 (IMPL_10): Viewport-aware Auto-Scroll bei Zeilenwachstum
        // Scrollt pixel-genau um die Differenz, statt zum nächsten Item zu springen
        LaunchedEffect(scrollToItemIndex) {
            scrollToItemIndex?.let { index ->
                delay(AUTO_SCROLL_DELAY_MS)  // Warten bis Layout-Pass abgeschlossen
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                val itemInfo = visibleItems.find { it.index == index }
                if (itemInfo != null) {
                    val viewportEnd = listState.layoutInfo.viewportEndOffset
                    val itemBottom = itemInfo.offset + itemInfo.size
                    if (itemBottom > viewportEnd) {
                        // Item ragt unter den sichtbaren Bereich — genau um die Differenz scrollen
                        listState.scroll { scrollBy((itemBottom - viewportEnd).toFloat()) }
                    }
                }
                scrollToItemIndex = null
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 🆕 v1.8.2 (IMPL_26): Unified items-Block statt drei getrennte Blöcke.
            // Bei getrennten itemsIndexed-Blöcken für unchecked/checked Items wird die
            // Composition zerstört wenn ein Item den Separator überschreitet (anderer
            // Content-Provider) → PointerInput wird destroyed → Drag abgebrochen.
            // Ein einziger items-Block bewahrt die Composition bei Key-Erhalt → Drag bleibt aktiv.
            val visualItemCount = if (showSeparator) items.size + 1 else items.size

            // Lokale Konvertierung mit aktuellem separatorVisualIndex (nicht vom dragDropState,
            // der hat ggf. noch den alten Wert bis SideEffect läuft)
            val localVisualToDataIndex = { visualIndex: Int ->
                if (!showSeparator || separatorVisualIndex < 0) visualIndex
                else if (visualIndex > separatorVisualIndex) visualIndex - 1
                else visualIndex
            }

            items(
                count = visualItemCount,
                key = { visualIndex ->
                    if (showSeparator && visualIndex == separatorVisualIndex) {
                        "separator"
                    } else {
                        items[localVisualToDataIndex(visualIndex)].id
                    }
                },
                contentType = { visualIndex ->
                    if (showSeparator && visualIndex == separatorVisualIndex) "separator"
                    else "checklist_item"
                }
            ) { visualIndex ->
                if (showSeparator && visualIndex == separatorVisualIndex) {
                    CheckedItemsSeparator(
                        checkedCount = checkedCount,
                        isDragActive = dragDropState.draggingItemIndex != null
                    )
                } else {
                    val dataIndex = localVisualToDataIndex(visualIndex)
                    val item = items[dataIndex]
                    DraggableChecklistItem(
                        item = item,
                        visualIndex = visualIndex,
                        dragDropState = dragDropState,
                        focusNewItemId = focusNewItemId,
                        onTextChange = onTextChange,
                        onCheckedChange = { id, checked ->
                            // 🔧 v1.9.0 (F14 fix): When checking the first visible item,
                            // pre-request scroll to index 0. requestScrollToItem runs DURING
                            // the next layout pass, overriding LazyColumn's key-tracking
                            // which would otherwise follow the checked item to the bottom.
                            if (checked && listState.firstVisibleItemIndex == 0) {
                                listState.requestScrollToItem(0, 0)
                            }
                            onCheckedChange(id, checked)
                        },
                        onDelete = onDelete,
                        onAddNewItemAfter = onAddNewItemAfter,
                        onFocusHandled = onFocusHandled,
                        onHeightChanged = { scrollToItemIndex = visualIndex }
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

/** 🆕 v1.9.0: TopAppBar title with optional autosave confirmation indicator. */
@Composable
private fun NoteEditorToolbarTitle(
    toolbarTitle: ToolbarTitle,
    autosaveIndicatorVisible: Boolean
) {
    Column {
        Text(
            text = when (toolbarTitle) {
                ToolbarTitle.NEW_NOTE -> stringResource(R.string.new_note)
                ToolbarTitle.EDIT_NOTE -> stringResource(R.string.edit_note)
                ToolbarTitle.NEW_CHECKLIST -> stringResource(R.string.new_checklist)
                ToolbarTitle.EDIT_CHECKLIST -> stringResource(R.string.edit_checklist)
            }
        )
        AnimatedVisibility(
            visible = autosaveIndicatorVisible,
            enter = fadeIn(animationSpec = tween(Constants.AUTOSAVE_INDICATOR_FADE_MS)),
            exit = fadeOut(animationSpec = tween(Constants.AUTOSAVE_INDICATOR_FADE_MS))
        ) {
            Text(
                text = stringResource(R.string.autosave_indicator),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
