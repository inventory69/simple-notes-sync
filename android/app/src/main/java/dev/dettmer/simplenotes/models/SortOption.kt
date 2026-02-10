package dev.dettmer.simplenotes.models

/**
 * 🆕 v1.8.0: Sortieroptionen für die Notizliste
 */
enum class SortOption(val prefsValue: String) {
    /** Zuletzt bearbeitete zuerst (Default) */
    UPDATED_AT("updatedAt"),
    
    /** Zuletzt erstellte zuerst */
    CREATED_AT("createdAt"),
    
    /** Alphabetisch nach Titel */
    TITLE("title"),
    
    /** Nach Notiz-Typ (Text / Checkliste) */
    NOTE_TYPE("noteType");
    
    companion object {
        fun fromPrefsValue(value: String): SortOption {
            return entries.find { it.prefsValue == value } ?: UPDATED_AT
        }
    }
}
