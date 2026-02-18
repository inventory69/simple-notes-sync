package dev.dettmer.simplenotes.noteimport

import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.models.NoteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-Tests für NotesImportWizard Parser-Logik.
 *
 * Testet alle Parser (Markdown, JSON, PlainText) isoliert.
 * Nutzt interne Hilfsfunktionen direkt statt NotesImportWizard-Instanz
 * (da AndroidViewModel-Kontext in reinen JUnit-Tests nicht verfügbar ist).
 *
 * v1.9.0: Issue #21
 */
class NotesImportWizardTest {

    // ═══════════════════════════════════════════════
    // Hilfsfunktionen (gespiegelt von NotesImportWizard)
    // ═══════════════════════════════════════════════

    private fun extractMarkdownTitle(content: String, fileName: String): String {
        val headingMatch = Regex("^#\\s+(.+)$", RegexOption.MULTILINE).find(content)
        if (headingMatch != null) return headingMatch.groupValues[1].trim()
        return fileName.removeSuffix(".md").replace("-", " ").replace("_", " ")
    }

    private fun extractMarkdownBody(content: String): String {
        val lines = content.lines()
        val firstHeadingIndex = lines.indexOfFirst { it.startsWith("# ") }
        return if (firstHeadingIndex >= 0) {
            lines.drop(firstHeadingIndex + 1)
                .dropWhile { it.isBlank() }
                .joinToString("\n")
                .trim()
        } else {
            content.trim()
        }
    }

    private fun detectFileType(fileName: String): NotesImportWizard.FileType {
        return when {
            fileName.endsWith(".md", ignoreCase = true) -> NotesImportWizard.FileType.MARKDOWN
            fileName.endsWith(".json", ignoreCase = true) -> NotesImportWizard.FileType.JSON
            else -> NotesImportWizard.FileType.PLAINTEXT
        }
    }

    private fun extractTimestampHelper(
        obj: com.google.gson.JsonObject,
        vararg keys: String
    ): Long? {
        for (key in keys) {
            val element = obj.get(key) ?: continue
            try {
                if (element.isJsonPrimitive) {
                    val prim = element.asJsonPrimitive
                    if (prim.isNumber) {
                        val value = prim.asLong
                        return if (value < 1_000_000_000_000L) value * 1000 else value
                    }
                }
            } catch (_: Exception) { continue }
        }
        return null
    }

    // ═══════════════════════════════════════════════
    // File Type Detection
    // ═══════════════════════════════════════════════

    @Test
    fun `detectFileType identifies markdown files`() {
        assertEquals(NotesImportWizard.FileType.MARKDOWN, detectFileType("note.md"))
        assertEquals(NotesImportWizard.FileType.MARKDOWN, detectFileType("My Note.MD"))
        assertEquals(NotesImportWizard.FileType.MARKDOWN, detectFileType("README.md"))
    }

    @Test
    fun `detectFileType identifies json files`() {
        assertEquals(NotesImportWizard.FileType.JSON, detectFileType("note.json"))
        assertEquals(NotesImportWizard.FileType.JSON, detectFileType("backup.JSON"))
    }

    @Test
    fun `detectFileType identifies plaintext files`() {
        assertEquals(NotesImportWizard.FileType.PLAINTEXT, detectFileType("note.txt"))
        assertEquals(NotesImportWizard.FileType.PLAINTEXT, detectFileType("readme.rst"))
        assertEquals(NotesImportWizard.FileType.PLAINTEXT, detectFileType("noextension"))
    }

    // ═══════════════════════════════════════════════
    // Markdown Title Extraction
    // ═══════════════════════════════════════════════

    @Test
    fun `extractMarkdownTitle finds H1 heading`() {
        val content = "# My Note Title\n\nSome content here."
        assertEquals("My Note Title", extractMarkdownTitle(content, "fallback.md"))
    }

    @Test
    fun `extractMarkdownTitle falls back to filename without extension`() {
        val content = "Some content without heading."
        assertEquals("my note", extractMarkdownTitle(content, "my-note.md"))
    }

    @Test
    fun `extractMarkdownTitle replaces dashes and underscores`() {
        val content = "No heading"
        assertEquals("shopping list 2024", extractMarkdownTitle(content, "shopping_list-2024.md"))
    }

    @Test
    fun `extractMarkdownTitle ignores H2 and lower headings`() {
        val content = "## Not H1\n### Also not H1\nContent"
        assertEquals("not h1 heading", extractMarkdownTitle(content, "not-h1-heading.md"))
    }

