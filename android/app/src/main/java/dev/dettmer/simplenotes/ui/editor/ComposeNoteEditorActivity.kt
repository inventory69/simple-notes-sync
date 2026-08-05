package dev.dettmer.simplenotes.ui.editor

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.android.material.color.DynamicColors
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.security.AppLock
import dev.dettmer.simplenotes.security.AppLockGate
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.ui.main.ComposeMainActivity
import dev.dettmer.simplenotes.ui.theme.ColorTheme
import dev.dettmer.simplenotes.ui.theme.FontSizeScale
import dev.dettmer.simplenotes.ui.theme.SimpleNotesTheme
import dev.dettmer.simplenotes.ui.theme.ThemeMode
import dev.dettmer.simplenotes.ui.theme.ThemePreferences
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import dev.dettmer.simplenotes.utils.NoteShareHelper
import dev.dettmer.simplenotes.utils.PdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Compose-based Note Editor Activity
 *
 * v1.5.0: Jetpack Compose NoteEditor Redesign
 * Replaces the old NoteEditorActivity with a modern Compose implementation.
 *
 * Supports:
 * - TEXT notes with title and content
 * - CHECKLIST notes with drag & drop reordering
 * - Auto-keyboard focus for new checklist items
 */
class ComposeNoteEditorActivity : FragmentActivity() {
    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
        const val EXTRA_NOTE_TYPE = "extra_note_type"
        const val EXTRA_FOLDER = "extra_folder"

        // Issue #117: Markiert, dass der Editor aus einem Widget-Tap gestartet wurde —
        // steuert die Back-Pfade (Up zur Notizliste statt in den bestehenden Task).
        const val EXTRA_FROM_WIDGET = "extra_from_widget"
        private const val TAG = "ComposeNoteEditorActivity" // 🆕 v1.10.0-Papa
        private const val KEY_SHARE_TYPE_CHOSEN = "share_type_chosen" // 🆕 v2.2.0
        private const val KEY_PICK_TARGET_NOTE = "pick_target_note" // 🆕 v2.6.0

        // 🆕 v1.10.0-P2: Result codes for deletion forwarding to MainViewModel
        const val RESULT_NOTE_DELETED = 10
        const val RESULT_EXTRA_NOTE_ID = "result_note_id"

