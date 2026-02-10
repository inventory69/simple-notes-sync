package dev.dettmer.simplenotes.models

/**
 * 🆕 v1.8.0: Sortierrichtung
 */
enum class SortDirection(val prefsValue: String) {
    ASCENDING("asc"),
    DESCENDING("desc");
    
    fun toggle(): SortDirection = when (this) {
        ASCENDING -> DESCENDING
        DESCENDING -> ASCENDING
    }
    
    companion object {
        fun fromPrefsValue(value: String): SortDirection {
            return entries.find { it.prefsValue == value } ?: DESCENDING
        }
    }
}