    @Test
    fun `extractMarkdownTitle handles H1 not on first line`() {
        val content = "Some preamble\n\n# Actual Title\n\nContent"
        assertEquals("Actual Title", extractMarkdownTitle(content, "fallback.md"))
    }

    // ═══════════════════════════════════════════════
    // Markdown Body Extraction
    // ═══════════════════════════════════════════════

    @Test
    fun `extractMarkdownBody removes H1 heading`() {
        val content = "# Title\n\nBody content here.\nMore content."
        assertEquals("Body content here.\nMore content.", extractMarkdownBody(content))
    }

    @Test
    fun `extractMarkdownBody returns full content without heading`() {
        val content = "Just plain content\nwithout any heading."
        assertEquals("Just plain content\nwithout any heading.", extractMarkdownBody(content))
    }

    @Test
    fun `extractMarkdownBody skips blank lines after heading`() {
        val content = "# Title\n\n\n\nBody starts here."
        assertEquals("Body starts here.", extractMarkdownBody(content))
    }

    @Test
    fun `extractMarkdownBody handles empty content after heading`() {
        val content = "# Title Only"
        assertEquals("", extractMarkdownBody(content))
    }

    // ═══════════════════════════════════════════════
    // Checklist Detection
    // ═══════════════════════════════════════════════

    @Test
    fun `checklist regex detects unchecked items`() {
        val regex = Regex("^- \\[([ xX])\\] (.*)$", RegexOption.MULTILINE)
        val content = "- [ ] Buy milk\n- [ ] Buy bread"
        val matches = regex.findAll(content).toList()
        assertEquals(2, matches.size)
        assertEquals(" ", matches[0].groupValues[1])
        assertEquals("Buy milk", matches[0].groupValues[2])
    }

    @Test
    fun `checklist regex detects checked items`() {
        val regex = Regex("^- \\[([ xX])\\] (.*)$", RegexOption.MULTILINE)
        val content = "- [x] Done task\n- [X] Also done"
        val matches = regex.findAll(content).toList()
        assertEquals(2, matches.size)
        assertEquals("x", matches[0].groupValues[1])
        assertEquals("X", matches[1].groupValues[1])
    }

    @Test
    fun `checklist regex detects mixed items`() {
        val regex = Regex("^- \\[([ xX])\\] (.*)$", RegexOption.MULTILINE)
        val content = "# Shopping\n\n- [x] Milk\n- [ ] Bread\n- [X] Eggs"
        val matches = regex.findAll(content).toList()
        assertEquals(3, matches.size)
    }

    @Test
    fun `checklist not detected with fewer than 2 items`() {
        val regex = Regex("^- \\[([ xX])\\] (.*)$", RegexOption.MULTILINE)
        val content = "Some text\n- [x] Only one item\nMore text"
        val matches = regex.findAll(content).toList()
        assertTrue("Should have fewer than 2 matches", matches.size < 2)
    }

    // ═══════════════════════════════════════════════
    // Markdown with Simple Notes Frontmatter
    // ═══════════════════════════════════════════════

    @Test
    fun `fromMarkdown parses Simple Notes frontmatter`() {
        val md = """
---
id: test-uuid-123
created: 2024-01-15T10:30:00Z
updated: 2024-06-20T14:00:00Z
device: android-abc123
type: text
---

# My Test Note

This is the content of my test note.
        """.trimIndent()

        val note = Note.fromMarkdown(md)
        assertNotNull("fromMarkdown should parse valid frontmatter", note)
        assertEquals("test-uuid-123", note!!.id)
        assertEquals("My Test Note", note.title)
        assertTrue(note.content.contains("This is the content"))
        assertEquals(NoteType.TEXT, note.noteType)
    }

    @Test
    fun `fromMarkdown returns null without frontmatter`() {
        val md = "# Just a Title\n\nSome content without YAML frontmatter."
        val note = Note.fromMarkdown(md)
        assertNull("fromMarkdown should return null without frontmatter", note)
    }

    // ═══════════════════════════════════════════════
    // JSON Parsing — Simple Notes Format
    // ═══════════════════════════════════════════════

    @Test
    fun `fromJson parses Simple Notes JSON`() {
        val json = """
        {
            "id": "abc-123",
            "title": "Test Note",
            "content": "Hello World",
            "createdAt": 1700000000000,
            "updatedAt": 1700001000000,
            "deviceId": "android-test",
            "syncStatus": "SYNCED",
            "noteType": "TEXT"
        }
        """.trimIndent()

        val note = Note.fromJson(json)
        assertNotNull("fromJson should parse valid Simple Notes JSON", note)
        assertEquals("abc-123", note!!.id)
        assertEquals("Test Note", note.title)
        assertEquals("Hello World", note.content)
    }

