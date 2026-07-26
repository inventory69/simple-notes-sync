package dev.dettmer.simplenotes.sync.webdav

import java.io.IOException
import java.net.URI
import java.util.Date

/**
 * 🆕 v2.14.0: Ein Eintrag aus einer PROPFIND-Antwort.
 *
 * Ersetzt Sardines `DavResource` mit identischer Property-Semantik, damit die
 * bestehenden Aufrufer unverändert weiterlaufen:
 * - [href] ist der rohe Server-Wert (in der Regel ein absoluter Pfad, keine volle URL)
 * - [path] ist der dekodierte Pfad des href
 * - [name] ist das letzte Pfadsegment ohne Trailing-Slash
 *
 * @param contentLength `-1` wenn der Server keine `getcontentlength`-Property liefert
 *                      (gleiche Konvention wie Sardine).
 */
data class WebDavResource(
    val href: URI,
    val modified: Date?,
    val contentLength: Long,
    val isDirectory: Boolean,
    val etag: String?
) {
    /** Dekodierter Pfad — entspricht `DavResource.getPath()`. */
    val path: String
        get() = href.path.orEmpty()

    /**
     * Letztes Pfadsegment — entspricht `DavResource.getName()`.
     * Exakt ein Trailing-Slash wird entfernt (Verzeichnis-hrefs enden auf "/").
     */
    val name: String
        get() {
            val trimmed = path.removeSuffix("/")
            return trimmed.substringAfterLast('/')
        }
}

/**
 * WebDAV-Fehler mit erhaltenem HTTP-Statuscode.
 *
 * Erbt von [IOException], damit bestehende `catch (e: IOException)`-Pfade greifen.
 * Der Statuscode wird **nicht** nur im Message-String transportiert
 * (Constraint: typisierte Exception statt String-Matching).
 */
class WebDavException(message: String, val statusCode: Int) : IOException(message)

private const val HTTP_NOT_FOUND = 404

/**
 * `true`, wenn der Fehler ein HTTP 404 des Servers ist.
 *
 * Für die 404-toleranten Löschpfade — spart das fragile `message.contains("404")`,
 * das auch auf eine 404 im Dateinamen anspringen würde.
 */
fun Throwable.isWebDavNotFound(): Boolean = this is WebDavException && statusCode == HTTP_NOT_FOUND

/**
 * 🆕 v2.14.0: Vergleicht zwei ETags formattolerant.
 *
 * Derselbe Server liefert denselben ETag mal als `"abc"` (PUT-Header) und mal als
 * `W/"abc"` (PROPFIND-`getetag`). Ohne diesen Vergleich gälte die Notiz beim nächsten
 * Sync als geändert und würde unnötig neu geladen — genau der Request, den der
 * PUT-ETag einsparen soll. Gespeichert wird weiterhin der rohe Wert.
 */
fun etagsMatch(a: String?, b: String?): Boolean {
    if (a == null || b == null) return false
    return a.normalizeEtag() == b.normalizeEtag()
}

private fun String.normalizeEtag(): String =
    trim().removePrefix("W/").removePrefix("w/").trim('"')
