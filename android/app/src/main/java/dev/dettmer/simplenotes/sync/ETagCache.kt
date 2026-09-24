package dev.dettmer.simplenotes.sync

import android.content.SharedPreferences
import androidx.core.content.edit
import dev.dettmer.simplenotes.utils.Logger

/**
 * 🆕 v2.0.0: Extracted from WebDavSyncService.
 * Manages E-Tag caching in SharedPreferences.
 *
 * Key conventions:
 * - JSON notes: "etag_json_<noteId>"
 * - Markdown files: "etag_md_<noteId>"
 * - Markdown file URL: "etag_md_path_<noteId>" (Präfix `etag_md_`, damit [clearAll] ihn mitnimmt)
 */
class ETagCache(private val prefs: SharedPreferences) {
    companion object {
        private const val TAG = "ETagCache"
        private const val PREFIX_JSON = "etag_json_"
        private const val PREFIX_MD = "etag_md_"
        private const val PREFIX_MD_PATH = "${PREFIX_MD}path_"
    }

    fun getJsonETag(noteId: String): String? = prefs.getString("$PREFIX_JSON$noteId", null)

    fun getMdETag(noteId: String): String? = prefs.getString("$PREFIX_MD$noteId", null)

    /**
     * 🆕 v2.19.0: Volle URL, unter der die MD-Kopie der Notiz zuletzt lag. Überlebt
     * [clearForNote] bewusst (läuft auch nach Downloads), damit ein Export die alte Kopie findet.
     */
    fun getMdPath(noteId: String): String? = prefs.getString("$PREFIX_MD_PATH$noteId", null)

    fun setMdPath(noteId: String, url: String) = prefs.edit { putString("$PREFIX_MD_PATH$noteId", url) }

    fun clearMdPath(noteId: String) = prefs.edit { remove("$PREFIX_MD_PATH$noteId") }

    /**
     * Batch-updates E-Tags. Keys must include the full prefix (e.g. "etag_json_<id>").
     * Null values remove the corresponding key.
     */
    fun batchUpdate(updates: Map<String, String?>) {
        try {
            var putCount = 0
            var removeCount = 0

            prefs.edit {
                updates.forEach { (key, value) ->
                    if (value != null) {
                        putString(key, value)
                        putCount++
                    } else {
                        remove(key)
                        removeCount++
                    }
                }
            }
            Logger.d(TAG, "⚡ Batch-updated E-Tags: $putCount saved, $removeCount removed")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to batch-update E-Tags", e)
        }
    }

    /** Removes all cached E-Tags (json + markdown). Used before a full restore. */
    fun clearAll() {
        try {
            prefs.edit {
                prefs.all.keys.filter { it.startsWith(PREFIX_JSON) }.forEach { remove(it) }
                prefs.all.keys.filter { it.startsWith(PREFIX_MD) }.forEach { remove(it) }
            }
            Logger.d(TAG, "🔄 Cleared all E-Tag caches")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to clear E-Tag caches", e)
        }
    }

    /** Removes cached E-Tags for a specific note (json + markdown). */
    fun clearForNote(noteId: String) {
        prefs.edit {
            remove("$PREFIX_JSON$noteId")
            remove("$PREFIX_MD$noteId")
        }
    }
}
