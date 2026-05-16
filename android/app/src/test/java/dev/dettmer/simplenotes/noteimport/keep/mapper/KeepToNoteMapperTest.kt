package dev.dettmer.simplenotes.noteimport.keep.mapper

import dev.dettmer.simplenotes.models.NoteType
import dev.dettmer.simplenotes.models.SyncStatus
import dev.dettmer.simplenotes.noteimport.keep.model.KeepAnnotation
import dev.dettmer.simplenotes.noteimport.keep.model.KeepAttachment
import dev.dettmer.simplenotes.noteimport.keep.model.KeepChecklistItem
import dev.dettmer.simplenotes.noteimport.keep.model.KeepLabel
import dev.dettmer.simplenotes.noteimport.keep.model.KeepNote
import dev.dettmer.simplenotes.noteimport.keep.model.KeepNoteState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * v2.5.0 — Tests #20 bis #26 aus Analyseplan §5.1.
 */
class KeepToNoteMapperTest {

    private lateinit var mapper: KeepToNoteMapper
    private val counter = AtomicInteger(0)

    @Before
    fun setUp() {
        counter.set(0)
        mapper = KeepToNoteMapper(
            deviceIdProvider = { "test-device" },
            idGenerator = { "id-${counter.incrementAndGet()}" },
        )
    }

    // Test-helper mirrors KeepNote fields to allow fully-readable per-test defaults;
    // all 12 params are intentional — no grouping would improve readability here.
    @Suppress("LongParameterList")
    private fun keepNoteOf(
        title: String = "T",
        text: String? = "Hello",
        checklist: List<KeepChecklistItem> = emptyList(),
        labels: List<KeepLabel> = emptyList(),
        attachments: List<KeepAttachment> = emptyList(),
        annotations: List<KeepAnnotation> = emptyList(),
        color: String = "DEFAULT",
        isPinned: Boolean = false,
        isShared: Boolean = false,
        state: KeepNoteState = KeepNoteState.ACTIVE,
        createdUsec: Long = 1_690_000_000_000_000L,
        updatedUsec: Long = 1_700_000_000_000_000L,
    ) = KeepNote(
        title = title, textContent = text, checklist = checklist, labels = labels,
        attachments = attachments, annotations = annotations, color = color,
        isPinned = isPinned, isShared = isShared, state = state,
        createdTimestampUsec = createdUsec, userEditedTimestampUsec = updatedUsec,
        sourceJsonName = "test.json",
    )

    // ───── #20 ───────────────────────────────────────────────────────
    @Test
    fun `map_textNote_setsTextType`() {
        val n = mapper.map(keepNoteOf(text = "Hello world"), importedAtMs = 42L)
        assertEquals(NoteType.TEXT, n.noteType)
        assertEquals("Hello world", n.content)
        assertNull(n.checklistItems)
        assertEquals(SyncStatus.PENDING, n.syncStatus)
        assertEquals("test-device", n.deviceId)
        assertEquals(1_690_000_000_000L, n.createdAt)
        assertEquals(1_700_000_000_000L, n.updatedAt)
        assertEquals(42L, n.importedAt)
    }

    // ───── #21 ───────────────────────────────────────────────────────
    @Test
    fun `map_listNote_setsChecklistType`() {
        val keep = keepNoteOf(
            text = null,
            checklist = listOf(
                KeepChecklistItem("Milch", false, 0),
                KeepChecklistItem("Brot", true, 0),
            ),
        )
        val n = mapper.map(keep, importedAtMs = 1L)
        assertEquals(NoteType.CHECKLIST, n.noteType)
        assertNotNull(n.checklistItems)
        assertEquals(2, n.checklistItems!!.size)
        assertEquals("Milch", n.checklistItems!![0].text)
        assertEquals(true, n.checklistItems!![1].isChecked)
    }

    // ───── #22 ───────────────────────────────────────────────────────
    @Test
    fun `map_setsImportedAtCurrentMs`() {
        val now = 1_234_567_890L
        val n = mapper.map(keepNoteOf(), importedAtMs = now)
        assertEquals(now, n.importedAt)
    }

