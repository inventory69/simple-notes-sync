package dev.dettmer.simplenotes.sync

import android.content.SharedPreferences
import dev.dettmer.simplenotes.utils.Logger

/**
 * 🆕 v2.0.0: Extracted from WebDavSyncService.
 * Manages E-Tag caching in SharedPreferences.
 *
 * Key conventions:
 * - JSON notes: "etag_json_<noteId>"
 * - Markdown files: "etag_md_<noteId>"
 */
class ETagCache(private val prefs: SharedPreferences) {

    companion object {
        private const val TAG = "ETagCache"
        private const val PREFIX_JSON = "etag_json_"
        private const val PREFIX_MD = "etag_md_"
    }

    fun getJsonETag(noteId: String): String? = prefs.getString("$PREFIX_JSON$noteId", null)

    fun getMdETag(noteId: String): String? = prefs.getString("$PREFIX_MD$noteId", null)

    /**
     * Batch-updates E-Tags. Keys must include the full prefix (e.g. "etag_json_<id>").
     * Null values remove the corresponding key.
     */
    fun batchUpdate(updates: Map<String, String?>) {
        try {
            val editor = prefs.edit()
            var putCount = 0
            var removeCount = 0

            updates.forEach { (key, value) ->
                if (value != null) {
                    editor.putString(key, value)
                    putCount++
                } else {
                    editor.remove(key)
                    removeCount++
                }
            }

            editor.apply()
            Logger.d(TAG, "⚡ Batch-updated E-Tags: $putCount saved, $removeCount removed")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to batch-update E-Tags", e)
        }
    }

    /** Removes all cached E-Tags (json + markdown). Used before a full restore. */
    fun clearAll() {
        try {
            val editor = prefs.edit()
            prefs.all.keys.filter { it.startsWith(PREFIX_JSON) }.forEach { editor.remove(it) }
            prefs.all.keys.filter { it.startsWith(PREFIX_MD) }.forEach { editor.remove(it) }
            editor.apply()
            Logger.d(TAG, "🔄 Cleared all E-Tag caches")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to clear E-Tag caches", e)
        }
    }

    /** Removes cached E-Tags for a specific note (json + markdown). */
    fun clearForNote(noteId: String) {
        prefs.edit()
            .remove("$PREFIX_JSON$noteId")
            .remove("$PREFIX_MD$noteId")
            .apply()
    }
}
