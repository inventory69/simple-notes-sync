package dev.dettmer.simplenotes.models

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * v2.5.0 — Round-Trip- und Backward-Compat-Tests für `ChecklistItem.indentationLevel`.
 *
 * Test-IDs (siehe `google-keep-import-analysis.md` §5.1):
 * - #45 indentationLevelDefaultsZeroForLegacy
 *
 * Plus zwei Defensiv-Tests:
 * - serialize_roundTrip_preservesIndentationLevel
 * - createEmpty_defaultIndentation_isZero
 */
class ChecklistItemRoundTripTest {

    private val gson = Gson()

    // ───── #45: legacy JSON ohne indentationLevel → 0 ─────
    @Test
    fun `indentationLevelDefaultsZeroForLegacy`() {
        // Genau das Format einer pre-v2.5.0-ChecklistItem-Serialisierung.
        val legacyJson = """
            {
              "id": "legacy-item-1",
              "text": "Milch",
              "isChecked": false,
              "order": 0,
              "originalOrder": 0,
              "createdAt": 1700000000000
            }
        """.trimIndent()

        val item = gson.fromJson(legacyJson, ChecklistItem::class.java)
        assertNotNull(item)
        assertEquals("Legacy item ohne indentationLevel muss 0 lesen", 0, item.indentationLevel)
        assertEquals("Milch", item.text)
        assertEquals(false, item.isChecked)
    }

    // ───── Round-Trip: indentationLevel wird korrekt serialisiert/deserialisiert ─────
    @Test
    fun `serialize_roundTrip_preservesIndentationLevel`() {
        val item = ChecklistItem(
            id = "rt-1",
            text = "Sub-Item",
            isChecked = true,
            order = 2,
            originalOrder = 2,
            createdAt = 1_700_000_000_000L,
            indentationLevel = 2
        )
        val json = gson.toJson(item)
        assertEquals(true, json.contains("\"indentationLevel\""))
        assertEquals(true, json.contains("2"))

        val round = gson.fromJson(json, ChecklistItem::class.java)
        assertEquals(2, round.indentationLevel)
        assertEquals("Sub-Item", round.text)
        assertEquals(true, round.isChecked)
    }

    // ───── createEmpty: Default-Indentation = 0 ─────
    @Test
    fun `createEmpty_defaultIndentation_isZero`() {
        val item = ChecklistItem.createEmpty(order = 0)
        assertEquals(0, item.indentationLevel)
    }

    // ───── createEmpty: explizite Indentation übernommen ─────
    @Test
    fun `createEmpty_explicitIndentation_isPropagated`() {
        val item = ChecklistItem.createEmpty(order = 1, indentationLevel = 3)
        assertEquals(3, item.indentationLevel)
        assertEquals(1, item.order)
    }

    // ───── Konstruktor-Default: Field nullt nicht versehentlich ─────
    @Test
    fun `constructor_defaultIndentation_isZero`() {
        val item = ChecklistItem(text = "X")
        assertEquals(0, item.indentationLevel)
    }

    // ───── Backward-Compat: ChecklistItem in einer Note bleibt ladbar ─────
    @Test
    fun `noteWithLegacyChecklist_loadsWithoutLoss`() {
        val legacyNoteJson = """
            {
              "id": "n-legacy",
              "title": "List",
              "content": "",
              "createdAt": 1700000000000,
              "updatedAt": 1700000000000,
              "deviceId": "d",
              "syncStatus": "LOCAL_ONLY",
              "noteType": "CHECKLIST",
              "checklistItems": [
                {"id":"i1","text":"A","isChecked":false,"order":0,"originalOrder":0,"createdAt":1700000000000},
                {"id":"i2","text":"B","isChecked":true,"order":1,"originalOrder":1,"createdAt":1700000000001}
              ]
            }
        """.trimIndent()
        val note = Note.fromJson(legacyNoteJson)
        assertNotNull(note)
        assertEquals(2, note!!.checklistItems?.size)
        assertEquals(0, note.checklistItems!![0].indentationLevel)
        assertEquals(0, note.checklistItems[1].indentationLevel)
        // Ebenfalls neue v2.5.0-Note-Felder sind null (Commit #1 verifiziert):
        assertNull(note.importedAt)
    }
}
