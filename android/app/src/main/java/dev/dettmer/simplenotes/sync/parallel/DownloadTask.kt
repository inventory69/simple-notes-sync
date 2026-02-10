package dev.dettmer.simplenotes.sync.parallel

import com.thegrizzlylabs.sardineandroid.DavResource

/**
 * 🆕 v1.8.0: Repräsentiert einen einzelnen Download-Task
 *
 * @param noteId Die ID der Notiz (ohne .json Extension)
 * @param url Vollständige URL zur JSON-Datei
 * @param resource WebDAV-Resource mit Metadaten
 * @param serverETag E-Tag vom Server (für Caching)
 * @param serverModified Letztes Änderungsdatum vom Server (Unix timestamp)
 */
data class DownloadTask(
    val noteId: String,
    val url: String,
    val resource: DavResource,
    val serverETag: String?,
    val serverModified: Long
)

/**
 * 🆕 v1.8.0: Ergebnis eines einzelnen Downloads
 *
 * Sealed class für typ-sichere Verarbeitung von Download-Ergebnissen.
 * Jeder Download kann erfolgreich sein, fehlschlagen oder übersprungen werden.
 */
sealed class DownloadTaskResult {
    /**
     * Download erfolgreich abgeschlossen
     *
     * @param noteId Die ID der heruntergeladenen Notiz
     * @param content JSON-Inhalt der Notiz
     * @param etag E-Tag vom Server (für zukünftiges Caching)
     */
    data class Success(
        val noteId: String,
        val content: String,
        val etag: String?
    ) : DownloadTaskResult()

    /**
     * Download fehlgeschlagen
     *
     * @param noteId Die ID der Notiz, die nicht heruntergeladen werden konnte
     * @param error Der aufgetretene Fehler
     */
    data class Failure(
        val noteId: String,
        val error: Throwable
    ) : DownloadTaskResult()

    /**
     * Download übersprungen (z.B. wegen gelöschter Notiz)
     *
     * @param noteId Die ID der übersprungenen Notiz
     * @param reason Grund für das Überspringen
     */
    data class Skipped(
        val noteId: String,
        val reason: String
    ) : DownloadTaskResult()
}
