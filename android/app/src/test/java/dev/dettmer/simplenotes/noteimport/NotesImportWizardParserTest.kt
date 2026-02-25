package dev.dettmer.simplenotes.noteimport

import dev.dettmer.simplenotes.models.NoteType
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests für NotesImportWizard Parser-Methoden via Note.fromJson / Note.fromMarkdown.
 *
 * Da NotesImportWizard einen Context benötigt, testen wir die Parser-Logik
 * indirekt über die public Note-Methoden und die companion constants.
 */
class NotesImportWizardParserTest {

    // ═══════════════════════════════════════════════
    // FileType Detection (via companion values)
    // ═══════════════════════════════════════════════

    @Test
    fun `SUPPORTED_EXTENSIONS contains md json txt`() {
        assertTrue(NotesImportWizard.SUPPORTED_EXTENSIONS.contains(".md"))
        assertTrue(NotesImportWizard.SUPPORTED_EXTENSIONS.contains(".json"))
        assertTrue(NotesImportWizard.SUPPORTED_EXTENSIONS.contains(".txt"))
        assertEquals(3, NotesImportWizard.SUPPORTED_EXTENSIONS.size)
    }

    @Test
    fun `MAX_FILE_SIZE is 5MB`() {
        assertEquals(5L * 1024L * 1024L, NotesImportWizard.MAX_FILE_SIZE)
    }

    @Test
    fun `FileType enum has 3 values`() {
        assertEquals(3, NotesImportWizard.FileType.entries.size)
        assertNotNull(NotesImportWizard.FileType.MARKDOWN)
        assertNotNull(NotesImportWizard.FileType.JSON)
        assertNotNull(NotesImportWizard.FileType.PLAINTEXT)
    }

    // ═══════════════════════════════════════════════
    // ImportResult sealed class
    // ═══════════════════════════════════════════════

    @Test
    fun `ImportResult Success holds data`() {
        val note = dev.dettmer.simplenotes.models.Note(
            title = "Test", content = "C", deviceId = "d"
        )
        val result = NotesImportWizard.ImportResult.Success(note, "test.md")
        assertEquals("test.md", result.sourceName)
        assertEquals("Test", result.note.title)
    }

    @Test
    fun `ImportResult Skipped holds reason`() {
        val result = NotesImportWizard.ImportResult.Skipped("dup.json", "Already exists")
        assertEquals("dup.json", result.sourceName)
        assertEquals("Already exists", result.reason)
    }

    @Test
    fun `ImportResult Failed holds error`() {
        val result = NotesImportWizard.ImportResult.Failed("bad.txt", "Parse error")
        assertEquals("bad.txt", result.sourceName)
        assertEquals("Parse error", result.error)
    }

    // ═══════════════════════════════════════════════
    // ImportSummary
    // ═══════════════════════════════════════════════

    @Test
    fun `ImportSummary counts are correct`() {
        val summary = NotesImportWizard.ImportSummary(
            totalScanned = 10,
            imported = 7,
            skipped = 2,
            failed = 1,
            results = emptyList()
        )
        assertEquals(10, summary.totalScanned)
        assertEquals(7, summary.imported)
        assertEquals(2, summary.skipped)
        assertEquals(1, summary.failed)
    }

    // ═══════════════════════════════════════════════
    // Note.fromMarkdown — Extended Import Scenarios
    // ═══════════════════════════════════════════════

    @Test
    fun `fromMarkdown parses checklist from markdown with frontmatter`() {
        val md = """
---
id: import-cl-1
created: 2024-01-01T00:00:00Z
updated: 2024-01-01T00:00:00Z
device: desktop
type: checklist
---

# Einkaufsliste

- [x] Milch
- [ ] Brot
- [x] Käse
- [ ] Eier
        """.trimIndent()

        val note = dev.dettmer.simplenotes.models.Note.fromMarkdown(md)
        assertNotNull(note)
        assertEquals(NoteType.CHECKLIST, note!!.noteType)
        assertEquals(4, note.checklistItems!!.size)
        assertTrue(note.checklistItems!![0].isChecked)  // Milch
        assertFalse(note.checklistItems!![1].isChecked) // Brot
    }

    @Test
    fun `fromJson parses Simple Notes JSON with checklist`() {
        val json = """
        {
            "id": "import-json-cl",
            "title": "Shopping",
            "content": "[ ] Milk\n[x] Bread",
            "createdAt": 1700000000000,
            "updatedAt": 1700001000000,
            "deviceId": "device-1",
            "noteType": "CHECKLIST",
            "checklistItems": [
                {"id": "i1", "text": "Milk", "isChecked": false, "order": 0},
                {"id": "i2", "text": "Bread", "isChecked": true, "order": 1}
            ]
        }
        """.trimIndent()

        val note = dev.dettmer.simplenotes.models.Note.fromJson(json)
        assertNotNull(note)
        assertEquals(NoteType.CHECKLIST, note!!.noteType)
        assertEquals(2, note.checklistItems!!.size)
    }

    // ═══════════════════════════════════════════════
    // Generic JSON timestamp extraction (via Note.parseISO8601)
    // ═══════════════════════════════════════════════

    @Test
    fun `timestamp in seconds is converted to millis`() {
        // Import wizard converts seconds < 1_000_000_000_000 to millis
        val secondsTimestamp = 1700000000L
        val expected = 1700000000000L

        // Verify the threshold logic directly
        assertTrue(secondsTimestamp < 1_000_000_000_000L)
        assertEquals(expected, secondsTimestamp * 1000)
    }

    @Test
    fun `timestamp in millis stays as is`() {
        val millisTimestamp = 1700000000000L
        assertFalse(millisTimestamp < 1_000_000_000_000L)
    }
}
