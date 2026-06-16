package dev.dettmer.simplenotes.sync

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import dev.dettmer.simplenotes.utils.Logger
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persistent queue for server deletions that couldn't be executed because the server was
 * unreachable at deletion time. Processed during the next successful sync (Step 4.5).
 *
 * 🆕 v2.7.0 (Folders): speichert pro Eintrag den folderName zum Löschzeitpunkt, damit der korrekte
 * Server-Pfad (/notes/<folder>/<id>.json) gelöscht wird. Liest das alte Format (reines String-Array
 * von IDs) backward-kompatibel als {id, folderName=null}.
 */
class PendingServerDeletions(context: Context) {
    private val file = File(context.filesDir, FILENAME)
    private val mutex = Mutex()
    private val gson = Gson()

    data class PendingDeletion(
        @SerializedName("id") val id: String,
        @SerializedName("folderName") val folderName: String? = null,
        // 🆕 true = die Notiz wurde nur verschoben; nur den alten Server-Pfad aufräumen,
        // KEINEN Eintrag im geteilten Lösch-Ledger (deletions.json) erzeugen.
        @SerializedName("isMove") val isMove: Boolean = false
    )

    suspend fun add(deletions: List<PendingDeletion>) = mutex.withLock {
        val current = loadInternal()
        // Bei ID-Kollision gewinnt der echte Delete (isMove=false) über einen Move-Eintrag,
        // damit eine endgültige Löschung niemals als bloßer Move behandelt wird.
        val merged = (current + deletions)
            .groupBy { it.id }
            .map { (_, group) -> group.firstOrNull { !it.isMove } ?: group.first() }
        saveInternal(merged)
        Logger.d(TAG, "📋 Pending server deletions: +${deletions.size} → ${merged.size} total")
    }

    suspend fun getAll(): List<PendingDeletion> = mutex.withLock { loadInternal() }

    suspend fun remove(noteIds: List<String>) = mutex.withLock {
        val current = loadInternal()
        val idSet = noteIds.toSet()
        val updated = current.filterNot { it.id in idSet }
        saveInternal(updated)
        Logger.d(TAG, "📋 Pending server deletions: -${noteIds.size} → ${updated.size} remaining")
    }

    suspend fun isEmpty(): Boolean = mutex.withLock { loadInternal().isEmpty() }

    private fun loadInternal(): List<PendingDeletion> {
        if (!file.exists()) return emptyList()
        return try {
            val json = file.readText()
            if (json.isBlank()) return emptyList()
            val root = JsonParser.parseString(json)
            val arr: JsonArray = if (root.isJsonArray) root.asJsonArray else return emptyList()
            // Altes Format: ["id1","id2",...]  →  {id, folderName=null}
            if (arr.size() > 0 && arr[0].isJsonPrimitive) {
                arr.map { PendingDeletion(it.asString, null) }
            } else {
                val type = object : TypeToken<List<PendingDeletion>>() {}.type
                gson.fromJson<List<PendingDeletion>>(json, type) ?: emptyList()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to load pending deletions: ${e.message}")
            emptyList()
        }
    }

    private fun saveInternal(deletions: List<PendingDeletion>) {
        try {
            file.writeText(gson.toJson(deletions))
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save pending deletions: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "PendingServerDeletions"
        const val FILENAME = "pending_server_deletions.json"
    }
}
