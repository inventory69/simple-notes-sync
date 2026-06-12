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

    /** User-Anlage: Eintrag anlegen/re-aktivieren. Setzt dirty-Flag (außer `dirty = false`,
     *  z. B. bei local-only-Anlage, die keinen Sync-Lauf braucht). */
    suspend fun addFolder(name: String, dirty: Boolean = true) {
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
            if (dirty) markDirty()
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

    /** Tombstoned old → new mit Farbe übernehmen. Setzt dirty-Flag. Migriert local-only-Eintrag.
     *  Local-only-Ordner: alter Eintrag wird hard-entfernt (kein Tombstone, kein dirty) —
     *  ein Tombstone würde beim nächsten Sync propagieren und gleichnamige Ordner anderer
     *  Geräte löschen, obwohl dieser Ordner den Server nie betreffen darf. */
    suspend fun rename(old: String, new: String) {
        val trimmedNew = new.trim()
        if (trimmedNew.isEmpty()) return
        val localOnly = isLocalOnly(old)
        mutex.withLock {
            val current = loadMetaUnsafe().toMutableList()
            val oldIdx = current.indexOfFirst { it.name.equals(old, ignoreCase = true) }
            val oldColor = current.getOrNull(oldIdx)?.color
            if (oldIdx >= 0) {
                if (localOnly) {
                    current.removeAt(oldIdx)
                } else {
                    current[oldIdx] = current[oldIdx].copy(deleted = true, updatedAt = now())
                }
            }
            val newIdx = current.indexOfFirst { it.name.equals(trimmedNew, ignoreCase = true) }
            if (newIdx >= 0) {
                current[newIdx] = current[newIdx].copy(name = trimmedNew, color = oldColor, deleted = false, updatedAt = now())
            } else {
                current.add(FolderMeta(name = trimmedNew, color = oldColor, updatedAt = now()))
            }
            writeMetaUnsafe(current.sortedBy { it.name.lowercase() })
            if (!localOnly) markDirty()
        }
        // local-only-Markierung auf den neuen Namen übertragen, alten Namen entfernen.
        migrateLocalOnly(old, trimmedNew)
    }

    private fun migrateLocalOnly(oldName: String, newName: String) {
        val current = getLocalOnlyFolderNames()
        if (oldName in current) {
            setLocalOnlyFolderNames(current - oldName + newName)
        }
        // Removal-Intent NICHT migrieren: er bezieht sich auf den alten Server-Eintrag.
        // Die Notiz-Dateien wurden beim Ausschließen bereits via PendingServerDeletions
        // eingeplant; der neue Name war nie auf dem Server.
        val removal = getServerRemovalQueue()
        if (oldName in removal) {
            setServerRemovalQueue(removal - oldName)
        }
    }

    // ── Local-Only (device-only, not synced to WebDAV) ────────────────────

    /** Gibt die Namen aller als "nur lokal" markierten Ordner zurück. */
    fun getLocalOnlyFolderNames(): Set<String> =
        prefs.getStringSet(KEY_LOCAL_ONLY_FOLDERS, emptySet()) ?: emptySet()

    /** Ersetzt die gespeicherte Menge atomisch. */
    fun setLocalOnlyFolderNames(names: Set<String>) {
        prefs.edit { putStringSet(KEY_LOCAL_ONLY_FOLDERS, names.toSet()) }
    }

    /** Case-insensitive Prüfung — Ordnernamen sind im Store case-insensitiv eindeutig. */
    fun isLocalOnly(name: String?): Boolean =
        name != null && getLocalOnlyFolderNames().any { it.equals(name, ignoreCase = true) }

    /** Setzt oder entfernt die "nur lokal"-Markierung. Beim Entfernen wird auch ein
     *  evtl. offener Removal-Intent verworfen (nur sinnvoll solange local-only). */
    fun setLocalOnly(name: String, localOnly: Boolean) {
        val current = getLocalOnlyFolderNames().toMutableSet()
        if (localOnly) current.add(name) else current.removeAll { it.equals(name, ignoreCase = true) }
        prefs.edit { putStringSet(KEY_LOCAL_ONLY_FOLDERS, current) }
        if (!localOnly) {
            setServerRemovalQueue(getServerRemovalQueue().filterNot { it.equals(name, ignoreCase = true) }.toSet())
        }
    }

    /** Ordner, deren Server-Eintrag beim nächsten folders.json-Sync tombstoned werden soll
     *  („Vom Server entfernen" beim nachträglichen Ausschließen). Selbst-löschend nach Erfolg. */
    fun getServerRemovalQueue(): Set<String> =
        prefs.getStringSet(KEY_LOCAL_ONLY_SERVER_REMOVAL, emptySet()) ?: emptySet()

    fun setServerRemovalQueue(names: Set<String>) {
        prefs.edit { putStringSet(KEY_LOCAL_ONLY_SERVER_REMOVAL, names.toSet()) }
    }

    /** Re-Include nach local-only: updatedAt-Bump, damit der lokale aktive Eintrag einen evtl.
     *  Server-Tombstone (aus „Vom Server entfernen") beim LWW-Merge schlägt. Setzt dirty-Flag. */
    suspend fun touch(name: String) = mutex.withLock {
        val current = loadMetaUnsafe().toMutableList()
        val idx = current.indexOfFirst { it.name.equals(name, ignoreCase = true) && !it.deleted }
        if (idx >= 0) {
            current[idx] = current[idx].copy(updatedAt = now())
            writeMetaUnsafe(current)
            markDirty()
        }
    }

    /** Tombstone statt Hard-Remove. Setzt dirty-Flag. Bereinigt ggf. local-only-Eintrag.
     *  Local-only-Ordner ohne Server-Propagation werden hard-entfernt — ein Tombstone würde
     *  gleichnamige Ordner anderer Geräte löschen. */
    suspend fun deleteFolder(name: String, propagateToServer: Boolean = true) {
        val silentRemove = isLocalOnly(name) && !propagateToServer
        mutex.withLock {
            val current = loadMetaUnsafe().toMutableList()
            val idx = current.indexOfFirst { it.name.equals(name, ignoreCase = true) && !it.deleted }
            if (idx >= 0) {
                if (silentRemove) {
                    current.removeAt(idx)
                    writeMetaUnsafe(current)
                } else {
                    current[idx] = current[idx].copy(deleted = true, updatedAt = now())
                    writeMetaUnsafe(current)
                    markDirty()
                }
            }
        }
        // Verwaisten Eintrag bereinigen: neuer Ordner gleichen Namens erbt sonst die Markierung.
        setLocalOnly(name, false)
    }

    /** Nur für Tests / „Alle Daten löschen". Entfernt auch local-only-Markierungen. */
    suspend fun clear() = mutex.withLock {
        try { if (file.exists()) file.delete() } catch (e: Exception) {
            Logger.w(TAG, "clear failed: ${e.message}")
        }
        prefs.edit {
            remove(KEY_LOCAL_ONLY_FOLDERS)
            remove(KEY_LOCAL_ONLY_SERVER_REMOVAL)
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
        private const val KEY_LOCAL_ONLY_FOLDERS = "local_only_folders"
        private const val KEY_LOCAL_ONLY_SERVER_REMOVAL = "local_only_folders_server_removal"
    }
}
