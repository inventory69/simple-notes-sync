package dev.dettmer.simplenotes.noteimport.keep.parser

import dev.dettmer.simplenotes.noteimport.keep.model.KeepNoteState
import dev.dettmer.simplenotes.noteimport.keep.zip.KeepZipEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * v2.5.0 — Tests #1 bis #12 aus Analyseplan §5.1.
 */
class KeepEntryParserTest {
    private lateinit var parser: KeepEntryParserImpl

    @Before
    fun setUp() {
        parser = KeepEntryParserImpl(htmlFallback = KeepHtmlFallbackParser())
    }

    private fun jsonEntry(name: String, body: String): KeepZipEntry {
        val bytes = body.toByteArray(Charsets.UTF_8)
        return KeepZipEntry(name = name, bytes = bytes, sizeBytes = bytes.size.toLong())
    }

    private fun htmlEntry(name: String, body: String): KeepZipEntry {
        val bytes = body.toByteArray(Charsets.UTF_8)
        return KeepZipEntry(name = name, bytes = bytes, sizeBytes = bytes.size.toLong())
    }

    // ───── #1 ────────────────────────────────────────────────────────
    @Test
    fun `parse_validPlaintextNote_returnsKeepNote`() {
        val json = """
            {
              "title": "Einkauf",
              "textContent": "Milch und Brot",
              "color": "RED",
              "isPinned": true,
              "isArchived": false,
              "isTrashed": false,
              "createdTimestampUsec": 1690000000000000,
              "userEditedTimestampUsec": 1700000000000000
            }
        """.trimIndent()
        val keep = parser.parse(jsonEntry("a.json", json), null)
        assertNotNull(keep)
        assertEquals("Einkauf", keep!!.title)
        assertEquals("Milch und Brot", keep.textContent)
        assertEquals("RED", keep.color)
        assertTrue(keep.isPinned)
        assertEquals(KeepNoteState.ACTIVE, keep.state)
        assertEquals(1_690_000_000_000_000L, keep.createdTimestampUsec)
        assertEquals(1_700_000_000_000_000L, keep.userEditedTimestampUsec)
    }

    // ───── #2 ────────────────────────────────────────────────────────
    @Test
    fun `parse_emptyTitle_returnsKeepNoteWithEmptyTitle`() {
        val json = """{"title":"","textContent":"x"}"""
        val keep = parser.parse(jsonEntry("a.json", json), null)
        assertNotNull(keep)
        assertEquals("", keep!!.title)
    }

    // ───── #3 ────────────────────────────────────────────────────────
    @Test
    fun `parse_emptyTextContent_withHtml_usesHtmlFallback`() {
        val json = """{"title":"H","textContent":""}"""
        val html = """<html><body><div class="content">Hallo<br>Welt</div></body></html>"""
        val keep = parser.parse(jsonEntry("a.json", json), htmlEntry("a.html", html))
        assertNotNull(keep)
        assertEquals("Hallo\nWelt", keep!!.textContent)
    }

    // ───── #162: geteilte Notiz, Kollaboratoren stehen als <ul> im HTML ─────
    private val shareesHtml = """
        <div class="sharees"><h2>Collaborators</h2>
        <ul><li class="sharee user" title="someone@example.com (owner)">someone@example.com</li></ul></div>
    """.trimIndent()

    @Test
    fun `parse_sharedTextNote_keepsJsonTextAndIgnoresHtmlLists`() {
        val json = """{"title":"T","textContent":"Echter Inhalt","sharees":[{"email":"someone@example.com"}]}"""
        val html = """<body><div class="content">Echter Inhalt</div>$shareesHtml</body>"""
        val keep = parser.parse(jsonEntry("a.json", json), htmlEntry("a.html", html))
        assertNotNull(keep)
        assertEquals("Echter Inhalt", keep!!.textContent)
        assertTrue(keep.checklist.isEmpty())
    }

    @Test
    fun `parse_sharedEmptyChecklist_doesNotTurnShareesIntoItems`() {
        val json = """{"title":"T","listContent":[],"sharees":[{"email":"someone@example.com"}]}"""
        val html = """<body><div class="content"><ul class="list"></ul></div>$shareesHtml</body>"""
        val keep = parser.parse(jsonEntry("a.json", json), htmlEntry("a.html", html))
        assertNotNull(keep)
        assertTrue(keep!!.checklist.isEmpty())
        assertNull(keep.textContent)
    }

    @Test
    fun `parse_jsonWithoutContent_usesHtmlChecklistOnly`() {
        val json = """{"title":"T","sharees":[{"email":"someone@example.com"}]}"""
        val html = """<body><div class="content"><ul class="list"><li class="listitem">Eier</li></ul></div>$shareesHtml</body>"""
        val keep = parser.parse(jsonEntry("a.json", json), htmlEntry("a.html", html))
        assertNotNull(keep)
        assertEquals(listOf("Eier"), keep!!.checklist.map { it.text })
        assertNull(keep.textContent)
    }

    // ───── #4 ────────────────────────────────────────────────────────
    @Test
    fun `parse_checklistJson_returnsKeepChecklistItems`() {
        val json = """
            {
              "title": "Liste",
              "listContent": [
                {"text":"Milch","isChecked":false},
                {"text":"Brot","isChecked":true}
              ]
            }
        """.trimIndent()
        val keep = parser.parse(jsonEntry("a.json", json), null)
        assertNotNull(keep)
        assertEquals(2, keep!!.checklist.size)
        assertEquals("Milch", keep.checklist[0].text)
        assertFalse(keep.checklist[0].isChecked)
        assertEquals("Brot", keep.checklist[1].text)
        assertTrue(keep.checklist[1].isChecked)
    }

