package dev.dettmer.simplenotes.widget

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * 🆕 v1.8.0: Widget-State Keys (per Widget-Instance)
 *
 * Gespeichert via PreferencesGlanceStateDefinition (DataStore).
 * Jede Widget-Instanz hat eigene Preferences.
 */
object NoteWidgetState {
    /** ID der angezeigten Notiz */
    val KEY_NOTE_ID = stringPreferencesKey("widget_note_id")

    /** Ob das Widget gesperrt ist (keine Bearbeitung möglich) */
    val KEY_IS_LOCKED = booleanPreferencesKey("widget_is_locked")

    /** Ob die Optionsleiste angezeigt wird */
    val KEY_SHOW_OPTIONS = booleanPreferencesKey("widget_show_options")

    /** Hintergrund-Transparenz (0.0 = vollständig transparent, 1.0 = opak) */
    val KEY_BACKGROUND_OPACITY = floatPreferencesKey("widget_bg_opacity")

    /** Timestamp des letzten Updates — erzwingt Widget-Recomposition */
    val KEY_LAST_UPDATED = longPreferencesKey("widget_last_updated")
}
