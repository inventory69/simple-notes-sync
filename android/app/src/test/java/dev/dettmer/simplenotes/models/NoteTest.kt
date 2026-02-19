package dev.dettmer.simplenotes.models

import org.junit.Assert.*
import org.junit.Test

/**
 * Vollständige Unit-Tests für die Note-Klasse.
 *
 * Testet:
 * - JSON Serialization/Deserialization (toJson/fromJson)
 * - Markdown Serialization/Deserialization (toMarkdown/fromMarkdown)
 * - ISO8601 Timestamp Parsing (alle Formate)
 * - Checklist Fallback & Recovery
 * - Backward Compatibility
 * - Edge Cases
 */
@Suppress("LargeClass")
class NoteTest {

    // ═══════════════════════════════════════════════
    // Hilfsfunktionen
    // ═══════════════════════════════════════════════

    private fun createTextNote(
        id: String = "test-id",
        title: String = "Test Title",
        content: String = "Test Content",
        deviceId: String = "test-device",
        syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
        createdAt: Long = 1700000000000L,
        updatedAt: Long = 1700001000000L,
        checklistSortOption: String? = null
    ) = Note(
        id = id,
        title = title,
        content = content,
        deviceId = deviceId,
        syncStatus = syncStatus,
        createdAt = createdAt,
        updatedAt = updatedAt,
        checklistSortOption = checklistSortOption
    )

    private fun createChecklistNote(
        id: String = "checklist-id",
        title: String = "Shopping List",
        items: List<ChecklistItem> = listOf(
            ChecklistItem("item-1", "Milk", false, 0),
            ChecklistItem("item-2", "Bread", true, 1),
            ChecklistItem("item-3", "Eggs", false, 2)
        ),
        deviceId: String = "test-device",
        createdAt: Long = 1700000000000L,
        updatedAt: Long = 1700001000000L,
        checklistSortOption: String? = null
    ) = Note(
        id = id,
        title = title,
        content = "",
        deviceId = deviceId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        noteType = NoteType.CHECKLIST,
        checklistItems = items,
        checklistSortOption = checklistSortOption
    )

    // ═══════════════════════════════════════════════
    // Default-Werte
    // ═══════════════════════════════════════════════

    @Test
    fun `default noteType is TEXT`() {
        val note = Note(title = "Test", content = "Content", deviceId = "dev")
        assertEquals(NoteType.TEXT, note.noteType)
    }

    @Test
    fun `default syncStatus is LOCAL_ONLY`() {
        val note = Note(title = "Test", content = "Content", deviceId = "dev")
        assertEquals(SyncStatus.LOCAL_ONLY, note.syncStatus)
    }

    @Test
    fun `default checklistItems is null`() {
        val note = Note(title = "Test", content = "Content", deviceId = "dev")
        assertNull(note.checklistItems)
    }

    @Test
    fun `default checklistSortOption is null`() {
        val note = Note(title = "Test", content = "Content", deviceId = "dev")
        assertNull(note.checklistSortOption)
    }

