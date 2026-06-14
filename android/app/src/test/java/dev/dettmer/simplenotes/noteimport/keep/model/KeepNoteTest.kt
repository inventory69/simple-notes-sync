package dev.dettmer.simplenotes.noteimport.keep.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2.5.0 — Smoke-Tests für das Intermediate-Modell.
 * Ausführlichere Round-Trip-Tests passieren über `KeepEntryParserTest` (Commit #5)
 * und `KeepToNoteMapperTest` (Commit #7). Hier nur das Verhalten der zwei
 * Hilfsmethoden auf `KeepNote` und der Lookup-Tabelle `KeepColor`.
 */
class KeepNoteTest {
    private fun base(
        textContent: String? = "Body",
        attachments: List<KeepAttachment> = emptyList(),
        checklist: List<KeepChecklistItem> = emptyList(),
        createdUsec: Long = 1_700_000_000_000_000L,
        editedUsec: Long = 1_700_000_001_000_000L
    ) = KeepNote(
        title = "T",
        textContent = textContent,
        checklist = checklist,
        attachments = attachments,
        createdTimestampUsec = createdUsec,
        userEditedTimestampUsec = editedUsec,
        sourceJsonName = "x.json"
    )

    @Test
    fun `createdAtMs_dividesByThousand`() {
        val n = base(createdUsec = 1_700_000_000_000_000L)
        assertEquals(1_700_000_000_000L, n.createdAtMs())
    }

    @Test
    fun `updatedAtMs_dividesByThousand`() {
        val n = base(editedUsec = 1_700_000_001_000_000L)
        assertEquals(1_700_000_001_000L, n.updatedAtMs())
    }

    @Test
    fun `createdAtMs_zero_returnsZero`() {
        val n = base(createdUsec = 0L)
        assertEquals(0L, n.createdAtMs())
    }

    @Test
    fun `hasOnlyAttachments_trueWhenTextEmptyAndAttachmentsPresent`() {
        val n = base(
            textContent = "",
            attachments = listOf(KeepAttachment("img.jpg", "image/jpeg"))
        )
        assertTrue(n.hasOnlyAttachments())
    }

    @Test
    fun `hasOnlyAttachments_falseWhenChecklistPresent`() {
        val n = base(
            textContent = null,
            attachments = listOf(KeepAttachment("img.jpg", "image/jpeg")),
            checklist = listOf(KeepChecklistItem("x", false))
        )
        assertFalse(n.hasOnlyAttachments())
    }

    @Test
    fun `hasOnlyAttachments_falseWhenTextPresent`() {
        val n = base(
            textContent = "Hello",
            attachments = listOf(KeepAttachment("img.jpg", "image/jpeg"))
        )
        assertFalse(n.hasOnlyAttachments())
    }

    @Test
    fun `keepColor_default_returnsNull`() {
        assertNull(KeepColor.toHex("DEFAULT"))
    }

    @Test
    fun `keepColor_red_returnsHex`() {
        assertEquals("#F28B82", KeepColor.toHex("RED"))
    }

    @Test
    fun `keepColor_caseInsensitive`() {
        assertEquals("#CBF0F8", KeepColor.toHex("blue"))
    }

    @Test
    fun `keepColor_unknown_returnsNull`() {
        assertNull(KeepColor.toHex("MAGENTA"))
    }

    @Test
    fun `keepColor_nullOrBlank_returnsNull`() {
        assertNull(KeepColor.toHex(null))
        assertNull(KeepColor.toHex(""))
        assertNull(KeepColor.toHex("   "))
    }

    @Test
    fun `keepNote_isShared_defaultsFalse`() {
        assertFalse(base().isShared)
    }
}
