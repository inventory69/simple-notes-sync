package dev.dettmer.simplenotes.sync

import android.content.SharedPreferences
import dev.dettmer.simplenotes.utils.Constants

/**
 * 🆕 v2.0.0: Extracted from WebDavSyncService (Commit 17).
 * Constructs WebDAV server URLs for notes and markdown folders.
 *
 * Reads the configured sync folder name from SharedPreferences on each call
 * so it always reflects the current user setting without caching.
 */
class SyncUrlBuilder(private val prefs: SharedPreferences) {
    companion object {
        private const val MARKDOWN_SUFFIX = "-md"
    }

    /**
     * Returns the configured server URL, or null if not set.
     */
    fun getServerUrl(): String? {
        return prefs.getString(Constants.KEY_SERVER_URL, null)
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
}
