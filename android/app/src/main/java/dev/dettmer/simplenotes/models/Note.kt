package dev.dettmer.simplenotes.models

import androidx.compose.runtime.Immutable
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import dev.dettmer.simplenotes.utils.Logger
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.ceil

/**
 * Note data class with Compose stability annotation.
 *
 * `@Immutable` is correct: all properties are `val`, and `checklistItems`
 * is always an immutable copy (never a MutableList in practice), even though
 * the `List` interface technically permits mutable implementations.
 * This enables Compose skipping optimizations during recomposition.
 */
@Immutable
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
    val checklistItems: List<ChecklistItem>? = null,
    // 🆕 v1.8.1 (IMPL_03): Persistierte Sortierung
    val checklistSortOption: String? = null,
    // 🆕 v2.5.0: Google-Keep-Import-Marker (ms epoch). null = nicht aus Keep importiert.
    val importedAt: Long? = null,
    // 🆕 v2.5.0: Vorbereitung Tag-Feature (v2.6.x). null = keine Tags. Pro Notiz persistiert,
    // zusätzlich aggregiert in `notes_labels.json` (siehe Commit #9 LabelStore).
    val labels: List<String>? = null,
    // 🆕 v2.5.0: Vorbereitung v2.6.0 Color-Feature. Hex-Format `#RRGGBB`. null = Standardfarbe.
    // UI-Inertheit v2.5.0: nur persistiert, NICHT gerendert (siehe Analyseplan §2.4.1).
    val color: String? = null,
    // 🆕 v2.5.0: Vorbereitung v2.6.0 Pin-Feature. null = nicht angepinnt.
    // UI-Inertheit v2.5.0: nur persistiert, NICHT gerendert (siehe Analyseplan §2.4.1).
    val isPinned: Boolean? = null,
    // 🆕 v2.7.0 (Folders): Ordner-Zuordnung. null = Root, sonst Verzeichnisname (ohne "/").
    // Lokal flach gespeichert; auf dem Server ein echtes Subdirectory (siehe FolderStore/Sync).
    val folderName: String? = null,
    // 🆕 v2.9.0 (Trash): Zeitpunkt (ms epoch), zu dem die Notiz in den Papierkorb verschoben wurde.
    // null = aktive Notiz. Synct als normale Notiz-Änderung (LWW). Alte Clients ignorieren das Feld;
    // Gson lässt null beim Serialisieren weg → ein Edit auf einem alten Client un-trasht automatisch.
    // NICHT in toMarkdown()/fromMarkdown() — der MD-Spiegel wird beim Trashen serverseitig gelöscht.
    val trashedAt: Long? = null,
    // 🆕 v2.11.0 (Archive): Zeitpunkt (ms epoch), zu dem die Notiz archiviert wurde.
    // null = nicht archiviert. Synct als normale Notiz-Änderung (LWW). Anders als trashedAt
    // WIRD das Feld in toMarkdown()/fromMarkdown() gespiegelt (`archived:`), weil der
    // MD-Spiegel archivierter Notizen auf dem Server bestehen bleibt (archiviert ≠ gelöscht).
    val archivedAt: Long? = null
) {
    /** v2.9.0 (Trash): true, wenn die Notiz im Papierkorb liegt. */
    val isTrashed: Boolean get() = trashedAt != null

    /** 🆕 v2.11.0 (Archive): true, wenn die Notiz archiviert ist. */
    val isArchived: Boolean get() = archivedAt != null

    /**
     * Serialisiert Note zu JSON
     * v1.4.0: Nutzt Gson für komplexe Strukturen
     * v1.4.1: Für Checklisten wird ein Fallback-Content generiert, damit ältere
     *         App-Versionen (v1.3.x) die Notiz als Text anzeigen können.
     */
    fun toJson(): String {
        // v1.4.1: Für Checklisten den Fallback-Content generieren
        val noteToSerialize = if (noteType == NoteType.CHECKLIST && checklistItems != null) {
            this.copy(content = generateChecklistFallbackContent())
        } else {
            this
        }

        return prettyGson.toJson(noteToSerialize)
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
        }.orEmpty()
    }

    /**
     * Exports this note as a Markdown file with YAML frontmatter.
     * Format kompatibel mit Obsidian, Joplin, Typora (Task #1.2.0-08)
     *
     * **Checklist format** (GFM Task Lists):
     * ```
     * ---
     * type: checklist
     * sort: MANUAL
     * ---
     * # Title
     *
     * - [ ] Unchecked item
     * - [x] Checked item
     * ```
     *
     * The frontmatter `type: checklist` is required for reliable round-trip import.
     * Without it, the import heuristic will only detect the file as a checklist if
     * ALL content lines are GFM task items (- [ ] / - [x]).
     *
     * @return Complete markdown string with YAML frontmatter
     * 🆕 v1.10.0-P2: KDoc updated
     */
    fun toMarkdown(): String {
        // 🆕 v1.8.1 (IMPL_03): Sortierung im Frontmatter
        val sortLine = if (noteType == NoteType.CHECKLIST && checklistSortOption != null) {
            "\nsort: $checklistSortOption"
        } else {
            ""
        }

        // 🆕 v2.5.0: optionale YAML-Felder. Nur schreiben wenn != null.
        // Ältere App-Versionen (<v2.5.0) ignorieren unbekannte YAML-Keys (siehe fromMarkdown Z. 312-320).
        val importedLine = importedAt?.let { "\nimported: $it" }.orEmpty()
        val labelsLine = labels?.takeIf { it.isNotEmpty() }
            ?.joinToString(prefix = "\nlabels: [", separator = ", ", postfix = "]")
            .orEmpty()
        val colorLine = color?.let { "\ncolor: \"$it\"" }.orEmpty()
        val pinnedLine = isPinned?.let { "\npinned: $it" }.orEmpty()
        val folderLine = folderName?.let { "\nfolder: \"$it\"" }.orEmpty()
        // 🆕 v2.11.0 (Archive): wie `imported` als ms-epoch, nur wenn != null.
        val archivedLine = archivedAt?.let { "\narchived: $it" }.orEmpty()

        val header = """
---
id: $id
created: ${formatISO8601(createdAt)}
updated: ${formatISO8601(updatedAt)}
device: $deviceId
type: ${noteType.name.lowercase()}$sortLine$importedLine$labelsLine$colorLine$pinnedLine$folderLine$archivedLine
---

# $title
        """.trimIndent()

        return when (noteType) {
            NoteType.TEXT -> {
                // FIX-01 (v2.2.0): Leerzeile zwischen # Titel und Content (CommonMark-konform)
                if (content.isNotBlank()) {
                    header + "\n\n" + content
                } else {
                    header + "\n"
                }
            }
            NoteType.CHECKLIST -> {
                val checklistMarkdown = checklistItems?.sortedBy { it.order }?.joinToString("\n") { item ->
                    val checkbox = if (item.isChecked) "[x]" else "[ ]"
                    "- $checkbox ${item.text}"
                }.orEmpty()
                // FIX-01 (v2.2.0): Leerzeile zwischen # Titel und Items verhindert Titel-Korruption beim Re-Import
                if (checklistMarkdown.isNotEmpty()) {
                    header + "\n\n" + checklistMarkdown
                } else {
                    header + "\n"
                }
            }
        }
    }

    companion object {
        private const val TAG = "Note"

        // 🔧 Perf: Gson-Instanz ist zustandslos/thread-safe und cacht ihre reflektionsbasierten
        // Type-Adapter intern — eine neue Instanz pro toJson()-Aufruf verwirft diesen
        // Cache und baut ihn bei jeder einzelnen Notiz neu auf. Bei tausenden Notizen (Cold-Start-Load)
        // summiert sich das spürbar; geteilte Instanz wiederverwenden behebt das.
        // Der Lesepfad (fromJson) nutzt seit v2.10 einen handgeschriebenen Streaming-Parser
        // (siehe unten) und braucht diese Instanz nicht mehr.
        private val prettyGson = com.google.gson.GsonBuilder().setPrettyPrinting().create()

        // Inverse von MarkdownSyncManager.rewriteAssetLinksForMdMirror: der MD-Mirror
        // referenziert Bilder relativ (`../<sf>-assets/…`), die JSON-Konvention ist `.assets/…`.
        // Ohne Rückwandlung matcht IMAGE_REGEX nicht und das Bild wird nicht gerendert.
        private val MD_MIRROR_ASSET_REGEX = Regex("""\]\((?:\.\./)+[^)]*?-assets/""")

        /**
         * Parst JSON zu Note-Objekt mit Backward Compatibility für alte Notizen ohne noteType
         *
         * 🔧 Perf: handgeschriebener Streaming-Parser (`JsonReader`) statt Baum-Aufbau
         * (`JsonParser.parseString`) + Reflection-Bind (`Gson.fromJson`). Vermeidet
         * `JsonObject`-Allokation und Feld-Reflection pro Notiz — bei tausenden Notizen
         * (Cold-Start-Load) linear relevant. Unbekannte Felder werden übersprungen
         * (Vorwärtskompatibilität mit neueren Clients bleibt erhalten).
         * ponytail: handgeschrieben statt generischem TypeAdapter/Reflection-Framework —
         * Ceiling: neue Note-Felder müssen hier manuell im `when`-Block ergänzt werden.
         */
        fun fromJson(json: String): Note? {
            return try {
                val fields = readNoteFields(json)

                // Backward Compatibility: Alte Notizen ohne noteType bekommen TEXT
                val noteType = resolveNoteType(fields.noteTypeRaw)

                // v1.4.1: Recovery-Mode - Falls Checkliste aber keine Items,
                // versuche Content als Fallback zu parsen
                var checklistItems = fields.checklistItems
                if (noteType == NoteType.CHECKLIST && checklistItems.isNullOrEmpty() && fields.content.isNotBlank()) {
                    val recoveredItems = parseChecklistFromContent(fields.content)
                    if (recoveredItems.isNotEmpty()) {
                        Logger.d(TAG, "🔄 Recovered ${recoveredItems.size} checklist items from content fallback")
                        checklistItems = recoveredItems.toMutableList()
                    }
                }

                // FIX-03 (v2.2.0): Titel-Korrektur für CHECKLIST-Notizen aus korruptem JSON
                val (cleanTitle, repairedItems) = if (noteType == NoteType.CHECKLIST) {
                    repairCorruptedChecklistTitle(fields.title, checklistItems)
                } else {
                    fields.title to checklistItems
                }

                // Note mit korrekten Werten erstellen
                Note(
                    id = fields.id ?: UUID.randomUUID().toString(),
                    title = cleanTitle,
                    content = fields.content,
                    createdAt = fields.createdAt,
                    updatedAt = fields.updatedAt,
                    deviceId = fields.deviceId,
                    syncStatus = fields.syncStatus ?: SyncStatus.LOCAL_ONLY,
                    noteType = noteType,
                    checklistItems = repairedItems,
                    checklistSortOption = fields.checklistSortOption, // 🆕 v1.8.1 (IMPL_03)
                    // 🆕 v2.5.0
                    importedAt = fields.importedAt,
                    labels = fields.labels,
                    color = fields.color,
                    isPinned = fields.isPinned,
                    folderName = fields.folderName,
                    // 🆕 v2.9.0 (Trash) — fehlendes Feld → null (= aktive Notiz)
                    trashedAt = fields.trashedAt,
                    archivedAt = fields.archivedAt // 🆕 v2.11.0 (Archive) — fehlendes Feld → null
                )
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to parse JSON: ${e.message}")
                null
            }
        }

        private fun resolveNoteType(noteTypeRaw: String?): NoteType {
            if (noteTypeRaw == null) return NoteType.TEXT
            return try {
                NoteType.valueOf(noteTypeRaw)
            } catch (e: Exception) {
                Logger.w(TAG, "Unknown noteType, defaulting to TEXT: ${e.message}")
                NoteType.TEXT
            }
        }

        /**
         * FIX-03 (v2.2.0): Korrigiert Titel für CHECKLIST-Notizen aus korruptem JSON —
         * frühere App-Versionen haben Checklist-Items versehentlich in den Titel geschrieben.
         * Rettet die verschluckten Items und hängt sie vor die bestehenden Items.
         */
        private fun repairCorruptedChecklistTitle(
            title: String,
            checklistItems: MutableList<ChecklistItem>?
        ): Pair<String, MutableList<ChecklistItem>?> {
            val checklistPatternInTitle = Regex("""[-*]\s*\[([ xX])\]\s+""")
            val splitMatch = checklistPatternInTitle.find(title) ?: return title to checklistItems

            val rescuedText = title.substring(splitMatch.range.first)
            val cleanTitle = title.substring(0, splitMatch.range.first).trim()

            // Alle verschluckten Items aus dem Titel extrahieren
            val rescuedItems = Regex("""[-*]\s*\[([ xX])\]\s+(.+?)(?=\s*[-*]\s*\[|${'$'})""")
                .findAll(rescuedText).mapIndexed { idx, m ->
                    ChecklistItem(
                        id = UUID.randomUUID().toString(),
                        text = m.groupValues[2].trim(),
                        isChecked = m.groupValues[1].lowercase() == "x",
                        order = idx
                    )
                }.toList()

            if (rescuedItems.isEmpty()) return cleanTitle to checklistItems

            val mergedItems = (rescuedItems + (checklistItems ?: emptyList()))
                .mapIndexed { i, item -> item.copy(order = i) }
                .toMutableList()
            Logger.w(
                TAG,
                "⚠️ CORRUPTION FIX (JSON): '$title' → '$cleanTitle', " +
                    "rescued ${rescuedItems.size} item(s)"
            )
            return cleanTitle to mergedItems
        }

        private class ParsedNoteFields {
            var id: String? = null
            var title: String = ""
            var content: String = ""
            var createdAt: Long = System.currentTimeMillis()
            var updatedAt: Long = System.currentTimeMillis()
            var deviceId: String = ""
            var syncStatus: SyncStatus? = null
            var noteTypeRaw: String? = null
            var checklistSortOption: String? = null
            var importedAt: Long? = null
            var labels: List<String>? = null
            var color: String? = null
            var isPinned: Boolean? = null
            var folderName: String? = null
            var trashedAt: Long? = null
            var archivedAt: Long? = null
            var checklistItems: MutableList<ChecklistItem>? = null
        }

        private fun readNoteFields(json: String): ParsedNoteFields {
            val fields = ParsedNoteFields()
            JsonReader(StringReader(json)).use { reader ->
                // Parität mit dem alten JsonParser.parseString()-Verhalten (immer lenient).
                reader.strictness = com.google.gson.Strictness.LENIENT
                reader.beginObject()
                while (reader.hasNext()) {
                    reader.readNoteField(fields)
                }
                reader.endObject()
            }
            return fields
        }

        private fun JsonReader.readNoteField(fields: ParsedNoteFields) {
            when (nextName()) {
                "id" -> fields.id = nextStringOrNull()
                "title" -> fields.title = nextStringOrNull() ?: ""
                "content" -> fields.content = nextStringOrNull() ?: ""
                "createdAt" -> fields.createdAt = nextLongOrNull() ?: fields.createdAt
                "updatedAt" -> fields.updatedAt = nextLongOrNull() ?: fields.updatedAt
                "deviceId" -> fields.deviceId = nextStringOrNull() ?: ""
                "syncStatus" -> fields.syncStatus = nextStringOrNull()
                    ?.let { raw -> runCatching { SyncStatus.valueOf(raw) }.getOrNull() }
                "noteType" -> fields.noteTypeRaw = nextStringOrNull()
                "checklistSortOption" -> fields.checklistSortOption = nextStringOrNull()
                "importedAt" -> fields.importedAt = nextLongOrNull()
                "labels" -> fields.labels = nextStringListOrNull()
                "color" -> fields.color = nextStringOrNull()
                "isPinned" -> fields.isPinned = nextBooleanOrNull()
                "folderName" -> fields.folderName = nextStringOrNull()
                "trashedAt" -> fields.trashedAt = nextLongOrNull()
                "archivedAt" -> fields.archivedAt = nextLongOrNull()
                "checklistItems" -> fields.checklistItems = nextChecklistItemsOrNull()
                else -> skipValue()
            }
        }

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
         * 🔧 v1.7.2 (IMPL_014): Optional serverModifiedTime für korrekte Timestamp-Sync
         *
         * @param md Markdown-String mit YAML Frontmatter
         * @param serverModifiedTime Optionaler Server-Datei mtime (Priorität über YAML timestamp)
         * @return Note-Objekt oder null bei Parse-Fehler
         */
        fun fromMarkdown(md: String, serverModifiedTime: Long? = null): Note? {
            return try {
                // FIX-06 (v2.2.0): Normalisiere Zeilenumbrüche vor Regex-Matching
                val normalizedMd = md.replace("\r\n", "\n").replace("\r", "\n")

                // Parse YAML Frontmatter + Markdown Content
                val frontmatterRegex = Regex("^---\\n(.+?)\\n---\\n(.*)$", RegexOption.DOT_MATCHES_ALL)
                val match = frontmatterRegex.find(normalizedMd) ?: return null

                val yamlBlock = match.groupValues[1]
                val contentBlock = match.groupValues[2]

                // Parse YAML (einfach per String-Split für MVP)
                val metadata = yamlBlock.lines()
                    .mapNotNull { line ->
                        val parts = line.split(":", limit = 2)
                        if (parts.size == 2) {
                            parts[0].trim() to parts[1].trim()
                        } else {
                            null
                        }
                    }.toMap()

                // Extract title from first # heading
                var title = contentBlock.lines()
                    .firstOrNull { it.startsWith("# ") }
                    ?.removePrefix("# ")?.trim() ?: "Untitled"

                // FIX-02 (v2.2.0): Titel-Korrektur wenn Checklist-Pattern im Titel
                // Bug: fehlende Leerzeile in toMarkdown() führte zu "Titel- [ ] Item" als Titelzeile
                val checklistPatternInTitle = Regex("""[-*]\s*\[([ xX])\]\s+""")
                var rescuedItemLine: String? = null
                checklistPatternInTitle.find(title)?.let { splitMatch ->
                    rescuedItemLine = title.substring(splitMatch.range.first).trim()
                    title = title.substring(0, splitMatch.range.first).trim()
                    Logger.w(TAG, "⚠️ CORRUPTION FIX: Checklist pattern in title → cleaned to '$title', rescued: '$rescuedItemLine'")
                }

                // v1.4.0: Prüfe ob type: checklist im Frontmatter
                val noteTypeStr = metadata["type"]?.lowercase() ?: "text"
                val noteType = when (noteTypeStr) {
                    "checklist" -> NoteType.CHECKLIST
                    else -> NoteType.TEXT
                }

                // 🆕 v1.8.1 (IMPL_03): Gespeicherte Sortierung aus YAML laden
                val checklistSortOption = metadata["sort"]

                // 🆕 v2.5.0: vier optionale Felder aus YAML laden. Alle Reads defensiv (null bei Fehler).
                val importedAt: Long? = metadata["imported"]?.trim()?.toLongOrNull()
                val labels: List<String>? = metadata["labels"]?.let { raw ->
                    // Inline-Liste "[a, b, c]" parsen. Auch leere Liste oder fehlende Brackets tolerieren.
                    val trimmed = raw.trim().removePrefix("[").removeSuffix("]").trim()
                    if (trimmed.isEmpty()) {
                        null
                    } else {
                        trimmed.split(",").map { it.trim().removeSurrounding("\"") }
                            .filter { it.isNotEmpty() }
                            .ifEmpty { null }
                    }
                }
                val color: String? = metadata["color"]?.trim()?.removeSurrounding("\"")
                    ?.takeIf { it.isNotEmpty() }
                val isPinned: Boolean? = metadata["pinned"]?.trim()?.let { v ->
                    when (v.lowercase()) {
                        "true" -> true
                        "false" -> false
                        else -> {
                            Logger.w(TAG, "Invalid 'pinned' YAML value '$v', ignoring")
                            null
                        }
                    }
                }
                // 🆕 v2.7.0 (Folders): optionaler Ordnername aus YAML.
                val folderName: String? = metadata["folder"]?.trim()?.removeSurrounding("\"")
                    ?.takeIf { it.isNotEmpty() }
                // 🆕 v2.11.0 (Archive): optionaler ms-epoch-Timestamp aus YAML (wie `imported`).
                val archivedAt: Long? = metadata["archived"]?.trim()?.toLongOrNull()

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

                // FIX-02 (v2.2.0): Gerettetes Item vor den restlichen Content einfügen
                val effectiveContent = if (rescuedItemLine != null) {
                    if (contentAfterTitle.isNotEmpty()) {
                        rescuedItemLine + "\n" + contentAfterTitle
                    } else {
                        rescuedItemLine
                    }
                } else {
                    contentAfterTitle
                }

                val content: String
                val checklistItems: List<ChecklistItem>?

                if (noteType == NoteType.CHECKLIST) {
                    // Parse Checklist Items
                    // 🆕 v1.10.0-P2: More lenient regex — supports `*` prefix and extra spaces
                    val checklistRegex = Regex("""^[-*]\s+\[([ xX])\]\s+(.*)$""", RegexOption.MULTILINE)
                    checklistItems = checklistRegex.findAll(effectiveContent).mapIndexed { index, matchResult ->
                        ChecklistItem(
                            id = UUID.randomUUID().toString(),
                            text = matchResult.groupValues[2].trim(),
                            isChecked = matchResult.groupValues[1].lowercase() == "x",
                            order = index
                        )
                    }.toList().ifEmpty { null }
                    content = "" // Checklisten haben keinen "content"
                } else {
                    content = MD_MIRROR_ASSET_REGEX.replace(effectiveContent, "](.assets/")
                    checklistItems = null
                }

                // 🔧 v1.8.2 (IMPL_025): YAML-Timestamp ist autoritativ
                // Server mtime nur verwenden wenn YAML-Timestamp fehlt/ungültig (= 0)
                // IMPL_014-Logik entfernt: Server mtime nach eigenem Export ist immer "jetzt",
                // was zu Feedback Loop führt (IMPL_025). Externe Editoren (Obsidian etc.)
                // aktualisieren den YAML-Header zuverlässig.
                val yamlUpdatedAt = parseISO8601(metadata["updated"].orEmpty())
                val effectiveUpdatedAt = when {
                    yamlUpdatedAt <= 0L && serverModifiedTime != null && serverModifiedTime > 0L -> {
                        Logger.d(TAG, "YAML timestamp missing/invalid, using server mtime: $serverModifiedTime")
                        serverModifiedTime
                    }
                    else -> {
                        if (serverModifiedTime != null && serverModifiedTime > yamlUpdatedAt) {
                            Logger.d(
                                TAG,
                                "Ignoring server mtime ($serverModifiedTime) — using YAML ($yamlUpdatedAt) to prevent loop"
                            )
                        }
                        yamlUpdatedAt
                    }
                }

                Note(
                    id = metadata["id"] ?: UUID.randomUUID().toString(),
                    title = title,
                    content = content,
                    createdAt = parseISO8601(metadata["created"].orEmpty()),
                    updatedAt = effectiveUpdatedAt,
                    deviceId = metadata["device"] ?: "desktop",
                    syncStatus = SyncStatus.SYNCED, // Annahme: Vom Server importiert
                    noteType = noteType,
                    checklistItems = checklistItems,
                    checklistSortOption = checklistSortOption, // 🆕 v1.8.1 (IMPL_03)
                    // 🆕 v2.5.0
                    importedAt = importedAt,
                    labels = labels,
                    color = color,
                    isPinned = isPinned,
                    folderName = folderName,
                    archivedAt = archivedAt // 🆕 v2.11.0 (Archive)
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
         * 🔧 v1.7.2 (IMPL_002): Robustes ISO8601 Parsing mit Multi-Format Unterstützung
         *
         * Unterstützte Formate (in Prioritätsreihenfolge):
         * 1. 2024-12-21T18:00:00Z (UTC mit Z)
         * 2. 2024-12-21T18:00:00+01:00 (mit Offset)
         * 3. 2024-12-21T18:00:00+0100 (Offset ohne Doppelpunkt)
         * 4. 2024-12-21T18:00:00.123Z (mit Millisekunden)
         * 5. 2024-12-21T18:00:00.123+01:00 (Millisekunden + Offset)
         * 6. 2024-12-21 18:00:00 (Leerzeichen statt T)
         *
         * Fallback: Aktueller Timestamp bei Fehler
         *
         * @param dateString ISO8601 Datum-String
         * @return Unix Timestamp in Millisekunden
         */
        internal fun parseISO8601(dateString: String): Long {
            if (dateString.isBlank()) {
                return System.currentTimeMillis()
            }

            // Normalisiere: Leerzeichen → T
            val normalized = dateString.trim().replace(' ', 'T')

            // Format-Patterns in Prioritätsreihenfolge
            val patterns = listOf(
                // Mit Timezone Z
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                // Mit Offset XXX (+01:00)
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                // Mit Offset ohne Doppelpunkt (+0100)
                "yyyy-MM-dd'T'HH:mm:ssZ",
                "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
                // Ohne Timezone (interpretiere als UTC)
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd'T'HH:mm:ss.SSS"
            )

            // Versuche alle Patterns nacheinander
            for (pattern in patterns) {
                @Suppress("SwallowedException") // Intentional: try all patterns before logging
                try {
                    val sdf = SimpleDateFormat(pattern, Locale.US)
                    // 🔧 v1.8.2 (IMPL_025): UTC für alle Patterns OHNE echtes Timezone-Token
                    // 'Z' (literal/quoted) ist KEIN Timezone-Token — nur unquoted Z und XXX sind es.
                    // Bug: pattern.contains("Z") matchte auch 'Z' (literal),
                    // wodurch UTC nicht gesetzt wurde → 1h Drift pro Sync-Zyklus (CET=UTC+1)
                    val hasRealTimezoneToken = pattern.contains("XXX") ||
                        (pattern.contains("Z") && !pattern.contains("'Z'"))
                    if (!hasRealTimezoneToken) {
                        sdf.timeZone = TimeZone.getTimeZone("UTC")
                    }
                    val parsed = sdf.parse(normalized)
                    if (parsed != null) {
                        return parsed.time
                    }
                } catch (e: Exception) {
                    // 🔇 Exception intentionally swallowed - try next pattern
                    // Only log if no pattern matches (see fallback below)
                    continue
                }
            }

            // Fallback wenn kein Pattern passt
            Logger.w(TAG, "Failed to parse ISO8601 date '$dateString' with any pattern, using current time")
            return System.currentTimeMillis()
        }
    }
}

// 🆕 v2.11.0: Rough average characters per rendered line at default font size/card width —
// used to estimate wrapped line count from raw text length (see estimateDisplayLines below).
private const val ESTIMATED_CHARS_PER_LINE = 32

/**
 * Schätzt, wie viele Anzeige-Zeilen ein Text (z. B. eine Notiz-Zeile oder ein Checklist-Item)
 * voraussichtlich belegt: explizite Zeilenumbrüche zählen einzeln, lange Zeilen werden anhand
 * [ESTIMATED_CHARS_PER_LINE] auf ihre Umbruchzeilen hochgerechnet.
 */
private fun estimateDisplayLines(text: String): Int =
    text.split("\n").sumOf { line ->
        if (line.isEmpty()) 1 else ceil(line.length / ESTIMATED_CHARS_PER_LINE.toDouble()).toInt()
    }

/**
 * 🎨 v1.7.0: Note size classification for Staggered Grid Layout
 */
enum class NoteSize {
    SMALL, // Compact display (≤ SMALL_LINE_THRESHOLD estimated display lines)
    LARGE; // Full-width display

    companion object {
        // 🆕 v2.11.0: Zeilen-basiert statt reine Zeichen-/Item-Zählung — erkennt jetzt auch
        // viele kurze Zeilen und wenige, aber lange Checklist-Items korrekt als LARGE.
        const val SMALL_LINE_THRESHOLD = 3
    }
}

/**
 * 🎨 v1.7.0: Determine note size for grid layout optimization
 */
fun Note.getSize(): NoteSize {
    val estimatedLines = when (noteType) {
        NoteType.TEXT -> estimateDisplayLines(content)
        NoteType.CHECKLIST -> checklistItems.orEmpty().sumOf { estimateDisplayLines(it.text) }
    }
    return if (estimatedLines <= NoteSize.SMALL_LINE_THRESHOLD) NoteSize.SMALL else NoteSize.LARGE
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

// ── Streaming-JSON-Helfer für Note.fromJson() (Perf: Cold-Start bei tausenden Notizen) ──

private fun JsonReader.nextStringOrNull(): String? =
    if (peek() == JsonToken.NULL) {
        nextNull()
        null
    } else {
        nextString()
    }

private fun JsonReader.nextLongOrNull(): Long? =
    if (peek() == JsonToken.NULL) {
        nextNull()
        null
    } else {
        nextLong()
    }

private fun JsonReader.nextBooleanOrNull(): Boolean? =
    if (peek() == JsonToken.NULL) {
        nextNull()
        null
    } else {
        nextBoolean()
    }

private fun JsonReader.nextIntOrNull(): Int? =
    if (peek() == JsonToken.NULL) {
        nextNull()
        null
    } else {
        nextInt()
    }

private fun JsonReader.nextStringListOrNull(): List<String>? {
    if (peek() == JsonToken.NULL) {
        nextNull()
        return null
    }
    val list = mutableListOf<String>()
    beginArray()
    while (hasNext()) list.add(nextString())
    endArray()
    return list
}

private fun JsonReader.nextChecklistItemsOrNull(): MutableList<ChecklistItem>? {
    if (peek() == JsonToken.NULL) {
        nextNull()
        return null
    }
    val items = mutableListOf<ChecklistItem>()
    beginArray()
    while (hasNext()) items.add(nextChecklistItem())
    endArray()
    return items
}

private fun JsonReader.nextChecklistItem(): ChecklistItem {
    var id: String? = null
    var text = ""
    var isChecked = false
    var order = 0
    // 🔧 Gson-Parität: Der bisherige reflektionsbasierte Parser hat den Default
    // `originalOrder = order` (Referenz auf ein anderes Konstruktor-Argument) NIE
    // ausgewertet — bei fehlendem Feld kam immer 0 heraus (verifiziert per Testsonde).
    // Absichtlich repliziert statt "korrigiert", um Verhalten für ungeänderte
    // Alt-Notizen (< v1.9.0) exakt beizubehalten.
    var originalOrder = 0
    var createdAt = System.currentTimeMillis()
    var indentationLevel = 0

    beginObject()
    while (hasNext()) {
        when (nextName()) {
            "id" -> id = nextStringOrNull()
            "text" -> text = nextStringOrNull() ?: ""
            "isChecked" -> isChecked = nextBooleanOrNull() ?: false
            "order" -> order = nextIntOrNull() ?: 0
            "originalOrder" -> originalOrder = nextIntOrNull() ?: 0
            "createdAt" -> createdAt = nextLongOrNull() ?: createdAt
            "indentationLevel" -> indentationLevel = nextIntOrNull() ?: 0
            else -> skipValue()
        }
    }
    endObject()

    return ChecklistItem(
        id = id ?: UUID.randomUUID().toString(),
        text = text,
        isChecked = isChecked,
        order = order,
        originalOrder = originalOrder,
        createdAt = createdAt,
        indentationLevel = indentationLevel
    )
}
