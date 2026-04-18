package dev.dettmer.simplenotes.noteimport

import android.content.Context
import android.net.Uri
import com.thegrizzlylabs.sardineandroid.Sardine
import dev.dettmer.simplenotes.models.ChecklistItem
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.DeviceIdGenerator
import dev.dettmer.simplenotes.utils.Logger
import java.util.UUID

/**
 * Universeller Import-Wizard für Notizen aus externen Quellen.
 *
 * Unterstützt:
 * - Markdown-Dateien (.md) — mit und ohne YAML-Frontmatter
 * - JSON-Dateien (.json) — Simple-Notes-Format und generische Formate
 * - Plain-Text-Dateien (.txt)
 * - Import von WebDAV-Server oder lokalem Dateisystem
 *
 * Design: Einmaliger, unidirektionaler Import. Importierte Notizen werden als
 * reguläre Simple-Notes-Notizen gespeichert (JSON in /notes/) und ab dann
 * normal synchronisiert. Die Originaldateien bleiben unverändert.
 *
 * v1.9.0: Issue #21
 */
class NotesImportWizard(private val storage: NotesStorage, private val context: Context) {
    companion object {
        private const val TAG = "NotesImportWizard"

        /** Maximale Dateigröße für Import (5 MB) */
        const val MAX_FILE_SIZE = 5 * 1024 * 1024L

        /** Unterstützte Dateiendungen */
        val SUPPORTED_EXTENSIONS = setOf(".md", ".json", ".txt")

        /** Schwellenwert für Unix-Timestamp-Heuristik (< 1e12 → Sekunden, sonst Millisekunden) */
        private const val UNIX_SECONDS_THRESHOLD = 1_000_000_000_000L

        /** Millisekunden pro Sekunde (für Unix-Timestamp-Konvertierung) */
        private const val MILLIS_PER_SECOND = 1000L
    }

    // ══════════════════════════════════════════════════════════════
    // Data Classes
    // ══════════════════════════════════════════════════════════════

    data class ImportCandidate(val name: String, val source: ImportSource, val size: Long, val modified: Long, val fileType: FileType)

    enum class FileType {
        MARKDOWN,
        JSON,
        PLAINTEXT
    }

    sealed class ImportSource {
        data class WebDav(val url: String, val sardine: Sardine) : ImportSource()

        data class LocalFile(val uri: Uri) : ImportSource()
    }

    sealed class ImportResult {
        data class Success(val note: Note, val sourceName: String) : ImportResult()

        data class Skipped(val sourceName: String, val reason: String) : ImportResult()

        data class Failed(val sourceName: String, val error: String) : ImportResult()
    }

    data class ImportSummary(val totalScanned: Int, val imported: Int, val skipped: Int, val failed: Int, val results: List<ImportResult>)

    // ══════════════════════════════════════════════════════════════
    // Scanning — WebDAV-Ordner nach importierbaren Dateien durchsuchen
    // ══════════════════════════════════════════════════════════════

