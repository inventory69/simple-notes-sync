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
     *  Gibt `true` zurück, wenn das Merge-Ergebnis vom lokalen Stand abweicht (Farbe/Name/Tombstone).
     *
     *  🆕 v2.8.0 (Local-Only Folders): Local-only-Ordner sind für beide Richtungen unsichtbar —
     *  Remote-Einträge gleichen Namens werden beim Merge ignoriert (sonst würde z. B. ein
     *  Remote-Tombstone den lokalen Ordner löschen) und lokale Einträge nie hochgeladen.
     *  Für die Upload-Seite wird stattdessen der Remote-Stand unverändert durchgereicht
     *  („Auf Server behalten") bzw. als Tombstone geschrieben, wenn der Ordner in der
     *  Server-Removal-Queue steht („Vom Server entfernen"). */
    suspend fun sync(sardine: Sardine, serverUrl: String): Boolean {
        return try {
            val url = foldersFileUrl(serverUrl)
            val remote = downloadRemote(sardine, url)
            val local = folderStore.loadMeta()

            val localOnlyLower = folderStore.getLocalOnlyFolderNames().map { it.lowercase() }.toSet()
            val removalQueue = folderStore.getServerRemovalQueue()
            val removalLower = removalQueue.map { it.lowercase() }.toSet()
            val excludedLower = localOnlyLower + removalLower

            val remoteVisible = remote.filterNot { it.name.lowercase() in excludedLower }
            val merged = mergeByName(local, remoteVisible)
            folderStore.replaceMeta(merged)

            val uploadList = buildUploadList(merged, remote, localOnlyLower, removalLower)
            val dirty = prefs.getBoolean(Constants.KEY_FOLDERS_DIRTY, false)
            if (dirty || uploadList.toSet() != remote.toSet()) {
                sardine.put(url, gson.toJson(uploadList).toByteArray(Charsets.UTF_8), "application/json")
            }
            prefs.edit { putBoolean(Constants.KEY_FOLDERS_DIRTY, false) }
            // Removal-Intents sind nach erfolgreichem Upload erledigt (Tombstone liegt auf dem Server).
            if (removalQueue.isNotEmpty()) {
                folderStore.setServerRemovalQueue(folderStore.getServerRemovalQueue() - removalQueue)
            }
            Logger.d(TAG, "📁 folders.json synced: ${merged.count { !it.deleted }} active, ${merged.count { it.deleted }} tombstones")
            merged.toSet() != local.toSet()
        } catch (e: Exception) {
            Logger.w(TAG, "folders.json sync failed (non-fatal): ${e.message}")
            false
        }
    }

    /** Upload-Sicht: merged ohne local-only-Einträge; für ausgeblendete Namen den Remote-Stand
     *  durchreichen (keep-on-server) oder tombstonen (Removal-Queue). */
    private fun buildUploadList(
        merged: List<FolderMeta>,
        remote: List<FolderMeta>,
        localOnlyLower: Set<String>,
        removalLower: Set<String>
    ): List<FolderMeta> {
        val excludedLower = localOnlyLower + removalLower
        val remoteByLower = remote.associateBy { it.name.lowercase() }
        val passThrough = excludedLower.mapNotNull { key ->
            val remoteEntry = remoteByLower[key]
            when {
                key in removalLower -> when {
                    remoteEntry == null -> null // war nie auf dem Server → nichts zu entfernen
                    remoteEntry.deleted -> remoteEntry // bereits Tombstone → konvergiert
                    else -> remoteEntry.copy(deleted = true, updatedAt = System.currentTimeMillis())
                }
                else -> remoteEntry // keep-on-server: Remote-Stand unverändert lassen
            }
        }
        return merged.filterNot { it.name.lowercase() in excludedLower } + passThrough
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
