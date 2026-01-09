package dev.dettmer.simplenotes.models

import dev.dettmer.simplenotes.utils.Logger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
    
    /**
     * Konvertiert Note zu Markdown mit YAML Frontmatter (Task #1.2.0-08)
     * Format kompatibel mit Obsidian, Joplin, Typora
     */
    fun toMarkdown(): String {
        return """
---
id: $id
created: ${formatISO8601(createdAt)}
updated: ${formatISO8601(updatedAt)}
device: $deviceId
---

# $title

$content
        """.trimIndent()
    }
    
    companion object {
        private const val TAG = "Note"
        
        fun fromJson(json: String): Note? {
            return try {
                val gson = com.google.gson.Gson()
                gson.fromJson(json, Note::class.java)
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to parse JSON: ${e.message}")
                null
            }
        }
        
        /**
         * Parst Markdown zurück zu Note-Objekt (Task #1.2.0-09)
         * 
         * @param md Markdown-String mit YAML Frontmatter
         * @return Note-Objekt oder null bei Parse-Fehler
         */
        fun fromMarkdown(md: String): Note? {
            return try {
                // Parse YAML Frontmatter + Markdown Content
                val frontmatterRegex = Regex("^---\\n(.+?)\\n---\\n(.*)$", RegexOption.DOT_MATCHES_ALL)
                val match = frontmatterRegex.find(md) ?: return null
                
                val yamlBlock = match.groupValues[1]
                val contentBlock = match.groupValues[2]
                
                // Parse YAML (einfach per String-Split für MVP)
                val metadata = yamlBlock.lines()
                    .mapNotNull { line ->
                        val parts = line.split(":", limit = 2)
                        if (parts.size == 2) {
                            parts[0].trim() to parts[1].trim()
                        } else null
                    }.toMap()
                
                // Extract title from first # heading
                val title = contentBlock.lines()
                    .firstOrNull { it.startsWith("# ") }
                    ?.removePrefix("# ")?.trim() ?: "Untitled"
                
                // Extract content (everything after heading)
                val content = contentBlock
                    .substringAfter("# $title\n\n", "")
                    .trim()
                
                Note(
                    id = metadata["id"] ?: UUID.randomUUID().toString(),
                    title = title,
                    content = content,
                    createdAt = parseISO8601(metadata["created"] ?: ""),
                    updatedAt = parseISO8601(metadata["updated"] ?: ""),
                    deviceId = metadata["device"] ?: "desktop",
                    syncStatus = SyncStatus.SYNCED  // Annahme: Vom Server importiert
                )
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to parse Markdown: ${e.message}")
                null
            }
        }
        
        /**
         * Formatiert Timestamp zu ISO8601 (Task #1.2.0-10)
         * Format: 2024-12-21T18:00:00Z (UTC)
         */
        private fun formatISO8601(timestamp: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(Date(timestamp))
        }
        
        /**
         * Parst ISO8601 zurück zu Timestamp (Task #1.2.0-10)
         * Fallback: Aktueller Timestamp bei Fehler
         */
        private fun parseISO8601(dateString: String): Long {
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(dateString)?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to parse ISO8601 date '$dateString': ${e.message}")
                System.currentTimeMillis()  // Fallback
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