    /**
     * Scannt einen WebDAV-Ordner nach importierbaren Dateien.
     * Scannt NUR den angegebenen Ordner (depth=1), NICHT /notes/ oder /notes-md/.
     *
     * @param sardine Authentifizierter Sardine-Client
     * @param folderUrl WebDAV-URL des zu scannenden Ordners
     * @return Liste der importierbaren Dateien
     */
    fun scanWebDavFolder(sardine: Sardine, folderUrl: String): List<ImportCandidate> {
        val baseUrl = folderUrl.trimEnd('/')
        val results = mutableListOf<ImportCandidate>()

        try {
            val resources = sardine.list(baseUrl, 1)

            // Dateien im aktuellen Ordner sammeln
            resources
                .filter { res ->
                    !res.isDirectory &&
                        res.contentLength <= MAX_FILE_SIZE &&
                        SUPPORTED_EXTENSIONS.any { ext -> res.name.endsWith(ext, ignoreCase = true) }
                }
                .mapTo(results) { resource ->
                    ImportCandidate(
                        name = resource.name,
                        source = ImportSource.WebDav(
                            url = "$baseUrl/${resource.name}",
                            sardine = sardine
                        ),
                        size = resource.contentLength,
                        modified = resource.modified?.time ?: System.currentTimeMillis(),
                        fileType = detectFileType(resource.name)
                    )
                }

            // Unterordner rekursiv scannen (eine Ebene tief, außer /notes/ — bereits synchronisiert)
            // baseName ausschließen: WebDAV list() gibt den Ordner selbst als erstes Element zurück
            val baseName = baseUrl.substringAfterLast('/')
            resources
                .filter { res ->
                    res.isDirectory &&
                        res.name != baseName &&
                        !isSyncFolder(res.name)
                }
                .forEach { subDir ->
                    val subUrl = "$baseUrl/${subDir.name}"
                    try {
                        val subResources = sardine.list(subUrl, 1)
                        subResources
                            .filter { res ->
                                !res.isDirectory &&
                                    res.contentLength <= MAX_FILE_SIZE &&
                                    SUPPORTED_EXTENSIONS.any { ext -> res.name.endsWith(ext, ignoreCase = true) }
                            }
                            .mapTo(results) { resource ->
                                ImportCandidate(
                                    name = "${subDir.name}/${resource.name}",
                                    source = ImportSource.WebDav(
                                        url = "$subUrl/${resource.name}",
                                        sardine = sardine
                                    ),
                                    size = resource.contentLength,
                                    modified = resource.modified?.time ?: System.currentTimeMillis(),
                                    fileType = detectFileType(resource.name)
                                )
                            }
                        Logger.d(TAG, "📂 Scanned $subUrl: ${subResources.size} resources")
                    } catch (e: Exception) {
                        Logger.e(TAG, "Scan failed for subfolder $subUrl: ${e.message}")
                    }
                }

            Logger.d(TAG, "📂 Total scan of $baseUrl: ${results.size} importable files found")
        } catch (e: Exception) {
            Logger.e(TAG, "Scan failed for $baseUrl: ${e.message}")
        }

        return results
    }

    // ══════════════════════════════════════════════════════════════
    // Import — Einzelne Datei importieren
    // ══════════════════════════════════════════════════════════════

    /**
     * Importiert eine einzelne Datei als Simple-Notes-Notiz.
     * Erkennt automatisch das Format und wählt den passenden Parser.
     */
    suspend fun importFile(candidate: ImportCandidate): ImportResult {
        return try {
            val content = readContent(candidate.source)

            if (content.isBlank()) {
                return ImportResult.Skipped(candidate.name, "File is empty")
            }

            val note = when (candidate.fileType) {
                FileType.MARKDOWN -> parseMarkdown(content, candidate)
                FileType.JSON -> parseJson(content, candidate)
                FileType.PLAINTEXT -> parsePlainText(content, candidate)
            }

            if (note == null) {
                return ImportResult.Failed(candidate.name, "Could not parse file")
            }

            // Duplikat-Check: Existiert bereits eine Notiz mit dieser ID?
            val existingNote = storage.loadNote(note.id)
            if (existingNote != null) {
                return ImportResult.Skipped(
                    candidate.name,
                    "Note with ID ${note.id} already exists (title: '${existingNote.title}')"
                )
            }

            // SyncStatus-Logik:
            // - Lokale Datei → immer PENDING (muss noch hochgeladen werden)
            // - WebDAV JSON → SYNCED (Simple-Notes-JSON kommt aus /notes/, ID ist bereits registriert)
            // - WebDAV Markdown MIT Frontmatter → SYNCED (UUID stammt aus /notes/, bereits synced)
            // - WebDAV Markdown OHNE Frontmatter → PENDING (neue Notiz, kein /notes/<uuid>.json vorhanden)
            val syncStatus = when {
                candidate.source is ImportSource.LocalFile -> SyncStatus.PENDING
                candidate.fileType == FileType.MARKDOWN -> {
                    val normalizedContent = content.replace("\\n", "\n")
                    if (Note.fromMarkdown(normalizedContent) != null) SyncStatus.SYNCED else SyncStatus.PENDING
                }
                else -> SyncStatus.SYNCED
            }
            val noteToSave = note.copy(syncStatus = syncStatus)
            storage.saveNote(noteToSave)

            // Falls die Notiz vorher gelöscht wurde, aus dem DeletionTracker entfernen
            // (sonst überspringt der Sync sie als "lokal gelöscht")
            val tracker = storage.loadDeletionTracker()
            if (tracker.isDeleted(noteToSave.id)) {
                tracker.removeDeletion(noteToSave.id)
                storage.saveDeletionTracker(tracker)
                Logger.d(TAG, "🗑️ Removed ${noteToSave.id} from deletion tracker (re-import)")
            }

            Logger.d(TAG, "✅ Imported: ${candidate.name} → ${noteToSave.id} ('${noteToSave.title}')")
            ImportResult.Success(noteToSave, candidate.name)
        } catch (e: Exception) {
            Logger.e(TAG, "Import failed for ${candidate.name}: ${e.message}")
            ImportResult.Failed(candidate.name, e.message ?: "Unknown error")
        }
    }

