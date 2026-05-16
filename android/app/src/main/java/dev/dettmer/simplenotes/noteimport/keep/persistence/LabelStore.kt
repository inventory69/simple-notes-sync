package dev.dettmer.simplenotes.noteimport.keep.persistence

import android.content.Context
import dev.dettmer.simplenotes.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * v2.5.0 — Mutex-geschützter Wrapper um den persistierten [LabelIndex].
 *
 * Pattern angelehnt an `NotesStorage.deletionTrackerMutex` (siehe
 * `NotesStorage.kt`). Schreib-Operationen sind atomar (load → modify → save
 * unter einem Lock); Read-Operationen sind ebenfalls Lock-geschützt, damit
 * konkurrente Writes ein konsistentes Ergebnis liefern.
 *
 * **Korruptionsverhalten**: Wird die JSON-Datei bei `load()` als ungültig
 * erkannt, wird sie nach Logger.w-Eintrag durch einen leeren Index ersetzt
 * (Datei wird beim nächsten Write überschrieben). Bewusste Wahl: kein
 * Crash-Pfad — der Label-Index ist kein User-Daten-Kanal sondern ein
 * Beschleunigungs-Cache.
 */
class LabelStore(private val context: Context) {

    private val mutex = Mutex()

    private val file: File
        get() = File(context.filesDir, FILE_NAME)

    /**
     * Lädt den aktuellen Index. Gibt einen leeren Index zurück, wenn die Datei
     * fehlt oder beschädigt ist.
     */
    suspend fun load(): LabelIndex = mutex.withLock { loadUnsafe() }

    /**
     * Fügt einer Notiz mehrere Labels hinzu (Append, deduplizierend pro Label).
     * Atomar.
     */
    suspend fun appendBatch(noteId: String, labelNames: List<String>) {
        if (noteId.isBlank() || labelNames.isEmpty()) return
        mutex.withLock {
            val index = loadUnsafe()
            for (raw in labelNames) {
                val name = raw.trim()
                if (name.isNotEmpty()) index.add(name, noteId)
            }
            writeUnsafe(index)
        }
    }

    /**
     * Setzt den Index zurück (nur für Tests / "Alle Daten löschen"-UI-Aktion).
     */
    suspend fun clear() = mutex.withLock {
        try {
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Logger.w(TAG, "clear failed: ${e.message}")
        }
    }

    // ───── Helpers (Mutex-FREE — nur unter withLock aufrufen!) ─────────

    private suspend fun loadUnsafe(): LabelIndex = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext LabelIndex()
        val raw = try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            Logger.w(TAG, "Failed reading $FILE_NAME: ${e.message}")
            return@withContext LabelIndex()
        }
        if (raw.isBlank()) return@withContext LabelIndex()
        LabelIndex.fromJson(raw) ?: LabelIndex()
    }

    private suspend fun writeUnsafe(index: LabelIndex) = withContext(Dispatchers.IO) {
        try {
            // Atomares Schreiben über tmp-Rename (vermeidet halb-geschriebene JSONs
            // bei Crash/Power-Loss).
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(index.toJson(), Charsets.UTF_8)
            if (file.exists()) file.delete()
            if (!tmp.renameTo(file)) {
                Logger.w(TAG, "rename tmp -> $FILE_NAME failed; falling back to direct write")
                file.writeText(index.toJson(), Charsets.UTF_8)
                tmp.delete()
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Failed writing $FILE_NAME: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "LabelStore"
        const val FILE_NAME = "notes_labels.json"
    }
}
