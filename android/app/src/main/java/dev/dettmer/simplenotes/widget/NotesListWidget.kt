package dev.dettmer.simplenotes.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteFilter
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.models.SortDirection
import dev.dettmer.simplenotes.models.SortOption
import dev.dettmer.simplenotes.storage.FolderStore
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.widget.NotesListWidgetState.KEY_APPLY_OPACITY_TO_CARDS
import dev.dettmer.simplenotes.widget.NotesListWidgetState.KEY_BACKGROUND_OPACITY
import dev.dettmer.simplenotes.widget.NotesListWidgetState.KEY_FAB_EXPANDED
import dev.dettmer.simplenotes.widget.NotesListWidgetState.KEY_HIDE_FOLDERS
import dev.dettmer.simplenotes.widget.NotesListWidgetState.KEY_HIDE_HEADER
import dev.dettmer.simplenotes.widget.NotesListWidgetState.KEY_HIDE_PINNED
import dev.dettmer.simplenotes.widget.NotesListWidgetState.KEY_NOTE_FILTER
import dev.dettmer.simplenotes.widget.NotesListWidgetState.KEY_SELECTED_FOLDER
import dev.dettmer.simplenotes.widget.NotesListWidgetState.KEY_FONT_SIZE_SCALE
import dev.dettmer.simplenotes.widget.NotesListWidgetState.KEY_SORT_DIRECTION
import dev.dettmer.simplenotes.widget.NotesListWidgetState.KEY_SORT_OPTION

private const val NOTES_LIST_WIDGET_MAX_NOTES = 50

class NotesListWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val storage = NotesStorage(context)
        val allNotes = storage.loadAllNotes()
        val folders = FolderStore(context).loadFolders()
        val folderNoteCounts = folders.associate { f -> f.name to allNotes.count { it.folderName == f.name } }

        provideContent {
            val prefs = currentState<Preferences>()
            val sortOption = SortOption.fromPrefsValue(prefs[KEY_SORT_OPTION] ?: SortOption.UPDATED_AT.prefsValue)
            val sortDir = SortDirection.fromPrefsValue(prefs[KEY_SORT_DIRECTION] ?: SortDirection.DESCENDING.prefsValue)
            val noteFilter = NoteFilter.fromPrefsValue(prefs[KEY_NOTE_FILTER] ?: NoteFilter.ALL.prefsValue)
            val bgOpacity = prefs[KEY_BACKGROUND_OPACITY] ?: 1.0f
            val applyOpacityToCards = prefs[KEY_APPLY_OPACITY_TO_CARDS] ?: false
            val cardOpacity = if (applyOpacityToCards) bgOpacity else 1.0f
            val fabExpanded = prefs[KEY_FAB_EXPANDED] ?: false
            val hideHeader = prefs[KEY_HIDE_HEADER] ?: false
            val hidePinned = prefs[KEY_HIDE_PINNED] ?: false
            val hideFolders = prefs[KEY_HIDE_FOLDERS] ?: false
            val selectedFolder = prefs[KEY_SELECTED_FOLDER]?.takeIf { it.isNotEmpty() }
            val fontSizeScale = prefs[KEY_FONT_SIZE_SCALE] ?: 1.0f

            val sourceNotes = if (selectedFolder != null) {
                allNotes.filter { it.folderName == selectedFolder }
            } else {
                allNotes.filter { it.folderName == null }
            }
            val hasPinnedNotes = sourceNotes.any { it.isPinned == true }
            val filteredByPinned = if (hidePinned) sourceNotes.filter { it.isPinned != true } else sourceNotes
            val notes = applyFilterAndSort(filteredByPinned, noteFilter, sortOption, sortDir)
            val foldersToShow = if (selectedFolder != null || hideFolders) emptyList() else folders

            GlanceTheme {
                NotesListWidgetContent(
                    notes = notes,
                    folders = foldersToShow,
                    folderNoteCounts = folderNoteCounts,
                    bgOpacity = bgOpacity,
                    cardBgOpacity = cardOpacity,
                    fabExpanded = fabExpanded,
                    hasPinnedNotes = hasPinnedNotes,
                    hideHeader = hideHeader,
                    fontSizeScale = fontSizeScale
                )
            }
        }
    }
}

fun applyFilterAndSort(
    notes: List<Note>,
    filter: NoteFilter,
    sortOption: SortOption,
    sortDirection: SortDirection
): List<Note> {
    val filtered = when (filter) {
        NoteFilter.ALL -> notes
        NoteFilter.TEXT_ONLY -> notes.filter { it.noteType == NoteType.TEXT }
        NoteFilter.CHECKLIST_ONLY -> notes.filter { it.noteType == NoteType.CHECKLIST }
    }

    // SortOption.COLOR is skipped — no color palette reference in widget; falls back to UPDATED_AT.
    val comparator: Comparator<Note> = when (sortOption) {
        SortOption.UPDATED_AT, SortOption.COLOR -> compareBy { it.updatedAt }
        SortOption.CREATED_AT -> compareBy { it.createdAt }
        SortOption.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        SortOption.NOTE_TYPE -> compareBy<Note> { it.noteType.ordinal }.thenByDescending { it.updatedAt }
    }

    val sorted = when (sortDirection) {
        SortDirection.ASCENDING -> filtered.sortedWith(comparator)
        SortDirection.DESCENDING -> filtered.sortedWith(comparator.reversed())
    }

    val result = sorted.filter { it.isPinned == true } + sorted.filter { it.isPinned != true }

    return result.take(NOTES_LIST_WIDGET_MAX_NOTES)
}
