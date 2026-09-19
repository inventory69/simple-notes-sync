package dev.dettmer.simplenotes.backup

import com.google.gson.Gson
import dev.dettmer.simplenotes.models.ChecklistItem
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Welche Notizen ein Restore aus einem Backup übernimmt.
 *
 * Hintergrund: Bis v2.17.0 lehnte eine einzige unlesbare Notiz das komplette Backup ab
 * (0 von N Notizen zurück), und ein Alt-Backup ohne `noteType` warf beim Prüfen eine NPE.
 */
class BackupRestorableNotesTest {
    private fun note(
        id: String = "id-1",
        title: String = "Titel",
        content: String = "Inhalt",
        type: NoteType = NoteType.TEXT,
        items: List<ChecklistItem>? = null
    ) = Note(
        id = id,
        title = title,
        content = content,
        deviceId = "test-device",
        noteType = type,
        checklistItems = items
    )

    @Test
    fun `note with title and content is restorable`() {
        assertTrue(BackupManager.isRestorable(note()))
    }

    @Test
    fun `note with only a title is restorable`() {
        assertTrue(BackupManager.isRestorable(note(content = "")))
    }

    @Test
    fun `checklist without title but with items is restorable`() {
        val items = listOf(ChecklistItem(text = "Milch", isChecked = false, order = 0))
        assertTrue(BackupManager.isRestorable(note(title = "", content = "", type = NoteType.CHECKLIST, items = items)))
    }

    @Test
    fun `completely empty note is not restorable`() {
        assertFalse(BackupManager.isRestorable(note(title = "", content = "")))
    }

    @Test
    fun `note without id is not restorable`() {
        assertFalse(BackupManager.isRestorable(note(id = "")))
    }

    @Test
    fun `old backup note without noteType does not crash and stays restorable`() {
        // Gson-Reflection umgeht Kotlins Non-Null-Typen: fehlt das Feld im JSON, ist es null.
        // Genau das passiert bei Backups von vor v1.4.0 — vorher NPE, ganzes Backup verworfen.
        val legacyJson = """
            {"id":"old-1","title":"Alte Notiz","content":"Text",
             "createdAt":1600000000000,"updatedAt":1600000000000,"deviceId":"d"}
        """.trimIndent()
        val legacyNote = Gson().fromJson(legacyJson, Note::class.java)

        @Suppress("SENSELESS_COMPARISON")
        val noteTypeIsNull = legacyNote.noteType == null
        assertTrue("Gson should leave the missing field null", noteTypeIsNull)
        assertTrue(BackupManager.isRestorable(legacyNote))
    }
}