    /**
     * Batch-Import: Importiert mehrere Dateien und gibt eine Zusammenfassung zurück.
     */
    suspend fun importFiles(
        candidates: List<ImportCandidate>,
        onProgress: (current: Int, total: Int, fileName: String) -> Unit = { _, _, _ -> }
    ): ImportSummary {
        val results = mutableListOf<ImportResult>()

        for ((index, candidate) in candidates.withIndex()) {
            onProgress(index + 1, candidates.size, candidate.name)
            results.add(importFile(candidate))
        }

        return ImportSummary(
            totalScanned = candidates.size,
            imported = results.count { it is ImportResult.Success },
            skipped = results.count { it is ImportResult.Skipped },
            failed = results.count { it is ImportResult.Failed },
            results = results
        ).also {
            Logger.d(
                TAG,
                "📊 Import complete: ${it.imported} imported, " +
                    "${it.skipped} skipped, ${it.failed} failed"
            )
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Parser — Format-spezifisches Parsing
    // ══════════════════════════════════════════════════════════════

    /**
     * Markdown-Parser: Unterstützt Plain-MD und MD mit Simple-Notes-Frontmatter.
     */
    internal fun parseMarkdown(content: String, candidate: ImportCandidate): Note? {
        // Literal \n normalisieren (z.B. von echo ohne -e erstellt)
        val normalizedContent = content.replace("\\n", "\n")

        // Versuch 1: Simple-Notes-Frontmatter vorhanden → Standard-Parser
        Note.fromMarkdown(normalizedContent)?.let { note ->
            Logger.d(TAG, "   📝 ${candidate.name}: Parsed with frontmatter (id=${note.id})")
            return note
        }

        // Versuch 2: Plain Markdown ohne Frontmatter
        // 🆕 v1.10.0-P2: Heuristic — if ALL non-empty lines are GFM task items, import as CHECKLIST
        val title = extractMarkdownTitle(normalizedContent, candidate.name)
        val body = extractMarkdownBody(normalizedContent)

        val heuristicTaskRegex = Regex("""^[-*]\s+\[([ xX])\]\s+(.*)$""")
        val nonEmptyLines = body.lines().filter { it.isNotBlank() }
        if (nonEmptyLines.isNotEmpty() && nonEmptyLines.all { heuristicTaskRegex.matches(it.trim()) }) {
            val items = nonEmptyLines.mapIndexed { index, line ->
                val m = requireNotNull(heuristicTaskRegex.find(line.trim()))
                ChecklistItem(
                    id = UUID.randomUUID().toString(),
                    text = m.groupValues[2].trim(),
                    isChecked = m.groupValues[1].lowercase() != " ",
                    order = index
                )
            }
            Logger.d(TAG, "   ✅ ${candidate.name}: Heuristic detection — imported as checklist (${items.size} items)")
            return Note(
                id = UUID.randomUUID().toString(),
                title = title,
                content = "",
                createdAt = candidate.modified,
                updatedAt = candidate.modified,
                deviceId = DeviceIdGenerator.getDeviceId(context),
                syncStatus = SyncStatus.PENDING,
                noteType = NoteType.CHECKLIST,
                checklistItems = items
            )
        }

        Logger.d(TAG, "   📄 ${candidate.name}: Parsed as plain markdown (text note)")

        return Note(
            id = UUID.randomUUID().toString(),
            title = title,
            content = body,
            createdAt = candidate.modified,
            updatedAt = candidate.modified,
            deviceId = DeviceIdGenerator.getDeviceId(context),
            syncStatus = SyncStatus.PENDING,
            noteType = NoteType.TEXT
        )
    }

    /**
     * JSON-Parser: Unterstützt Simple-Notes-JSON und generische Formate.
     */
    internal fun parseJson(content: String, candidate: ImportCandidate): Note? {
        val jsonElement = try {
            com.google.gson.JsonParser.parseString(content)
        } catch (e: Exception) {
            Logger.w(TAG, "   ⚠️ ${candidate.name}: Invalid JSON: ${e.message}")
            return null
        }
        return when {
            jsonElement.isJsonObject -> parseJsonObject(jsonElement.asJsonObject, candidate)
            jsonElement.isJsonArray -> parseJsonArray(jsonElement.asJsonArray, candidate)
            else -> null
        }
    }

    // 🆕 v1.9.0: Check if folder name matches configured sync folder (or its markdown sibling)
    private fun isSyncFolder(folderName: String): Boolean {
        val syncFolder = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            .getString(Constants.KEY_SYNC_FOLDER_NAME, Constants.DEFAULT_SYNC_FOLDER_NAME)
            ?: Constants.DEFAULT_SYNC_FOLDER_NAME
        return folderName == syncFolder || folderName == "$syncFolder-md"
    }

    private fun parseJsonObject(obj: com.google.gson.JsonObject, candidate: ImportCandidate): Note? {
        if (obj.has("id") && obj.has("title") && obj.has("createdAt")) {
            try {
                val note = Note.fromJson(com.google.gson.Gson().toJson(obj))
                if (note != null) {
                    Logger.d(TAG, "   📋 ${candidate.name}: Simple Notes JSON (id=${note.id})")
                    return note
                }
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to parse as Simple Notes JSON for ${candidate.name}: ${e.message}")
                // Fallthrough to generic parsing
            }
        }
        return parseGenericJsonObject(obj, candidate)
    }

    private fun parseJsonArray(array: com.google.gson.JsonArray, candidate: ImportCandidate): Note? {
        if (array.size() == 0) return null
        Logger.d(TAG, "   📋 ${candidate.name}: JSON array with ${array.size()} entries")
        val firstElement = array[0]
        return if (firstElement.isJsonObject) parseGenericJsonObject(firstElement.asJsonObject, candidate) else null
    }

    /**
     * Versucht ein generisches JSON-Objekt als Notiz zu interpretieren.
     */
    internal fun parseGenericJsonObject(obj: com.google.gson.JsonObject, candidate: ImportCandidate): Note? {
        val title = listOf("title", "name", "subject", "header", "Title", "Name")
            .firstNotNullOfOrNull { key -> obj.get(key)?.asString?.takeIf { it.isNotBlank() } }
            ?: candidate.name.removeSuffix(".json")

        val content = listOf(
            "content", "body", "text", "note", "description",
            "Content", "Body", "Text", "markdown"
        )
            .firstNotNullOfOrNull { key -> obj.get(key)?.asString?.takeIf { it.isNotBlank() } }
            .orEmpty()

        if (title.isBlank() && content.isBlank()) {
            Logger.w(TAG, "   ⚠️ ${candidate.name}: No title or content found in JSON")
            return null
        }

        val createdAt = extractTimestamp(
            obj,
            "createdAt",
            "created",
            "created_at",
            "dateCreated",
            "createTime"
        )
            ?: candidate.modified
        val updatedAt = extractTimestamp(
            obj,
            "updatedAt",
            "updated",
            "updated_at",
            "dateModified",
            "modifyTime",
            "modified"
        )
            ?: candidate.modified

        val id = obj.get("id")?.asString?.takeIf { it.isNotBlank() }
            ?: obj.get("uuid")?.asString?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()

        Logger.d(TAG, "   📋 ${candidate.name}: Generic JSON → title='$title', content=${content.length} chars")

        return Note(
            id = id,
            title = title,
            content = content,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deviceId = DeviceIdGenerator.getDeviceId(context),
            syncStatus = SyncStatus.PENDING,
            noteType = NoteType.TEXT
        )
    }

    /**
     * Plain-Text-Parser.
     */
    internal fun parsePlainText(content: String, candidate: ImportCandidate): Note {
        val title = candidate.name.removeSuffix(".txt").replace("-", " ").replace("_", " ")

        Logger.d(TAG, "   📄 ${candidate.name}: Plain text (${content.length} chars)")

        return Note(
            id = UUID.randomUUID().toString(),
            title = title,
            content = content.trim(),
            createdAt = candidate.modified,
            updatedAt = candidate.modified,
            deviceId = DeviceIdGenerator.getDeviceId(context),
            syncStatus = SyncStatus.PENDING,
            noteType = NoteType.TEXT
        )
    }

    // ══════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════

    private fun readContent(source: ImportSource): String {
        return when (source) {
            is ImportSource.WebDav -> {
                source.sardine.get(source.url)?.bufferedReader()?.use { it.readText() }
                    ?: run {
                        Logger.w(TAG, "WebDAV resource not found: ${source.url}")
                        ""
                    }
            }
            is ImportSource.LocalFile -> {
                checkNotNull(context.contentResolver.openInputStream(source.uri)) {
                    "Cannot open file: ${source.uri}"
                }.bufferedReader().use { it.readText() }
            }
        }
    }

    internal fun detectFileType(fileName: String): FileType {
        return when {
            fileName.endsWith(".md", ignoreCase = true) -> FileType.MARKDOWN
            fileName.endsWith(".json", ignoreCase = true) -> FileType.JSON
            else -> FileType.PLAINTEXT
        }
    }

    internal fun extractMarkdownTitle(content: String, fileName: String): String {
        val headingMatch = Regex("^#\\s+(.+)$", RegexOption.MULTILINE).find(content)
        if (headingMatch != null) return headingMatch.groupValues[1].trim()
        return fileName.removeSuffix(".md").replace("-", " ").replace("_", " ")
    }

    internal fun extractMarkdownBody(content: String): String {
        val lines = content.lines()
        val firstHeadingIndex = lines.indexOfFirst { it.startsWith("# ") }
        return if (firstHeadingIndex >= 0) {
            // 🆕 v1.10.0-P2: Preserve content that appears before the heading
            val preHeading = lines.take(firstHeadingIndex)
                .dropWhile { it.isBlank() }
                .dropLastWhile { it.isBlank() }
            val postHeading = lines.drop(firstHeadingIndex + 1)
                .dropWhile { it.isBlank() }
            buildString {
                if (preHeading.isNotEmpty()) {
                    append(preHeading.joinToString("\n"))
                    append("\n\n")
                }
                append(postHeading.joinToString("\n").trim())
            }.trim()
        } else {
            content.trim()
        }
    }

    /**
     * Extrahiert einen Timestamp aus einem JSON-Objekt.
     * Unterstützt: Unix-Millis (Long), Unix-Sekunden (Long), ISO-8601 (String).
     */
    internal fun extractTimestamp(obj: com.google.gson.JsonObject, vararg keys: String): Long? {
        return keys.firstNotNullOfOrNull { key -> parseTimestampElement(obj.get(key)) }
    }

    private fun parseTimestampElement(element: com.google.gson.JsonElement?): Long? {
        element ?: return null
        return try {
            if (!element.isJsonPrimitive) return null
            val prim = element.asJsonPrimitive
            when {
                prim.isNumber -> {
                    val value = prim.asLong
                    if (value < UNIX_SECONDS_THRESHOLD) value * MILLIS_PER_SECOND else value
                }
                prim.isString -> Note.parseISO8601(prim.asString).takeIf { it > 0L }
                else -> null
            }
        } catch (e: Exception) {
            Logger.d(TAG, "Timestamp parsing failed: ${e.message}")
            null
        }
    }
}
