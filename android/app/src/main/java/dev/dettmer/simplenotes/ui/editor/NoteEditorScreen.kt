package dev.dettmer.simplenotes.ui.editor

import dev.dettmer.simplenotes.utils.Logger
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.dettmer.simplenotes.BuildConfig
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.markdown.MarkdownEngine
import dev.dettmer.simplenotes.markdown.MarkdownPreview
import dev.dettmer.simplenotes.models.ChecklistSortOption
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.ui.editor.components.CheckedItemsSeparator
import dev.dettmer.simplenotes.ui.editor.components.ChecklistItemRow
import dev.dettmer.simplenotes.ui.editor.components.ChecklistSortDialog
import dev.dettmer.simplenotes.ui.editor.components.ChecklistTargetPickerDialog
import dev.dettmer.simplenotes.ui.editor.components.MarkdownToolbar
import dev.dettmer.simplenotes.ui.main.components.DeleteConfirmationDialog
import dev.dettmer.simplenotes.ui.main.components.NoteColorPickerSheet
import dev.dettmer.simplenotes.ui.theme.NoteColorPalette
import dev.dettmer.simplenotes.utils.Constants
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import android.content.ClipData
import android.os.Build
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard

private const val LAYOUT_DELAY_MS = 100L
private const val AUTO_SCROLL_DELAY_MS = 50L
private const val ITEM_CORNER_RADIUS_DP = 8
private const val DRAGGING_ITEM_Z_INDEX = 10f
// 🆕 v2.5.0: Z-Index für Items in Check/Uncheck-Animation. Liegt zwischen 0f
// (Standard-Items) und DRAGGING_ITEM_Z_INDEX (10f). Hebt das animierende Item
// während der LazyColumn-Placement-Animation über seine Nachbarn — behebt die
// Asymmetrie, dass aufwärts wandernde Items (Uncheck) sonst hinter Nachbarn
// gezeichnet werden, weil ihre neue (niedrigere) Composition-Position sie früher
// im Draw-Tree platziert. Drag dominiert weiterhin (10f > 5f).
private const val CHECKING_ITEM_Z_INDEX = 5f
// 🆕 v2.5.0: Gesamt-Dauer der Check-Tap-Animation in Millisekunden. Deckt
// Scale-Up (CHECK_ANIM_SCALE_UP_MS = 80ms) + Spring-Back + Glow-Fade-Out
// (CHECK_GLOW_FADE_OUT_MS = 450ms) + LazyColumn-Placement-Spring komfortabel ab.
// Wird vom LaunchedEffect in DraggableChecklistItem genutzt, um isCheckAnimating
// zurückzusetzen (deterministischer Reset statt finishedListener-Race).
private const val CHECK_ANIMATION_TOTAL_MS = 500L
// 🔧 v2.5.x: Maximale Wartezeit, bis das initiale LazyColumn-Layout als „stabil"
// gilt. Sicherheitsnetz für den Settle-LaunchedEffect in ChecklistEditor: falls externe
// Effekte (Text-Messung, dynamische Höhen, Sync-Updates) die Liste länger als diese
// Schwelle in Bewegung halten, wird das Placement-Animation-Gate trotzdem geöffnet,
// damit Reorder-Animationen nach Check/Uncheck garantiert spielen.
// 250 ms ≈ 15 Frames @ 60Hz — genug Puffer für Cold-Open auch auf langsamen
// Geräten, kurz genug, dass User es nicht wahrnehmen.
private const val INITIAL_LAYOUT_SETTLE_TIMEOUT_MS = 250L
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
fun NoteEditorScreen(viewModel: NoteEditorViewModel, onNavigateBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val checklistItems by viewModel.checklistItems.collectAsState()

    // 🌟 v1.6.0: Offline mode state
    val isOfflineMode by viewModel.isOfflineMode.collectAsState()

    // 🔧 v2.3.0: Block ALL rendering until async note load completes.
    // Must be before any remember/LaunchedEffect blocks so they never
    // execute with stale UiState defaults (noteType=TEXT, isNewNote=true).
    if (uiState.isLoading) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {}
        return
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    // 🆕 v2.0.1: Markdown Preview default for existing TEXT notes
    // v2.8.0: savedPreviewMode persists across TEXT↔CHECKLIST type changes so Undo
    // can restore the exact mode the user was in before the conversion — not just the
    // global default. Initialized from the user preference so first open is correct.
    var savedPreviewMode by remember { mutableStateOf(uiState.defaultStartInPreviewMode) }
    // Both isNewNote and noteType are keys so the value is recomputed synchronously
    // (in the same frame) whenever the type changes — avoids a one-frame flash.
    var isPreviewMode by remember(uiState.isNewNote, uiState.noteType) {
        mutableStateOf(
            when {
                uiState.isNewNote -> false
                uiState.noteType == NoteType.CHECKLIST -> false
                else -> savedPreviewMode // TEXT: restore saved value (covers Undo)
            }
        )
    }
    var showChecklistSortDialog by remember { mutableStateOf(false) } // 🔀 v1.8.0
    val lastChecklistSortOption by viewModel.lastChecklistSortOption.collectAsState() // 🔀 v1.8.0
    val autosaveIndicatorVisible by viewModel.autosaveIndicatorVisible.collectAsState() // 🆕 v1.9.0
    val canUndo by viewModel.canUndo.collectAsState() // 🆕 v1.10.0
    val canRedo by viewModel.canRedo.collectAsState() // 🆕 v1.10.0
    var showOverflowMenu by remember { mutableStateOf(false) } // 🆕 v1.10.0-Papa
    var showColorPicker by remember { mutableStateOf(false) }  // 🆕 v2.5.0
    var showConvertDialog by remember { mutableStateOf(false) }
    var focusNewItemId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 🆕 v2.2.0: Checklist Item Context Menu — State für Aktion 3
    var copyToChecklistItemId by remember { mutableStateOf<String?>(null) }
    val otherChecklists by viewModel.otherChecklists.collectAsState()
    val clipboard = LocalClipboard.current

    // v2.0.1: Compact toolbar for narrow displays or large font scale (Issue #48)
    // Uses LocalWindowInfo (preferred for foldable/multi-window) over LocalConfiguration.
    // effectiveWidth = window dp / fontScale — if < 360, Undo/Redo go to overflow and title is shortened
    val isCompactToolbar = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.width.toDp().value / fontScale < 360f
    }

    // 🆕 v2.5.0: Resolve note accent colour for the 3 dp stripe below the TopAppBar.
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val noteAccentColor: Color? = NoteColorPalette
        .resolveContainer(uiState.color, isDark)
        .takeIf { it != Color.Unspecified }

    // Strings for toast messages (avoid LocalContextGetResourceValueCall lint)
    val msgNoteIsEmpty = stringResource(R.string.note_is_empty)
    val msgNoteSaved = stringResource(R.string.note_saved)
    val msgNoteDeleted = stringResource(R.string.note_deleted)
    val msgItemCopiedToChecklist = stringResource(R.string.checklist_item_copied_toast) // 🆕 v2.2.0
    val msgNoteCopied = stringResource(R.string.toast_note_copied)

    // v1.5.0: Auto-keyboard support
    val keyboardController = LocalSoftwareKeyboardController.current
    val titleFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }

    // 🆕 v1.9.0 (F07): Lifted TextFieldState for toolbar access
    val textFieldState = rememberTextFieldState(initialText = uiState.content)

    // v2.0.0: Register content provider so saveOnBack() can read the latest
    // TextFieldState content directly — avoids snapshotFlow race condition
    DisposableEffect(textFieldState) {
        viewModel.contentProvider = { textFieldState.text.toString() }
        onDispose { viewModel.contentProvider = null }
    }

    // Cursor ans Ende setzen wenn Content geladen wird (einmalig)
    // 🔧 v2.3.0 (FIX-011): Sync TextFieldState when content arrives from async storage load.
    // rememberTextFieldState only uses initialText on first call; this LaunchedEffect handles
    // the case where uiState.content is updated after first composition.
    LaunchedEffect(uiState.content) {
        if (textFieldState.text.isEmpty() && uiState.content.isNotEmpty()) {
            textFieldState.edit {
                replace(0, length, uiState.content)
                placeCursorAtEnd()
            }
        }
    }

    // 🆕 v1.9.0 (F07): Auto-show keyboard when switching from preview → edit
    // v2.8.0: Also keeps savedPreviewMode in sync with isPreviewMode while in TEXT mode.
    // Guard !isNewNote prevents overwriting savedPreviewMode during the initial composition
    // (when isPreviewMode is false because the note hasn't loaded yet).
    LaunchedEffect(isPreviewMode) {
        if (uiState.noteType == NoteType.TEXT && !uiState.isNewNote) {
            savedPreviewMode = isPreviewMode
        }
        if (!isPreviewMode && uiState.noteType == NoteType.TEXT && !uiState.isNewNote) {
            delay(LAYOUT_DELAY_MS)
            contentFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // v1.5.0: Auto-focus and show keyboard
    // v2.0.1: Skip auto-focus for existing TEXT notes (they start in preview mode)
    // v2.8.0: Show keyboard when existing TEXT note loads directly in edit mode (user preference)
    LaunchedEffect(uiState.isNewNote) {
        delay(LAYOUT_DELAY_MS) // Wait for layout
        when {
            uiState.isNewNote -> {
                // New note: focus title
                titleFocusRequester.requestFocus()
                keyboardController?.show()
            }
            !isPreviewMode && uiState.noteType == NoteType.TEXT -> {
                // Existing note opened in edit mode via user preference — isPreviewMode stays
                // false (no state change), so LaunchedEffect(isPreviewMode) never fires here.
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
                        ToastMessage.ITEM_COPIED_TO_CHECKLIST -> msgItemCopiedToChecklist // 🆕 v2.2.0
                    }
                    scope.launch { snackbarHostState.showSnackbar(message) }
                }
                is NoteEditorEvent.NavigateBack -> onNavigateBack()
                is NoteEditorEvent.ShowDeleteConfirmation -> showDeleteDialog = true
                is NoteEditorEvent.RestoreContent -> { // 🆕 v1.10.0: Undo/Redo
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
                is NoteEditorEvent.CopyToClipboard -> {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", event.text)))
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            snackbarHostState.showSnackbar(msgNoteCopied)
                        }
                    }
                }
                is NoteEditorEvent.ActivatePreviewMode -> { isPreviewMode = uiState.defaultStartInPreviewMode }
            }
        }
    }

    // Collect snackbar messages from Activity-originated actions (share, PDF, etc.)
    LaunchedEffect(Unit) {
        viewModel.showSnackbar.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    NoteEditorToolbarTitle(
                        toolbarTitle = uiState.toolbarTitle,
                        autosaveIndicatorVisible = autosaveIndicatorVisible,
                        compact = isCompactToolbar
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

                    // v2.0.1: Undo/Redo in toolbar for wide displays, overflow for narrow (Issue #48)
                    if (!isCompactToolbar) {
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
                    }

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
                            shape = MaterialTheme.shapes.large, // 🆕 v1.10.0-P2: Rounded corners
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, // 🆕 v1.10.0-P2
                            shadowElevation = 6.dp, // 🆕 v1.10.0-P2
                            tonalElevation = 2.dp // 🆕 v1.10.0-P2
                        ) {
                            // v2.0.1: Undo/Redo in overflow only for compact displays (Issue #48)
                            if (isCompactToolbar) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.editor_undo)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Undo,
                                            contentDescription = null,
                                            tint = if (canUndo) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                            }
                                        )
                                    },
                                    enabled = canUndo,
                                    onClick = {
                                        viewModel.undo()
                                        // Don't dismiss — user may want to undo multiple times
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.editor_redo)) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Redo,
                                            contentDescription = null,
                                            tint = if (canRedo) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                            }
                                        )
                                    },
                                    enabled = canRedo,
                                    onClick = {
                                        viewModel.redo()
                                        // Don't dismiss — user may want to redo multiple times
                                    }
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_set_note_color)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Palette,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showColorPicker = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (uiState.noteType == NoteType.CHECKLIST)
                                                R.string.action_convert_to_note
                                            else
                                                R.string.action_convert_to_checklist
                                        )
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (uiState.noteType == NoteType.CHECKLIST)
                                            Icons.AutoMirrored.Outlined.Notes
                                        else
                                            Icons.Outlined.Checklist,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    showConvertDialog = true
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
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
                                text = { Text(stringResource(R.string.action_copy_note_text)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.ContentCopy,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.copyNoteText()
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
                    containerColor = MaterialTheme.colorScheme.surface // 🆕 v2.5.0: neutral bar; accent shown as stripe below
                )
            )
        },
        modifier = Modifier.imePadding()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 🆕 v2.5.0: 3 dp accent stripe — full width, outside content padding
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(noteAccentColor ?: Color.Transparent)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .wrapContentWidth(align = Alignment.CenterHorizontally) // 🆕 v1.10.0-P2: Center on tablets
                    .widthIn(max = 720.dp) // 🆕 v1.10.0-P2: Constrain width for readability
                    .fillMaxWidth() // 🆕 v1.10.0-P2: Fill up to constrained width
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
                singleLine = true, // 🆕 v1.8.2 (IMPL_09): Enter navigiert statt Newline
                // 🆕 v1.8.2: Auto-Großschreibung für Wortanfänge im Titel
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next // 🆕 v1.8.2 (IMPL_09): Weiter-Taste
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
                        currentSortOption = lastChecklistSortOption, // 🔀 v1.8.0
                        checklistScrollAction = viewModel.checklistScrollAction, // 🆕 v1.9.0 (F14)
                        onTextChange = { id, text -> viewModel.updateChecklistItemText(id, text) },
                        onCheckedChange = { id, checked ->
                            viewModel.updateChecklistItemChecked(id, checked)
                        },
                        onDelete = { id -> viewModel.deleteChecklistItem(id) },
                        onAddNewItemAfter = { id ->
                            val newId = viewModel.addChecklistItemAfter(id)
                            focusNewItemId = newId
                        },
                        onCopyText = { itemId ->                                     // 🆕 v2.2.0
                            val text = checklistItems.find { it.id == itemId }?.text
                            if (!text.isNullOrBlank()) {
                                scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", text))) }
                            }
                        },
                        onDuplicate = { itemId ->                                    // 🆕 v2.2.0
                            val newId = viewModel.duplicateChecklistItem(itemId)
                            if (newId != null) {
                                focusNewItemId = newId
                            }
                        },
                        onCopyToChecklist = { itemId ->                              // 🆕 v2.2.0
                            copyToChecklistItemId = itemId
                            viewModel.loadOtherChecklists()
                        },
                        onAddItemAtEnd = {
                            val newId = viewModel.addChecklistItemAtEnd()
                            focusNewItemId = newId
                        },
                        onMove = { from, to -> viewModel.moveChecklistItem(from, to) },
                        onFocusHandled = { focusNewItemId = null },
                        onSortClick = { showChecklistSortDialog = true }, // 🔀 v1.8.0
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
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

    // 🆕 v2.5.0: Note color picker sheet
    if (showColorPicker) {
        NoteColorPickerSheet(
            currentColor = uiState.color,
            onColorSelected = { hex ->
                viewModel.setColor(hex)
            },
            onDismiss = { showColorPicker = false },
        )
    }

    if (showConvertDialog) {
        AlertDialog(
            onDismissRequest = { showConvertDialog = false },
            title = {
                Text(
                    stringResource(
                        if (uiState.noteType == NoteType.CHECKLIST)
                            R.string.action_convert_to_note
                        else
                            R.string.action_convert_to_checklist
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        if (uiState.noteType == NoteType.CHECKLIST)
                            R.string.confirm_convert_to_note
                        else
                            R.string.confirm_convert_to_checklist
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConvertDialog = false
                    viewModel.convertNoteType()
                }) {
                    Text(stringResource(R.string.action_convert))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConvertDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 🆕 v2.2.0: Checklist Target Picker Dialog (Aktion 3)
    otherChecklists?.let { checklists ->
        ChecklistTargetPickerDialog(
            checklists = checklists,
            onSelect = { targetNoteId ->
                copyToChecklistItemId?.let { itemId ->
                    viewModel.copyItemToChecklist(itemId, targetNoteId)
                }
                copyToChecklistItemId = null
            },
            onDismiss = {
                viewModel.dismissChecklistPicker()
                copyToChecklistItemId = null
            }
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
            .drop(1) // 🆕 v1.9.0: skip initial emission — snapshotFlow always emits current
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
    dragDropState: DragDropListState,
    focusNewItemId: String?,
    onTextChange: (String, String) -> Unit,
    onCheckedChange: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onAddNewItemAfter: (String) -> Unit,
    onCopyText: (String) -> Unit,          // 🆕 v2.2.0: Aktion 1
    onDuplicate: (String) -> Unit,         // 🆕 v2.2.0: Aktion 2
    onCopyToChecklist: (String) -> Unit,   // 🆕 v2.2.0: Aktion 3
    onFocusHandled: () -> Unit,
    onHeightChanged: () -> Unit, // 🆕 v1.8.1 (IMPL_05)
    placementAnimationsEnabled: Boolean // 🔧 v2.5.x: Gate gegen Open-Burst
) {
    // 🆕 v2.0.0 (IMPL_29b): Key-basiertes isDragging statt Index-basiert.
    // Index-basiert hat Timing-Lücke: draggingItemIndex (aus visibleItemsInfo, OLD) vs.
    // visualIndex (aus Composition, NEW) für 1-2 Frames nach jedem Swap → sichtbarer Sprung.
    // Key-basiert ist immun gegen Layout/Composition-Desync.
    val isDragging = dragDropState.isDraggingItem(item.id) && dragDropState.isDragConfirmed
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

    // 🆕 v2.5.0: Trigger für Check-Tap-Animation. Wird beim Checkbox-Tap auf true
    // gesetzt (über onCheckboxTap-Callback an die Row), und nach CHECK_ANIMATION_TOTAL_MS
    // automatisch zurückgesetzt. Solange true → erhöhter zIndex (siehe unten) →
    // Row wird über Nachbarn gezeichnet, unabhängig von der Draw-Reihenfolge nach
    // dem Reorder. Key item.id: Reset-Timer überlebt Reorder, weil Composition
    // erhalten bleibt.
    var isCheckAnimating by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(isCheckAnimating, item.id) {
        if (isCheckAnimating) {
            delay(CHECK_ANIMATION_TOTAL_MS)
            isCheckAnimating = false
        }
    }

    ChecklistItemRow(
        item = item,
        onTextChange = { onTextChange(item.id, it) },
        onCheckedChange = { onCheckedChange(item.id, it) },
        onDelete = { onDelete(item.id) },
        onAddNewItem = { onAddNewItemAfter(item.id) },
        onCopyText = { onCopyText(item.id) },             // 🆕 v2.2.0
        onDuplicate = { onDuplicate(item.id) },            // 🆕 v2.2.0
        onCopyToChecklist = { onCopyToChecklist(item.id) },// 🆕 v2.2.0
        isCheckAnimating = isCheckAnimating,               // 🆕 v2.5.0
        onCheckboxTap = { isCheckAnimating = true },       // 🆕 v2.5.0
        requestFocus = shouldFocus,
        isDragging = isDragging,
        isAnyItemDragging = dragDropState.isAnyItemDragging,
        dragModifier = Modifier.dragContainer(
            dragDropState = dragDropState,
            itemKey = item.id
        ),
        onHeightChanged = onHeightChanged, // 🆕 v1.8.1 (IMPL_05)
        modifier = Modifier
            // 🆕 v1.8.2 (IMPL_11): Placement-Animation für nicht-gedraggte Items.
            // Historisch (vor IMPL_11): animateItem() mit fadeInSpec/fadeOutSpec auf ALLEN Items
            // → sichtbares Flickering bei langen Items beim schnellen Scrollen.
            // Lösung damals: fade entfernen, animateItem nur während Drag.
            // 🆕 v2.5.0: Bedingung von „nur während Drag" auf „immer außer beim gedraggten Item"
            // erweitert. Grund: Beim Check/Uncheck-Sort (außerhalb DnD) gab es vorher keine
            // Placement-Animation → Items sprangen abrupt. Da fadeInSpec/fadeOutSpec weiterhin
            // null sind, gilt die ursprüngliche Flicker-Ursache nicht. Der gedraggte Item bleibt
            // ausgenommen, weil seine Position via graphicsLayer.translationY gesteuert wird.
            // 🔧 v2.5.x: Zusätzliches Gate `placementAnimationsEnabled` verhindert, dass
            // Modifier.animateItem zwischen den ersten Layout-Pässen nach Open der Checkliste
            // die Offset-Diffs animiert (Symptom: Items „schieben sich rein"). Sobald das
            // initiale Layout stabil ist, ist das Gate offen — alle Check/Uncheck-Reorder-
            // Animationen aus Commit 7156c12 spielen unverändert.
            .then(
                if (!isDragging && placementAnimationsEnabled) {
                    Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)
                } else {
                    Modifier
                }
            )
            // 🆕 v2.5.0: zIndex-Tabelle:
            //   isDragging              → DRAGGING_ITEM_Z_INDEX (10f)  – Drag wins
            //   isCheckAnimating        → CHECKING_ITEM_Z_INDEX  (5f)  – Glow/Move on top
            //   sonst                   → 0f
            .zIndex(
                when {
                    isDragging -> DRAGGING_ITEM_Z_INDEX
                    isCheckAnimating -> CHECKING_ITEM_Z_INDEX
                    else -> 0f
                }
            )
            .graphicsLayer {
                if (isDragging) translationY = dragDropState.draggingItemOffset
                shadowElevation = elevation.toPx()
                shape = RoundedCornerShape(ITEM_CORNER_RADIUS_DP.dp)
                clip = isDragging || elevation > 0.dp
            }
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(ITEM_CORNER_RADIUS_DP.dp)
            )
    )
}

@Suppress("LongParameterList") // Compose functions commonly have many callback parameters
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChecklistEditor(
    items: List<ChecklistItemState>,
    scope: kotlinx.coroutines.CoroutineScope,
    focusNewItemId: String?,
    currentSortOption: ChecklistSortOption, // 🔀 v1.8.0: Aktuelle Sortierung
    checklistScrollAction: SharedFlow<NoteEditorViewModel.ChecklistScrollAction>, // 🆕 v1.9.0 (F14): Scroll action on check/un-check
    onTextChange: (String, String) -> Unit,
    onCheckedChange: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onAddNewItemAfter: (String) -> Unit,
    onCopyText: (String) -> Unit,          // 🆕 v2.2.0: Aktion 1
    onDuplicate: (String) -> Unit,         // 🆕 v2.2.0: Aktion 2
    onCopyToChecklist: (String) -> Unit,   // 🆕 v2.2.0: Aktion 3
    onAddItemAtEnd: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onFocusHandled: () -> Unit,
    onSortClick: () -> Unit, // 🔀 v1.8.0
    modifier: Modifier = Modifier
) {
    // IMPL_29q Q1: LazyLayoutCacheWindow ersetzt beyondBoundsItemCount (entfernt in Compose 1.10+).
    // Hält 0.55 × Viewport auf jeder Seite composiert → 865px Buffer (3 große Items à 271px).
    // Verhindert Composable-Recycling beim Auto-Scroll+Swap → kein toter Pointer-Scope.
    val listState = rememberLazyListState(LazyLayoutCacheWindow(0.55f, 0.55f))
    val dragDropState = rememberDragDropListState(
        lazyListState = listState,
        scope = scope,
        onMove = onMove
    )

    // 🔧 v2.5.x: Gate für LazyColumn-Placement-Animationen (Modifier.animateItem).
    // Beim Öffnen einer Checkliste durchläuft die LazyColumn mehrere Layout-Pässe
    // (Text-Messung im OutlinedTextField, dynamische Höhen via onHeightChanged,
    // LazyLayoutCacheWindow-Vorbau). Wenn animateItem in diesem Fenster aktiv ist,
    // animiert LazyListItemAnimator die Offset-Diffs zwischen den Pässen → Items
    // „schieben sich rein". Dieses Gate hält animateItem deaktiviert, bis das
    // Initial-Layout zwei aufeinanderfolgende Frames lang unverändert geblieben
    // ist (oder INITIAL_LAYOUT_SETTLE_TIMEOUT_MS abgelaufen ist). Nach Aktivierung
    // bleibt das Gate für die Lebenszeit des Screens offen — alle späteren Reorder
    // (Check/Uncheck — siehe Commit 7156c12) animieren wie vorgesehen.
    // Userinteraktionen (Tap, Drag) erfolgen lange nach dem Settle → kein Konflikt
    // mit der Check-Tap-Animation aus dem Commit.
    var placementAnimationsEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val deadline = withFrameNanos { it } + INITIAL_LAYOUT_SETTLE_TIMEOUT_MS * 1_000_000L
        var previousSnapshot: List<Pair<Any, Int>> = emptyList()
        var stableFrames = 0
        var settled = false
        while (!settled) {
            val now = withFrameNanos { it }
            val current = listState.layoutInfo.visibleItemsInfo.map { it.key to it.offset }
            if (current.isNotEmpty() && current == previousSnapshot) {
                stableFrames++
                if (stableFrames >= 2) settled = true
            } else {
                stableFrames = 0
            }
            previousSnapshot = current
            if (now >= deadline) settled = true
        }
        placementAnimationsEnabled = true
    }

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
                    currentSortOption == ChecklistSortOption.UNCHECKED_FIRST ||
                    currentSortOption == ChecklistSortOption.CREATION_DATE ||
                    currentSortOption == ChecklistSortOption.CREATION_DATE_DESC
                val unchecked = items.count { !it.isChecked }
                val visualIndex = if (hasSeparator && dataIndex >= unchecked) {
                    dataIndex + 1 // +1 für Separator
                } else {
                    dataIndex
                }
                scrollToNewItemIndex = visualIndex
            }
        }
    }

    // 🆕 v1.8.0 (IMPL_017 + IMPL_020): Separator nur bei MANUAL, UNCHECKED_FIRST und CREATION_DATE anzeigen
    val uncheckedCount = items.count { !it.isChecked }
    val checkedCount = items.count { it.isChecked }
    val shouldShowSeparator = currentSortOption == ChecklistSortOption.MANUAL ||
        currentSortOption == ChecklistSortOption.UNCHECKED_FIRST ||
        currentSortOption == ChecklistSortOption.CREATION_DATE ||
        currentSortOption == ChecklistSortOption.CREATION_DATE_DESC
    val showSeparator = shouldShowSeparator &&
        (
            (uncheckedCount > 0 && checkedCount > 0) ||
                // 🆕 v1.8.2 (IMPL_26): Separator während Drag beibehalten wenn er vorher sichtbar war.
                // Wenn das letzte Item einer Seite über den Separator gezogen wird, wird ein Count 0.
                // Ohne diesen Guard verschwindet der Separator → visualItemCount ändert sich →
                // draggingItemIndex zeigt auf falschen Slot → Drag bricht ab.
                // dragDropState.separatorVisualIndex hat noch den Wert der VORHERIGEN Composition
                // (SideEffect läuft erst nach Composition) → >= 0 = Separator war vorher sichtbar.
                (dragDropState.isAnyItemDragging && dragDropState.separatorVisualIndex >= 0)
            )

    Column(modifier = modifier) {
        // 🆕 v1.8.1 IMPL_14: Separator-Position für DragDropState aktualisieren
        // 🆕 v1.8.2 (IMPL_26): SideEffect statt LaunchedEffect — synchron nach Composition,
        // damit separatorVisualIndex sofort aktuell ist für den nächsten onDrag-Event
        val separatorVisualIndex = if (showSeparator) uncheckedCount else -1
        SideEffect {
            dragDropState.separatorVisualIndex = separatorVisualIndex
            if (BuildConfig.DEBUG && dragDropState.isAnyItemDragging) {
                Logger.d("DragDrop", "[SEPARATOR] idx=$separatorVisualIndex")
            }
        }

        // 🆕 v1.8.1 + v1.8.2 (IMPL_10): Viewport-aware Auto-Scroll bei Zeilenwachstum
        // Scrollt pixel-genau um die Differenz, statt zum nächsten Item zu springen
        LaunchedEffect(scrollToItemIndex) {
            scrollToItemIndex?.let { index ->
                delay(AUTO_SCROLL_DELAY_MS) // Warten bis Layout-Pass abgeschlossen
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
            verticalArrangement = Arrangement.spacedBy(2.dp),
            // IMPL_29f F2: Während Drag: User-Scroll deaktivieren.
            // Verhindert Gesture-Konkurrenz zwischen LazyColumn-Scroll und Drag-Gesture (RC-2).
            // Scrollen erfolgt ausschließlich über programmatisches Auto-Scroll.
            userScrollEnabled = !dragDropState.isAnyItemDragging
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
                if (!showSeparator || separatorVisualIndex < 0) {
                    visualIndex
                } else if (visualIndex > separatorVisualIndex) {
                    visualIndex - 1
                } else {
                    visualIndex
                }
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
                    if (showSeparator && visualIndex == separatorVisualIndex) {
                        "separator"
                    } else {
                        "checklist_item"
                    }
                }
            ) { visualIndex ->
                if (showSeparator && visualIndex == separatorVisualIndex) {
                    CheckedItemsSeparator(
                        checkedCount = checkedCount,
                        isDragActive = dragDropState.isAnyItemDragging,
                        modifier = if (placementAnimationsEnabled) Modifier.animateItem() else Modifier
                    )
                } else {
                    val dataIndex = localVisualToDataIndex(visualIndex)
                    val item = items[dataIndex]
                    DraggableChecklistItem(
                        item = item,
                        dragDropState = dragDropState,
                        focusNewItemId = focusNewItemId,
                        onTextChange = onTextChange,
                        onCheckedChange = { id, checked ->
                            // 🔧 v2.5.x (Dual-Fix Top + Bottom): Pre-anchor the LazyColumn before
                            // the model reorder so LazyListItemAnimator captures from/to coords
                            // against a stable anchor. requestScrollToItem runs synchronously in
                            // the next measure pass and clears LazyListState.lastKnownFirstItemKey,
                            // disabling key-based re-anchoring that would otherwise follow the
                            // toggled item to its new position and shift the viewport (→ animation
                            // would run over ~0 visible delta and be invisible to the user).
                            //
                            // Two viewport regimes need different anchoring strategies:
                            //
                            // A) Viewport at top (firstVisibleItemIndex == 0): pin index 0 at the
                            //    viewport top via requestScrollToItem(0, 0). This is the proven
                            //    7156c12 path and covers the index-0 toggle as well as any toggle
                            //    while the user is scrolled to the very top. Using the generalized
                            //    "first non-toggled visible item" anchor here breaks the idx-0 case
                            //    because it forces a non-zero index with negative scrollOffset,
                            //    which has edge-case behaviour at the top boundary.
                            //
                            // B) Viewport scrolled away from top (firstVisibleItemIndex > 0):
                            //    snapshot the first visible item that is NOT the toggled item,
                            //    then requestScrollToItem(anchor.index, -anchor.offset) to keep
                            //    its current pixel position. The toggled item is excluded so it
                            //    is free to animate to its new index; all other visible items
                            //    stay anchored. Covers the bottom-viewport case (toggled item
                            //    crossing the CheckedItemsSeparator).
                            if (listState.firstVisibleItemIndex == 0) {
                                // 🔧 v2.5.x Phase 2: Pre-Anchor preserves the user's exact scroll
                                // offset within item 0 instead of snapping to (0,0). The original
                                // requestScrollToItem(0, 0) call snapped the viewport up by the
                                // current firstVisibleItemScrollOffset (e.g. 92–175 px), which
                                // landed in the SAME measure pass as the data-driven reorder.
                                // LazyListItemAnimator then interpreted the combined layout
                                // change as a scroll (not as a reorder) and skipped
                                // Modifier.animateItem placement animation entirely — the toggled
                                // item teleported to its new position, scale-pop & glow fired on
                                // an already-displaced item, visually "nothing happened".
                                //
                                // Diagnose-Logs (Phase 1) compared s2 (offset=0, requestScrollToItem
                                // is a no-op → ~30 interpolated PLACE frames) with s3–s6 (offset
                                // 92–175 → 1 PLACE frame, then static). Calling
                                // requestScrollToItem(0, currentOffset) still sets the anchor
                                // key to item-0 (preventing re-anchor onto the toggled item) but
                                // does not move the viewport, so LazyListItemAnimator captures
                                // valid from/to coords and the placement animation runs.
                                val preservedOffset = listState.firstVisibleItemScrollOffset
                                listState.requestScrollToItem(0, preservedOffset)
                            } else {
                                val layoutInfo = listState.layoutInfo
                                val anchor = layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                    info.key != id
                                } ?: layoutInfo.visibleItemsInfo.firstOrNull()
                                if (anchor != null) {
                                    listState.requestScrollToItem(anchor.index, -anchor.offset)
                                }
                            }
                            onCheckedChange(id, checked)
                        },
                        onDelete = onDelete,
                        onAddNewItemAfter = onAddNewItemAfter,
                        onCopyText = onCopyText,                 // 🆕 v2.2.0
                        onDuplicate = onDuplicate,               // 🆕 v2.2.0
                        onCopyToChecklist = onCopyToChecklist,   // 🆕 v2.2.0
                        onFocusHandled = onFocusHandled,
                        onHeightChanged = { scrollToItemIndex = visualIndex },
                        placementAnimationsEnabled = placementAnimationsEnabled,
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
private fun NoteEditorToolbarTitle(toolbarTitle: ToolbarTitle, autosaveIndicatorVisible: Boolean, compact: Boolean = false) {
    Column {
        Text(
            text = if (compact) {
                when (toolbarTitle) {
                    ToolbarTitle.NEW_NOTE, ToolbarTitle.NEW_CHECKLIST -> stringResource(R.string.toolbar_title_new)
                    ToolbarTitle.EDIT_NOTE, ToolbarTitle.EDIT_CHECKLIST -> stringResource(R.string.toolbar_title_edit)
                }
            } else {
                when (toolbarTitle) {
                    ToolbarTitle.NEW_NOTE -> stringResource(R.string.new_note)
                    ToolbarTitle.EDIT_NOTE -> stringResource(R.string.edit_note)
                    ToolbarTitle.NEW_CHECKLIST -> stringResource(R.string.new_checklist)
                    ToolbarTitle.EDIT_CHECKLIST -> stringResource(R.string.edit_checklist)
                }
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
