package dev.dettmer.simplenotes.widget

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.dettmer.simplenotes.models.ChecklistItem
import dev.dettmer.simplenotes.models.ChecklistSortOption
import dev.dettmer.simplenotes.models.ChecklistSorter
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.FolderStore
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.sync.SyncScheduler
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State des Quick-Edit-Overlays.
 *
 * [isReady] ist false, solange die Notiz noch von der Platte kommt — das Overlay rendert dann
 * nichts, damit nicht für einen Frame eine leere Karte mit falschem Hinweistext steht.
 * [isChecklist] entscheidet zugleich über den Modus: eine Checkliste bekommt ein neues Item
 * angehängt, eine Text-Notiz ihren Inhalt ersetzt.
 */
data class QuickEditUiState(
    val noteTitle: String = "",
    val text: String = "",
    val isChecklist: Boolean = false,
    val isReady: Boolean = false
)

sealed interface QuickEditEvent {
    data object Close : QuickEditEvent
}

/**
 * ViewModel für [WidgetQuickEditActivity] — die einzige Möglichkeit, Text einer Notiz vom
 * Homescreen aus zu ändern: RemoteViews inflatet im Launcher-Prozess nur eine feste Allowlist
 * von View-Klassen, und `EditText` steht auf keiner API-Stufe darauf.
 *
 * Der Speicherpfad spiegelt `NoteEditorViewModel.performSave()` + `saveNote()`:
 * schreiben → Sync anstoßen → Widgets auffrischen.
 */
class WidgetQuickEditViewModel(
    application: Application,
    handle: SavedStateHandle
) : AndroidViewModel(application) {
    companion object {
        const val ARG_NOTE_ID = "quick_edit_note_id"
        private const val TAG = "WidgetQuickEdit"
    }

    private val storage = NotesStorage(application)
    private val noteId: String? = handle[ARG_NOTE_ID]

    private val _state = MutableStateFlow(QuickEditUiState())
    val state: StateFlow<QuickEditUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<QuickEditEvent>()
    val events: SharedFlow<QuickEditEvent> = _events.asSharedFlow()

    /** Geladene Fassung — Basis für das `copy()` beim Speichern. */
    private var note: Note? = null

    init {
        viewModelScope.launch {
            val loaded = noteId?.let { storage.loadNote(it) }
            if (loaded == null) {
                Logger.w(TAG, "Note '$noteId' not found — closing quick edit")
                _events.emit(QuickEditEvent.Close)
                return@launch
            }
            note = loaded
            val isChecklist = loaded.noteType == NoteType.CHECKLIST
            _state.value = QuickEditUiState(
                noteTitle = loaded.title,
                // Checkliste startet leer (neues Item), Text-Notiz mit ihrem bisherigen Inhalt.
                text = if (isChecklist) "" else loaded.content,
                isChecklist = isChecklist,
                isReady = true
            )
        }
    }

    fun updateText(text: String) {
        _state.update { it.copy(text = text) }
    }

    fun save() {
        val current = note ?: return
        viewModelScope.launch {
            val updated = buildUpdatedNote(current, _state.value.text)
            if (updated == null) {
                _events.emit(QuickEditEvent.Close)
                return@launch
            }
            storage.saveNote(updated)
            note = updated
            SyncScheduler(getApplication()).triggerOnSaveSync(reason = "widgetQuickEdit")
            WidgetUpdateHelper.refreshAllWidgets(getApplication())
            _events.emit(QuickEditEvent.Close)
        }
    }

    /** `null` = nichts zu speichern (leeres neues Checklist-Item). */
    private fun buildUpdatedNote(current: Note, text: String): Note? {
        val syncStatus = if (FolderStore(getApplication()).isLocalOnly(current.folderName)) {
            SyncStatus.LOCAL_ONLY
        } else {
            SyncStatus.PENDING
        }

        if (current.noteType != NoteType.CHECKLIST) {
            return current.copy(
                content = text.trim(),
                updatedAt = System.currentTimeMillis(),
                syncStatus = syncStatus
            )
        }

        val itemText = text.trim()
        if (itemText.isEmpty()) return null

        val items = current.checklistItems.orEmpty()
        val newItem = ChecklistItem
            .createEmpty(order = (items.maxOfOrNull { it.order } ?: -1) + 1)
            .copy(text = itemText)

        return current.copy(
            // Pflicht laut Skill `widget-glance`: ChecklistSorter ist Single-Source-of-Truth,
            // identisch zu ToggleChecklistItemAction.
            checklistItems = ChecklistSorter.sort(items + newItem, sortOptionOf(current)),
            updatedAt = System.currentTimeMillis(),
            syncStatus = syncStatus
        )
    }

    private fun sortOptionOf(note: Note): ChecklistSortOption = try {
        note.checklistSortOption?.let { ChecklistSortOption.valueOf(it) }
    } catch (e: IllegalArgumentException) {
        Logger.d(TAG, "Unknown checklistSortOption '${note.checklistSortOption}': ${e.message}")
        null
    } ?: ChecklistSortOption.MANUAL
}
