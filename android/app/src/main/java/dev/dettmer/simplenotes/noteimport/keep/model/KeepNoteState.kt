package dev.dettmer.simplenotes.noteimport.keep.model

/**
 * Lifecycle-Status einer Keep-Notiz nach Auswertung von `isArchived` / `isTrashed`.
 *
 * Entscheidungstabelle:
 * - `isTrashed=true`             → [TRASHED]
 * - `isTrashed=false`, `isArchived=true`  → [ARCHIVED]
 * - sonst                                 → [ACTIVE]
 */
enum class KeepNoteState { ACTIVE, ARCHIVED, TRASHED }
