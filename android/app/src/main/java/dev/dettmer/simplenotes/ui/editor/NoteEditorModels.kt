package dev.dettmer.simplenotes.ui.editor

import androidx.compose.runtime.Immutable
import dev.dettmer.simplenotes.markdown.MarkdownEngine
import dev.dettmer.simplenotes.models.ChecklistItem
import dev.dettmer.simplenotes.models.ChecklistNesting
import dev.dettmer.simplenotes.models.ChecklistRow
import dev.dettmer.simplenotes.models.ChecklistSortOption
import dev.dettmer.simplenotes.models.ChecklistSorter
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.ui.editor.components.WordCounterVisibility
import java.util.UUID
import kotlinx.coroutines.flow.map

// Zustand, Events und Checklisten-Hilfen des Editors, aus NoteEditorViewModel.kt ausgelagert.

data class NoteEditorUiState(
    val title: String = "",
    val content: String = "",
    val noteType: NoteType = NoteType.TEXT,
    val isNewNote: Boolean = true,
    val isLoading: Boolean = false,
    val toolbarTitle: ToolbarTitle = ToolbarTitle.NEW_NOTE,
    val color: String? = null, // 🆕 v2.5.0 (Issue #65): note background colour
    val defaultStartInPreviewMode: Boolean = false,
    val checklistStartInReadMode: Boolean = false, // 🆕 Issue #156
    val newNoteFocusContent: Boolean = false, // 🆕 v2.11.0
    // 🆕 (#126-Nachgang): aus / immer / nur im Lesemodus
    val wordCounterVisibility: WordCounterVisibility = WordCounterVisibility.ALWAYS,
    val formattingBarEnabled: Boolean = true, // #131 (E11)
    val titleFieldEnabled: Boolean = true, // UI-Beta: leeres Titelfeld anzeigen
    val isArchived: Boolean = false, // 🆕 v2.11.0 (Archive)
    // 🆕 v2.16.0: Notiz steht auf CONFLICT — der Editor zeigt darüber das Auflösungs-Banner.
    val hasConflict: Boolean = false,
    // 🆕 v2.16.0: Beide Fassungen für den Vergleichsdialog. null = Dialog zu.
    val conflictVersions: ConflictVersions? = null,
    val conflictCompareLoading: Boolean = false
)

/** 🆕 v2.16.0: Die zwei Fassungen, die der Vergleichsdialog gegenüberstellt. */
@Immutable
data class ConflictVersions(val local: Note, val server: Note)

data class ChecklistItemState(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    override val isChecked: Boolean = false,
    val order: Int = 0,
    val originalOrder: Int = order, // 🆕 v1.9.0 (F04): Position restore on un-check
    val createdAt: Long = System.currentTimeMillis(), // 🆕 v1.11.0: Timestamp for sort-by-creation-date
    override val indentationLevel: Int = 0 // Unterpunkte: 0 oder 1, siehe ChecklistNesting
) : ChecklistRow {
    fun toItem(order: Int = this.order) = ChecklistItem(
        id = id,
        text = text,
        isChecked = isChecked,
        order = order,
        originalOrder = originalOrder,
        createdAt = createdAt,
        indentationLevel = indentationLevel
    )

    companion object {
        // 🆕 v1.11.0: Monoton steigender Timestamp — verhindert gleiche createdAt-Werte
        // wenn mehrere Items in derselben Millisekunde erstellt werden (z.B. schnelles Enter-Drücken).
        // maxOf(now, last+1) garantiert: jedes neue Item hat einen strikt größeren Wert als das vorige.
        private var lastCreatedAt = 0L

        fun createEmpty(order: Int): ChecklistItemState {
            val now = System.currentTimeMillis()
            lastCreatedAt = maxOf(now, lastCreatedAt + 1)
            return ChecklistItemState(
                id = UUID.randomUUID().toString(),
                text = "",
                isChecked = false,
                order = order,
                originalOrder = order,
                createdAt = lastCreatedAt
            )
        }
    }
}

/**
 * Normalisiert eine Textzeile zu einem Checklist-Item (TEXT → CHECKLIST).
 *
 * Top-Level, weil zustandslos — so direkt testbar ohne ViewModel-Instanz.
 *
 * Für GFM-Zeilen gilt bewusst dieselbe [MarkdownEngine.TASK_LIST_REGEX] wie in der Preview:
 * was als Checkbox gerendert wird, wird auch als Checklist-Item konvertiert. Der Checked-Test
 * ist `== "x"` und nicht `!= " "`, sonst würde `- []` (leere Klammern) als abgehakt gelten.
 */
internal fun normalizeLineToChecklistItem(line: String, index: Int): ChecklistItemState {
    // Unterpunkte: ab zwei Leerzeichen (oder einem Tab) Einzug wird die Zeile Unterpunkt
    return parseChecklistLine(line, index).copy(indentationLevel = ChecklistNesting.levelOfLine(line))
}

