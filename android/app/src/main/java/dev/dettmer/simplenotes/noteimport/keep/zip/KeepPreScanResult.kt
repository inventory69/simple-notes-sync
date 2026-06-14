package dev.dettmer.simplenotes.noteimport.keep.zip

/**
 * v2.5.0 — Klassifikations-Ergebnis eines Pre-Scan-Passes über ein
 * Keep-Takeout-ZIP. Wird im Configuring-Dialog (§4.2 [3]) angezeigt.
 *
 * Hinweis: `labelCount` zählt **distinkte** Label-Namen über alle Notizen.
 *
 * `matchingCount` ist parameterisiert mit Booleans (statt einer Options-Klasse),
 * damit dieser ZIP-Layer keine Abhängigkeit zur Use-Case-Schicht (Commit #10) hat.
 */
data class KeepPreScanResult(
    val totalNotes: Int,
    val activeCount: Int,
    val archivedCount: Int,
    val trashedCount: Int,
    val labelCount: Int,
    val sharedCount: Int,
    val notesWithAttachments: Int,
    val sizeBytes: Long
) {
    /**
     * Wieviele Notizen würden mit den gegebenen Filter-Schaltern verarbeitet?
     * (Active wird immer eingeschlossen.)
     */
    fun matchingCount(includeArchived: Boolean, includeTrashed: Boolean): Int {
        var n = activeCount
        if (includeArchived) n += archivedCount
        if (includeTrashed) n += trashedCount
        return n
    }

    companion object {
        val EMPTY = KeepPreScanResult(
            totalNotes = 0,
            activeCount = 0,
            archivedCount = 0,
            trashedCount = 0,
            labelCount = 0,
            sharedCount = 0,
            notesWithAttachments = 0,
            sizeBytes = 0L
        )
    }
}
