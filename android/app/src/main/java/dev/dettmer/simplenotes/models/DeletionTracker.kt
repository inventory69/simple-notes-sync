package dev.dettmer.simplenotes.models

import dev.dettmer.simplenotes.utils.Logger
import org.json.JSONArray
import org.json.JSONObject

data class DeletionRecord(
    val id: String,
    val deletedAt: Long,
    val deviceId: String
)

data class DeletionTracker(
    val version: Int = 1,
    val deletedNotes: MutableList<DeletionRecord> = mutableListOf()
) {
    fun addDeletion(noteId: String, deviceId: String) {
        if (!deletedNotes.any { it.id == noteId }) {
            deletedNotes.add(DeletionRecord(noteId, System.currentTimeMillis(), deviceId))
        }
    }
    
    fun isDeleted(noteId: String): Boolean {
        return deletedNotes.any { it.id == noteId }
    }
    
    fun getDeletionTimestamp(noteId: String): Long? {
        return deletedNotes.find { it.id == noteId }?.deletedAt
    }
    
    fun removeDeletion(noteId: String) {
        deletedNotes.removeIf { it.id == noteId }
    }
    
    fun toJson(): String {
        val jsonObject = JSONObject()
        jsonObject.put("version", version)
        
        val notesArray = JSONArray()
        for (record in deletedNotes) {
            val recordObj = JSONObject()
            recordObj.put("id", record.id)
            recordObj.put("deletedAt", record.deletedAt)
            recordObj.put("deviceId", record.deviceId)
            notesArray.put(recordObj)
        }
        jsonObject.put("deletedNotes", notesArray)
        
        return jsonObject.toString(2)  // Pretty print with 2-space indent
    }
    
    companion object {
        private const val TAG = "DeletionTracker"
        
        fun fromJson(json: String): DeletionTracker? {
            return try {
                val jsonObject = JSONObject(json)
                val version = jsonObject.optInt("version", 1)
                val deletedNotes = mutableListOf<DeletionRecord>()
                
                val notesArray = jsonObject.optJSONArray("deletedNotes")
                if (notesArray != null) {
                    for (i in 0 until notesArray.length()) {
                        val recordObj = notesArray.getJSONObject(i)
                        val record = DeletionRecord(
                            id = recordObj.getString("id"),
                            deletedAt = recordObj.getLong("deletedAt"),
                            deviceId = recordObj.getString("deviceId")
                        )
                        deletedNotes.add(record)
                    }
                }
                
                DeletionTracker(version, deletedNotes)
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to parse DeletionTracker JSON: ${e.message}")
                null
            }
        }
    }
}
