package dev.dettmer.simplenotes.noteimport.keep.model

/**
 * Vollständiges Intermediate-Modell einer Keep-Notiz.
 *
 * v2.5.0 — bewusst getrennt vom App-`Note`-Modell, damit Keep-spezifische
 * Felder (`color`, `isPinned`, `annotations`, `attachments`) das App-Modell
 * nicht verschmutzen. Mapping nach `Note` passiert in `KeepToNoteMapper`
 * (Commit #7).
 *
 * **Timestamp-Konvention:** Keep liefert `…TimestampUsec` in **Mikrosekunden**.
 * Diese Klasse hält den Roh-Wert; Konvertierung passiert ausschließlich in
 * [createdAtMs] / [updatedAtMs] (Faktor 1_000), siehe Analyseplan §2.1 #1.
 *
 * @property title Kann `""` sein (Keep erlaubt leere Titel).
 * @property textContent `null` bei reinen Listen-Notizen, `""` bei Notizen
 *           mit nur Attachments oder leerem Web-Clip.
 * @property checklist Leere Liste = nicht-Checklisten-Notiz.
 * @property labels Leere Liste = keine Labels.
 * @property attachments v2.5.0: nur Statistik-Counter; Bytes werden verworfen.
 * @property annotations v2.5.0: nur `WEBLINK`-URLs werden an Content angehängt.
 * @property color Roh-Keep-Farbname (`"DEFAULT"`, `"RED"`, `"BLUE"`, …).
 *           Mapping auf Hex erfolgt in [KeepColor.toHex] beim Mapping.
 * @property isPinned Roh-Wert. Mapper schreibt ihn nach `Note.isPinned`.
 * @property state Lifecycle (siehe [KeepNoteState]).
 * @property createdTimestampUsec Mikrosekunden seit Epoch.
 * @property userEditedTimestampUsec Mikrosekunden seit Epoch.
 * @property sourceJsonName Quell-Dateiname im ZIP — nur für Logging/Fehler-Reporting.
 */
data class KeepNote(
    val title: String,
    val textContent: String?,
    val checklist: List<KeepChecklistItem> = emptyList(),
    val labels: List<KeepLabel> = emptyList(),
    val attachments: List<KeepAttachment> = emptyList(),
    val annotations: List<KeepAnnotation> = emptyList(),
    val color: String = "DEFAULT",
    val isPinned: Boolean = false,
    val state: KeepNoteState = KeepNoteState.ACTIVE,
    val createdTimestampUsec: Long,
    val userEditedTimestampUsec: Long,
    val sourceJsonName: String,
    /**
     * 🆕 v2.5.0 — `sharees != []` aus dem Keep-JSON. Wird in [KeepImportSummary]
     * (Commit #10) als `sharedNotesImported` gezählt, aber nicht als Label
     * geschrieben (Maintainer-Entscheidung 2026-05-08, siehe §7.2).
     */
    val isShared: Boolean = false
) {
    /** Mikrosekunden → Millisekunden (Faktor 1_000). */
    fun createdAtMs(): Long = createdTimestampUsec / USEC_PER_MS

    /** Mikrosekunden → Millisekunden (Faktor 1_000). */
    fun updatedAtMs(): Long = userEditedTimestampUsec / USEC_PER_MS

    /** True, wenn die Notiz nur Attachments hat — leerer Text und keine Checkliste. */
    fun hasOnlyAttachments(): Boolean =
        textContent.isNullOrBlank() && checklist.isEmpty() && attachments.isNotEmpty()

    companion object {
        private const val USEC_PER_MS = 1_000L
    }
}
