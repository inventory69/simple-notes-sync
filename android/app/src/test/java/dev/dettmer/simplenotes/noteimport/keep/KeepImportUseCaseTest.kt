package dev.dettmer.simplenotes.noteimport.keep

import android.net.Uri
import dev.dettmer.simplenotes.noteimport.keep.conflict.ConflictResolver
import dev.dettmer.simplenotes.noteimport.keep.mapper.KeepToNoteMapper
import dev.dettmer.simplenotes.noteimport.keep.model.KeepLabel
import dev.dettmer.simplenotes.noteimport.keep.model.KeepNote
import dev.dettmer.simplenotes.noteimport.keep.model.KeepNoteState
import dev.dettmer.simplenotes.noteimport.keep.parser.KeepEntryParser
import dev.dettmer.simplenotes.noteimport.keep.persistence.LabelIndex
import dev.dettmer.simplenotes.noteimport.keep.persistence.LabelStore
import dev.dettmer.simplenotes.noteimport.keep.zip.KeepPreScanResult
import dev.dettmer.simplenotes.noteimport.keep.zip.KeepZipEntry
import dev.dettmer.simplenotes.noteimport.keep.zip.KeepZipReader
import dev.dettmer.simplenotes.storage.NotesStorage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * v2.5.0 — Tests #36 bis #40 aus Analyseplan §5.1 + 1 Defensiv-Test.
 */
class KeepImportUseCaseTest {
    private lateinit var zipReader: KeepZipReader
    private lateinit var entryParser: KeepEntryParser
    private lateinit var mapper: KeepToNoteMapper
    private lateinit var resolver: ConflictResolver
    private lateinit var storage: NotesStorage
    private lateinit var labelStore: LabelStore
    private lateinit var useCase: KeepImportUseCase

    private val fakeUri: Uri = mockk()

    @Before
    fun setUp() {
        zipReader = mockk()
        entryParser = mockk()
        mapper = KeepToNoteMapper(deviceIdProvider = { "test-dev" }, idGenerator = { UUID.randomUUID().toString() })
        resolver = mockk()
        storage = mockk(relaxed = true)
        labelStore = mockk(relaxed = true) {
            coEvery { load() } returns LabelIndex()
        }
        useCase = KeepImportUseCase(
            zipReader = zipReader,
            entryParser = entryParser,
            mapper = mapper,
            conflictResolver = resolver,
            storage = storage,
            labelStore = labelStore,
            nowMsProvider = { 1_000L }
        )
    }

    private fun keepNoteOf(
        title: String = "T",
        state: KeepNoteState = KeepNoteState.ACTIVE,
        labels: List<String> = emptyList()
    ) = KeepNote(
        title = title, textContent = "x", checklist = emptyList(),
        labels = labels.map(::KeepLabel),
        attachments = emptyList(), annotations = emptyList(),
        color = "DEFAULT", isPinned = false, isShared = false,
        state = state, createdTimestampUsec = 0L, userEditedTimestampUsec = 0L,
        sourceJsonName = "$title.json"
    )

    private fun stubZipWith(jsonNames: List<String>) {
        val entries = jsonNames.map {
            KeepZipEntry(name = it, bytes = byteArrayOf(), sizeBytes = 0L)
        }
        coEvery { zipReader.readEntries(fakeUri) } returns flow { entries.forEach { emit(it) } }
    }

    private fun stubPreScan(active: Int = 0, archived: Int = 0, trashed: Int = 0) {
        coEvery { zipReader.preScan(fakeUri) } returns KeepPreScanResult(
            totalNotes = active + archived + trashed,
            activeCount = active,
            archivedCount = archived,
            trashedCount = trashed,
            labelCount = 0,
            sharedCount = 0,
            notesWithAttachments = 0,
            sizeBytes = 0L
        )
    }

    // ───── #36 ───────────────────────────────────────────────────────
    @Test
    fun `import_filterArchivedFalse_skipsArchivedEntries`() = runBlocking {
        stubZipWith(listOf("a.json", "b.json"))
        stubPreScan(active = 1, archived = 1)
        every { entryParser.parse(match { it.name == "a.json" }, any()) } returns
            keepNoteOf("A", KeepNoteState.ACTIVE)
        every { entryParser.parse(match { it.name == "b.json" }, any()) } returns
            keepNoteOf("B", KeepNoteState.ARCHIVED)
        coEvery { resolver.resolve(any(), any()) } returns ConflictResolver.Resolution.Create

        val s = useCase.import(fakeUri, KeepImportOptions(includeArchived = false))
        assertEquals(1, s.imported)
        assertEquals(1, s.skipped)
        assertEquals(0, s.failed)
    }

