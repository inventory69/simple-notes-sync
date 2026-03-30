package dev.dettmer.simplenotes.utils

import android.content.SharedPreferences
import dev.dettmer.simplenotes.models.ChecklistItem
import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.storage.NotesStorage
import java.util.UUID

/**
 * v2.2.0: Einmalige Reparatur-Routine für korrupte Checklist-Titel.
 *
 * Bug: Fehlende Leerzeile in toMarkdown() führte dazu, dass beim Markdown-Re-Import
 * das erste Checklist-Item in den Titel rutschte ("Einkaufen- [ ] Bacon").
 * Diese Migration repariert alle betroffenen Notizen beim ersten Start nach dem Update.
 */
object NoteCorruptionRepair {
    private const val TAG = "NoteCorruptionRepair"
    private const val REPAIR_KEY = "corruption_repair_v2_2_0_done"

    fun repairIfNeeded(storage: NotesStorage, prefs: SharedPreferences) {
        if (prefs.getBoolean(REPAIR_KEY, false)) return

        val allNotes = storage.loadAllNotes()
        val checklistPattern = Regex("""[-*]\s*\[([ xX])\]\s+""")
        var repairedCount = 0

        for (note in allNotes) {
            if (note.noteType != NoteType.CHECKLIST || !checklistPattern.containsMatchIn(note.title)) continue

            val match = checklistPattern.find(note.title)!!
            val cleanTitle = note.title.substring(0, match.range.first).trim()
            val rescuedText = note.title.substring(match.range.first)

            // Alle verschluckten Items aus dem Titel extrahieren
            val rescuedItems = Regex("""[-*]\s*\[([ xX])\]\s+(.+?)(?=\s*[-*]\s*\[|${'$'})""")
                .findAll(rescuedText).mapIndexed { index, m ->
                    ChecklistItem(
                        id = UUID.randomUUID().toString(),
                        text = m.groupValues[2].trim(),
                        isChecked = m.groupValues[1].lowercase() == "x",
                        order = index
                    )
                }.toList()

            if (rescuedItems.isNotEmpty()) {
                // Gerettete Items VOR die existierenden Items einfügen und neu nummerieren
                val repairedItems = (rescuedItems + (note.checklistItems ?: emptyList()))
                    .mapIndexed { i, item -> item.copy(order = i) }

                val repairedNote = note.copy(
                    title = cleanTitle,
                    checklistItems = repairedItems,
                    syncStatus = SyncStatus.PENDING // Wird beim nächsten Sync als korrigierte Version hochgeladen
                )

                storage.saveNote(repairedNote)
                repairedCount++
                Logger.w(TAG, "🔧 Repaired note ${note.id}: '${note.title}' → '$cleanTitle' + rescued ${rescuedItems.size} item(s)")
            }
        }

        prefs.edit().putBoolean(REPAIR_KEY, true).apply()
        if (repairedCount > 0) {
            Logger.w(TAG, "🔧 Corruption repair complete: $repairedCount note(s) repaired")
        } else {
            Logger.d(TAG, "✅ No corrupted checklist titles found")
        }
    }
}