    // ───── #23 ───────────────────────────────────────────────────────
    @Test
    fun `map_setsLabelsList`() {
        val keep = keepNoteOf(labels = listOf(KeepLabel("Privat"), KeepLabel("Reise"), KeepLabel("Privat")))
        val n = mapper.map(keep, importedAtMs = 0L)
        assertEquals(listOf("Privat", "Reise"), n.labels)
    }

    // ───── #24 ───────────────────────────────────────────────────────
    @Test
    fun `map_dropsAttachmentsButCountsThemSeparately`() {
        val keep = keepNoteOf(
            attachments = listOf(
                KeepAttachment("a.jpg", "image/jpeg", null),
                KeepAttachment("b.png", "image/png", null),
            ),
        )
        val n = mapper.map(keep, importedAtMs = 0L)
        // Note hat kein attachments-Feld → der Mapper darf nichts daran ändern.
        // Caller-Verifikation:
        assertEquals(2, keep.attachments.size)
        // Inhalt der Note enthält keine Datei-Pfade.
        assertTrue(!n.content.contains("a.jpg"))
        assertTrue(!n.content.contains("b.png"))
    }

    // ───── #25 ───────────────────────────────────────────────────────
    @Test
    fun `mapChecklist_flattensIndentationLevelButPreservesField`() {
        val items = listOf(
            KeepChecklistItem("Obst", false, 0),
            KeepChecklistItem("Äpfel", false, 1),
            KeepChecklistItem("Granny", false, 2),
            KeepChecklistItem("Brot", false, 0),
        )
        val out = mapper.mapChecklist(items)
        assertEquals(4, out.size)
        // Texte unverändert (kein extra trim hier, weil Items schon getrimmt waren).
        assertEquals("Obst", out[0].text)
        assertEquals("Äpfel", out[1].text)
        // indentationLevel BLEIBT erhalten.
        assertEquals(0, out[0].indentationLevel)
        assertEquals(1, out[1].indentationLevel)
        assertEquals(2, out[2].indentationLevel)
        assertEquals(0, out[3].indentationLevel)
    }

    // ───── #26 ───────────────────────────────────────────────────────
    @Test
    fun `mapChecklist_preservesOrder`() {
        val items = (1..5).map { KeepChecklistItem("Item $it", false, 0) }
        val out = mapper.mapChecklist(items)
        out.forEachIndexed { idx, item ->
            assertEquals(idx, item.order)
            assertEquals(idx, item.originalOrder)
            assertEquals("Item ${idx + 1}", item.text)
        }
    }

    // ───── Defensiv: Annotations werden für TEXT angehängt ──────────
    @Test
    fun `map_textNote_appendsAnnotationUrls`() {
        val keep = keepNoteOf(
            text = "Article snippet",
            annotations = listOf(
                KeepAnnotation("WEBLINK", "https://example.org/a", "A"),
                KeepAnnotation("WEBLINK", "https://example.org/b", "B"),
            ),
        )
        val n = mapper.map(keep, importedAtMs = 0L)
        assertTrue(n.content.contains("Article snippet"))
        assertTrue(n.content.contains("— https://example.org/a"))
        assertTrue(n.content.contains("— https://example.org/b"))
    }

    // ───── Defensiv: color="DEFAULT" → null, isPinned=false → null ──
    @Test
    fun `map_defaultColorAndUnpinned_resolveToNullFields`() {
        val n = mapper.map(keepNoteOf(color = "DEFAULT", isPinned = false), importedAtMs = 0L)
        assertNull(n.color)
        assertNull(n.isPinned)
    }

    // ───── Keep palette extension: CERULEAN aliases to DARK_BLUE slot ──
    @Test
    fun `map_ceruleanColor_aliasesToDarkBlueSlot`() {
        val n = mapper.map(keepNoteOf(color = "CERULEAN"), importedAtMs = 0L)
        assertEquals("#AECBFA", n.color)
    }

    // ───── Unknown Keep colour name falls back to null (no override) ──
    @Test
    fun `map_unknownColorName_fallsBackToNull`() {
        val n = mapper.map(keepNoteOf(color = "TOTALLY_NEW_COLOR_NAME"), importedAtMs = 0L)
        assertNull(n.color)
    }
}
