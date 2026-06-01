package dev.dettmer.simplenotes.sync

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.thegrizzlylabs.sardineandroid.Sardine
import dev.dettmer.simplenotes.storage.FolderMeta
import dev.dettmer.simplenotes.storage.FolderStore
import dev.dettmer.simplenotes.storage.sanitized
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger

/**
 * 🆕 v2.7.0 (Folders): Synchronisiert die zentrale `<syncFolder>/folders.json` mit dem Server.
 * Per-Name-LWW-Merge (höchstes updatedAt gewinnt); Tombstones propagieren Löschungen.
 * Alle Fehler sind non-fatal — der Sync-Flow wird nicht unterbrochen.
 */
class FolderSyncManager(
    private val urlBuilder: SyncUrlBuilder,
    private val folderStore: FolderStore,
    private val prefs: SharedPreferences
) {
    private val gson = Gson()

    /** Lädt Server-folders.json, merged per-Ordner-LWW mit lokal, schreibt beide Seiten zurück.
     *  Gibt `true` zurück, wenn das Merge-Ergebnis vom lokalen Stand abweicht (Farbe/Name/Tombstone). */
    suspend fun sync(sardine: Sardine, serverUrl: String): Boolean {
        return try {
            val url = foldersFileUrl(serverUrl)
            val remote = downloadRemote(sardine, url)
            val local = folderStore.loadMeta()
            val merged = mergeByName(local, remote)
            folderStore.replaceMeta(merged)
            val dirty = prefs.getBoolean(Constants.KEY_FOLDERS_DIRTY, false)
            if (dirty || merged.toSet() != remote.toSet()) {
                sardine.put(url, gson.toJson(merged).toByteArray(Charsets.UTF_8), "application/json")
            }
            prefs.edit { putBoolean(Constants.KEY_FOLDERS_DIRTY, false) }
            Logger.d(TAG, "📁 folders.json synced: ${merged.count { !it.deleted }} active, ${merged.count { it.deleted }} tombstones")
            merged.toSet() != local.toSet()
        } catch (e: Exception) {
            Logger.w(TAG, "folders.json sync failed (non-fatal): ${e.message}")
            false
        }
    }

    private fun downloadRemote(sardine: Sardine, url: String): List<FolderMeta> = try {
        val exists = when (sardine) {
            is SafeSardineWrapper -> sardine.exists(url)
            else -> try { sardine.exists(url) } catch (_: Exception) { false }
        }
        if (!exists) {
            emptyList()
        } else {
            sardine.get(url).use { input ->
                val type = object : TypeToken<List<FolderMeta>>() {}.type
                (gson.fromJson<List<FolderMeta>>(input.reader(), type) ?: emptyList()).sanitized()
            }
        }
    } catch (e: Exception) {
        Logger.w(TAG, "download folders.json: ${e.message}")
        emptyList()
    }

    /** Per-Name-Merge (case-insensitiv): höchstes updatedAt gewinnt. Bei Gleichstand bleibt der erste (lokal). */
    internal fun mergeByName(local: List<FolderMeta>, remote: List<FolderMeta>): List<FolderMeta> {
        val byKey = LinkedHashMap<String, FolderMeta>()
        // .sanitized() schützt vor Gson-null-Namen (NPE in name.lowercase()) auch auf der lokalen Seite.
        (local + remote).sanitized().forEach { m ->
            val key = m.name.lowercase()
            val cur = byKey[key]
            if (cur == null || m.updatedAt > cur.updatedAt) byKey[key] = m
        }
        return byKey.values.toList()
    }

    private fun foldersFileUrl(serverUrl: String): String =
        urlBuilder.getNotesUrl(serverUrl).trimEnd('/') + "/" + FOLDERS_FILE_NAME

    companion object {
        private const val TAG = "FolderSyncManager"
        const val FOLDERS_FILE_NAME = "folders.json"
    }
}
