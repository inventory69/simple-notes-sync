package dev.dettmer.simplenotes.models

/**
 * 🆕 v1.9.0 (F06): Filter-Optionen für die Notizliste.
 *
 * Ermöglicht das Filtern nach Notiz-Typ (Text, Checkliste, oder Alle).
 *
 * @property prefsValue Der Wert, der in SharedPreferences gespeichert wird.
 */
enum class NoteFilter(val prefsValue: String) {
    ALL("all"),
    TEXT_ONLY("text"),
    CHECKLIST_ONLY("checklist");

    companion object {
        fun fromPrefsValue(value: String): NoteFilter {
            return entries.find { it.prefsValue == value } ?: ALL
        }
    }
}
