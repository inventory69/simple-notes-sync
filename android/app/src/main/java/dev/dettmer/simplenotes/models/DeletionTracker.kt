package dev.dettmer.simplenotes.models

import dev.dettmer.simplenotes.utils.Logger
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class DeletionRecord(val id: String, val deletedAt: Long, val deviceId: String)

/**
 * 🔧 Perf: intern Map-basiert (statt Liste) für O(1) Lookups statt O(n) `.any{}`/`.find{}`.
 * JSON-Wire-Format (Array `deletedNotes`) bleibt unverändert — nur die interne
 * Repräsentation ändert sich. `deletedNotes` bleibt als Read-Only-List-Property erhalten,
 * damit bestehende Lese-Call-Sites unverändert funktionieren.
 */
data class DeletionTracker(
    val version: Int = 1,
    private val recordsById: LinkedHashMap<String, DeletionRecord> = LinkedHashMap()
) {
    val deletedNotes: List<DeletionRecord>
        get() = recordsById.values.toList()

    fun addDeletion(noteId: String, deviceId: String) {
        recordsById.putIfAbsent(noteId, DeletionRecord(noteId, System.currentTimeMillis(), deviceId))
    }

    fun isDeleted(noteId: String): Boolean {
        return recordsById.containsKey(noteId)
    }

    fun getDeletionTimestamp(noteId: String): Long? {
        return recordsById[noteId]?.deletedAt
    }

    fun removeDeletion(noteId: String) {
        recordsById.remove(noteId)
    }

    /**
     * Übernimmt [record] nur, wenn dafür noch kein Eintrag existiert oder der bestehende
     * älter ist. Ersetzt das frühere "find → removeIf → add"-Muster beim Merge von
     * Remote-Deletion-Ledgers (DeletionSyncManager, WebDavSyncService).
     */
    fun upsertIfNewer(record: DeletionRecord) {
        val existing = recordsById[record.id]
        if (existing == null || record.deletedAt > existing.deletedAt) {
            recordsById[record.id] = record
        }
    }

    /** Entfernt alle Einträge, die älter als [maxAgeMs] relativ zu [now] sind. */
    fun pruneOlderThan(maxAgeMs: Long, now: Long = System.currentTimeMillis()) {
        recordsById.values.removeIf { now - it.deletedAt > maxAgeMs }
    }

    fun toJson(): String {
        val jsonObject = JSONObject()
        jsonObject.put("version", version)

        val notesArray = JSONArray()
        for (record in recordsById.values) {
            val recordObj = JSONObject()
            recordObj.put("id", record.id)
            recordObj.put("deletedAt", record.deletedAt)
            recordObj.put("deviceId", record.deviceId)
            notesArray.put(recordObj)
        }
        jsonObject.put("deletedNotes", notesArray)

        return jsonObject.toString(2) // Pretty print with 2-space indent
    }

    companion object {
        private const val TAG = "DeletionTracker"

        fun fromJson(json: String): DeletionTracker? {
            return try {
                val jsonObject = JSONObject(json)
                val version = jsonObject.optInt("version", 1)
                val recordsById = LinkedHashMap<String, DeletionRecord>()

                val notesArray = jsonObject.optJSONArray("deletedNotes")
                if (notesArray != null) {
                    for (i in 0 until notesArray.length()) {
                        val recordObj = notesArray.getJSONObject(i)
                        val record = DeletionRecord(
                            id = recordObj.getString("id"),
                            deletedAt = recordObj.getLong("deletedAt"),
                            deviceId = recordObj.getString("deviceId")
                        )
                        recordsById[record.id] = record
                    }
                }

                DeletionTracker(version, recordsById)
            } catch (e: JSONException) {
                Logger.w(TAG, "Failed to parse DeletionTracker JSON: ${e.message}")
                null
            }
        }
    }
}