    @Test
    fun `id is auto-generated UUID if not provided`() {
        val note1 = Note(title = "Test", content = "Content", deviceId = "dev")
        val note2 = Note(title = "Test", content = "Content", deviceId = "dev")
        assertNotEquals(note1.id, note2.id)
        assertTrue(note1.id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
    }

    // ═══════════════════════════════════════════════
    // JSON Serialization — toJson()
    // ═══════════════════════════════════════════════

    @Test
    fun `toJson contains all required fields`() {
        val note = createTextNote()
        val json = note.toJson()

        assertTrue(json.contains("\"id\""))
        assertTrue(json.contains("\"title\""))
        assertTrue(json.contains("\"content\""))
        assertTrue(json.contains("\"createdAt\""))
        assertTrue(json.contains("\"updatedAt\""))
        assertTrue(json.contains("\"deviceId\""))
        assertTrue(json.contains("\"syncStatus\""))
        assertTrue(json.contains("\"noteType\""))
    }

    @Test
    fun `toJson preserves exact values`() {
        val note = createTextNote(
            id = "exact-id-123",
            title = "Exact Title",
            content = "Exact Content"
        )
        val json = note.toJson()

        assertTrue(json.contains("exact-id-123"))
        assertTrue(json.contains("Exact Title"))
        assertTrue(json.contains("Exact Content"))
    }

    @Test
    fun `toJson generates checklist fallback content`() {
        val note = createChecklistNote()
        val json = note.toJson()

        // Fallback content should contain GitHub-style checkboxes
        assertTrue("JSON should contain fallback content with [ ]", json.contains("[ ] Milk"))
        assertTrue("JSON should contain fallback content with [x]", json.contains("[x] Bread"))
    }

    @Test
    fun `toJson text note does not modify content`() {
        val note = createTextNote(content = "Original Content")
        val json = note.toJson()
        assertTrue(json.contains("Original Content"))
    }

    @Test
    fun `toJson handles special characters`() {
        val note = createTextNote(
            title = "Spécial: äöü 🎉",
            content = "Line1\nLine2\tTab"
        )
        val json = note.toJson()
        assertNotNull(json)
        // Verify roundtrip
        val restored = Note.fromJson(json)
        assertNotNull(restored)
        assertEquals("Spécial: äöü 🎉", restored!!.title)
    }

    @Test
    fun `toJson includes checklistSortOption when set`() {
        val note = createChecklistNote(checklistSortOption = "UNCHECKED_FIRST")
        val json = note.toJson()
        assertTrue(json.contains("UNCHECKED_FIRST"))
    }

    // ═══════════════════════════════════════════════
    // JSON Deserialization — fromJson()
    // ═══════════════════════════════════════════════

    @Test
    fun `fromJson roundtrip preserves text note`() {
        val original = createTextNote()
        val json = original.toJson()
        val restored = Note.fromJson(json)

        assertNotNull(restored)
        assertEquals(original.id, restored!!.id)
        assertEquals(original.title, restored.title)
        assertEquals(original.content, restored.content)
        assertEquals(original.deviceId, restored.deviceId)
        assertEquals(original.createdAt, restored.createdAt)
        assertEquals(original.updatedAt, restored.updatedAt)
        assertEquals(original.noteType, restored.noteType)
    }

    @Test
    fun `fromJson roundtrip preserves checklist note`() {
        val original = createChecklistNote()
        val json = original.toJson()
        val restored = Note.fromJson(json)

        assertNotNull(restored)
        assertEquals(NoteType.CHECKLIST, restored!!.noteType)
        assertNotNull(restored.checklistItems)
        assertEquals(3, restored.checklistItems!!.size)
        assertEquals("Milk", restored.checklistItems!![0].text)
        assertFalse(restored.checklistItems!![0].isChecked)
        assertEquals("Bread", restored.checklistItems!![1].text)
        assertTrue(restored.checklistItems!![1].isChecked)
    }

    @Test
    fun `fromJson roundtrip preserves checklistSortOption`() {
        val original = createChecklistNote(checklistSortOption = "ALPHABETICAL_ASC")
        val json = original.toJson()
        val restored = Note.fromJson(json)

        assertNotNull(restored)
        assertEquals("ALPHABETICAL_ASC", restored!!.checklistSortOption)
    }

    @Test
    fun `fromJson with missing noteType defaults to TEXT`() {
        val json = """
            {
                "id": "legacy-note",
                "title": "Old Note",
                "content": "From v1.3.x",
                "createdAt": 1700000000000,
                "updatedAt": 1700001000000,
                "deviceId": "old-device"
            }
        """.trimIndent()

        val note = Note.fromJson(json)
        assertNotNull(note)
        assertEquals(NoteType.TEXT, note!!.noteType)
    }

    @Test
    fun `fromJson with missing syncStatus defaults to LOCAL_ONLY`() {
        val json = """
            {
                "id": "no-status",
                "title": "No Status",
                "content": "Content",
                "createdAt": 1700000000000,
                "updatedAt": 1700001000000,
                "deviceId": "device"
            }
        """.trimIndent()

        val note = Note.fromJson(json)
        assertNotNull(note)
        assertEquals(SyncStatus.LOCAL_ONLY, note!!.syncStatus)
    }

    @Test
    fun `fromJson with invalid noteType defaults to TEXT`() {
        val json = """
            {
                "id": "bad-type",
                "title": "Bad Type",
                "content": "Content",
                "createdAt": 1700000000000,
                "updatedAt": 1700001000000,
                "deviceId": "device",
                "noteType": "UNKNOWN_TYPE"
            }
        """.trimIndent()

        val note = Note.fromJson(json)
        assertNotNull(note)
        assertEquals(NoteType.TEXT, note!!.noteType)
    }

    @Test
    fun `fromJson with null noteType defaults to TEXT`() {
        val json = """
            {
                "id": "null-type",
                "title": "Null Type",
                "content": "Content",
                "createdAt": 1700000000000,
                "updatedAt": 1700001000000,
                "deviceId": "device",
                "noteType": null
            }
        """.trimIndent()

        val note = Note.fromJson(json)
        assertNotNull(note)
        assertEquals(NoteType.TEXT, note!!.noteType)
    }

    @Test
    fun `fromJson recovers checklist items from content fallback`() {
        // v1.4.1: Recovery mode — Checklist without checklistItems but with fallback content
        val json = """
            {
                "id": "recovery-note",
                "title": "Recovery",
                "content": "[ ] Item A\n[x] Item B\n[ ] Item C",
                "createdAt": 1700000000000,
                "updatedAt": 1700001000000,
                "deviceId": "device",
                "noteType": "CHECKLIST"
            }
        """.trimIndent()

        val note = Note.fromJson(json)
        assertNotNull(note)
        assertEquals(NoteType.CHECKLIST, note!!.noteType)
        assertNotNull(note.checklistItems)
        assertEquals(3, note.checklistItems!!.size)
        assertEquals("Item A", note.checklistItems!![0].text)
        assertFalse(note.checklistItems!![0].isChecked)
        assertEquals("Item B", note.checklistItems!![1].text)
        assertTrue(note.checklistItems!![1].isChecked)
    }

    @Test
    fun `fromJson with invalid JSON returns null`() {
        assertNull(Note.fromJson("not valid json"))
        assertNull(Note.fromJson("{"))
        assertNull(Note.fromJson(""))
    }

    @Test
    fun `fromJson with empty object does not crash`() {
        // Should not throw; may return null or a note with defaults
        Note.fromJson("{}")
    }

    @Test
    fun `fromJson with null checklistSortOption returns null sortOption`() {
        val json = """
            {
                "id": "no-sort",
                "title": "No Sort",
                "content": "",
                "createdAt": 1700000000000,
                "updatedAt": 1700001000000,
                "deviceId": "device",
                "noteType": "CHECKLIST",
                "checklistItems": [{"id":"i1","text":"Item","isChecked":false,"order":0}],
                "checklistSortOption": null
            }
        """.trimIndent()

        val note = Note.fromJson(json)
        assertNotNull(note)
        assertNull(note!!.checklistSortOption)
    }

    // ═══════════════════════════════════════════════
    // Markdown Serialization — toMarkdown()
    // ═══════════════════════════════════════════════

    @Test
    fun `toMarkdown contains YAML frontmatter`() {
        val note = createTextNote()
        val md = note.toMarkdown()

        assertTrue(md.startsWith("---"))
        assertTrue(md.contains("id: test-id"))
        assertTrue(md.contains("device: test-device"))
        assertTrue(md.contains("type: text"))
        assertTrue(md.contains("# Test Title"))
    }

    @Test
    fun `toMarkdown text note contains content after title`() {
        val note = createTextNote(content = "My content here")
        val md = note.toMarkdown()

        assertTrue(md.contains("# Test Title"))
        assertTrue(md.contains("My content here"))
    }

    @Test
    fun `toMarkdown checklist note contains markdown checkboxes`() {
        val note = createChecklistNote()
        val md = note.toMarkdown()

        assertTrue(md.contains("type: checklist"))
        assertTrue(md.contains("- [ ] Milk"))
        assertTrue(md.contains("- [x] Bread"))
        assertTrue(md.contains("- [ ] Eggs"))
    }

    @Test
    fun `toMarkdown checklist items are sorted by order`() {
        val items = listOf(
            ChecklistItem("id-3", "Third", false, 2),
            ChecklistItem("id-1", "First", false, 0),
            ChecklistItem("id-2", "Second", true, 1)
        )
        val note = createChecklistNote(items = items)
        val md = note.toMarkdown()

        val firstIdx = md.indexOf("First")
        val secondIdx = md.indexOf("Second")
        val thirdIdx = md.indexOf("Third")

        assertTrue(firstIdx < secondIdx)
        assertTrue(secondIdx < thirdIdx)
    }

    @Test
    fun `toMarkdown includes sort option when set`() {
        val note = createChecklistNote(checklistSortOption = "UNCHECKED_FIRST")
        val md = note.toMarkdown()

        assertTrue(md.contains("sort: UNCHECKED_FIRST"))
    }

    @Test
    fun `toMarkdown does not include sort option when null`() {
        val note = createChecklistNote(checklistSortOption = null)
        val md = note.toMarkdown()

        assertFalse(md.contains("sort:"))
    }

    @Test
    fun `toMarkdown does not include sort for text notes`() {
        val note = createTextNote(checklistSortOption = "MANUAL")
        val md = note.toMarkdown()

        assertFalse(md.contains("sort:"))
    }

    @Test
    fun `toMarkdown timestamps are ISO8601 UTC`() {
        // 1700000000000 = 2023-11-14T22:13:20Z
        val note = createTextNote(createdAt = 1700000000000L, updatedAt = 1700000000000L)
        val md = note.toMarkdown()

        assertTrue(md.contains("created: 2023-11-14T22:13:20Z"))
        assertTrue(md.contains("updated: 2023-11-14T22:13:20Z"))
    }

    // ═══════════════════════════════════════════════
    // Markdown Deserialization — fromMarkdown()
    // ═══════════════════════════════════════════════

    @Test
    fun `fromMarkdown parses text note correctly`() {
        val md = """
---
id: md-note-1
created: 2024-01-15T10:00:00Z
updated: 2024-06-20T14:00:00Z
device: android-xyz
type: text
---

# My Test Note

This is the content.
Second line.
        """.trimIndent()

        val note = Note.fromMarkdown(md)
        assertNotNull(note)
        assertEquals("md-note-1", note!!.id)
        assertEquals("My Test Note", note.title)
        assertTrue(note.content.contains("This is the content."))
        assertTrue(note.content.contains("Second line."))
        assertEquals("android-xyz", note.deviceId)
        assertEquals(NoteType.TEXT, note.noteType)
        assertEquals(SyncStatus.SYNCED, note.syncStatus)
    }

    @Test
    fun `fromMarkdown parses checklist note correctly`() {
        val md = """
---
id: md-checklist-1
created: 2024-01-15T10:00:00Z
updated: 2024-06-20T14:00:00Z
device: android-xyz
type: checklist
---

# Shopping

- [x] Milk
- [ ] Bread
- [x] Eggs
        """.trimIndent()

        val note = Note.fromMarkdown(md)
        assertNotNull(note)
        assertEquals(NoteType.CHECKLIST, note!!.noteType)
        assertNotNull(note.checklistItems)
        assertEquals(3, note.checklistItems!!.size)

        assertTrue(note.checklistItems!![0].isChecked)
        assertEquals("Milk", note.checklistItems!![0].text)
        assertFalse(note.checklistItems!![1].isChecked)
        assertEquals("Bread", note.checklistItems!![1].text)
    }

    @Test
    fun `fromMarkdown reads sort option from frontmatter`() {
        val md = """
---
id: sort-note
created: 2024-01-15T10:00:00Z
updated: 2024-06-20T14:00:00Z
device: android-xyz
type: checklist
sort: CHECKED_FIRST
---

# Sorted List

- [ ] Item A
- [x] Item B
        """.trimIndent()

        val note = Note.fromMarkdown(md)
        assertNotNull(note)
        assertEquals("CHECKED_FIRST", note!!.checklistSortOption)
    }

    @Test
    fun `fromMarkdown without frontmatter returns null`() {
        val md = "# Just A Title\n\nSome content"
        assertNull(Note.fromMarkdown(md))
    }

    @Test
    fun `fromMarkdown with malformed frontmatter returns null`() {
        val md = """
---
broken yaml without closing
# Title
Content
        """.trimIndent()
        assertNull(Note.fromMarkdown(md))
    }

    @Test
    fun `fromMarkdown missing type defaults to TEXT`() {
        val md = """
---
id: no-type
created: 2024-01-15T10:00:00Z
updated: 2024-06-20T14:00:00Z
device: dev
---

# No Type

Content here.
        """.trimIndent()

        val note = Note.fromMarkdown(md)
        assertNotNull(note)
        assertEquals(NoteType.TEXT, note!!.noteType)
    }

    @Test
    fun `fromMarkdown missing id generates UUID`() {
        val md = """
---
created: 2024-01-15T10:00:00Z
updated: 2024-06-20T14:00:00Z
device: dev
type: text
---

# No ID

Content.
        """.trimIndent()

        val note = Note.fromMarkdown(md)
        assertNotNull(note)
        assertTrue(note!!.id.isNotBlank())
    }

    @Test
    fun `fromMarkdown missing device defaults to desktop`() {
        val md = """
---
id: test-id
created: 2024-01-15T10:00:00Z
updated: 2024-06-20T14:00:00Z
type: text
---

# Title

Content.
        """.trimIndent()

        val note = Note.fromMarkdown(md)
        assertNotNull(note)
        assertEquals("desktop", note!!.deviceId)
    }

    @Test
    fun `fromMarkdown empty checklist returns null checklistItems`() {
        val md = """
---
id: empty-cl
created: 2024-01-15T10:00:00Z
updated: 2024-06-20T14:00:00Z
device: dev
type: checklist
---

# Empty Checklist

No checkbox items here, just text.
        """.trimIndent()

        val note = Note.fromMarkdown(md)
        assertNotNull(note)
        assertEquals(NoteType.CHECKLIST, note!!.noteType)
        assertNull(note.checklistItems) // ifEmpty { null }
    }

    @Test
    fun `fromMarkdown roundtrip text note`() {
        val original = createTextNote(
            content = "Line 1\nLine 2\nLine 3"
        )
        val md = original.toMarkdown()
        val restored = Note.fromMarkdown(md)

        assertNotNull(restored)
        assertEquals(original.id, restored!!.id)
        assertEquals(original.title, restored.title)
        assertTrue(restored.content.contains("Line 1"))
        assertTrue(restored.content.contains("Line 3"))
    }

    @Test
    fun `fromMarkdown roundtrip checklist note preserves items`() {
        val original = createChecklistNote()
        val md = original.toMarkdown()
        val restored = Note.fromMarkdown(md)

        assertNotNull(restored)
        assertEquals(NoteType.CHECKLIST, restored!!.noteType)
        assertEquals(original.checklistItems!!.size, restored.checklistItems!!.size)
    }

    @Test
    fun `fromMarkdown with serverModifiedTime uses YAML timestamp over server time`() {
        val md = """
---
id: yaml-time-test
created: 2024-01-15T10:00:00Z
updated: 2024-06-20T14:00:00Z
device: dev
type: text
---

# YAML Time

Content.
        """.trimIndent()

        val serverMtime = System.currentTimeMillis()  // "now"
        val note = Note.fromMarkdown(md, serverModifiedTime = serverMtime)

        assertNotNull(note)
        // YAML timestamp should be used if valid, not server mtime
        // 2024-06-20T14:00:00Z != "now"
        assertTrue(note!!.updatedAt < serverMtime)
    }

    // ═══════════════════════════════════════════════
    // ISO8601 Parsing — parseISO8601()
    // ═══════════════════════════════════════════════

    @Test
    fun `parseISO8601 parses UTC with Z`() {
        val result = Note.parseISO8601("2024-06-20T14:00:00Z")
        // Verify it's a valid timestamp (not fallback to currentTimeMillis)
        assertTrue(result > 0)
        assertTrue(result < System.currentTimeMillis())
    }

    @Test
    fun `parseISO8601 parses with timezone offset XXX`() {
        val result = Note.parseISO8601("2024-06-20T15:00:00+01:00")
        assertTrue(result > 0)
        assertTrue(result < System.currentTimeMillis())
    }

    @Test
    fun `parseISO8601 parses milliseconds with Z`() {
        val result = Note.parseISO8601("2024-06-20T14:00:00.123Z")
        assertTrue(result > 0)
    }

    @Test
    fun `parseISO8601 parses with space instead of T`() {
        val result = Note.parseISO8601("2024-06-20 14:00:00")
        assertTrue(result > 0)
        // Should be treated as UTC
    }

    @Test
    fun `parseISO8601 UTC and offset+0 produce same timestamp`() {
        val utcResult = Note.parseISO8601("2024-06-20T14:00:00Z")
        val offsetResult = Note.parseISO8601("2024-06-20T14:00:00+00:00")
        // Both should represent the same moment in time
        assertEquals(utcResult, offsetResult)
    }

    @Test
    fun `parseISO8601 CET is 1h behind UTC`() {
        val utcResult = Note.parseISO8601("2024-06-20T14:00:00Z")
        val cetResult = Note.parseISO8601("2024-06-20T15:00:00+01:00")
        // Both represent the same moment in UTC
        assertEquals(utcResult, cetResult)
    }

    @Test
    fun `parseISO8601 blank string returns current time`() {
        val before = System.currentTimeMillis()
        val result = Note.parseISO8601("")
        val after = System.currentTimeMillis()

        assertTrue(result >= before)
        assertTrue(result <= after)
    }

    @Test
    fun `parseISO8601 whitespace-only returns current time`() {
        val before = System.currentTimeMillis()
        val result = Note.parseISO8601("   ")
        val after = System.currentTimeMillis()

        assertTrue(result >= before)
        assertTrue(result <= after)
    }

    @Test
    fun `parseISO8601 unparseable string returns current time`() {
        val before = System.currentTimeMillis()
        val result = Note.parseISO8601("not-a-date")
        val after = System.currentTimeMillis()

        assertTrue(result >= before)
        assertTrue(result <= after)
    }

    @Test
    fun `parseISO8601 without timezone treated as UTC`() {
        val withZ = Note.parseISO8601("2024-06-20T14:00:00Z")
        val withoutTZ = Note.parseISO8601("2024-06-20T14:00:00")

        // Both should be the same (both treated as UTC)
        assertEquals(withZ, withoutTZ)
    }

    // ═══════════════════════════════════════════════
    // NoteSize / getSize()
    // ═══════════════════════════════════════════════

    @Test
    fun `getSize text note empty content is SMALL`() {
        val note = createTextNote(content = "")
        assertEquals(NoteSize.SMALL, note.getSize())
    }

    // ═══════════════════════════════════════════════
    // escapeJson Extension
    // ═══════════════════════════════════════════════

    @Test
    fun `escapeJson escapes backslash`() {
        assertEquals("\\\\", "\\".escapeJson())
    }

    @Test
    fun `escapeJson escapes quotes`() {
        assertEquals("\\\"", "\"".escapeJson())
    }

    @Test
    fun `escapeJson escapes newline and tab`() {
        assertEquals("\\n", "\n".escapeJson())
        assertEquals("\\r", "\r".escapeJson())
        assertEquals("\\t", "\t".escapeJson())
    }

    @Test
    fun `escapeJson leaves normal text unchanged`() {
        assertEquals("Hello World", "Hello World".escapeJson())
    }

    @Test
    fun `escapeJson handles combined special characters`() {
        val input = "Line1\nLine2\t\"quoted\""
        val expected = "Line1\\nLine2\\t\\\"quoted\\\""
        assertEquals(expected, input.escapeJson())
    }
}
