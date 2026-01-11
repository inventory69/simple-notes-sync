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
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    // v1.4.0: Checklisten-Felder
    val noteType: NoteType = NoteType.TEXT,
    val checklistItems: List<ChecklistItem>? = null
) {
    /**
     * Serialisiert Note zu JSON
     * v1.4.0: Nutzt Gson für komplexe Strukturen
     * v1.4.1: Für Checklisten wird ein Fallback-Content generiert, damit ältere
     *         App-Versionen (v1.3.x) die Notiz als Text anzeigen können.
     */
    fun toJson(): String {
        val gson = com.google.gson.GsonBuilder()
            .setPrettyPrinting()
            .create()
        
        // v1.4.1: Für Checklisten den Fallback-Content generieren
        val noteToSerialize = if (noteType == NoteType.CHECKLIST && checklistItems != null) {
            this.copy(content = generateChecklistFallbackContent())
        } else {
            this
        }
        
        return gson.toJson(noteToSerialize)
    }
    
    /**
     * v1.4.1: Generiert einen lesbaren Text-Fallback aus Checklist-Items.
     * Format: GitHub-Style Task-Listen (kompatibel mit Markdown)
     * 
     * Beispiel:
     * [ ] Milch kaufen
     * [x] Brot gekauft
     * [ ] Eier
     * 
     * Wird von älteren App-Versionen (v1.3.x) als normaler Text angezeigt.
     */
    private fun generateChecklistFallbackContent(): String {
        return checklistItems?.sortedBy { it.order }?.joinToString("\n") { item ->
            val checkbox = if (item.isChecked) "[x]" else "[ ]"
            "$checkbox ${item.text}"
        } ?: ""
    }
    
    /**
     * Konvertiert Note zu Markdown mit YAML Frontmatter (Task #1.2.0-08)
     * Format kompatibel mit Obsidian, Joplin, Typora
     * v1.4.0: Unterstützt jetzt auch Checklisten-Format
     */
    fun toMarkdown(): String {
        val header = """
---
id: $id
created: ${formatISO8601(createdAt)}
updated: ${formatISO8601(updatedAt)}
device: $deviceId
type: ${noteType.name.lowercase()}
---

# $title

""".trimIndent()
        
        return when (noteType) {
            NoteType.TEXT -> header + content
            NoteType.CHECKLIST -> {
                val checklistMarkdown = checklistItems?.sortedBy { it.order }?.joinToString("\n") { item ->
                    val checkbox = if (item.isChecked) "[x]" else "[ ]"
                    "- $checkbox ${item.text}"
                } ?: ""
                header + checklistMarkdown
            }
        }
    }
    
    companion object {
        private const val TAG = "Note"
        
        /**
         * Parst JSON zu Note-Objekt mit Backward Compatibility für alte Notizen ohne noteType
         */
        fun fromJson(json: String): Note? {
            return try {
                val gson = com.google.gson.Gson()
                val jsonObject = com.google.gson.JsonParser.parseString(json).asJsonObject
                
                // Backward Compatibility: Alte Notizen ohne noteType bekommen TEXT
                val noteType = if (jsonObject.has("noteType") && !jsonObject.get("noteType").isJsonNull) {
                    try {
                        NoteType.valueOf(jsonObject.get("noteType").asString)
                    } catch (e: Exception) {
                        Logger.w(TAG, "Unknown noteType, defaulting to TEXT: ${e.message}")
                        NoteType.TEXT
                    }
                } else {
                    NoteType.TEXT
                }
                
                // Parsen der Basis-Note
                val rawNote = gson.fromJson(json, NoteRaw::class.java)
                
                // Checklist-Items parsen (kann null sein)
                val checklistItemsType = object : com.google.gson.reflect.TypeToken<List<ChecklistItem>>() {}.type
                var checklistItems: List<ChecklistItem>? = if (jsonObject.has("checklistItems") &&
                    !jsonObject.get("checklistItems").isJsonNull
                ) {
                    gson.fromJson<List<ChecklistItem>>(
                        jsonObject.get("checklistItems"),
                        checklistItemsType
                    )
                } else {
                    null
                }
                
                // v1.4.1: Recovery-Mode - Falls Checkliste aber keine Items, 
                // versuche Content als Fallback zu parsen
                if (noteType == NoteType.CHECKLIST && 
                    (checklistItems == null || checklistItems.isEmpty()) &&
                    rawNote.content.isNotBlank()) {
                    
                    val recoveredItems = parseChecklistFromContent(rawNote.content)
                    if (recoveredItems.isNotEmpty()) {
                        Logger.d(TAG, "🔄 Recovered ${recoveredItems.size} checklist items from content fallback")
                        checklistItems = recoveredItems
                    }
                }
                
                // Note mit korrekten Werten erstellen
                Note(
                    id = rawNote.id,
                    title = rawNote.title,
                    content = rawNote.content,
                    createdAt = rawNote.createdAt,
                    updatedAt = rawNote.updatedAt,
                    deviceId = rawNote.deviceId,
                    syncStatus = rawNote.syncStatus ?: SyncStatus.LOCAL_ONLY,
                    noteType = noteType,
                    checklistItems = checklistItems
                )
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to parse JSON: ${e.message}")
                null
            }
        }
        
        /**
         * Hilfsklasse für Gson-Parsing mit nullable Feldern
         */
        private data class NoteRaw(
            val id: String = UUID.randomUUID().toString(),
            val title: String = "",
            val content: String = "",
            val createdAt: Long = System.currentTimeMillis(),
            val updatedAt: Long = System.currentTimeMillis(),
            val deviceId: String = "",
            val syncStatus: SyncStatus? = null
        )
        
        /**
         * v1.4.1: Parst GitHub-Style Checklisten aus Text (Recovery-Mode).
         * 
         * Unterstützte Formate:
         * - [ ] Unchecked item
         * - [x] Checked item
         * - [X] Checked item (case insensitive)
         * 
         * Wird verwendet, wenn eine v1.4.0 Checkliste von einer älteren
         * App-Version (v1.3.x) bearbeitet wurde und die checklistItems verloren gingen.
         * 
         * @param content Der Text-Content der Notiz
         * @return Liste von ChecklistItems oder leere Liste
         */
        private fun parseChecklistFromContent(content: String): List<ChecklistItem> {
            val pattern = Regex("""^\s*\[([ xX])\]\s*(.+)$""", RegexOption.MULTILINE)
            return pattern.findAll(content).mapIndexed { index, match ->
                val checked = match.groupValues[1].lowercase() == "x"
                val text = match.groupValues[2].trim()
                ChecklistItem(
                    id = UUID.randomUUID().toString(),
                    text = text,
                    isChecked = checked,
                    order = index
                )
            }.toList()
        }
        
        /**
         * Parst Markdown zurück zu Note-Objekt (Task #1.2.0-09)
         * v1.4.0: Unterstützt jetzt auch Checklisten-Format
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
                
                // v1.4.0: Prüfe ob type: checklist im Frontmatter
                val noteTypeStr = metadata["type"]?.lowercase() ?: "text"
                val noteType = when (noteTypeStr) {
                    "checklist" -> NoteType.CHECKLIST
                    else -> NoteType.TEXT
                }
                
                // v1.4.0: Parse Content basierend auf Typ
                // FIX: Robusteres Parsing - suche nach dem Titel-Header und extrahiere den Rest
                val titleLineIndex = contentBlock.lines().indexOfFirst { it.startsWith("# ") }
                val contentAfterTitle = if (titleLineIndex >= 0) {
                    // Alles nach der Titel-Zeile, überspringe führende Leerzeilen
                    contentBlock.lines()
                        .drop(titleLineIndex + 1)
                        .dropWhile { it.isBlank() }
                        .joinToString("\n")
                        .trim()
                } else {
                    // Fallback: Gesamter Content (kein Titel gefunden)
                    contentBlock.trim()
                }
                
                val content: String
                val checklistItems: List<ChecklistItem>?
                
                if (noteType == NoteType.CHECKLIST) {
                    // Parse Checklist Items
                    val checklistRegex = Regex("^- \\[([ xX])\\] (.*)$", RegexOption.MULTILINE)
                    checklistItems = checklistRegex.findAll(contentAfterTitle).mapIndexed { index, matchResult ->
                        ChecklistItem(
                            id = UUID.randomUUID().toString(),
                            text = matchResult.groupValues[2].trim(),
                            isChecked = matchResult.groupValues[1].lowercase() == "x",
                            order = index
                        )
                    }.toList().ifEmpty { null }
                    content = "" // Checklisten haben keinen "content"
                } else {
                    content = contentAfterTitle
                    checklistItems = null
                }
                
                Note(
                    id = metadata["id"] ?: UUID.randomUUID().toString(),
                    title = title,
                    content = content,
                    createdAt = parseISO8601(metadata["created"] ?: ""),
                    updatedAt = parseISO8601(metadata["updated"] ?: ""),
                    deviceId = metadata["device"] ?: "desktop",
                    syncStatus = SyncStatus.SYNCED,  // Annahme: Vom Server importiert
                    noteType = noteType,
                    checklistItems = checklistItems
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
