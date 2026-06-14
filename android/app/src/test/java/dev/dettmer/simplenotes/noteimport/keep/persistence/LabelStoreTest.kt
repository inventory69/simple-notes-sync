package dev.dettmer.simplenotes.noteimport.keep.persistence

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * v2.5.0 — Tests #41 + #42 aus Analyseplan §5.1.
 *
 * Wir mocken nur `Context.getFilesDir()` — der Rest ist echtes File-IO in einem
 * temporären Verzeichnis. Kein Robolectric nötig (JVM-Test-Runner reicht).
 */
class LabelStoreTest {
    private lateinit var tmpDir: File
    private lateinit var context: Context
    private lateinit var store: LabelStore

    @Before
    fun setUp() {
        tmpDir = Files.createTempDirectory("labelstore-test").toFile()
        context = mockk { every { filesDir } returns tmpDir }
        store = LabelStore(context)
    }

    @After
    fun tearDown() {
        tmpDir.deleteRecursively()
    }

    // ───── #41 ───────────────────────────────────────────────────────
    @Test
    fun `appendBatch_persistsAndDeduplicates`() = runBlocking {
        store.appendBatch("note-A", listOf("Privat", "Reise"))
        store.appendBatch("note-B", listOf("Reise"))
        store.appendBatch("note-A", listOf("Reise")) // duplicate für note-A unter "Reise"

        val idx = store.load()
        assertEquals(setOf("Privat", "Reise"), idx.labels.keys)
        assertEquals(setOf("note-A"), idx.labels["Privat"])
        assertEquals(setOf("note-A", "note-B"), idx.labels["Reise"])

        // Datei wurde geschrieben.
        val file = File(tmpDir, LabelStore.FILE_NAME)
        assertTrue(file.exists())
        assertTrue(file.readText().contains("Privat"))
    }

    // ───── #42 ───────────────────────────────────────────────────────
    @Test
    fun `appendBatch_concurrent_isMutexProtected`() = runBlocking {
        val concurrent = 50
        withContext(Dispatchers.Default) {
            (1..concurrent).map { i ->
                async { store.appendBatch("note-$i", listOf("Common", "Tag-$i")) }
            }.awaitAll()
        }
        val idx = store.load()
        assertEquals(concurrent + 1, idx.labels.size) // 1 "Common" + N distinkte
        assertEquals(concurrent, idx.labels["Common"]?.size)
        // Jede Note erscheint im Common-Set.
        for (i in 1..concurrent) {
            assertTrue("note-$i in Common", idx.labels["Common"]!!.contains("note-$i"))
        }
    }

    // ───── Defensiv: leere Eingabe → keine Datei-Mutation ────────────
    @Test
    fun `appendBatch_emptyLabels_doesNotWriteFile`() = runBlocking {
        store.appendBatch("note-A", emptyList())
        val file = File(tmpDir, LabelStore.FILE_NAME)
        assertFalse(file.exists())
    }

    // ───── Defensiv: corrupt JSON → leerer Index, kein Crash ─────────
    @Test
    fun `load_corruptJson_returnsEmptyIndex`() = runBlocking {
        File(tmpDir, LabelStore.FILE_NAME).writeText("{this is not json")
        val idx = store.load()
        assertNotNull(idx)
        assertEquals(0, idx.labels.size)
    }
}
