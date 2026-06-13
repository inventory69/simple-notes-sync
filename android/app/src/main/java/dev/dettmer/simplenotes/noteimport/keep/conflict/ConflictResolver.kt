package dev.dettmer.simplenotes.noteimport.keep.conflict

import dev.dettmer.simplenotes.models.ChecklistItem
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.storage.NotesStorage
import dev.dettmer.simplenotes.utils.Logger
import java.security.MessageDigest

/**
 * v2.5.0 — Entscheidet pro Kandidat: `Create` / `Replace(existingId)` / `Skip(reason)`.
 *
 * Hash-Definition (Analyseplan §7.1.7):
 *   `SHA-256(title + 0x01 + canonicalContent)`
 * mit
 *   - canonicalContent (TEXT)      = `content.trim()`
 *   - canonicalContent (CHECKLIST) = items.sortedBy{order}.joinToString("\n") {
 *                                       "${it.isChecked}\u0001${it.text}" }
 *
 * Der Hash ist **stabil** über Re-Imports desselben Keep-Exports — neue UUIDs
 * pro Kandidat (vom Mapper vergeben) ändern den Hash nicht.
 */
class ConflictResolver(private val storage: NotesStorage) {

    sealed class Resolution {
        object Create : Resolution()
        data class Replace(val existingId: String) : Resolution()
        data class Skip(val reason: String) : Resolution()
    }

    /**
     * Findet ein bestehendes Note-Match per Content-Hash und wendet die Strategy an.
     *
     * Caller-Tipp: Für große Imports `loadAllNotes()` **einmal pro Session** cachen
     * (Use-Case Commit #10 hält die Hash-Tabelle in einem `MutableMap`-Field), damit
     * dieser Resolver nicht n-mal die Storage scannt. Der Resolver selbst trifft
     * keine Caching-Entscheidung.
     */
    suspend fun resolve(candidate: Note, strategy: ConflictStrategy): Resolution {
        if (strategy == ConflictStrategy.ALWAYS_CREATE) return Resolution.Create

        val candidateHash = computeContentHash(candidate)
        val existing = try {
            // 🆕 v2.9.0 (Trash): getrashte Notizen ignorieren — der Import legt sonst keine frische
            // Kopie an, sondern überspringt still gegen eine bereits gelöschte Notiz.
            storage.loadActiveNotes().firstOrNull { computeContentHash(it) == candidateHash }
        } catch (e: Exception) {
            Logger.w(TAG, "Conflict scan failed: ${e.message}")
            null
        }

        return when {
            existing == null -> Resolution.Create
            strategy == ConflictStrategy.SKIP -> Resolution.Skip(reason = "duplicate (hash match)")
            strategy == ConflictStrategy.REPLACE -> Resolution.Replace(existingId = existing.id)
            else -> Resolution.Create // unreachable; defensives Fallback
        }
    }

    fun computeContentHash(note: Note): String {
        val canonical = canonicalContent(note)
        val seed = note.title + SEPARATOR + canonical
        return sha256Hex(seed.toByteArray(Charsets.UTF_8))
    }

    private fun canonicalContent(note: Note): String =
        when (note.noteType) {
            NoteType.TEXT -> note.content.trim()
            NoteType.CHECKLIST -> {
                val items = note.checklistItems ?: emptyList()
                canonicalChecklist(items)
            }
        }

    private fun canonicalChecklist(items: List<ChecklistItem>): String =
        items.sortedBy { it.order }
            .joinToString(separator = "\n") { "${it.isChecked}$SEPARATOR${it.text}" }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            sb.append(HEX_CHARS[(b.toInt() ushr SHIFT_4) and HEX_MASK])
            sb.append(HEX_CHARS[b.toInt() and HEX_MASK])
        }
        return sb.toString()
    }

    companion object {
        private const val TAG = "ConflictResolver"
        private const val SEPARATOR = "\u0001"
        private const val HEX_CHARS = "0123456789abcdef"
        private const val HEX_MASK = 0x0F
        private const val SHIFT_4 = 4
    }
}