        // 🆕 v2.11.0 (Archive): Result code for archive-toggle forwarding to MainViewModel
        const val RESULT_NOTE_ARCHIVE_TOGGLED = 11
    }

    // 🆕 v2.2.0: Share Intent — Typ-Auswahl-State
    // chosenShareNoteType wird VOR dem ersten viewModel-Zugriff gesetzt.
    // Die viewModelFactory liest diesen Wert im initializer-Lambda.
    private var chosenShareNoteType: String? = null
    private var isShareTypeChosen by mutableStateOf(false)
    private var isShareIntent = false
    private var eventCollectionStarted = false

    // 🆕 v2.6.0: Append-to-note flow state
    private var isPickingTargetNote by mutableStateOf(false)
    private var chosenAppendNoteId: String? = null

    // Issue #117: Widget-Ursprung — steuert Up- vs. Back-Verhalten
    private var fromWidget = false

    private val viewModel: NoteEditorViewModel by viewModels {
        viewModelFactory {
            initializer {
                val handle = createSavedStateHandle()
                handle[NoteEditorViewModel.ARG_NOTE_ID] = intent.getStringExtra(EXTRA_NOTE_ID)
                handle[NoteEditorViewModel.ARG_NOTE_TYPE] =
                    intent.getStringExtra(EXTRA_NOTE_TYPE) ?: NoteType.TEXT.name
                handle[NoteEditorViewModel.ARG_FOLDER] = intent.getStringExtra(EXTRA_FOLDER)

                // 🆕 v2.2.0: Share Intent — Text aus anderen Apps empfangen
                if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                    val sharedSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
                    handle[NoteEditorViewModel.ARG_SHARED_TEXT] = sharedText
                    handle[NoteEditorViewModel.ARG_SHARED_SUBJECT] = sharedSubject
                    if (chosenAppendNoteId != null) {
                        // 🆕 v2.6.0: Append mode — load existing note and append
                        handle[NoteEditorViewModel.ARG_NOTE_ID] = null
                        handle[NoteEditorViewModel.ARG_APPEND_TO_NOTE_ID] = chosenAppendNoteId
                    } else {
                        handle[NoteEditorViewModel.ARG_NOTE_TYPE] =
                            chosenShareNoteType ?: NoteType.TEXT.name
                        handle[NoteEditorViewModel.ARG_NOTE_ID] = null
                    }
                }

                NoteEditorViewModel(application, handle)
            }
        }
    }

    // v2.0.0: Theme state — initialized in onCreate, refreshed in onResume after returning from Settings
    private val editorPrefs by lazy { getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE) }
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    private var colorTheme by mutableStateOf(ColorTheme.DYNAMIC)
    private var fontSizeScale by mutableStateOf(FontSizeScale.SYSTEM)

    // Abbau: TECH_DEBT_ROADMAP.md §4 (Bestand, keinem Refactoring-Slice zugeordnet)
    @Suppress("LongMethod")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Issue #117: Widget-Ursprung vor setContent auswerten
        fromWidget = intent.getBooleanExtra(EXTRA_FROM_WIDGET, false)

        // v2.0.0: Load theme from prefs (context available after super.onCreate)
        themeMode = ThemePreferences.getThemeMode(editorPrefs)
        colorTheme = ThemePreferences.getColorTheme(editorPrefs)
        fontSizeScale = ThemePreferences.getFontSizeScale(editorPrefs)

        // Apply Dynamic Colors for Android 12+ (Material You)
        DynamicColors.applyToActivityIfAvailable(this)

        enableEdgeToEdge()

        // Must run after enableEdgeToEdge(): it sets window bar colors last so the
        // Recents secure-placeholder color isn't clobbered by edge-to-edge's own colors.
        AppLock.applySecureFlag(this)

        // v2.0.0: Register both OPEN and CLOSE transitions for consistent
        // Shared Axis X animation on all back paths (arrow button + swipe gesture).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                R.anim.shared_axis_x_enter,
                R.anim.shared_axis_x_exit
            )
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                R.anim.shared_axis_x_pop_enter,
                R.anim.shared_axis_x_pop_exit
            )
        }

        // v2.0.0: On API 35+ (mandatory predictive back), overrideActivityTransition(CLOSE)
        // is only respected for explicit finish() calls — the system uses its own animation
        // for gesture-driven back. Routing through OnBackPressedCallback + finish() fixes this.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (fromWidget) exitToLauncher() else finishWithTransition()
                }
            }
        )

        // 🆕 v2.2.0: Share Intent Erkennung
        isShareIntent = intent.action == Intent.ACTION_SEND && intent.type == "text/plain"

        // Restore state nach Configuration Change (z.B. Rotation)
        if (savedInstanceState != null) {
            isShareTypeChosen = savedInstanceState.getBoolean(KEY_SHARE_TYPE_CHOSEN, false)
            isPickingTargetNote = savedInstanceState.getBoolean(KEY_PICK_TARGET_NOTE, false)
        } else {
            // Nicht-Share-Intents brauchen keinen Dialog → sofort ready
            isShareTypeChosen = !isShareIntent
        }

        setContent {
            SimpleNotesTheme(themeMode = themeMode, colorTheme = colorTheme, fontSizeScale = fontSizeScale) {
                AppLockGate {
                    when {
                        isShareIntent && !isShareTypeChosen && isPickingTargetNote -> {
                            // 🆕 v2.6.0: Note-Picker-Dialog für Append-Modus
                            ShareNotePickerDialog(
                                storage = NotesStorage(this@ComposeNoteEditorActivity),
                                onNoteSelected = { noteId ->
                                    chosenAppendNoteId = noteId
                                    isPickingTargetNote = false
                                    isShareTypeChosen = true
                                    startEventCollectionIfNeeded()
                                },
                                onDismiss = { isPickingTargetNote = false }
                            )
                        }
                        isShareIntent && !isShareTypeChosen -> {
                            // 🆕 v2.2.0: Typ-Auswahl-Dialog für Share Intent
                            ShareNoteTypeDialog(
                                onTextNote = {
                                    chosenShareNoteType = NoteType.TEXT.name
                                    isShareTypeChosen = true
                                    startEventCollectionIfNeeded()
                                },
                                onChecklist = {
                                    chosenShareNoteType = NoteType.CHECKLIST.name
                                    isShareTypeChosen = true
                                    startEventCollectionIfNeeded()
                                },
                                onAppendNote = { isPickingTargetNote = true },
                                onDismiss = { finishWithTransition() }
                            )
                        }
                        else -> {
                            NoteEditorScreen(
                                viewModel = viewModel,
                                onNavigateBack = {
                                    if (fromWidget) navigateUpToNotesList() else finishWithTransition()
                                }
                            )
                        }
                    }
                } // AppLockGate
            }
        }

        // Event Collection nur starten wenn kein Share-Dialog angezeigt wird
        if (!isShareIntent) {
            startEventCollectionIfNeeded()
        }
    }

    // 🆕 v2.2.0: Persist Share-Dialog-State über Configuration Changes
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_SHARE_TYPE_CHOSEN, isShareTypeChosen)
        outState.putBoolean(KEY_PICK_TARGET_NOTE, isPickingTargetNote)
    }

    /**
     * 🆕 v1.10.0-Papa: Reload Checklist-State falls Widget Änderungen gemacht hat.
     *
     * Wenn die Activity aus dem Hintergrund zurückkehrt (z.B. nach Widget-Toggle),
     * wird der aktuelle Note-Stand von Disk geladen und der ViewModel-State
     * für Checklist-Items aktualisiert.
     */
    override fun onResume() {
        super.onResume()
        // Re-sync FLAG_SECURE + Recents placeholder color (see ComposeMainActivity.onResume).
        AppLock.applySecureFlag(this)
        // v2.0.0: Refresh theme in case user returned from Settings
        themeMode = ThemePreferences.getThemeMode(editorPrefs)
        colorTheme = ThemePreferences.getColorTheme(editorPrefs)
        fontSizeScale = ThemePreferences.getFontSizeScale(editorPrefs)
        // 🆕 v2.2.0: Guard — nur wenn ViewModel bereits initialisiert (Share-Dialog abgeschlossen)
        if (isShareTypeChosen) {
            viewModel.reloadFromStorage()
        }
    }

    override fun onStop() {
        super.onStop()
        AppLock.applySecureFlag(this)
    }

    // v2.0.0: Save unsaved changes when activity pauses (Back gesture, Home, task switch).
    // Must happen in onPause (not onStop) so data is on disk BEFORE the parent
    // activity's onResume reloads the note list.
    override fun onPause() {
        super.onPause()
        // 🆕 v2.2.0: Guard — nur wenn ViewModel bereits initialisiert
        if (isShareTypeChosen) {
            viewModel.saveOnBack()
        }
    }

    /**
     * 🆕 v2.2.0: Startet Event-Collection für Calendar/Share/Delete Events.
     * Wird nach Share-Dialog-Auswahl oder direkt in onCreate() aufgerufen.
     * Guard verhindert doppelten Start nach Config Change.
     */
    private fun startEventCollectionIfNeeded() {
        if (eventCollectionStarted) return
        eventCollectionStarted = true
        lifecycleScope.launch {
            viewModel.events.collect { event ->
                when (event) {
                    is NoteEditorEvent.OpenCalendar -> handleCalendarExport(event)
                    is NoteEditorEvent.ShareAsText -> handleShareAsText(event)
                    is NoteEditorEvent.ShareAsPdf -> handleShareAsPdf(event)
                    is NoteEditorEvent.NoteDeleteRequested -> {
                        val resultIntent = Intent().apply {
                            putExtra(RESULT_EXTRA_NOTE_ID, event.noteId)
                        }
                        setResult(RESULT_NOTE_DELETED, resultIntent)
                        finishWithTransition()
                    }
                    is NoteEditorEvent.NoteArchiveToggleRequested -> {
                        val resultIntent = Intent().apply {
                            putExtra(RESULT_EXTRA_NOTE_ID, event.noteId)
                        }
                        setResult(RESULT_NOTE_ARCHIVE_TOGGLED, resultIntent)
                        finishWithTransition()
                    }
                    else -> { /* handled by Composable */ }
                }
            }
        }
    }

    private fun finishWithTransition() {
        finish()
        // API < 34: overrideActivityTransition not available, use deprecated API
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(
                R.anim.shared_axis_x_pop_enter,
                R.anim.shared_axis_x_pop_exit
            )
        }
    }

    // Issue #117: Up-Navigation — Editor aus Widget führt zur Notizliste, nicht "zurück" in den Task.
    private fun navigateUpToNotesList() {
        val intent = Intent(this, ComposeMainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val options = ActivityOptionsCompat.makeCustomAnimation(
            this,
            R.anim.shared_axis_x_pop_enter,
            R.anim.shared_axis_x_pop_exit
        )
        startActivity(intent, options.toBundle())
        finish()
    }

    // Issue #117: Back aus Widget verlässt die App — auch wenn darunter noch ein App-Task liegt.
    private fun exitToLauncher() {
        moveTaskToBack(true)
        finish()
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🆕 v1.10.0-Papa: Calendar Export & Share Handlers
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Opens a calendar app with the note data pre-filled.
     * Uses ACTION_INSERT — no calendar permissions required.
     */
    private fun handleCalendarExport(event: NoteEditorEvent.OpenCalendar) {
        val beginTime = System.currentTimeMillis()
        val endTime = beginTime + 60 * 60 * 1000L // +1 hour
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, event.title)
            putExtra(CalendarContract.Events.DESCRIPTION, event.description)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginTime)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
            putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
            if (event.location.isNotBlank()) putExtra(CalendarContract.Events.EVENT_LOCATION, event.location)
            if (event.attendees.isNotBlank()) {
                val emails = event.attendees.split(",", ";").map { it.trim() }.filter { it.isNotBlank() }
                if (emails.isNotEmpty()) putExtra(Intent.EXTRA_EMAIL, ArrayList(emails))
            }
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Logger.w(TAG, "No calendar app found: ${e.message}")
            viewModel.emitSnackbar(getString(R.string.share_no_calendar_app))
        }
    }

    /**
     * Opens the Android share sheet with the note as plain text.
     * 🆕 Bild-Attachments: referenzierte Bilder werden als zusätzliche Streams mitgeteilt
     * (ACTION_SEND_MULTIPLE) — ein `.assets/`-Link ist außerhalb der App nicht auflösbar.
     */
    private fun handleShareAsText(event: NoteEditorEvent.ShareAsText) {
        val imageUris = NoteShareHelper.resolveShareableImageUris(this, event.text)
        Logger.d(TAG, "handleShareAsText: textLength=${event.text.length}, imageUris=${imageUris.size}")
        // Bilder gehen als eigener Stream raus — der rohe ![alt](.assets/...)-Tag im Text wird
        // durch einen Platzhalter ersetzt (Duplikat wäre sonst Bild + Tag-Text beim Empfänger).
        val shareText = if (imageUris.isEmpty()) {
            event.text
        } else {
            NoteShareHelper.formatTextForShare(event.text) { alt ->
                if (alt.isBlank()) getString(R.string.share_image_placeholder) else getString(R.string.share_image_placeholder_alt, alt)
            }
        }
        val shareIntent = when (imageUris.size) {
            0 -> Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, event.title)
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            // Singuläres ACTION_SEND mit einem EXTRA_STREAM + plain-String EXTRA_TEXT ist der
            // Intent-Flow, den WhatsApp/Gmail/Signal für "Foto mit Bildunterschrift" tatsächlich
            // lesen. ACTION_SEND_MULTIPLE ist für mehrere Streams gedacht; die meisten Empfänger
            // ignorieren dort EXTRA_TEXT komplett.
            1 -> Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_SUBJECT, event.title)
                putExtra(Intent.EXTRA_TEXT, shareText)
                putExtra(Intent.EXTRA_STREAM, imageUris.first())
                clipData = ClipData.newUri(contentResolver, "", imageUris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            else -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_SUBJECT, event.title)
                // Plain String statt ArrayList<CharSequence>: die AOSP-Doku beschreibt Letzteres
                // für ACTION_SEND_MULTIPLE, aber praktisch jeder Empfänger liest EXTRA_TEXT per
                // getStringExtra() — das liefert bei einer ArrayList null (leerer Body).
                putExtra(Intent.EXTRA_TEXT, shareText)
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(imageUris))
                clipData = ClipData.newUri(contentResolver, "", imageUris.first()).apply {
                    imageUris.drop(1).forEach { addItem(ClipData.Item(it)) }
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        try {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_chooser_title)))
        } catch (e: ActivityNotFoundException) {
            Logger.w(TAG, "No share target found: ${e.message}")
            viewModel.emitSnackbar(getString(R.string.share_error))
        }
    }

    /**
     * 🆕 v1.10.0-Papa: Generates a PDF from the current note and opens the share dialog.
     *
     * Uses android.graphics.pdf.PdfDocument (no external libraries).
     * PDF is saved to cacheDir/shared_pdfs/ and shared via FileProvider.
     */
    private fun handleShareAsPdf(event: NoteEditorEvent.ShareAsPdf) {
        val state = viewModel.uiState.value
        val checklistItems = viewModel.checklistItems.value

        val pdfFile = PdfExporter.generatePdf(
            context = this,
            title = event.title,
            noteType = state.noteType,
            textContent = state.content,
            checklistItems = checklistItems
        )

        if (pdfFile == null || !pdfFile.exists()) {
            viewModel.emitSnackbar(getString(R.string.share_pdf_error))
            return
        }

        val uri = FileProvider.getUriForFile(
            this,
            "${applicationInfo.packageName}.fileprovider",
            pdfFile
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, event.title)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_chooser_title)))
        } catch (e: ActivityNotFoundException) {
            Logger.w(TAG, "No PDF share target found: ${e.message}")
            viewModel.emitSnackbar(getString(R.string.share_error))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 🆕 v2.2.0: Share Intent — Notiztyp-Auswahl-Dialog
// ═══════════════════════════════════════════════════════════════════════════

private const val PICKER_PREVIEW_MAX_LENGTH = 50

@Composable
private fun ShareNoteTypeDialog(
    onTextNote: () -> Unit,
    onChecklist: () -> Unit,
    onAppendNote: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.share_type_dialog_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.share_type_dialog_message),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.size(8.dp))
                TextButton(onClick = onTextNote, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.share_type_text_note))
                }
                TextButton(onClick = onChecklist, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.share_type_checklist))
                }
                TextButton(onClick = onAppendNote, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.share_type_append_note))
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun ShareNotePickerDialog(
    storage: NotesStorage,
    onNoteSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val allNotes by produceState<List<Note>>(initialValue = emptyList()) {
        // 🆕 v2.9.0 (Trash): getrashte Notizen nicht im Share-Picker anbieten.
        value = withContext(Dispatchers.IO) { storage.loadActiveNotes() }
            .sortedByDescending { it.updatedAt }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.share_append_picker_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(allNotes, key = { it.id }) { note ->
                    ShareNotePickerCard(note = note, onClick = { onNoteSelected(note.id) })
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun ShareNotePickerCard(note: Note, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (note.noteType) {
                    NoteType.TEXT -> Icons.Outlined.Description
                    NoteType.CHECKLIST -> Icons.AutoMirrored.Outlined.List
                },
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.title.ifEmpty { stringResource(R.string.untitled) },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1
                )
                Text(
                    text = when (note.noteType) {
                        NoteType.TEXT -> note.content.take(PICKER_PREVIEW_MAX_LENGTH).replace("\n", " ")
                        NoteType.CHECKLIST -> {
                            val items = note.checklistItems.orEmpty()
                            val checked = items.count { it.isChecked }
                            "✔ $checked/${items.size}"
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1
                )
            }
        }
    }
}
