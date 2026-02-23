package dev.dettmer.simplenotes.ui.editor

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import dev.dettmer.simplenotes.ui.main.components.DeleteConfirmationDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
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
                        scrollToRestoredItemId = viewModel.scrollToRestoredItemId,  // 🆕 v1.9.0 (F04)
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
    // 🆕 v1.8.2 (IMPL_07): Migration zu TextFieldState-API für scrollState-Unterstützung
    val textFieldState = rememberTextFieldState(initialText = content)
    val scrollState = rememberScrollState()
    
    // Focus-State tracken für Auto-Scroll bei Tastaturöffnung
    var isFocused by remember { mutableStateOf(false) }
    
    // Cursor ans Ende setzen wenn Content geladen wird (einmalig)
    LaunchedEffect(Unit) {
        if (content.isNotEmpty()) {
            textFieldState.edit { placeCursorAtEnd() }
        }
    }
    
    // Text-Änderungen an ViewModel propagieren
    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
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
    scrollToRestoredItemId: SharedFlow<String>,  // 🆕 v1.9.0 (F04): Scroll to restored item on un-check
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

    // 🆕 v1.8.2 (IMPL_10): Scroll vor Fokus — Item muss sichtbar sein bevor fokussiert wird
    LaunchedEffect(scrollToNewItemIndex) {
        scrollToNewItemIndex?.let { index ->
            // Scroll zum neuen Item (nicht zum nächsten — item soll oben/mittig sichtbar sein)
            listState.animateScrollToItem(
                index = index,
                scrollOffset = 0
            )
            scrollToNewItemIndex = null
        }
    }

    // 🆕 v1.9.0 (F04): Scroll to an item that was restored to its original position on un-check
    LaunchedEffect(Unit) {
        scrollToRestoredItemId.collect { itemId ->
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
                listState.animateScrollToItem(index = visualIndex, scrollOffset = 0)
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
                        onCheckedChange = onCheckedChange,
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
