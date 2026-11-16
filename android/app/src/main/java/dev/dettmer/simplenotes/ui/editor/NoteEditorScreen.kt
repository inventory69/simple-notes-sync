package dev.dettmer.simplenotes.ui.editor

import android.content.ClipData
import android.content.res.Configuration
import android.os.Build
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.markdown.MarkdownEngine
import dev.dettmer.simplenotes.markdown.MarkdownOutputTransformation
import dev.dettmer.simplenotes.markdown.MarkdownPreview
import dev.dettmer.simplenotes.markdown.TapAnchor
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.ui.editor.components.ChecklistSortDialog
import dev.dettmer.simplenotes.ui.editor.components.ChecklistTargetPickerDialog
import dev.dettmer.simplenotes.ui.editor.components.EditorBarTitle
import dev.dettmer.simplenotes.ui.editor.components.MarkdownToolbar
import dev.dettmer.simplenotes.ui.editor.components.NoteMetaLine
import dev.dettmer.simplenotes.ui.editor.components.NoteStatsPill
import dev.dettmer.simplenotes.ui.editor.components.SaveStateButton
import dev.dettmer.simplenotes.ui.editor.components.SheetTitleField
import dev.dettmer.simplenotes.ui.editor.components.WordCounterVisibility
import dev.dettmer.simplenotes.ui.editor.components.collapseUp
import dev.dettmer.simplenotes.ui.editor.components.noteColorGradient
import dev.dettmer.simplenotes.ui.editor.components.rememberImeOpenFraction
import dev.dettmer.simplenotes.ui.editor.components.shrinkWithKeyboard
import dev.dettmer.simplenotes.ui.main.components.NoteColorPickerSheet
import dev.dettmer.simplenotes.ui.theme.Dimensions
import dev.dettmer.simplenotes.ui.theme.LocalFontSizeMultiplier
import dev.dettmer.simplenotes.ui.theme.LocalNewDesign
import dev.dettmer.simplenotes.ui.theme.NoteColorPalette
import dev.dettmer.simplenotes.utils.AssetReferences
import dev.dettmer.simplenotes.utils.NoteShareHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal const val LAYOUT_DELAY_MS = 100L

// UI-Beta: Überblenden zwischen Lesen und Bearbeiten
private const val MODE_CROSSFADE_MS = 180
private const val IME_HIDE_TIMEOUT_MS = 600L

/**
 * Main Composable for the Note Editor screen.
 *
 * v1.5.0: Jetpack Compose NoteEditor Redesign
 * - Supports both TEXT and CHECKLIST notes
 * - Drag & Drop reordering for checklist items
 * - Auto-keyboard focus for new items
 */