    // ═══════════════════════════════════════════════
    // JSON Parsing — Generisches Format
    // ═══════════════════════════════════════════════

    @Test
    fun `generic JSON with title and content fields`() {
        val json = com.google.gson.JsonParser.parseString("""
            {"title": "Shopping List", "content": "Buy eggs and milk"}
        """).asJsonObject

        val title = listOf("title", "name", "subject")
            .firstNotNullOfOrNull { key -> json.get(key)?.asString?.takeIf { it.isNotBlank() } }
        assertEquals("Shopping List", title)

        val content = listOf("content", "body", "text")
            .firstNotNullOfOrNull { key -> json.get(key)?.asString?.takeIf { it.isNotBlank() } }
        assertEquals("Buy eggs and milk", content)
    }

    @Test
    fun `generic JSON with body field instead of content`() {
        val json = com.google.gson.JsonParser.parseString("""
            {"name": "My Note", "body": "Note body text"}
        """).asJsonObject

        val title = listOf("title", "name", "subject")
            .firstNotNullOfOrNull { key -> json.get(key)?.asString?.takeIf { it.isNotBlank() } }
        assertEquals("My Note", title)

        val body = listOf("content", "body", "text")
            .firstNotNullOfOrNull { key -> json.get(key)?.asString?.takeIf { it.isNotBlank() } }
        assertEquals("Note body text", body)
    }

    @Test
    fun `generic JSON with no recognizable fields returns null`() {
        val json = com.google.gson.JsonParser.parseString("""
            {"foo": "bar", "baz": 42}
        """).asJsonObject

        val title = listOf("title", "name", "subject", "header")
            .firstNotNullOfOrNull { key -> json.get(key)?.asString?.takeIf { it.isNotBlank() } }
        assertNull(title)

        val content = listOf("content", "body", "text", "note", "description")
            .firstNotNullOfOrNull { key -> json.get(key)?.asString?.takeIf { it.isNotBlank() } }
        assertNull(content)
    }

    // ═══════════════════════════════════════════════
    // Timestamp Extraction
    // ═══════════════════════════════════════════════

    @Test
    fun `extractTimestamp handles milliseconds`() {
        val json = com.google.gson.JsonParser.parseString("""{"createdAt": 1700000000000}""").asJsonObject
        assertEquals(1700000000000L, extractTimestampHelper(json, "createdAt"))
    }

    @Test
    fun `extractTimestamp converts seconds to milliseconds`() {
        val json = com.google.gson.JsonParser.parseString("""{"created": 1700000000}""").asJsonObject
        assertEquals(1700000000000L, extractTimestampHelper(json, "created"))
    }

    @Test
    fun `extractTimestamp tries multiple keys`() {
        val json = com.google.gson.JsonParser.parseString("""{"updated_at": 1700001000000}""").asJsonObject
        assertEquals(1700001000000L, extractTimestampHelper(json, "updatedAt", "updated", "updated_at"))
    }

    @Test
    fun `extractTimestamp returns null for missing keys`() {
        val json = com.google.gson.JsonParser.parseString("""{"foo": "bar"}""").asJsonObject
        assertNull(extractTimestampHelper(json, "createdAt", "created"))
    }

    // ═══════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════

    @Test
    fun `empty markdown returns empty body`() {
        assertEquals("", extractMarkdownBody(""))
    }

    @Test
    fun `markdown with only whitespace`() {
        assertEquals("", extractMarkdownBody("   \n  \n   "))
    }

    @Test
    fun `SUPPORTED_EXTENSIONS contains expected types`() {
        assertTrue(NotesImportWizard.SUPPORTED_EXTENSIONS.contains(".md"))
        assertTrue(NotesImportWizard.SUPPORTED_EXTENSIONS.contains(".json"))
        assertTrue(NotesImportWizard.SUPPORTED_EXTENSIONS.contains(".txt"))
        assertEquals(3, NotesImportWizard.SUPPORTED_EXTENSIONS.size)
    }

    @Test
    fun `MAX_FILE_SIZE is 5MB`() {
        assertEquals(5L * 1024 * 1024, NotesImportWizard.MAX_FILE_SIZE)
    }
}
