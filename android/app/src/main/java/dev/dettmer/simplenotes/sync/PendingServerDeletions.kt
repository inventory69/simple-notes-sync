package dev.dettmer.simplenotes.sync

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import dev.dettmer.simplenotes.utils.Constants
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
 *
 * 🔧 v2.22.0: jeder Eintrag ist an sein Sync-Ziel gebunden (`target` = Notes-URL zum
 * Einreihzeitpunkt). Nach einem Server-/Ordnerwechsel liefen die Löschungen sonst gegen das neue
 * Ziel und landeten in dessen `deletions.json`. Abgearbeitet wird nur [getForCurrentTarget];
 * fremde Einträge warten, bis das Ziel wieder aktiv ist, und verfallen nach
 * [Constants.TRASH_RETENTION_MS].
 */
class PendingServerDeletions(
    private val context: Context,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    private val file = File(context.filesDir, FILENAME)
    private val mutex = Mutex()
    private val gson = Gson()

    data class PendingDeletion(
        @SerializedName("id") val id: String,
        @SerializedName("folderName") val folderName: String? = null,
        // 🆕 true = die Notiz wurde nur verschoben; nur den alten Server-Pfad aufräumen,
        // KEINEN Eintrag im geteilten Lösch-Ledger (deletions.json) erzeugen.
        @SerializedName("isMove") val isMove: Boolean = false,
        // 🔧 v2.22.0: setzt [add] selbst, die Aufrufer lassen beides leer
        @SerializedName("target") val target: String? = null,
        @SerializedName("queuedAt") val queuedAt: Long? = null
    )

    suspend fun add(deletions: List<PendingDeletion>) = mutex.withLock {
        val current = loadInternal()
        val target = currentTarget()
        val now = nowMs()
        val tagged = deletions.map { it.copy(target = target, queuedAt = now) }
        // Bei ID-Kollision gewinnt der echte Delete (isMove=false) über einen Move-Eintrag,
        // damit eine endgültige Löschung niemals als bloßer Move behandelt wird.
        val merged = (current + tagged)
            .groupBy { it.id to it.target }
            .map { (_, group) -> group.firstOrNull { !it.isMove } ?: group.first() }
        saveInternal(merged)
        Logger.d(TAG, "📋 Pending server deletions: +${deletions.size} → ${merged.size} total")
    }

    suspend fun getAll(): List<PendingDeletion> = mutex.withLock { loadInternal() }

    /** Nur die Einträge des aktuellen Sync-Ziels, alles andere wartet. */
    suspend fun getForCurrentTarget(): List<PendingDeletion> = mutex.withLock {
        val target = currentTarget()
        loadInternal().filter { it.target == target }
    }

    /** @param onlyCurrentTarget true = Einträge anderer Ziele mit derselben ID bleiben stehen. */
    suspend fun remove(noteIds: List<String>, onlyCurrentTarget: Boolean = false) = mutex.withLock {
        val current = loadInternal()
        val idSet = noteIds.toSet()
        val target = currentTarget()
        val updated = current.filterNot { it.id in idSet && (!onlyCurrentTarget || it.target == target) }
        saveInternal(updated)
        Logger.d(TAG, "📋 Pending server deletions: -${noteIds.size} → ${updated.size} remaining")
    }

    suspend fun isEmpty(): Boolean = mutex.withLock { loadInternal().isEmpty() }

    /** Notes-URL des aktiven Ziels, frisch aus den Prefs. `null` = kein Server eingerichtet. */
    private fun currentTarget(): String? {
        val urls = SyncUrlBuilder(context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE))
        return urls.getServerUrl()?.let { urls.getNotesUrl(it) }
    }

    /**
     * Altdaten ohne `target` gelten als aktuelles Ziel (beste Schätzung, entspricht dem Verhalten
     * vor v2.22.0) und werden sofort festgeschrieben, damit ein späterer Wechsel sie nicht mitnimmt.
     * Fremde Einträge, die älter als [Constants.TRASH_RETENTION_MS] sind, fliegen raus.
     */
    private fun loadInternal(): List<PendingDeletion> {
        val raw = loadRaw()
        if (raw.isEmpty()) return raw
        val target = currentTarget()
        val now = nowMs()
        val migrated = raw.map { if (it.target == null) it.copy(target = target, queuedAt = it.queuedAt ?: now) else it }
        val kept = migrated.filter { it.target == target || now - (it.queuedAt ?: now) < Constants.TRASH_RETENTION_MS }
        if (kept != raw) {
            if (kept.size < migrated.size) {
                Logger.d(TAG, "📋 Dropped ${migrated.size - kept.size} expired pending deletion(s) of other targets")
            }
            saveInternal(kept)
        }
        return kept
    }

    private fun loadRaw(): List<PendingDeletion> {
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