private fun parseChecklistLine(line: String, index: Int): ChecklistItemState {
    val markerRegex = Regex("""^[-*•]\s+(.*)$""")
    val cbRegex = Regex("""^\[([ xX]?)\]\s*(.*)$""") // group 1 = mark, group 2 = text
    val checkmarkRegex = Regex("""^[✓☑✔]\s+(.*)$""")
    val t = line.trim()
    val gfm = MarkdownEngine.TASK_LIST_REGEX.matchEntire(t)
    return if (gfm != null) {
        ChecklistItemState.createEmpty(index).copy(
            text = gfm.groupValues[2].trim(),
            isChecked = gfm.groupValues[1].equals("x", ignoreCase = true)
        )
    } else {
        val checkmark = checkmarkRegex.find(t)
        if (checkmark != null) {
            ChecklistItemState.createEmpty(index).copy(
                text = checkmark.groupValues[1].trim(),
                isChecked = true
            )
        } else {
            val afterMarker = markerRegex.find(t)?.groupValues?.get(1) ?: t
            val cbMatch = cbRegex.find(afterMarker.trim())
            if (cbMatch != null) {
                ChecklistItemState.createEmpty(index).copy(
                    text = cbMatch.groupValues[2].trim(),
                    isChecked = cbMatch.groupValues[1].equals("x", ignoreCase = true)
                )
            } else {
                ChecklistItemState.createEmpty(index).copy(text = afterMarker.trim())
            }
        }
    }
}

enum class ToolbarTitle {
    NEW_NOTE,
    EDIT_NOTE,
    NEW_CHECKLIST,
    EDIT_CHECKLIST
}

enum class ToastMessage {
    NOTE_IS_EMPTY,
    NOTE_SAVED,
    NOTE_DELETED,
    ITEM_COPIED_TO_CHECKLIST // 🆕 v2.2.0: Aktion 3 Bestätigung
}

sealed interface NoteEditorEvent {
    data class ShowToast(val message: ToastMessage) : NoteEditorEvent

    data object NavigateBack : NoteEditorEvent

    data class RestoreContent(val content: String) : NoteEditorEvent // 🆕 v1.10.0: Undo/Redo

    /** 🆕 v1.10.0-P2: Signals Activity to set result and finish so MainViewModel shows undo snackbar. */
    data class NoteDeleteRequested(val noteId: String) : NoteEditorEvent

    /** 🆕 v2.11.0 (Archive): Activity setzt Result und schließt; MainViewModel toggelt archivedAt. */
    data class NoteArchiveToggleRequested(val noteId: String) : NoteEditorEvent

    // 🆕 v1.10.0-Papa: Calendar & Share events
    // 🆕 v2.12.0: location/attendees populated by the persisted calendar-parsing strategy.
    data class OpenCalendar(
        val title: String,
        val description: String,
        val location: String = "",
        val attendees: String = ""
    ) : NoteEditorEvent

    data class ShareAsText(val title: String, val text: String) : NoteEditorEvent

    data class ShareAsPdf(val title: String) : NoteEditorEvent

    data class CopyToClipboard(val text: String) : NoteEditorEvent

    data object ActivatePreviewMode : NoteEditorEvent

    data object RequestContentFocus : NoteEditorEvent
}

/**
 * Sortiert Editor-States über [ChecklistSorter]. Top-level, weil der Editor dieselbe Sortierung
 * braucht, um vorherzusagen, wo ein aufgehakter Eintrag landet (Scroll oder Aufwachsen am Ort).
 */
internal fun sortChecklistStates(
    items: List<ChecklistItemState>,
    option: ChecklistSortOption
): List<ChecklistItemState> = ChecklistSorter.sort(items.map { it.toItem() }, option).map { it.toState() }

internal fun ChecklistItem.toState() = ChecklistItemState(
    id = id,
    text = text,
    isChecked = isChecked,
    order = order,
    originalOrder = originalOrder,
    createdAt = createdAt,
    indentationLevel = indentationLevel
)

/**
 * Gespeicherte Einträge als Editor-States, nach `order`. Altdaten bekommen Ersatzwerte: vor
 * v1.9.0 stand `originalOrder` überall auf 0 (dann gilt `order`), vor v1.11.0 fehlte `createdAt`
 * (dann gilt die Position).
 */
internal fun List<ChecklistItem>.toEditorStates(): List<ChecklistItemState> {
    val raw = sortedBy { it.order }
    val preOriginalOrder = raw.all { it.originalOrder == 0 }
    val preCreatedAt = raw.all { it.createdAt == 0L }
    return raw.mapIndexed { index, item ->
        item.toState().copy(
            originalOrder = if (preOriginalOrder) item.order else item.originalOrder,
            createdAt = if (preCreatedAt) index.toLong() else item.createdAt
        )
    }
}

/** Wendet eine [dev.dettmer.simplenotes.models.ChecklistNesting]-Operation auf Editor-States an. */
internal inline fun List<ChecklistItemState>.nested(op: (List<ChecklistItem>) -> List<ChecklistItem>) =
    op(map { it.toItem() }).map { it.toState() }
