package dev.dettmer.simplenotes.widget

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.dettmer.simplenotes.models.NoteFilter
import dev.dettmer.simplenotes.models.SortDirection
import dev.dettmer.simplenotes.models.SortOption

data class NotesListWidgetConfig(
    val sortOption: SortOption = SortOption.UPDATED_AT,
    val sortDirection: SortDirection = SortDirection.DESCENDING,
    val filter: NoteFilter = NoteFilter.ALL,
    val opacity: Float = 1.0f,
    val applyOpacityToCards: Boolean = false,
    val hidePinned: Boolean = false,
    val hideFolders: Boolean = false,
    val selectedFolder: String = ""
)

object NotesListWidgetState {
    val KEY_SORT_OPTION = stringPreferencesKey("nl_sort_option")
    val KEY_SORT_DIRECTION = stringPreferencesKey("nl_sort_direction")
    val KEY_NOTE_FILTER = stringPreferencesKey("nl_note_filter")
    val KEY_LAST_UPDATED = longPreferencesKey("nl_last_updated")
    val KEY_BACKGROUND_OPACITY = floatPreferencesKey("nl_bg_opacity")
    val KEY_APPLY_OPACITY_TO_CARDS = booleanPreferencesKey("nl_cards_opacity")
    val KEY_FAB_EXPANDED = booleanPreferencesKey("nl_fab_expanded")
    val KEY_HIDE_PINNED = booleanPreferencesKey("nl_hide_pinned")
    val KEY_HIDE_FOLDERS = booleanPreferencesKey("nl_hide_folders")
    val KEY_SELECTED_FOLDER = stringPreferencesKey("nl_selected_folder")
}
