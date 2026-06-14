package dev.dettmer.simplenotes.noteimport.keep

/**
 * v2.5.0 — Per-Entry-Fehler. `sourceName` ist der ZIP-Entry-Name, damit der
 * User die Notiz im ZIP wiederfinden kann.
 */
data class KeepImportError(
    val sourceName: String,
    val message: String
)
