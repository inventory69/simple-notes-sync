package dev.dettmer.simplenotes.sync.parallel

import dev.dettmer.simplenotes.models.Note

/**
 * 🆕 v1.9.0: Repräsentiert einen einzelnen Upload-Task
 *
 * @param note Die hochzuladende Notiz
 * @param noteUrl Vollständige URL zur JSON-Datei (z.B. https://server/notes/<uuid>.json)
 * @param markdownExportEnabled Ob Markdown-Export für diese Notiz aktiviert ist
 * @param markdownDirExists Ob notes-md/ Ordner bereits existiert
 */
data class UploadTask(
    val note: Note,
    val noteUrl: String,
    val markdownExportEnabled: Boolean,
    val markdownDirExists: Boolean
)

/**
 * 🆕 v1.9.0: Ergebnis eines einzelnen Uploads
 */
sealed class UploadTaskResult {
    /**
     * Upload erfolgreich
     *
     * @param noteId Die ID der hochgeladenen Notiz
     * @param etag E-Tag vom Server nach Upload (null wenn nicht verfügbar)
     */
    data class Success(
        val noteId: String,
        val etag: String?
    ) : UploadTaskResult()

    /**
     * Upload fehlgeschlagen
     *
     * @param noteId Die ID der nicht hochgeladenen Notiz
     * @param error Der aufgetretene Fehler
     */
    data class Failure(
        val noteId: String,
        val error: Throwable
    ) : UploadTaskResult()

    /**
     * Upload übersprungen (z.B. Content unverändert)
     *
     * @param noteId Die ID der übersprungenen Notiz
     * @param reason Grund für das Überspringen
     */
    data class Skipped(
        val noteId: String,
        val reason: String
    ) : UploadTaskResult()
}
