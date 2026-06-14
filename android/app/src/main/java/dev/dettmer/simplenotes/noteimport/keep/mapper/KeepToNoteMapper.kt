package dev.dettmer.simplenotes.noteimport.keep.mapper

import dev.dettmer.simplenotes.models.ChecklistItem
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.noteimport.keep.model.KeepChecklistItem
import dev.dettmer.simplenotes.noteimport.keep.model.KeepColor
import dev.dettmer.simplenotes.noteimport.keep.model.KeepNote
import dev.dettmer.simplenotes.noteimport.keep.parser.TimestampMapper
import dev.dettmer.simplenotes.utils.Logger
import java.util.UUID

/**
 * v2.5.0 — Konvertiert ein [KeepNote] in ein App-`Note` + flache
 * `List<ChecklistItem>`.
 *
 * Vertrag:
 *  - **Reine Funktion** — keine I/O, kein Suspend.
 *  - Generiert eine **neue UUID** als `Note.id` (Konflikt-Auflösung
 *    geschieht später im [ConflictResolver], Commit #8).
 *  - `importedAt` wird vom Caller als Parameter übergeben (testbar &
 *    konsistent über alle Notizen einer Import-Session).
 *  - Annotations (Web-Clips) werden für TEXT-Notizen an `content` angehängt;
 *    für CHECKLIST-Notizen ignoriert (würden die Listen-Semantik kaputt machen).
 *  - Attachments werden **nicht** ins Modell übernommen — der Use-Case
 *    inspiziert `keepNote.attachments.isNotEmpty()` und führt einen
 *    eigenen Counter (`notesWithDroppedAttachments`).
 *  - `sharees` werden hier ignoriert (kein Label, keine Markierung in `Note`);
 *    der Use-Case nutzt `keepNote.isShared` für den `sharedNotesImported`-Counter.
 */
class KeepToNoteMapper(
    private val deviceIdProvider: () -> String,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() }
) {
    /**
     * @param importedAtMs Wert für [Note.importedAt] — typischerweise
     * `System.currentTimeMillis()`, aber injizierbar für Tests.
     */
    fun map(keep: KeepNote, importedAtMs: Long): Note {
        val noteType = if (keep.checklist.isNotEmpty()) NoteType.CHECKLIST else NoteType.TEXT
        val checklistItems = if (noteType == NoteType.CHECKLIST) mapChecklist(keep.checklist) else null
        val content = buildContent(keep, noteType)
        val labels = keep.labels.map { it.name }.distinct().filter { it.isNotBlank() }
        val color = mapColor(keep.color)
        val createdAt = TimestampMapper.usecToMs(keep.createdTimestampUsec)
        val updatedAt = TimestampMapper.usecToMs(keep.userEditedTimestampUsec)

        if (content.length > LARGE_CONTENT_WARN_BYTES) {
            Logger.w(TAG, "Large note '${keep.sourceJsonName}' (${content.length} chars)")
        }

        return Note(
            id = idGenerator(),
            title = keep.title,
            content = content,
            createdAt = createdAt,
            updatedAt = updatedAt,
            deviceId = deviceIdProvider(),
            syncStatus = SyncStatus.PENDING,
            noteType = noteType,
            checklistItems = checklistItems,
            checklistSortOption = null,
            importedAt = importedAtMs,
            labels = labels.takeIf { it.isNotEmpty() },
            color = color,
            isPinned = keep.isPinned.takeIf { it }
        )
    }

    /**
     * Flatten-Regel v2.5.0 (Analyseplan §2.3):
     *  - `text` wird `trimStart()` (Leading-Whitespace war pseudo-Indent).
     *  - `indentationLevel` wird **erhalten** für späteres Nested-Feature.
     *  - `order` = laufender Index (stabile Reihenfolge).
     */
    fun mapChecklist(items: List<KeepChecklistItem>): List<ChecklistItem> =
        items.mapIndexed { idx, k ->
            ChecklistItem(
                id = idGenerator(),
                text = k.text.trimStart(),
                isChecked = k.isChecked,
                order = idx,
                originalOrder = idx,
                indentationLevel = k.indentationLevel.coerceIn(0, MAX_INDENT_LEVEL)
            )
        }

    private fun buildContent(keep: KeepNote, noteType: NoteType): String {
        // Für CHECKLIST-Notizen ist `content` typischerweise leer; die Daten
        // liegen in checklistItems. Wir nehmen trotzdem textContent mit, falls
        // Keep eine Hybrid-Notiz hatte (selten, aber dokumentiert).
        val base = (keep.textContent ?: "").trimEnd()
        if (noteType == NoteType.CHECKLIST) return base
        // TEXT: ggf. Web-Clips anhängen.
        val urls = keep.annotations.mapNotNull { it.url?.takeIf { u -> u.isNotBlank() } }
        if (urls.isEmpty()) return base
        val suffix = urls.joinToString(separator = "\n") { "— $it" }
        return if (base.isEmpty()) suffix else "$base\n\n$suffix"
    }

    private fun mapColor(keepColorName: String): String? {
        return try {
            KeepColor.toHex(keepColorName)
        } catch (e: Exception) {
            Logger.w(TAG, "Color lookup failed for '$keepColorName': ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "KeepToNoteMapper"
        private const val MAX_INDENT_LEVEL = 3
        private const val LARGE_CONTENT_WARN_BYTES = 500_000
    }
}
