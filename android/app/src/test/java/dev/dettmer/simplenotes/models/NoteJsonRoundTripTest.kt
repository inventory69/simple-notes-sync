package dev.dettmer.simplenotes.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.5.0 — Round-Trip-Tests für die vier neuen Note-Felder
 * (`importedAt`, `labels`, `color`, `isPinned`) sowohl für JSON als auch Markdown.
 *
 * Test-IDs (siehe `google-keep-import-analysis.md` §5.1):
 * - #43  serialize_includesImportedAtAndLabels
 * - #43b serialize_includesColorAndIsPinned
 * - #44  deserialize_legacyJsonWithoutNewFields_returnsNullDefaults
 * - #44b deserialize_legacyJsonWithoutColorOrPinned_returnsNullDefaults
 *
 * Plus: Markdown-Round-Trip-Tests, da Commit #1 sowohl `toJson`/`fromJson` als auch
 * `toMarkdown`/`fromMarkdown` erweitert (siehe Analyseplan §2.4.1 Bullet 2).
 */
class NoteJsonRoundTripTest {

    private fun baseNote(
        importedAt: Long? = null,
        labels: List<String>? = null,
        color: String? = null,
        isPinned: Boolean? = null
    ): Note = Note(
        id = "n-1",
        title = "Title",
        content = "Body",
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_001_000L,
        deviceId = "dev-1",
        syncStatus = SyncStatus.LOCAL_ONLY,
        importedAt = importedAt,
        labels = labels,
        color = color,
        isPinned = isPinned
    )

    // ───── #43: JSON serialize/deserialize includes importedAt + labels ─────
    @Test
    fun `serialize_includesImportedAtAndLabels`() {
        val note = baseNote(
            importedAt = 1_710_000_000_000L,
            labels = listOf("Inbox", "Work")
        )
        val json = note.toJson()

        assertTrue("JSON must contain importedAt", json.contains("\"importedAt\""))
        assertTrue("JSON must contain labels", json.contains("\"labels\""))
        assertTrue(json.contains("1710000000000"))
        assertTrue(json.contains("Inbox"))
        assertTrue(json.contains("Work"))

        val round = Note.fromJson(json)
        assertNotNull(round)
        assertEquals(1_710_000_000_000L, round!!.importedAt)
        assertEquals(listOf("Inbox", "Work"), round.labels)
    }

    // ───── #43b: JSON serialize/deserialize includes color + isPinned ─────
    @Test
    fun `serialize_includesColorAndIsPinned`() {
        val note = baseNote(color = "#FFAABB", isPinned = true)
        val json = note.toJson()

        assertTrue(json.contains("\"color\""))
        assertTrue(json.contains("\"isPinned\""))
        assertTrue(json.contains("#FFAABB"))
        assertTrue(json.contains("\"isPinned\": true"))

        val round = Note.fromJson(json)
        assertNotNull(round)
        assertEquals("#FFAABB", round!!.color)
        assertEquals(true, round.isPinned)
    }

    // ───── #44: legacy JSON without new fields → null defaults ─────
    @Test
    fun `deserialize_legacyJsonWithoutNewFields_returnsNullDefaults`() {
        // Exakt das Format einer pre-v2.5.0-Notiz (ohne die vier neuen Felder).
        val legacyJson = """
            {
              "id": "legacy-1",
              "title": "Legacy",
              "content": "Old content",
              "createdAt": 1700000000000,
              "updatedAt": 1700000000000,
              "deviceId": "old-dev",
              "syncStatus": "SYNCED",
              "noteType": "TEXT"
            }
        """.trimIndent()

        val note = Note.fromJson(legacyJson)
        assertNotNull("Legacy JSON must parse", note)
        assertNull("importedAt defaults to null", note!!.importedAt)
        assertNull("labels defaults to null", note.labels)
        assertEquals("Legacy", note.title)
    }

    // ───── #44b: legacy JSON without color/pinned → null defaults ─────
    @Test
    fun `deserialize_legacyJsonWithoutColorOrPinned_returnsNullDefaults`() {
        // Eine Notiz, die in v2.5.0 mit labels+importedAt geschrieben wurde,
        // aber ohne color/pinned (z.B. nicht aus Keep importiert).
        val partialJson = """
            {
              "id": "partial-1",
              "title": "Partial",
              "content": "Body",
              "createdAt": 1700000000000,
              "updatedAt": 1700000000000,
              "deviceId": "dev",
              "syncStatus": "LOCAL_ONLY",
              "noteType": "TEXT",
              "importedAt": 1710000000000,
              "labels": ["A"]
            }
        """.trimIndent()

        val note = Note.fromJson(partialJson)
        assertNotNull(note)
        assertNull("color defaults to null", note!!.color)
        assertNull("isPinned defaults to null", note.isPinned)
        assertEquals(1_710_000_000_000L, note.importedAt)
        assertEquals(listOf("A"), note.labels)
    }

