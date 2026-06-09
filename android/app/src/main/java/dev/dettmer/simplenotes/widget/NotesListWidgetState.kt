package dev.dettmer.simplenotes.widget

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object NotesListWidgetState {
    val KEY_SORT_OPTION = stringPreferencesKey("nl_sort_option")
    val KEY_SORT_DIRECTION = stringPreferencesKey("nl_sort_direction")
    val KEY_NOTE_FILTER = stringPreferencesKey("nl_note_filter")
    val KEY_LAST_UPDATED = longPreferencesKey("nl_last_updated")
    val KEY_BACKGROUND_OPACITY = floatPreferencesKey("nl_bg_opacity")
    val KEY_APPLY_OPACITY_TO_CARDS = booleanPreferencesKey("nl_cards_opacity")
    val KEY_FAB_EXPANDED = booleanPreferencesKey("nl_fab_expanded")
}
