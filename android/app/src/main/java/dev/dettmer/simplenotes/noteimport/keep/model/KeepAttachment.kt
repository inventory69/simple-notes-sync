package dev.dettmer.simplenotes.noteimport.keep.model

/**
 * Vollständige Attachment-Metadaten aus dem Keep-JSON.
 *
 * v2.5.0: nicht in die App-Notiz übernommen — nur Counter in
 * [dev.dettmer.simplenotes.noteimport.keep.KeepImportSummary.notesWithDroppedAttachments].
 *
 * In v2.6.x+ liest ein "Re-import attachments only"-Flow das ZIP nochmal,
 * matcht über `Note.importedAt` und übernimmt die Bilder (Analyseplan §3.7.3).
 *
 * @property filePath Relativ zum Takeout-Root. Häufig im selben Verzeichnis wie das JSON.
 * @property mimeType z.B. `image/jpeg`, `image/png`.
 * @property sizeBytes Optional, wird nur gesetzt wenn der ZIP-Reader die Größe
 *           ohnehin kennt (siehe `KeepZipReader` Commit #6).
 */
data class KeepAttachment(
    val filePath: String,
    val mimeType: String,
    val sizeBytes: Long? = null
)