// Abbau: TECH_DEBT_ROADMAP.md Slice 5
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun NoteEditorScreen(viewModel: NoteEditorViewModel, onNavigateBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val checklistItems by viewModel.checklistItems.collectAsState()
    // 🆕 (#126-Nachgang): Anzeigemodus der Statistik-Pille, notizübergreifend gemerkt.
    val noteStatsMode by viewModel.noteStatsMode.collectAsState()

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

    // 🆕 v2.0.1: Markdown Preview default for existing TEXT notes
    // v2.8.0: savedPreviewMode persists across TEXT↔CHECKLIST type changes so Undo
    // can restore the exact mode the user was in before the conversion — not just the
    // global default. Initialized from the user preference so first open is correct.
    var savedPreviewMode by rememberSaveable { mutableStateOf(uiState.defaultStartInPreviewMode) }
    // 🆕 Issue #156: Gegenstück für Checklisten. Nur eine als Checkliste geöffnete Notiz startet
    // im Lesemodus; nach TEXT→CHECKLIST-Konvertierung bleibt es beim Bearbeiten.
    var savedChecklistReadMode by rememberSaveable {
        mutableStateOf(uiState.noteType == NoteType.CHECKLIST && uiState.checklistStartInReadMode)
    }
    // Both isNewNote and noteType are keys so the value is recomputed synchronously
    // (in the same frame) whenever the type changes — avoids a one-frame flash.
    // rememberSaveable: Modus überlebt Drehen/Größenwechsel (Activity hat kein configChanges).
    var isPreviewMode by rememberSaveable(uiState.isNewNote, uiState.noteType) {
        mutableStateOf(
            when {
                uiState.isNewNote -> false
                uiState.noteType == NoteType.CHECKLIST -> savedChecklistReadMode
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
    var showColorPicker by remember { mutableStateOf(false) } // 🆕 v2.5.0
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

    // UI-Beta: Editor als Blatt (E1-E11). Alter Pfad bleibt unverändert, bis die Beta entschieden ist.
    val newDesign = LocalNewDesign.current
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsState()
    val lastEditedAt by viewModel.lastEditedAt.collectAsState()
    // E5: Haken nur, wenn es eine gespeicherte Fassung gibt (neue, nie gespeicherte Notiz → Diskette).
    val showSaved = newDesign && !hasUnsavedChanges && lastEditedAt != null
    // E10: Tipp auf den Text im Lesemodus öffnet das Bearbeiten an derselben Stelle.
    val previewScrollState = rememberScrollState()
    var tapAnchor by remember { mutableStateOf<TapAnchor?>(null) }

    // Strings for toast messages (avoid LocalContextGetResourceValueCall lint)
    val msgNoteIsEmpty = stringResource(R.string.note_is_empty)
    val msgNoteSaved = stringResource(R.string.note_saved)
    val msgNoteDeleted = stringResource(R.string.note_deleted)
    val msgItemCopiedToChecklist = stringResource(R.string.checklist_item_copied_toast) // 🆕 v2.2.0
    val msgNoteCopied = stringResource(R.string.toast_note_copied)
    val msgImageCopied = stringResource(R.string.toast_image_copied)
    val msgImagePlaceholder = stringResource(R.string.share_image_placeholder)
    val msgImagePlaceholderAltTemplate = stringResource(R.string.share_image_placeholder_alt)

    // v1.5.0: Auto-keyboard support
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeInsets = WindowInsets.ime
    val density = LocalDensity.current
    val isImeVisible by remember { derivedStateOf { imeInsets.getBottom(density) > 0 } }
    val titleFocusRequester = remember { FocusRequester() }
    val contentFocusRequester = remember { FocusRequester() }
    var isTitleFocused by remember { mutableStateOf(false) }
    var isContentFocused by remember { mutableStateOf(false) }
    // E9 (#131): bei offener Tastatur rückt der Titel in die Leiste; Tippen darauf macht sie zum Titelfeld.
    var barTitleEditing by remember { mutableStateOf(false) }
    val barTitleFocusRequester = remember { FocusRequester() }
    val headerToBar = newDesign &&
        uiState.noteType == NoteType.TEXT &&
        !isPreviewMode &&
        (isContentFocused || barTitleEditing)
    val titleInBar = headerToBar && isImeVisible
    // E9: Kopf im Blatt und Titel in der Leiste folgen Bild für Bild der Tastatur statt eigener Animation, so
    // bewegen sich Text und Tastatur als eine Bewegung. Der Faktor fängt Wechsel bei offener Tastatur ab (Auge-Knopf).
    val headerToBarFactor = animateFloatAsState(if (headerToBar) 1f else 0f, label = "headerToBar")
    val imeOpen = rememberImeOpenFraction()
    val barProgress = remember { derivedStateOf { imeOpen.value * headerToBarFactor.value } }
    val barTitleShown by remember { derivedStateOf { barProgress.value > 0f } }
    LaunchedEffect(isImeVisible) {
        if (!isImeVisible) barTitleEditing = false
    }
    // UI-Beta: ein leerer Titel steht nur mit Schalter „Titelfeld“ da, in beiden Modi gleich, sonst
    // springt der Text beim Wechsel. „Titel hinzufügen“ im Menü blendet ihn einmalig ein, danach hält
    // ihn der Fokus bzw. der Text.
    var focusTitleOnShow by remember { mutableStateOf(false) }
    val showSheetTitle = uiState.title.isNotEmpty() ||
        isTitleFocused ||
        focusTitleOnShow ||
        uiState.titleFieldEnabled

    // 🆕 v1.9.0 (F07): Lifted TextFieldState for toolbar access
    val textFieldState = rememberTextFieldState(initialText = uiState.content)

    // 🆕 Bild-Attachments: Photo-Picker → ViewModel verarbeitet + speichert → Markdown einfügen
    val isAttachingImage by viewModel.isAttachingImage.collectAsState()
    val imagePickerLauncher = rememberImagePickerLauncher(viewModel, textFieldState, scope)

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
        if (uiState.noteType == NoteType.CHECKLIST && !uiState.isNewNote) {
            savedChecklistReadMode = isPreviewMode
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
    // 🆕 v2.11.0: New-note focus target configurable (title vs. content / first checklist item)
    LaunchedEffect(uiState.isNewNote) {
        delay(LAYOUT_DELAY_MS) // Wait for layout
        when {
            uiState.isNewNote && !uiState.newNoteFocusContent && (!newDesign || uiState.titleFieldEnabled) -> {
                // New note (default): focus title
                titleFocusRequester.requestFocus()
                keyboardController?.show()
            }
            uiState.isNewNote && uiState.noteType == NoteType.TEXT -> {
                // 🆕 v2.11.0: user preference — start in content field
                contentFocusRequester.requestFocus()
                keyboardController?.show()
            }
            uiState.isNewNote -> {
                // 🆕 v2.11.0: CHECKLIST — focus the first (empty) item via the existing
                // focusNewItemId mechanism (same path as the title onNext handler below)
                focusNewItemId = checklistItems.firstOrNull()?.id
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
                is NoteEditorEvent.RestoreContent -> { // 🆕 v1.10.0: Undo/Redo
                    textFieldState.edit {
                        replace(0, length, event.content)
                        placeCursorAtEnd()
                    }
                }
                // 🆕 v1.10.0-P2: handled by Activity (deletion forwarded to MainViewModel)
                is NoteEditorEvent.NoteDeleteRequested -> Unit
                is NoteEditorEvent.NoteArchiveToggleRequested -> Unit // 🆕 v2.11.0: handled by Activity
                // 🆕 v1.10.0-Papa: handled by Activity
                is NoteEditorEvent.OpenCalendar -> Unit
                is NoteEditorEvent.ShareAsText -> Unit
                is NoteEditorEvent.ShareAsPdf -> Unit
                is NoteEditorEvent.CopyToClipboard -> {
                    scope.launch {
                        // 🆕 Bild-Attachments: bewusst reiner Text mit Platzhaltern. Ein Clip mit
                        // Text + Bild-URIs liefert pro Empfänger ein anderes Ergebnis (Telegram und
                        // Thunderbird nehmen den Text, Element X nur die Bilder und verliert den Text) —
                        // der Sender kann das nicht steuern. Bilder gehen über Teilen (SEND_MULTIPLE).
                        val text = if (AssetReferences.extractAssetNames(event.text).isEmpty()) {
                            event.text
                        } else {
                            NoteShareHelper.formatTextForShare(event.text) { alt ->
                                if (alt.isBlank()) msgImagePlaceholder else msgImagePlaceholderAltTemplate.format(alt)
                            }
                        }
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", text)))
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            snackbarHostState.showSnackbar(msgNoteCopied)
                        }
                    }
                }
                is NoteEditorEvent.ActivatePreviewMode -> {
                    isPreviewMode = uiState.defaultStartInPreviewMode
                }
                is NoteEditorEvent.RequestContentFocus -> {
                    delay(LAYOUT_DELAY_MS)
                    contentFocusRequester.requestFocus()
                    keyboardController?.show()
                }
            }
        }
    }

    // Collect snackbar messages from Activity-originated actions (share, PDF, etc.)
    LaunchedEffect(Unit) {
        viewModel.showSnackbar.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val noteStatsVisible = uiState.wordCounterVisibility.visibleIn(isPreviewMode)
    val surfaceColor = MaterialTheme.colorScheme.surface
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = if (newDesign) Color.Transparent else surfaceColor,
        // contentColorFor(Transparent) fällt auf Schwarz zurück; Icons ohne tint wären im dunklen Theme unsichtbar.
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            TopAppBar(
                title = {
                    if (!newDesign) {
                        NoteEditorToolbarTitle(
                            toolbarTitle = uiState.toolbarTitle,
                            autosaveIndicatorVisible = autosaveIndicatorVisible,
                            compact = isCompactToolbar
                        )
                    }
                    // E3: ohne Tastatur bleibt die Leiste leer, der Titel steht groß im Blatt.
                    // E9: mit Tastatur gleitet er in die Leiste, im Gleichlauf mit dem Blatt-Titel unten.
                    if (newDesign && barTitleShown) {
                        Box(
                            modifier = Modifier.graphicsLayer {
                                val p = barProgress.value
                                alpha = p
                                translationY = (1f - p) * size.height / 2
                            }
                        ) {
                            EditorBarTitle(
                                title = uiState.title,
                                showTitle = uiState.title.isNotEmpty() || uiState.titleFieldEnabled,
                                editing = barTitleEditing,
                                onTitleChange = { viewModel.updateTitle(it) },
                                onStartEditing = { barTitleEditing = true },
                                onEditingDone = { backToText ->
                                    barTitleEditing = false
                                    if (backToText) contentFocusRequester.requestFocus()
                                },
                                focusRequester = barTitleFocusRequester,
                                meta = {
                                    NoteMetaLine(
                                        editedAt = null,
                                        text = uiState.content,
                                        mode = noteStatsMode,
                                        showStats = noteStatsVisible,
                                        onCycle = { viewModel.cycleNoteStatsMode() },
                                        style = MaterialTheme.typography.labelMedium,
                                        cycleOnTap = false
                                    )
                                }
                            )
                        }
                    }
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
                    // E9: Leiste als Titelfeld zeigt nur Zurück und Speichern.
                    if (barTitleEditing && titleInBar) {
                        SaveStateButton(saved = showSaved) { viewModel.saveNote() }
                        return@TopAppBar
                    }
                    // 🆕 v1.9.0 (F07): Markdown Preview Toggle
                    // 🆕 Issue #156: auch für Checklisten (Lesemodus)
                    IconButton(
                        onClick = {
                            // Crossfade hält das Feld kurz weiter; die Tastatur soll trotzdem sofort gehen.
                            if (newDesign && !isPreviewMode) keyboardController?.hide()
                            isPreviewMode = !isPreviewMode
                        }
                    ) {
                        Icon(
                            imageVector = if (isPreviewMode) {
                                Icons.Outlined.Edit
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = stringResource(
                                if (uiState.noteType == NoteType.TEXT) {
                                    R.string.editor_toggle_preview
                                } else {
                                    R.string.editor_toggle_read_mode
                                }
                            )
                        )
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

                    // Save button (E5: im neuen Design mit Zustand)
                    SaveStateButton(saved = showSaved) { viewModel.saveNote() }

                    // 🆕 v1.10.0-Papa: Overflow menu (Calendar, Share, PDF, Delete)
                    // 🆕 v1.10.0-P2: Box ensures menu anchors to the ⋮ button,
                    // not to the full actions block (fixes position inconsistency
                    // between text and checklist note types)
                    Box {
                        IconButton(onClick = {
                            if (isImeVisible) {
                                // Issue #114: Das Menü-Popup misst sich nicht neu, wenn die Tastatur nach dem
                                // Öffnen verschwindet; Archiv/Löschen wären abgeschnitten. Erst Tastatur zu, dann Menü.
                                keyboardController?.hide()
                                scope.launch {
                                    withTimeoutOrNull(IME_HIDE_TIMEOUT_MS) { snapshotFlow { isImeVisible }.first { !it } }
                                    showOverflowMenu = true
                                }
                            } else {
                                showOverflowMenu = true
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.share_overflow_menu)
                            )
                        }
                        EditorOverflowMenu(
                            expanded = showOverflowMenu,
                            onDismiss = { showOverflowMenu = false },
                            viewModel = viewModel,
                            uiState = uiState,
                            flags = EditorMenuFlags(
                                compactToolbar = isCompactToolbar,
                                canUndo = canUndo,
                                canRedo = canRedo,
                                isPreviewMode = isPreviewMode,
                                isAttachingImage = isAttachingImage,
                                newDesign = newDesign,
                                showSheetTitle = showSheetTitle
                            )
                        ) { action ->
                            when (action) {
                                EditorMenuAction.PICK_IMAGE -> imagePickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                                EditorMenuAction.SORT -> showChecklistSortDialog = true
                                EditorMenuAction.ADD_TITLE -> focusTitleOnShow = true
                                EditorMenuAction.COLOR -> showColorPicker = true
                                EditorMenuAction.CONVERT -> showConvertDialog = true
                            }
                        }
                    } // Box
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    // 🆕 v2.5.0: neutral bar; accent shown as stripe below. E7: im neuen Design durchsichtig
                    // über dem Farbverlauf.
                    containerColor = if (newDesign) Color.Transparent else MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = Modifier
            .imePadding()
            .then(if (newDesign) Modifier.noteColorGradient(noteAccentColor, surfaceColor, isDark) else Modifier)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 🆕 v2.5.0: 3 dp accent stripe, full width, outside content padding (E7: im neuen Design Verlauf)
            if (!newDesign) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(noteAccentColor ?: Color.Transparent)
                )
            }
            // Formatierleiste sichtbar? Dann kein Abstand darunter, sonst bleiben ~20 dp bis zur Tastatur leer (#131).
            // E6: im neuen Design erst mit der Tastatur, sonst steht die Leiste kurz allein am unteren Rand.
            // Mit Hardware-Tastatur kommt keine Bildschirmtastatur, dann sofort.
            val hasHardwareKeyboard =
                LocalConfiguration.current.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
            val showFormattingBar = uiState.noteType == NoteType.TEXT &&
                !isPreviewMode &&
                isContentFocused &&
                uiState.formattingBarEnabled &&
                (!newDesign || isImeVisible || hasHardwareKeyboard)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .wrapContentWidth(align = Alignment.CenterHorizontally) // 🆕 v1.10.0-P2: Center on tablets
                    .widthIn(max = 720.dp) // 🆕 v1.10.0-P2: Constrain width for readability
                    .fillMaxWidth() // 🆕 v1.10.0-P2: Fill up to constrained width
                    .padding(
                        start = Dimensions.SpacingLarge,
                        end = Dimensions.SpacingLarge,
                        top = if (newDesign) Dimensions.SpacingSmall else Dimensions.SpacingLarge,
                        bottom = if (showFormattingBar) 0.dp else Dimensions.SpacingLarge
                    )
            ) {
                // 🆕 v2.16.0: Konflikt-Banner über allem anderen — die Entscheidung kommt vor
                // dem Weiterschreiben, sonst läuft der nächste Upload wieder in dasselbe 412.
                if (uiState.hasConflict) {
                    ConflictBanner(
                        onKeepLocal = { viewModel.resolveConflictKeepLocal() },
                        onUseServer = { viewModel.resolveConflictUseServer() },
                        onCompare = { viewModel.showConflictCompare() },
                        compareEnabled = !uiState.conflictCompareLoading,
                        modifier = Modifier.padding(bottom = Dimensions.SpacingMedium)
                    )
                }
                uiState.conflictVersions?.let { versions ->
                    ConflictCompareDialog(
                        versions = versions,
                        onKeepLocal = { viewModel.resolveConflictKeepLocal() },
                        onUseServer = { viewModel.resolveConflictUseServer() },
                        onDismiss = { viewModel.dismissConflictCompare() }
                    )
                }

                // 🆕 v1.8.2 (IMPL_09): Nach Enter/Next → ins passende Feld springen
                val titleKeyboardActions = KeyboardActions(
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
                )
                // Title Input (for both types)
                if (newDesign) {
                    // E1: Titel ohne Rahmen als Überschrift des Blatts. E9: bei offener Tastatur in der Leiste.
                    Column(modifier = Modifier.collapseUp { barProgress.value }) {
                        AnimatedVisibility(
                            visible = showSheetTitle,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            SheetTitleField(
                                title = uiState.title,
                                onTitleChange = { viewModel.updateTitle(it) },
                                keyboardActions = titleKeyboardActions,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(titleFocusRequester)
                                    .onFocusChanged { isTitleFocused = it.isFocused }
                            )
                            // Erst jetzt hängt der FocusRequester am Feld.
                            LaunchedEffect(focusTitleOnShow) {
                                if (focusTitleOnShow) {
                                    titleFocusRequester.requestFocus()
                                    keyboardController?.show()
                                    focusTitleOnShow = false
                                }
                            }
                        }
                        // E4 / C3: Änderungsdatum und Wortzahl bzw. offen/erledigt statt Pille.
                        NoteMetaLine(
                            // #180: Wortzähler „Aus“ nimmt die ganze Zeile weg, das Datum steht auch auf der Karte.
                            editedAt = lastEditedAt.takeIf { uiState.wordCounterVisibility != WordCounterVisibility.OFF },
                            text = uiState.content,
                            mode = noteStatsMode,
                            showStats = noteStatsVisible,
                            onCycle = { viewModel.cycleNoteStatsMode() },
                            checklistCounts = if (uiState.noteType == NoteType.CHECKLIST) {
                                checklistItems.count { !it.isChecked } to checklistItems.count { it.isChecked }
                            } else {
                                null
                            },
                            modifier = Modifier.padding(
                                horizontal = Dimensions.SpacingSmall,
                                vertical = Dimensions.SpacingXSmall
                            )
                        )
                        Spacer(modifier = Modifier.height(Dimensions.SpacingMediumLarge))
                    }
                } else {
                    OutlinedTextField(
                        value = uiState.title,
                        onValueChange = { viewModel.updateTitle(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(titleFocusRequester)
                            .onFocusChanged { isTitleFocused = it.isFocused },
                        label = { Text(stringResource(R.string.title)) },
                        singleLine = true, // 🆕 v1.8.2 (IMPL_09): Enter navigiert statt Newline
                        // 🆕 v1.8.2: Auto-Großschreibung für Wortanfänge im Titel
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next // 🆕 v1.8.2 (IMPL_09): Weiter-Taste
                        ),
                        // 🆕 v1.8.2 (IMPL_09): Nach Enter/Next → ins passende Feld springen
                        keyboardActions = titleKeyboardActions,
                        shape = RoundedCornerShape(16.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                when (uiState.noteType) {
                    NoteType.TEXT -> {
                        val linkColor = MaterialTheme.colorScheme.primary
                        val codeBackground = MaterialTheme.colorScheme.surfaceVariant
                        val codeColor = MaterialTheme.colorScheme.onSurfaceVariant
                        val markerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        val fontSizeMultiplier = LocalFontSizeMultiplier.current
                        val markdownTransformation = remember(linkColor, codeBackground, codeColor, markerColor, fontSizeMultiplier) {
                            MarkdownOutputTransformation(linkColor, codeBackground, codeColor, markerColor, fontSizeMultiplier)
                        }
                        // 🔧 (#126-Nachgang): Inhalt und Statistik-Pille teilen sich eine Box —
                        // die Pille schwebt darüber, statt eine eigene 48-dp-Zeile zu belegen.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            // UI-Beta: Lesen und Bearbeiten stehen an derselben Stelle (E2), der Wechsel blendet über.
                            // Altes Design: snap() = sofortiger Wechsel wie bisher.
                            Crossfade(
                                targetState = isPreviewMode,
                                animationSpec = if (newDesign) tween(MODE_CROSSFADE_MS) else snap(),
                                label = "previewMode"
                            ) { preview ->
                                if (preview) {
                                    // 🆕 v1.9.0 (F07): Markdown rendered preview
                                    val blocks = remember(uiState.content) {
                                        MarkdownEngine.parse(uiState.content)
                                    }
                                    MarkdownPreview(
                                        blocks = blocks,
                                        modifier = Modifier.fillMaxSize(),
                                        scrollState = previewScrollState,
                                        // E10 (#131), Stufe 1: Tipp auf den Text öffnet das Bearbeiten an derselben Stelle.
                                        onTextTap = if (newDesign) {
                                            { anchor ->
                                                tapAnchor = anchor
                                                isPreviewMode = false
                                            }
                                        } else {
                                            null
                                        },
                                        // 🆕 Bild-Attachments v2: Long-Press-Menü schreibt Größe/Ausrichtung
                                        // zurück in die Markdown-Source. Explizites updateContent ist
                                        // zwingend: die snapshotFlow-Bridge lebt in TextNoteContent (im
                                        // Preview-Mode nicht komponiert), die Preview rendert aus uiState.content.
                                        onImageTokensChange = { image, size, align, altText ->
                                            applyImageTokenRewrite(textFieldState, viewModel, image, size, align, altText)
                                        },
                                        // Ab Android 13 zeigt das System selbst eine Kopier-Bestätigung.
                                        onImageCopied = {
                                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                                scope.launch { snackbarHostState.showSnackbar(msgImageCopied) }
                                            }
                                        }
                                    )
                                } else {
                                    // Content Input for TEXT notes
                                    TextNoteContent(
                                        textFieldState = textFieldState,
                                        onContentChange = { viewModel.updateContent(it) },
                                        focusRequester = contentFocusRequester,
                                        outputTransformation = markdownTransformation,
                                        // 🆕 v2.12.0: Bilder aus der Zwischenablage
                                        onPasteImages = { uris ->
                                            scope.launch { attachAndInsertImages(viewModel, textFieldState, uris) }
                                        },
                                        onFocusChanged = { isContentFocused = it },
                                        modifier = Modifier.fillMaxSize(),
                                        tapAnchor = tapAnchor,
                                        onTapAnchorApplied = { tapAnchor = null }
                                    )
                                }
                            }

                            // 🆕 v2.16.0 (Issue #126): Wort-/Zeichenzahl, Tippen wechselt.
                            // E4: im neuen Design steht die Zahl in der Zeile unter dem Titel.
                            if (!newDesign && noteStatsVisible) {
                                NoteStatsPill(
                                    text = uiState.content,
                                    mode = noteStatsMode,
                                    onCycle = { viewModel.cycleNoteStatsMode() },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(Dimensions.SpacingMedium)
                                )
                            }
                        }

                        // Bleibt außerhalb der Box: sonst schwebte die Pille über den Formatier-Icons.
                        if (showFormattingBar) {
                            MarkdownToolbar(
                                floating = newDesign, // E6: abgerundeter, schwebender Balken
                                // #180: geht mit der Tastatur; verschwände sie erst am Ende, spränge der Text.
                                modifier = if (newDesign && !hasHardwareKeyboard) {
                                    Modifier.shrinkWithKeyboard(Dimensions.SpacingLarge) { imeOpen.value }
                                } else {
                                    Modifier
                                },
                                textFieldState = textFieldState,
                                onImageClick = {
                                    imagePickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                isAttachingImage = isAttachingImage
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
                            scrollTopOnUncheck = viewModel.scrollTopOnUncheck, // 🆕 Issue #112
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
                            onCopyText = { itemId ->
                                // 🆕 v2.2.0
                                val text = checklistItems.find { it.id == itemId }?.text
                                if (!text.isNullOrBlank()) {
                                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("", text))) }
                                }
                            },
                            onDuplicate = { itemId ->
                                // 🆕 v2.2.0
                                val newId = viewModel.duplicateChecklistItem(itemId)
                                if (newId != null) {
                                    focusNewItemId = newId
                                }
                            },
                            onCopyToChecklist = { itemId ->
                                // 🆕 v2.2.0
                                copyToChecklistItemId = itemId
                                viewModel.loadOtherChecklists()
                            },
                            onAddToCalendar = { itemId ->
                                viewModel.openChecklistItemInCalendar(itemId)
                            },
                            onAddItemAtEnd = {
                                val newId = viewModel.addChecklistItemAtEnd()
                                focusNewItemId = newId
                            },
                            onMove = { from, to -> viewModel.moveChecklistItem(from, to) },
                            onIndent = { id -> viewModel.indentChecklistItem(id) },
                            onOutdent = { id -> viewModel.outdentChecklistItem(id) },
                            onDragStarted = { id -> viewModel.startChecklistDrag(id) },
                            onDragFinished = { viewModel.onChecklistDragFinished() },
                            onFocusHandled = { focusNewItemId = null },
                            onSortClick = { showChecklistSortDialog = true }, // 🔀 v1.8.0
                            readOnly = isPreviewMode, // 🆕 Issue #156
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            }
        }
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
            onDismiss = { showColorPicker = false }
        )
    }

    if (showConvertDialog) {
        AlertDialog(
            onDismissRequest = { showConvertDialog = false },
            title = {
                Text(
                    stringResource(
                        if (uiState.noteType == NoteType.CHECKLIST) {
                            R.string.action_convert_to_note
                        } else {
                            R.string.action_convert_to_checklist
                        }
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        if (uiState.noteType == NoteType.CHECKLIST) {
                            R.string.confirm_convert_to_note
                        } else {
                            R.string.confirm_convert_to_checklist
                        }
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
