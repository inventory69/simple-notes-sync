package dev.dettmer.simplenotes.noteimport.keep.model

/**
 * Hierarchie-fähiges Intermediate-Modell für eine Keep-Checklisten-Zeile.
 *
 * v2.5.0: `indentationLevel` wird beim Mapping (Commit #7) auf
 * [dev.dettmer.simplenotes.models.ChecklistItem] übertragen, dort aber
 * vorerst nicht gerendert (Analyseplan §2.4.2 + §3.7.1).
 *
 * @property text Der Item-Text. Leading-Whitespace ist als pseudo-Indent zu lesen
 *           und in `indentationLevel` aufzulösen, bevor diese Klasse erzeugt wird;
 *           der Mapper trim-startet `text` nochmal defensiv (siehe Commit #7).
 * @property isChecked Ob das Item abgehakt ist.
 * @property indentationLevel 0 = Top-Level. In der Praxis max. 3 (Keep-Web-UI-Cap).
 *           Wird vom Parser aus Leading-Whitespace abgeleitet (2 Spaces ≈ 1 Level).
 */
data class KeepChecklistItem(
    val text: String,
    val isChecked: Boolean,
    val indentationLevel: Int = 0
)
