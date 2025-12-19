package dev.dettmer.simplenotes.models

import java.util.UUID

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deviceId: String,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
) {
    fun toJson(): String {
        return """
        {
            "id": "$id",
            "title": "${title.escapeJson()}",
            "content": "${content.escapeJson()}",
            "createdAt": $createdAt,
            "updatedAt": $updatedAt,
            "deviceId": "$deviceId",
            "syncStatus": "${syncStatus.name}"
        }
        """.trimIndent()
    }
    
    companion object {
        fun fromJson(json: String): Note? {
            return try {
                val gson = com.google.gson.Gson()
                gson.fromJson(json, Note::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}

// Extension für JSON-Escaping
fun String.escapeJson(): String {
    return this
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