    // ───── #5 ────────────────────────────────────────────────────────
    @Test
    fun `parse_indentedChecklist_preservesIndentationLevel`() {
        val json = """
            {
              "listContent": [
                {"text":"Obst","isChecked":false},
                {"text":"  Äpfel","isChecked":false},
                {"text":"    Granny","isChecked":false},
                {"text":"Brot","isChecked":false}
              ]
            }
        """.trimIndent()
        val keep = parser.parse(jsonEntry("a.json", json), null)
        assertNotNull(keep)
        val items = keep!!.checklist
        assertEquals(0, items[0].indentationLevel)
        assertEquals("Obst", items[0].text)
        assertEquals(1, items[1].indentationLevel)
        assertEquals("Äpfel", items[1].text)
        assertEquals(2, items[2].indentationLevel)
        assertEquals("Granny", items[2].text)
        assertEquals(0, items[3].indentationLevel)
    }

    // ───── #6 ────────────────────────────────────────────────────────
    @Test
    fun `parse_labelsArray_returnsKeepLabels`() {
        val json = """
            {"labels":[{"name":"Privat"},{"name":"Reise"},{"name":""}]}
        """.trimIndent()
        val keep = parser.parse(jsonEntry("a.json", json), null)
        assertNotNull(keep)
        assertEquals(2, keep!!.labels.size)
        assertEquals("Privat", keep.labels[0].name)
        assertEquals("Reise", keep.labels[1].name)
    }

    // ───── #7 ────────────────────────────────────────────────────────
    @Test
    fun `parse_attachmentsArray_returnsKeepAttachmentsFullMetadata`() {
        val json = """
            {"attachments":[
              {"filePath":"abc.jpg","mimetype":"image/jpeg"},
              {"filePath":"xyz.png","mimetype":"image/png"}
            ]}
        """.trimIndent()
        val keep = parser.parse(jsonEntry("a.json", json), null)
        assertNotNull(keep)
        assertEquals(2, keep!!.attachments.size)
        assertEquals("abc.jpg", keep.attachments[0].filePath)
        assertEquals("image/jpeg", keep.attachments[0].mimeType)
        assertEquals("image/png", keep.attachments[1].mimeType)
    }

    // ───── #8 ────────────────────────────────────────────────────────
    @Test
    fun `parse_missingOptionalFields_usesDefaults`() {
        // Minimal-JSON: nichts außer einem leeren Objekt.
        val keep = parser.parse(jsonEntry("a.json", "{}"), null)
        assertNotNull(keep)
        assertEquals("", keep!!.title)
        assertNull(keep.textContent)
        assertTrue(keep.checklist.isEmpty())
        assertTrue(keep.labels.isEmpty())
        assertTrue(keep.attachments.isEmpty())
        assertTrue(keep.annotations.isEmpty())
        assertEquals("DEFAULT", keep.color)
        assertFalse(keep.isPinned)
        assertFalse(keep.isShared)
        assertEquals(KeepNoteState.ACTIVE, keep.state)
        assertEquals(0L, keep.createdTimestampUsec)
        assertEquals(0L, keep.userEditedTimestampUsec)
    }

    // ───── #9 ────────────────────────────────────────────────────────
    @Test
    fun `parse_specialCharsAndUmlauts_preserved`() {
        // BOM + UTF-8 + Emoji + RTL + CJK
        val body = "\uFEFF" + """{"title":"ä ö ü € 中文 שלום 🌟","textContent":"x"}"""
        val keep = parser.parse(jsonEntry("a.json", body), null)
        assertNotNull(keep)
        assertEquals("ä ö ü € 中文 שלום 🌟", keep!!.title)
    }

    // ───── #10 ───────────────────────────────────────────────────────
    @Test
    fun `parse_invalidJson_returnsNullAndLogs`() {
        val keep = parser.parse(jsonEntry("a.json", "{this is not json"), null)
        assertNull(keep)
    }

    // ───── #11 ───────────────────────────────────────────────────────
    @Test
    fun `parse_isArchivedTrue_setsStateArchived`() {
        val json = """{"isArchived":true}"""
        val keep = parser.parse(jsonEntry("a.json", json), null)
        assertNotNull(keep)
        assertEquals(KeepNoteState.ARCHIVED, keep!!.state)
    }

    // ───── #12 ───────────────────────────────────────────────────────
    @Test
    fun `parse_isTrashedTrue_setsStateTrashed`() {
        // Trashed hat Vorrang vor Archived (defensiv).
        val json = """{"isArchived":true,"isTrashed":true}"""
        val keep = parser.parse(jsonEntry("a.json", json), null)
        assertNotNull(keep)
        assertEquals(KeepNoteState.TRASHED, keep!!.state)
    }

    // ───── Defensiv: sharees != [] markiert isShared ─────────────────
    @Test
    fun `parse_shareesPresent_marksIsSharedTrue`() {
        val json = """{"sharees":[{"email":"x@y.z"}]}"""
        val keep = parser.parse(jsonEntry("a.json", json), null)
        assertNotNull(keep)
        assertTrue(keep!!.isShared)
    }

    // ───── Defensiv: leere Bytes ─────────────────────────────────────
    @Test
    fun `parse_emptyBytes_returnsNull`() {
        val keep = parser.parse(jsonEntry("a.json", ""), null)
        assertNull(keep)
    }
}
