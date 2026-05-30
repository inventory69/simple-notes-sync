package dev.dettmer.simplenotes.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import dev.dettmer.simplenotes.models.Folder
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 🆕 v2.7.0 (Folders): Persistierte Ordner-Metadaten (lokal + serverseitig in folders.json). */
data class FolderMeta(
    @SerializedName("name") val name: String,
    @SerializedName("color") val color: String? = null,
    @SerializedName("updatedAt") val updatedAt: Long = 0L,
    @SerializedName("deleted") val deleted: Boolean = false
)

/**
 * Gson umgeht Kotlin-Null-Safety und kann das non-null `name`-Feld zur Laufzeit mit `null`
 * befüllen (fehlender/`null`-Key im JSON). Solche korrupten Einträge würden später bei
 * `name.lowercase()` einen NPE auslösen → hier defensiv verwerfen.
 */
internal fun List<FolderMeta>.sanitized(): List<FolderMeta> = filter { !it.name.isNullOrBlank() }

/**
 * 🆕 v2.7.0 (Folders): Persistiert Ordner-Metadaten (Name, Farbe, Tombstones) in `filesDir/folders.json`.
 *
 * Altes Format `["A","B"]` wird beim Lesen auf `FolderMeta(name, updatedAt=0, deleted=false)`
 * abgebildet (Backward-Compat). Schreib-Operationen sind Mutex-geschützt und atomar (tmp-Rename).
 * Dirty-Flag in SharedPreferences signalisiert ausstehende Uploads an den `FolderSyncManager`.
 */
class FolderStore(private val context: Context) {
    private val mutex = Mutex()
    private val gson = Gson()
    private val file: File get() = File(context.filesDir, FILE_NAME)
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** Alle Einträge inkl. Tombstones (für Sync-Merge). */
    suspend fun loadMeta(): List<FolderMeta> = mutex.withLock { loadMetaUnsafe() }

    /** Komplette Liste schreiben (Sync-Merge-Ergebnis). Kein dirty-Flag. */
    suspend fun replaceMeta(list: List<FolderMeta>) = mutex.withLock {
        writeMetaUnsafe(list)
    }

    /** Sichtbare Ordnernamen (nicht deleted), alphabetisch case-insensitiv. */
    suspend fun load(): List<String> = mutex.withLock {
        loadMetaUnsafe().filter { !it.deleted }.map { it.name }.sortedBy { it.lowercase() }
    }

    /** Sichtbare Ordner als Folder(name, color), sortiert. */
    suspend fun loadFolders(): List<Folder> = mutex.withLock {
        loadMetaUnsafe()
            .filter { !it.deleted }
            .map { Folder(it.name, it.color) }
            .sortedBy { it.name.lowercase() }
    }

