package dev.dettmer.simplenotes.ui.editor

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.android.material.color.DynamicColors
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.ui.theme.ColorTheme
import dev.dettmer.simplenotes.ui.theme.SimpleNotesTheme
import dev.dettmer.simplenotes.ui.theme.ThemeMode
import dev.dettmer.simplenotes.ui.theme.ThemePreferences
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import dev.dettmer.simplenotes.utils.PdfExporter
import kotlinx.coroutines.launch

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
class ComposeNoteEditorActivity : ComponentActivity() {
    companion object {
        const val EXTRA_NOTE_ID = "extra_note_id"
        const val EXTRA_NOTE_TYPE = "extra_note_type"
        private const val TAG = "ComposeNoteEditorActivity" // 🆕 v1.10.0-Papa
        private const val KEY_SHARE_TYPE_CHOSEN = "share_type_chosen" // 🆕 v2.2.0

        // 🆕 v1.10.0-P2: Result codes for deletion forwarding to MainViewModel
        const val RESULT_NOTE_DELETED = 10
        const val RESULT_EXTRA_NOTE_ID = "result_note_id"
        const val RESULT_EXTRA_DELETE_FROM_SERVER = "result_delete_from_server"
    }

    // 🆕 v2.2.0: Share Intent — Typ-Auswahl-State
    // chosenShareNoteType wird VOR dem ersten viewModel-Zugriff gesetzt.
    // Die viewModelFactory liest diesen Wert im initializer-Lambda.
    private var chosenShareNoteType: String? = null
    private var isShareTypeChosen by mutableStateOf(false)
    private var isShareIntent = false
    private var eventCollectionStarted = false

    private val viewModel: NoteEditorViewModel by viewModels {
        viewModelFactory {
            initializer {
                val handle = createSavedStateHandle()
                handle[NoteEditorViewModel.ARG_NOTE_ID] = intent.getStringExtra(EXTRA_NOTE_ID)
                handle[NoteEditorViewModel.ARG_NOTE_TYPE] =
                    intent.getStringExtra(EXTRA_NOTE_TYPE) ?: NoteType.TEXT.name

                // 🆕 v2.2.0: Share Intent — Text aus anderen Apps empfangen
                if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                    val sharedSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
                    handle[NoteEditorViewModel.ARG_SHARED_TEXT] = sharedText
                    handle[NoteEditorViewModel.ARG_SHARED_SUBJECT] = sharedSubject
                    handle[NoteEditorViewModel.ARG_NOTE_TYPE] =
                        chosenShareNoteType ?: NoteType.TEXT.name
                    handle[NoteEditorViewModel.ARG_NOTE_ID] = null
                }

                NoteEditorViewModel(application, handle)
            }
        }
    }

    // v2.0.0: Theme state — initialized in onCreate, refreshed in onResume after returning from Settings
    private val editorPrefs by lazy { getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE) }
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    private var colorTheme by mutableStateOf(ColorTheme.DYNAMIC)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // v2.0.0: Load theme from prefs (context available after super.onCreate)
        themeMode = ThemePreferences.getThemeMode(editorPrefs)
        colorTheme = ThemePreferences.getColorTheme(editorPrefs)

        // Apply Dynamic Colors for Android 12+ (Material You)
        DynamicColors.applyToActivityIfAvailable(this)

        enableEdgeToEdge()

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
                    finishWithTransition()
                }
            }
        )

        // 🆕 v2.2.0: Share Intent Erkennung
        isShareIntent = intent.action == Intent.ACTION_SEND && intent.type == "text/plain"

        // Restore state nach Configuration Change (z.B. Rotation)
        if (savedInstanceState != null) {
            isShareTypeChosen = savedInstanceState.getBoolean(KEY_SHARE_TYPE_CHOSEN, false)
        } else {
            // Nicht-Share-Intents brauchen keinen Dialog → sofort ready
            isShareTypeChosen = !isShareIntent
        }

        setContent {
            SimpleNotesTheme(themeMode = themeMode, colorTheme = colorTheme) {
                if (isShareIntent && !isShareTypeChosen) {
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
                        onDismiss = { finishWithTransition() }
                    )
                } else {
                    NoteEditorScreen(
                        viewModel = viewModel,
                        onNavigateBack = { finishWithTransition() }
                    )
                }
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
        // v2.0.0: Refresh theme in case user returned from Settings
        themeMode = ThemePreferences.getThemeMode(editorPrefs)
        colorTheme = ThemePreferences.getColorTheme(editorPrefs)
        // 🆕 v2.2.0: Guard — nur wenn ViewModel bereits initialisiert (Share-Dialog abgeschlossen)
        if (isShareTypeChosen) {
            viewModel.reloadFromStorage()
        }
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
                            putExtra(RESULT_EXTRA_DELETE_FROM_SERVER, event.deleteFromServer)
                        }
                        setResult(RESULT_NOTE_DELETED, resultIntent)
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
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Logger.w(TAG, "No calendar app found: ${e.message}")
            Toast.makeText(this, getString(R.string.share_no_calendar_app), Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Opens the Android share sheet with the note as plain text.
     */
    private fun handleShareAsText(event: NoteEditorEvent.ShareAsText) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, event.title)
            putExtra(Intent.EXTRA_TEXT, event.text)
        }
        try {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_chooser_title)))
        } catch (e: ActivityNotFoundException) {
            Logger.w(TAG, "No share target found: ${e.message}")
            Toast.makeText(this, getString(R.string.share_error), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, getString(R.string.share_pdf_error), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, getString(R.string.share_error), Toast.LENGTH_SHORT).show()
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 🆕 v2.2.0: Share Intent — Notiztyp-Auswahl-Dialog
// ═══════════════════════════════════════════════════════════════════════════

/**
 * Leichtgewichtiger Material3 AlertDialog zur Auswahl des Notiztyps
 * für geteilten Text. Wird angezeigt bevor der Editor initialisiert wird.
 *
 * - Dismiss-Position (links): "Checkliste" (sekundäre Aktion)
 * - Confirm-Position (rechts): "Textnotiz" (primäre/Standard-Aktion)
 * - Back-Taste / Tap außerhalb: Activity wird geschlossen (onDismiss)
 */
@Composable
private fun ShareNoteTypeDialog(
    onTextNote: () -> Unit,
    onChecklist: () -> Unit,
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
            Text(
                text = stringResource(R.string.share_type_dialog_message),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        dismissButton = {
            TextButton(onClick = onChecklist) {
                Text(stringResource(R.string.share_type_checklist))
            }
        },
        confirmButton = {
            TextButton(onClick = onTextNote) {
                Text(stringResource(R.string.share_type_text_note))
            }
        }
    )
}
