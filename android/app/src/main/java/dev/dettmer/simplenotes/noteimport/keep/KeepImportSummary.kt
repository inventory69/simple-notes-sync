package dev.dettmer.simplenotes.noteimport.keep

/**
 * v2.5.0 — End-Zusammenfassung. Wird im Result-Dialog (§4.2 [5]) angezeigt.
 *
 * Counter-Definitionen:
 *  - [imported]: neu erstellte Notizen.
 *  - [replaced]: per REPLACE-Strategy überschriebene Notizen.
 *  - [skipped]: per SKIP-Strategy oder Filter übersprungen.
 *  - [failed]: Per-Entry-Fehler (Soft-Fail-Pfad, Analyseplan §3.5).
 *  - [notesWithDroppedAttachments]: Notizen, die mind. 1 Attachment hatten.
 *  - [labelsImported]: Anzahl distinkter Labels nach Import.
 *  - [sharedNotesImported]: Notizen mit `sharees != []` (Kollaboratoren).
 *  - [notesWithColor]: Notizen mit nicht-DEFAULT Color.
 *  - [notesWithPin]: Notizen mit isPinned=true.
 */
data class KeepImportSummary(
    val totalEntries: Int,
    val imported: Int,
    val skipped: Int,
    val replaced: Int,
    val failed: Int,
    val notesWithDroppedAttachments: Int,
    val labelsImported: Int,
    val sharedNotesImported: Int,
    val notesWithColor: Int,
    val notesWithPin: Int,
    val errors: List<KeepImportError>,
) {
    companion object {
        val EMPTY = KeepImportSummary(
            totalEntries = 0, imported = 0, skipped = 0, replaced = 0, failed = 0,
            notesWithDroppedAttachments = 0, labelsImported = 0, sharedNotesImported = 0,
            notesWithColor = 0, notesWithPin = 0, errors = emptyList(),
        )
    }
}