    /** User-Anlage: Eintrag anlegen/re-aktivieren. Setzt dirty-Flag. */
    suspend fun addFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        mutex.withLock {
            val current = loadMetaUnsafe().toMutableList()
            val idx = current.indexOfFirst { it.name.equals(trimmed, ignoreCase = true) }
            if (idx >= 0) {
                val existing = current[idx]
                if (!existing.deleted && existing.name == trimmed) return@withLock
                current[idx] = existing.copy(name = trimmed, deleted = false, updatedAt = now())
            } else {
                current.add(FolderMeta(name = trimmed, updatedAt = now()))
            }
            writeMetaUnsafe(current.sortedBy { it.name.lowercase() })
            markDirty()
        }
    }

    /** Discovery-Mirror: Registriert nur unbekannte Namen mit updatedAt=0. Kein dirty-Flag. */
    suspend fun addFolders(names: Collection<String>) {
        if (names.isEmpty()) return
        mutex.withLock {
            val current = loadMetaUnsafe().toMutableList()
            var changed = false
            for (raw in names) {
                val t = raw.trim()
                if (t.isNotEmpty() && current.none { it.name.equals(t, ignoreCase = true) }) {
                    current.add(FolderMeta(name = t, updatedAt = 0L))
                    changed = true
                }
            }
            if (changed) writeMetaUnsafe(current.sortedBy { it.name.lowercase() })
        }
    }

    /** Farbe setzen. Setzt dirty-Flag. */
    suspend fun setColor(name: String, color: String?) = mutex.withLock {
        val current = loadMetaUnsafe().toMutableList()
        val idx = current.indexOfFirst { it.name.equals(name, ignoreCase = true) && !it.deleted }
        if (idx >= 0) {
            current[idx] = current[idx].copy(color = color, updatedAt = now())
            writeMetaUnsafe(current)
            markDirty()
        }
    }

    /** Tombstoned old → new mit Farbe übernehmen. Setzt dirty-Flag. */
    suspend fun rename(old: String, new: String) {
        val trimmedNew = new.trim()
        if (trimmedNew.isEmpty()) return
        mutex.withLock {
            val current = loadMetaUnsafe().toMutableList()
            val oldIdx = current.indexOfFirst { it.name.equals(old, ignoreCase = true) }
            val oldColor = current.getOrNull(oldIdx)?.color
            if (oldIdx >= 0) {
                current[oldIdx] = current[oldIdx].copy(deleted = true, updatedAt = now())
            }
            val newIdx = current.indexOfFirst { it.name.equals(trimmedNew, ignoreCase = true) }
            if (newIdx >= 0) {
                current[newIdx] = current[newIdx].copy(name = trimmedNew, color = oldColor, deleted = false, updatedAt = now())
            } else {
                current.add(FolderMeta(name = trimmedNew, color = oldColor, updatedAt = now()))
            }
            writeMetaUnsafe(current.sortedBy { it.name.lowercase() })
            markDirty()
        }
    }

    /** Tombstone statt Hard-Remove. Setzt dirty-Flag. */
    suspend fun deleteFolder(name: String) = mutex.withLock {
        val current = loadMetaUnsafe().toMutableList()
        val idx = current.indexOfFirst { it.name.equals(name, ignoreCase = true) && !it.deleted }
        if (idx >= 0) {
            current[idx] = current[idx].copy(deleted = true, updatedAt = now())
            writeMetaUnsafe(current)
            markDirty()
        }
    }

    /** Nur für Tests / „Alle Daten löschen". */
    suspend fun clear() = mutex.withLock {
        try { if (file.exists()) file.delete() } catch (e: Exception) {
            Logger.w(TAG, "clear failed: ${e.message}")
        }
    }

    private fun markDirty() {
        prefs.edit { putBoolean(Constants.KEY_FOLDERS_DIRTY, true) }
    }

    private fun now() = System.currentTimeMillis()

    // ── Helpers (Mutex-FREE — nur unter withLock aufrufen!) ───────────────

    private suspend fun loadMetaUnsafe(): List<FolderMeta> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        val raw = try { file.readText() } catch (e: Exception) {
            Logger.w(TAG, "read failed: ${e.message}"); return@withContext emptyList()
        }
        if (raw.isBlank()) return@withContext emptyList()
        try {
            val arr = JsonParser.parseString(raw).asJsonArray
            if (arr.size() == 0) return@withContext emptyList()
            return@withContext if (arr[0].isJsonObject) {
                // Neues Format: List<FolderMeta>
                val type = object : TypeToken<List<FolderMeta>>() {}.type
                (gson.fromJson<List<FolderMeta>>(raw, type) ?: emptyList()).sanitized()
            } else {
                // Altes Format: List<String> → Backward-Compat-Migration
                val type = object : TypeToken<List<String>>() {}.type
                val names = gson.fromJson<List<String>>(raw, type) ?: emptyList()
                names.map { FolderMeta(name = it, updatedAt = 0L) }.sanitized()
            }
        } catch (e: Exception) {
            Logger.w(TAG, "parse failed: ${e.message}")
            emptyList()
        }
    }

    private suspend fun writeMetaUnsafe(list: List<FolderMeta>) = withContext(Dispatchers.IO) {
        try {
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(gson.toJson(list))
            if (file.exists()) file.delete()
            if (!tmp.renameTo(file)) {
                file.writeText(gson.toJson(list))
                tmp.delete()
            }
        } catch (e: Exception) {
            Logger.w(TAG, "write failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "FolderStore"
        const val FILE_NAME = "folders.json"
    }
}