    // ───── Markdown-Round-Trip: alle vier Felder ─────
    @Test
    fun `markdownRoundTrip_preservesAllFourNewFields`() {
        val note = baseNote(
            importedAt = 1_715_000_000_000L,
            labels = listOf("Tag1", "Tag mit Space"),
            color = "#11AA22",
            isPinned = false
        )
        val md = note.toMarkdown()

        assertTrue("YAML must contain imported", md.contains("imported: 1715000000000"))
        assertTrue("YAML must contain labels", md.contains("labels: [Tag1, Tag mit Space]"))
        assertTrue("YAML must contain color in quotes", md.contains("color: \"#11AA22\""))
        assertTrue("YAML must contain pinned", md.contains("pinned: false"))

        val round = Note.fromMarkdown(md)
        assertNotNull(round)
        assertEquals(1_715_000_000_000L, round!!.importedAt)
        assertEquals(listOf("Tag1", "Tag mit Space"), round.labels)
        assertEquals("#11AA22", round.color)
        assertEquals(false, round.isPinned)
    }

    // ───── Markdown-Backward-Compat: alte MD ohne neue Felder ─────
    @Test
    fun `markdown_legacyWithoutNewFields_returnsNullDefaults`() {
        val legacyMd = """
            ---
            id: l-1
            created: 2024-12-21T18:00:00Z
            updated: 2024-12-21T18:00:00Z
            device: old
            type: text
            ---

            # Legacy Title

            Some body
        """.trimIndent()

        val note = Note.fromMarkdown(legacyMd)
        assertNotNull(note)
        assertNull(note!!.importedAt)
        assertNull(note.labels)
        assertNull(note.color)
        assertNull(note.isPinned)
        assertEquals("Legacy Title", note.title)
    }

    // ───── Markdown: leere labels-Liste wird NICHT geschrieben ─────
    @Test
    fun `markdown_emptyLabelsList_doesNotEmitYamlField`() {
        val note = baseNote(labels = emptyList())
        val md = note.toMarkdown()
        assertTrue("Empty labels must not emit YAML key", !md.contains("labels:"))
    }

    // ───── v2.7.0 (Folders): JSON round-trip für folderName ─────
    @Test fun `json_roundTrip_preservesFolderName`() {
        val note = baseNote().copy(folderName = "Rezepte")
        val round = Note.fromJson(note.toJson())
        assertEquals("Rezepte", round!!.folderName)
    }

    @Test fun `json_legacyWithoutFolder_isNullRoot`() {
        val legacy = """
            {"id":"l","title":"T","content":"B","createdAt":1,"updatedAt":1,
             "deviceId":"d","syncStatus":"SYNCED","noteType":"TEXT"}
        """.trimIndent()
        assertNull(Note.fromJson(legacy)!!.folderName)
    }

    @Test fun `markdown_roundTrip_preservesFolder`() {
        val note = baseNote().copy(folderName = "Reise 2024")
        val md = note.toMarkdown()
        assertTrue(md.contains("folder: \"Reise 2024\""))
        assertEquals("Reise 2024", Note.fromMarkdown(md)!!.folderName)
    }

    // ───── v2.9.0 (Trash): trashedAt überlebt JSON-Round-Trip ─────
    @Test fun `json_roundTrip_preservesTrashedAt`() {
        val note = baseNote().copy(trashedAt = 1_720_000_000_000L)
        val json = note.toJson()
        assertTrue("JSON must contain trashedAt", json.contains("\"trashedAt\""))
        assertTrue(json.contains("1720000000000"))
        val round = Note.fromJson(json)
        assertEquals(1_720_000_000_000L, round!!.trashedAt)
        assertTrue("isTrashed must be true", round.isTrashed)
    }

    // ───── v2.9.0 (Trash): JSON ohne Feld → null (= aktive Notiz) ─────
    @Test fun `json_legacyWithoutTrashedAt_isNullActive`() {
        val legacy = """
            {"id":"l","title":"T","content":"B","createdAt":1,"updatedAt":1,
             "deviceId":"d","syncStatus":"SYNCED","noteType":"TEXT"}
        """.trimIndent()
        val note = Note.fromJson(legacy)
        assertNull("trashedAt defaults to null", note!!.trashedAt)
        assertTrue("isTrashed must be false", !note.isTrashed)
    }

    // ───── v2.9.0 (Trash): un-getrashte Notiz serialisiert OHNE Key (LWW un-trash) ─────
    @Test fun `json_activeNote_doesNotEmitTrashedAtKey`() {
        val note = baseNote() // trashedAt == null
        val json = note.toJson()
        assertTrue("Active note must not emit trashedAt key", !json.contains("\"trashedAt\""))
    }

    // ───── v2.9.0 (Trash): trashedAt NICHT in Markdown ─────
    @Test fun `markdown_doesNotEmitTrashedAt`() {
        val note = baseNote().copy(trashedAt = 1_720_000_000_000L)
        val md = note.toMarkdown()
        assertTrue("Markdown must not contain trashed metadata", !md.contains("trashed"))
    }

    // ───── Markdown: invalides 'pinned' → null + Logger.w ─────
    @Test
    fun `markdown_invalidPinnedValue_returnsNull`() {
        val md = """
            ---
            id: x-1
            created: 2024-12-21T18:00:00Z
            updated: 2024-12-21T18:00:00Z
            device: d
            type: text
            pinned: maybe
            ---

            # T

            B
        """.trimIndent()
        val note = Note.fromMarkdown(md)
        assertNotNull(note)
        assertNull(note!!.isPinned)
    }
}
