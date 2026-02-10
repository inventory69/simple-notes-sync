package dev.dettmer.simplenotes.models

/**
 * 🆕 v1.8.0: Sortieroptionen für Checklist-Items im Editor
 */
enum class ChecklistSortOption {
    /** Manuelle Reihenfolge (Drag & Drop) — kein Re-Sort */
    MANUAL,
    
    /** Alphabetisch A→Z */
    ALPHABETICAL_ASC,
    
    /** Alphabetisch Z→A */
    ALPHABETICAL_DESC,
    
    /** Unchecked zuerst, dann Checked */
    UNCHECKED_FIRST,
    
    /** Checked zuerst, dann Unchecked */
    CHECKED_FIRST
}
