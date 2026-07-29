package dev.dettmer.simplenotes.sync

import android.content.SharedPreferences
import dev.dettmer.simplenotes.utils.Constants
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 🆕 v2.0.0: Extracted from WebDavSyncService (Commit 17).
 * Constructs WebDAV server URLs for notes and markdown folders.
 *
 * Reads the configured sync folder name from SharedPreferences on each call
 * so it always reflects the current user setting without caching.
 */
class SyncUrlBuilder(private val prefs: SharedPreferences) {
    companion object {
        internal const val MARKDOWN_SUFFIX = "-md"
        internal const val ASSETS_SUFFIX = "-assets"
    }

    /**
     * Returns the configured server URL, or null if not set.
     *
     * 🔧 Kanonisiert den gespeicherten Wert über OkHttps `HttpUrl`: der kommt aus einem Textfeld
     * und darf rohe Sonderzeichen im Pfad enthalten — etwa das Leerzeichen in
     * `.../Userstore/Max Mustermann/notes/` (OX App Suite legt den Userstore unter dem Klarnamen
     * an). OkHttp kodiert das beim Request selbst, `java.net.URI` in [dev.dettmer.simplenotes.sync
     * .webdav.groupByFolder] wirft dagegen `URISyntaxException` und ließ den kompletten Sync
     * scheitern. Nach dem Encoding liefert `URI(url).path` denselben dekodierten Pfad, gegen den
     * die PROPFIND-hrefs verglichen werden.
     *
     * Unparsbare Werte (z. B. der Platzhalter `https://`) gehen unverändert zurück — die
     * bestehenden Leer-Prüfungen der Aufrufer greifen wie bisher.
     */
    fun getServerUrl(): String? {
        val raw = prefs.getString(Constants.KEY_SERVER_URL, null) ?: return null
        return raw.toHttpUrlOrNull()?.toString() ?: raw
    }

    /**
     * Constructs the notes folder URL from the base server URL.
     *
     * Examples:
     * - http://server:8080/ → http://server:8080/notes/
     * - http://server:8080/notes/ → http://server:8080/notes/
     * - http://server:8080/notes → http://server:8080/notes/
     * - http://server:8080/my-path/ → http://server:8080/my-path/notes/
     *
     * @param baseUrl Base server URL
     * @return Notes folder URL (with trailing slash)
     */
    fun getNotesUrl(baseUrl: String): String {
        val folderName = prefs.getString(Constants.KEY_SYNC_FOLDER_NAME, Constants.DEFAULT_SYNC_FOLDER_NAME)
            ?: Constants.DEFAULT_SYNC_FOLDER_NAME
        val normalized = baseUrl.trimEnd('/')
        return if (normalized.endsWith("/$folderName")) {
            "$normalized/"
        } else {
            "$normalized/$folderName/"
        }
    }

    /**
     * Constructs the markdown folder URL based on the notes folder URL.
     *
     * Examples:
     * - http://server:8080/ → http://server:8080/notes-md/
     * - http://server:8080/notes/ → http://server:8080/notes-md/
     * - http://server:8080/my-path/ → http://server:8080/my-path/notes-md/
     *
     * @param baseUrl Base server URL
     * @return Markdown folder URL (with trailing slash)
     */
    fun getMarkdownUrl(baseUrl: String): String {
        val folderName = prefs.getString(Constants.KEY_SYNC_FOLDER_NAME, Constants.DEFAULT_SYNC_FOLDER_NAME)
            ?: Constants.DEFAULT_SYNC_FOLDER_NAME
        val notesUrl = getNotesUrl(baseUrl)
        val normalized = notesUrl.trimEnd('/')
        return normalized.replace("/$folderName", "/$folderName$MARKDOWN_SUFFIX") + "/"
    }

    /**
     * 🆕 v2.7.0 (Folders): URL eines JSON-Ordners. folderName == null → identisch zu getNotesUrl.
     * Das Pfadsegment wird URL-enkodiert (Leerzeichen → %20 usw.).
     */
    fun getNotesFolderUrl(baseUrl: String, folderName: String?): String {
        val base = getNotesUrl(baseUrl)
        if (folderName.isNullOrEmpty()) return base
        return base + encodeSegment(folderName) + "/"
    }

    /**
     * 🆕 v2.7.0 (Folders): URL eines Markdown-Ordners (nested mirror).
     * Robust gebaut: Folder-Segment NACH der berechneten md-Basis angehängt — kein String-replace
     * auf "/notes", damit ein Ordner namens "notes" den Pfad nicht korrumpiert.
     */
    fun getMarkdownFolderUrl(baseUrl: String, folderName: String?): String {
        val base = getMarkdownUrl(baseUrl)
        if (folderName.isNullOrEmpty()) return base
        return base.trimEnd('/') + "/" + encodeSegment(folderName) + "/"
    }

    /**
     * 🆕 Bild-Attachments: Geschwister-Ordner `<syncFolder>-assets/` (flach), analog zu
     * [getMarkdownUrl]. Bewusst NICHT `.assets/` im Notiz-Baum — jedes Unterverzeichnis
     * dort würde von bereits ausgelieferten Clients (Subdir-Scan) als Notiz-Ordner gelesen.
     */
    fun getAssetsUrl(baseUrl: String): String {
        val folderName = prefs.getString(Constants.KEY_SYNC_FOLDER_NAME, Constants.DEFAULT_SYNC_FOLDER_NAME)
            ?: Constants.DEFAULT_SYNC_FOLDER_NAME
        val notesUrl = getNotesUrl(baseUrl)
        val normalized = notesUrl.trimEnd('/')
        return normalized.replace("/$folderName", "/$folderName$ASSETS_SUFFIX") + "/"
    }

    /** URL einer einzelnen Asset-Datei im Geschwister-Ordner (flach, keine Unterordner). */
    fun getAssetUrl(baseUrl: String, assetName: String): String =
        getAssetsUrl(baseUrl) + encodeSegment(assetName)

    private fun encodeSegment(segment: String): String =
        java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
}