    // ───── #37 ───────────────────────────────────────────────────────
    @Test
    fun `import_filterTrashedFalse_skipsTrashedEntries`() = runBlocking {
        stubZipWith(listOf("a.json", "b.json"))
        stubPreScan(active = 1, trashed = 1)
        every { entryParser.parse(match { it.name == "a.json" }, any()) } returns
            keepNoteOf("A", KeepNoteState.ACTIVE)
        every { entryParser.parse(match { it.name == "b.json" }, any()) } returns
            keepNoteOf("B", KeepNoteState.TRASHED)
        coEvery { resolver.resolve(any(), any()) } returns ConflictResolver.Resolution.Create

        val s = useCase.import(fakeUri, KeepImportOptions(includeTrashed = false))
        assertEquals(1, s.imported)
        assertEquals(1, s.skipped)
    }

    // ───── #38 ───────────────────────────────────────────────────────
    @Test
    fun `import_emitsProgressForEachEntry`() = runBlocking {
        stubZipWith(listOf("a.json", "b.json", "c.json"))
        stubPreScan(active = 3)
        every { entryParser.parse(any(), any()) } answers {
            keepNoteOf(firstArg<KeepZipEntry>().name, KeepNoteState.ACTIVE)
        }
        coEvery { resolver.resolve(any(), any()) } returns ConflictResolver.Resolution.Create

        val seen = mutableListOf<KeepImportProgress>()
        useCase.import(fakeUri, KeepImportOptions()) { seen += it }

        assertEquals(3, seen.size)
        assertEquals(listOf(1, 2, 3), seen.map { it.processed })
        assertTrue(seen.all { it.total == 3 })
    }

    // ───── #39 ───────────────────────────────────────────────────────
    @Test
    fun `import_failureInOneEntry_continuesWithRest`() = runBlocking {
        stubZipWith(listOf("ok1.json", "broken.json", "ok2.json"))
        stubPreScan(active = 3)
        every { entryParser.parse(match { it.name == "broken.json" }, any()) } returns null
        every { entryParser.parse(match { it.name != "broken.json" }, any()) } answers {
            keepNoteOf(firstArg<KeepZipEntry>().name, KeepNoteState.ACTIVE)
        }
        coEvery { resolver.resolve(any(), any()) } returns ConflictResolver.Resolution.Create

        val s = useCase.import(fakeUri, KeepImportOptions())
        assertEquals(2, s.imported)
        assertEquals(1, s.failed)
        assertEquals(1, s.errors.size)
        assertEquals("broken.json", s.errors[0].sourceName)
    }

    // ───── #40 ───────────────────────────────────────────────────────
    @Test
    fun `import_summaryCountsCorrect`() = runBlocking {
        stubZipWith(listOf("a.json", "b.json", "c.json", "d.json"))
        stubPreScan(active = 4)
        every { entryParser.parse(any(), any()) } answers {
            keepNoteOf(firstArg<KeepZipEntry>().name, KeepNoteState.ACTIVE)
        }
        // 1× Create, 1× Replace, 1× Skip, 1× failed (parser-null) — letzteres erzwingen wir separat:
        coEvery { resolver.resolve(any(), any()) } returnsMany listOf(
            ConflictResolver.Resolution.Create,
            ConflictResolver.Resolution.Replace(existingId = "OLD"),
            ConflictResolver.Resolution.Skip(reason = "dupe"),
            ConflictResolver.Resolution.Create
        )

        val s = useCase.import(fakeUri, KeepImportOptions())
        assertEquals(4, s.totalEntries)
        assertEquals(2, s.imported) // a + d
        assertEquals(1, s.replaced)
        assertEquals(1, s.skipped)
        assertEquals(0, s.failed)

        coVerify(atLeast = 3) { storage.saveNote(any()) }
    }

    // ───── Defensiv: leeres ZIP → leere Summary, kein Crash ─────────
    @Test
    fun `import_emptyZip_returnsZeroSummary`() = runBlocking {
        stubZipWith(emptyList())
        stubPreScan()
        val s = useCase.import(fakeUri, KeepImportOptions())
        assertEquals(0, s.totalEntries)
        assertEquals(0, s.imported)
        assertEquals(KeepImportSummary.EMPTY.copy(labelsImported = 0), s)
    }
}
