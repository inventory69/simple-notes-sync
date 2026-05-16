package dev.dettmer.simplenotes.noteimport.keep.parser

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import dev.dettmer.simplenotes.noteimport.keep.model.KeepAnnotation
import dev.dettmer.simplenotes.noteimport.keep.model.KeepAttachment
import dev.dettmer.simplenotes.noteimport.keep.model.KeepChecklistItem
import dev.dettmer.simplenotes.noteimport.keep.model.KeepLabel
import dev.dettmer.simplenotes.noteimport.keep.model.KeepNote
import dev.dettmer.simplenotes.noteimport.keep.model.KeepNoteState
import dev.dettmer.simplenotes.noteimport.keep.parser.dto.KeepNoteJson
import dev.dettmer.simplenotes.noteimport.keep.zip.KeepZipEntry
import dev.dettmer.simplenotes.utils.Logger

/**
 * v2.5.0 — Gson-basierte Implementierung. Nutzt [KeepHtmlFallbackParser],
 * wenn `textContent` blank UND keine Checkliste vorhanden ist.
 *
 * Indent-Heuristik für Checklisten aus JSON:
 *   Leading-Spaces / Tabs in `text` → indentationLevel = (whitespace / 2),
 *   gecappt auf [MAX_INDENT_LEVEL]. Tab zählt als 2 Spaces (Keep-Web-UI-Verhalten).
 */
internal class KeepEntryParserImpl(
    private val htmlFallback: KeepHtmlFallbackParser,
    private val gson: Gson = Gson(),
) : KeepEntryParser {

    override fun parse(jsonEntry: KeepZipEntry, htmlEntry: KeepZipEntry?): KeepNote? {
        val raw = readUtf8Stripped(jsonEntry.bytes)
        if (raw.isBlank()) {
            Logger.w(TAG, "Empty JSON entry '${jsonEntry.name}', skipping")
            return null
        }
        val dto = parseDto(raw, jsonEntry.name) ?: return null

        val title = dto.title ?: ""
        val checklistFromJson = dto.listContent
            ?.mapNotNull { mapListItem(it) }
            ?: emptyList()

        // Plaintext: bevorzugt JSON, sonst HTML-Fallback (nur wenn keine Checkliste vorliegt).
        val textContent: String? = when {
            !dto.textContent.isNullOrBlank() -> dto.textContent
            checklistFromJson.isEmpty() && htmlEntry != null -> {
                val html = readUtf8Stripped(htmlEntry.bytes)
                htmlFallback.extractPlainText(html).ifBlank { null }
            }
            else -> dto.textContent
        }

        // Falls JSON keine Checkliste lieferte, HTML als zweite Quelle versuchen.
        val checklist: List<KeepChecklistItem> = when {
            checklistFromJson.isNotEmpty() -> checklistFromJson
            htmlEntry != null -> {
                val html = readUtf8Stripped(htmlEntry.bytes)
                htmlFallback.extractChecklist(html)
            }
            else -> emptyList()
        }

        val state = when {
            dto.isTrashed == true -> KeepNoteState.TRASHED
            dto.isArchived == true -> KeepNoteState.ARCHIVED
            else -> KeepNoteState.ACTIVE
        }

        return KeepNote(
            title = title,
            textContent = textContent,
            checklist = checklist,
            labels = dto.labels?.mapNotNull { l -> l.name?.takeIf { it.isNotBlank() }?.let(::KeepLabel) }
                ?: emptyList(),
            attachments = dto.attachments?.mapNotNull { a ->
                val path = a.filePath?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val mime = a.mimetype ?: "application/octet-stream"
                KeepAttachment(filePath = path, mimeType = mime, sizeBytes = null)
            } ?: emptyList(),
            annotations = dto.annotations?.mapNotNull { a ->
                val src = a.source?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                KeepAnnotation(source = src, url = a.url, title = a.title)
            } ?: emptyList(),
            color = dto.color ?: "DEFAULT",
            isPinned = dto.isPinned ?: false,
            isShared = !dto.sharees.isNullOrEmpty(),
            state = state,
            createdTimestampUsec = dto.createdTimestampUsec ?: 0L,
            userEditedTimestampUsec = dto.userEditedTimestampUsec ?: 0L,
            sourceJsonName = jsonEntry.name,
        )
    }

    private fun parseDto(raw: String, name: String): KeepNoteJson? {
        return try {
            gson.fromJson(raw, KeepNoteJson::class.java) ?: run {
                Logger.w(TAG, "Gson returned null for '$name', skipping")
                null
            }
        } catch (e: JsonSyntaxException) {
            Logger.w(TAG, "Invalid JSON in '$name': ${e.message}")
            null
        } catch (e: Exception) {
            Logger.w(TAG, "Unexpected parse error in '$name': ${e.message}")
            null
        }
    }

    private fun mapListItem(item: dev.dettmer.simplenotes.noteimport.keep.parser.dto.KeepListItemJson): KeepChecklistItem? {
        val raw = item.text ?: return null
        val leading = raw.takeWhile { it == ' ' || it == '\t' }
        val indent = leading.sumOf { if (it == '\t') TAB_AS_SPACES else 1 } / SPACES_PER_INDENT
        return KeepChecklistItem(
            text = raw.trimStart(),
            isChecked = item.isChecked ?: false,
            indentationLevel = indent.coerceIn(0, MAX_INDENT_LEVEL),
        )
    }

    private fun readUtf8Stripped(bytes: ByteArray): String {
        val s = String(bytes, Charsets.UTF_8)
        // BOM strippen (Analyseplan §2.1, Edge-Case 7).
        return if (s.isNotEmpty() && s[0] == '\uFEFF') s.substring(1) else s
    }

    companion object {
        private const val TAG = "KeepEntryParser"
        private const val MAX_INDENT_LEVEL = 3
        private const val SPACES_PER_INDENT = 2
        private const val TAB_AS_SPACES = 2
    }
}
