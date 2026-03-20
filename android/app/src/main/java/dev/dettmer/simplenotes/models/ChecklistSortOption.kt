package dev.dettmer.simplenotes.models

/**
 * 🆕 v1.8.0: Sortieroptionen für Checklist-Items im Editor
 * 🆕 v1.11.0: CREATION_DATE_DESC hinzugefügt
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
    CHECKED_FIRST,

    /** 🆕 v1.11.0: Nach Erstellungszeitpunkt — älteste zuerst (aufsteigend) */
    CREATION_DATE,

    /** 🆕 v1.11.0: Nach Erstellungszeitpunkt — neueste zuerst (absteigend) */
    CREATION_DATE_DESC
}
