package dev.dettmer.simplenotes.sync.parallel

import com.thegrizzlylabs.sardineandroid.DavResource
import com.thegrizzlylabs.sardineandroid.Sardine
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 🆕 v1.9.0: Unit-Tests für parallele Upload-Logik.
 *
 * Testet das Semaphore + async + Retry-Pattern analog zu ParallelDownloaderTest,
 * aber für Uploads:
 * - Bounded parallelism (Semaphore)
 * - Retry mit Exponential Backoff
 * - ConcurrentHashMap für E-Tag-Updates
 * - Mutex für Storage-Write-Serialisierung
 * - Progress-Callback thread-safety
 * - Batch-E-Tag-Fetch per list(depth=1)
 * - notes-md/ Exists-Cache (einmalige Prüfung)
 */
class ParallelUploadTest {

    private fun mockSardine(): Sardine = mockk(relaxed = true)

    /**
     * Simuliert uploadSingleNoteParallel() Kernlogik:
     * sardine.put() → storageMutex.withLock {} → optional MD-Export
     */
    private suspend fun simulateUpload(
        sardine: Sardine,
        noteId: String,
        storageMutex: Mutex,
        storageWrites: MutableList<String>,
        markdownExportEnabled: Boolean = false,
        markdownDirExists: Boolean = true
    ): UploadTaskResult {
        return try {
            val noteUrl = "https://server/notes/$noteId.json"
            sardine.put(noteUrl, "{}".toByteArray(), "application/json")

            storageMutex.withLock {
                storageWrites.add(noteId)
            }

            if (markdownExportEnabled) {
                if (!markdownDirExists) {
                    // Simuliere Ordner-Check (Fallback-Pfad)
                    sardine.exists("https://server/notes-md/")
                }
                sardine.put("https://server/notes-md/$noteId.md", "# Test".toByteArray(), "text/markdown")
            }

            UploadTaskResult.Success(noteId = noteId, etag = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UploadTaskResult.Failure(noteId, e)
        }
    }

    /**
     * Simuliert uploadSingleNoteParallel() mit Retry-Logik.
     */
    private suspend fun simulateUploadWithRetry(
        sardine: Sardine,
        noteId: String,
        storageMutex: Mutex,
        storageWrites: MutableList<String>,
        maxRetries: Int = 2,
        retryDelayMs: Long = 50L
    ): UploadTaskResult {
        var lastError: Throwable? = null
        repeat(maxRetries + 1) { attempt ->
            try {
                val noteUrl = "https://server/notes/$noteId.json"
                sardine.put(noteUrl, "{}".toByteArray(), "application/json")
                storageMutex.withLock {
                    storageWrites.add(noteId)
                }
                return UploadTaskResult.Success(noteId = noteId, etag = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (attempt < maxRetries) {
                    delay(retryDelayMs * (attempt + 1))
                }
            }
        }
        return UploadTaskResult.Failure(noteId, lastError ?: Exception("Unknown"))
    }

    // ═══════════════════════════════════════════════
    // Leere Upload-Liste
    // ═══════════════════════════════════════════════

    @Test
    fun `empty note list returns zero uploads`() = runTest {
        val sardine = mockSardine()
        val pendingNotes = emptyList<String>()
        assertEquals(0, pendingNotes.size)
        verify(exactly = 0) { sardine.put(any<String>(), any<ByteArray>(), any<String>()) }
    }

    // ═══════════════════════════════════════════════
    // Einzelner Upload erfolgreich
    // ═══════════════════════════════════════════════

    @Test
    fun `single note upload calls sardine put once`() = runTest {
        val sardine = mockSardine()
        val storageMutex = Mutex()
        val storageWrites = mutableListOf<String>()

        val result = simulateUpload(sardine, "note-1", storageMutex, storageWrites)

        assertTrue(result is UploadTaskResult.Success)
        assertEquals("note-1", (result as UploadTaskResult.Success).noteId)
        verify(exactly = 1) { sardine.put(match<String> { it.contains("note-1.json") }, any<ByteArray>(), any<String>()) }
        assertEquals(listOf("note-1"), storageWrites)
    }

    // ═══════════════════════════════════════════════
    // Parallele Uploads mit Semaphore
    // ═══════════════════════════════════════════════

    @Test
    fun `parallel uploads with semaphore complete all tasks`() = runTest {
        val sardine = mockSardine()
        val storageMutex = Mutex()
        val storageWrites = mutableListOf<String>()
        val semaphore = Semaphore(3)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)

        val noteIds = (1..5).map { "note-$it" }

        val results: List<UploadTaskResult> = coroutineScope {
            noteIds.map { noteId ->
                async(dispatcher) {
                    semaphore.withPermit {
                        simulateUpload(sardine, noteId, storageMutex, storageWrites)
                    }
                }
            }.awaitAll()
        }

        assertEquals(5, results.size)
        assertEquals(5, results.count { it is UploadTaskResult.Success })
        assertEquals(5, storageWrites.size)
        assertEquals(noteIds.toSet(), storageWrites.toSet())
    }

    // ═══════════════════════════════════════════════
    // Fehlerbehandlung: Mixed Success + Failure
    // ═══════════════════════════════════════════════

    @Test
    fun `mixed success and failure - failures do not stop other uploads`() = runTest {
        val sardine = mockSardine()
        val storageMutex = Mutex()
        val storageWrites = mutableListOf<String>()
        val semaphore = Semaphore(3)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)

        // note-2 schlägt fehl
        every { sardine.put(match<String> { it.contains("note-2.json") }, any<ByteArray>(), any<String>()) } throws
            IOException("Server error")

        val noteIds = listOf("note-1", "note-2", "note-3")

        val results: List<UploadTaskResult> = coroutineScope {
            noteIds.map { noteId ->
                async(dispatcher) {
                    semaphore.withPermit {
                        simulateUpload(sardine, noteId, storageMutex, storageWrites)
                    }
                }
            }.awaitAll()
        }

        assertEquals(3, results.size)
        assertEquals(2, results.count { it is UploadTaskResult.Success })
        assertEquals(1, results.count { it is UploadTaskResult.Failure })
        val failedId = (results.first { it is UploadTaskResult.Failure } as UploadTaskResult.Failure).noteId
        assertEquals("note-2", failedId)
        assertEquals(setOf("note-1", "note-3"), storageWrites.toSet())
    }

    // ═══════════════════════════════════════════════
    // Retry-Logik
    // ═══════════════════════════════════════════════

    @Test
    fun `retry succeeds on second attempt`() = runTest {
        val sardine = mockSardine()
        val storageMutex = Mutex()
        val storageWrites = mutableListOf<String>()
        var callCount = 0

        every { sardine.put(any<String>(), any<ByteArray>(), any<String>()) } answers {
            callCount++
            if (callCount == 1) throw IOException("Transient error")
            // Zweiter Versuch erfolgreich
        }

        val result = simulateUploadWithRetry(
            sardine, "note-retry", storageMutex, storageWrites,
            maxRetries = 2, retryDelayMs = 1L
        )

        assertTrue("Should succeed after retry", result is UploadTaskResult.Success)
        assertTrue("Should have retried", callCount >= 2)
        assertEquals(listOf("note-retry"), storageWrites)
    }

    @Test
    fun `retry exhausted returns Failure`() = runTest {
        val sardine = mockSardine()
        val storageMutex = Mutex()
        val storageWrites = mutableListOf<String>()

        every { sardine.put(any<String>(), any<ByteArray>(), any<String>()) } throws IOException("Persistent failure")

        val result = simulateUploadWithRetry(
            sardine, "note-fail", storageMutex, storageWrites,
            maxRetries = 2, retryDelayMs = 1L
        )

        assertTrue("Should fail after all retries", result is UploadTaskResult.Failure)
        assertTrue(storageWrites.isEmpty())
        verify(exactly = 3) { sardine.put(any<String>(), any<ByteArray>(), any<String>()) }
    }

    // ═══════════════════════════════════════════════
    // CancellationException wird nicht verschluckt
    // ═══════════════════════════════════════════════

    @Test(expected = CancellationException::class)
    fun `CancellationException is propagated not caught`() = runTest {
        val sardine = mockSardine()
        val storageMutex = Mutex()
        val storageWrites = mutableListOf<String>()

        every { sardine.put(any<String>(), any<ByteArray>(), any<String>()) } throws CancellationException("Job cancelled")

        simulateUploadWithRetry(
            sardine, "note-cancel", storageMutex, storageWrites,
            maxRetries = 2
        )
    }

    // ═══════════════════════════════════════════════
    // Progress-Callback
    // ═══════════════════════════════════════════════

    @Test
    fun `progress callback is called for each completed upload`() = runTest {
        val sardine = mockSardine()
        val storageMutex = Mutex()
        val storageWrites = mutableListOf<String>()
        val semaphore = Semaphore(3)
        val completedCount = AtomicInteger(0)
        val progressCalls = mutableListOf<Pair<Int, Int>>()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)

        val noteIds = listOf("note-1", "note-2", "note-3")
        val total = noteIds.size

        coroutineScope {
            noteIds.map { noteId ->
                async(dispatcher) {
                    semaphore.withPermit {
                        val result = simulateUpload(sardine, noteId, storageMutex, storageWrites)
                        val completed = completedCount.incrementAndGet()
                        synchronized(progressCalls) {
                            progressCalls.add(completed to total)
                        }
                        result
                    }
                }
            }.awaitAll()
        }

        assertEquals(3, progressCalls.size)
        assertTrue(progressCalls.all { it.second == 3 })
        assertEquals(setOf(1, 2, 3), progressCalls.map { it.first }.toSet())
    }

    // ═══════════════════════════════════════════════
    // Storage-Mutex serialisiert Writes
    // ═══════════════════════════════════════════════

    @Test
    fun `storage mutex prevents concurrent writes`() = runTest {
        val sardine = mockSardine()
        val storageMutex = Mutex()
        val storageWrites = mutableListOf<String>()
        val concurrentWrites = AtomicInteger(0)
        val maxConcurrentWrites = AtomicInteger(0)
        val semaphore = Semaphore(5)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)

        val noteIds = (1..10).map { "note-$it" }

        coroutineScope {
            noteIds.map { noteId ->
                async(dispatcher) {
                    semaphore.withPermit {
                        sardine.put("url/$noteId.json", "{}".toByteArray(), "application/json")
                        storageMutex.withLock {
                            val current = concurrentWrites.incrementAndGet()
                            maxConcurrentWrites.updateAndGet { maxOf(it, current) }
                            storageWrites.add(noteId)
                            concurrentWrites.decrementAndGet()
                        }
                    }
                }
            }.awaitAll()
        }

        assertEquals(10, storageWrites.size)
        assertEquals(
            "Concurrent writes should be exactly 1 (serialized by mutex)",
            1,
            maxConcurrentWrites.get()
        )
    }

    // ═══════════════════════════════════════════════
    // Batch-E-Tag-Fetch (Opt. 4)
    // ═══════════════════════════════════════════════

    @Test
    fun `batch etag fetch via list depth 1 extracts etags correctly`() = runTest {
        val sardine = mockSardine()

        val resources = listOf(
            mockk<DavResource>(relaxed = true).apply {
                every { name } returns "note-1.json"
                every { etag } returns "\"etag-aaa\""
            },
            mockk<DavResource>(relaxed = true).apply {
                every { name } returns "note-2.json"
                every { etag } returns "\"etag-bbb\""
            },
            mockk<DavResource>(relaxed = true).apply {
                every { name } returns "note-3.json"
                every { etag } returns "\"etag-ccc\""
            },
            mockk<DavResource>(relaxed = true).apply {
                every { name } returns ""  // Parent directory entry
                every { etag } returns null
            }
        )
        every { sardine.list(any(), eq(1)) } returns resources

        val successfulNoteIds = setOf("note-1", "note-3")  // note-2 war kein Upload
        val allResources = sardine.list("https://server/notes/", 1)

        val batchEtagUpdates = mutableMapOf<String, String?>()
        for (resource in allResources) {
            val filename = resource.name
            if (!filename.endsWith(".json")) continue
            val noteId = filename.removeSuffix(".json")
            if (noteId in successfulNoteIds) {
                batchEtagUpdates["etag_json_$noteId"] = resource.etag
            }
        }

        assertEquals(2, batchEtagUpdates.size)
        assertEquals("\"etag-aaa\"", batchEtagUpdates["etag_json_note-1"])
        assertEquals("\"etag-ccc\"", batchEtagUpdates["etag_json_note-3"])
        assertFalse(batchEtagUpdates.containsKey("etag_json_note-2"))
    }

    @Test
    fun `batch etag fetch detects missing etags and invalidates`() = runTest {
        val sardine = mockSardine()

        val resources = listOf(
            mockk<DavResource>(relaxed = true).apply {
                every { name } returns "note-1.json"
                every { etag } returns "\"etag-aaa\""
            }
        )
        every { sardine.list(any(), eq(1)) } returns resources

        val successfulNoteIds = setOf("note-1", "note-2")
        val allResources = sardine.list("https://server/notes/", 1)

        val batchEtagUpdates = mutableMapOf<String, String?>()
        for (resource in allResources) {
            val filename = resource.name
            if (!filename.endsWith(".json")) continue
            val noteId = filename.removeSuffix(".json")
            if (noteId in successfulNoteIds) {
                batchEtagUpdates["etag_json_$noteId"] = resource.etag
            }
        }

        // Fehlende E-Tags invalidieren
        val foundIds = batchEtagUpdates.keys.map { it.removePrefix("etag_json_") }.toSet()
        val missingEtags = successfulNoteIds - foundIds
        for (noteId in missingEtags) {
            batchEtagUpdates["etag_json_$noteId"] = null
        }

        assertEquals(2, batchEtagUpdates.size)
        assertEquals("\"etag-aaa\"", batchEtagUpdates["etag_json_note-1"])
        assertNull(batchEtagUpdates["etag_json_note-2"])
    }

    @Suppress("SwallowedException")
    @Test
    fun `batch etag fetch handles list failure gracefully`() = runTest {
        val sardine = mockSardine()
        every { sardine.list(any(), eq(1)) } throws IOException("PROPFIND failed")

        val successfulNoteIds = setOf("note-1", "note-2")

        val invalidationMap = try {
            sardine.list("https://server/notes/", 1)
            emptyMap<String, String?>()  // Sollte nicht erreicht werden
        } catch (e: Exception) {
            successfulNoteIds.associate { "etag_json_$it" to null as String? }
        }

        assertEquals(2, invalidationMap.size)
        assertNull(invalidationMap["etag_json_note-1"])
        assertNull(invalidationMap["etag_json_note-2"])
    }

    // ═══════════════════════════════════════════════
    // notes-md/ Exists-Cache (Opt. 1)
    // ═══════════════════════════════════════════════

    @Test
    fun `markdown dir check is not called when markdownDirExists is true`() = runTest {
        val sardine = mockSardine()
        val storageMutex = Mutex()
        val storageWrites = mutableListOf<String>()
        val semaphore = Semaphore(3)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val markdownDirExists = true

        val noteIds = listOf("note-1", "note-2", "note-3")

        coroutineScope {
            noteIds.map { noteId ->
                async(dispatcher) {
                    semaphore.withPermit {
                        simulateUpload(
                            sardine, noteId, storageMutex, storageWrites,
                            markdownExportEnabled = true,
                            markdownDirExists = markdownDirExists
                        )
                    }
                }
            }.awaitAll()
        }

        // exists(notes-md/) sollte NIE aufgerufen werden wenn markdownDirExists=true
        verify(exactly = 0) { sardine.exists(match<String> { it.contains("notes-md") }) }
        // MD-put() sollte 3× aufgerufen werden
        verify(exactly = 3) { sardine.put(match<String> { it.endsWith(".md") }, any<ByteArray>(), eq("text/markdown")) }
    }

    @Test
    fun `markdown dir check is called when markdownDirExists is false`() = runTest {
        val sardine = mockSardine()
        val storageMutex = Mutex()
        val storageWrites = mutableListOf<String>()

        every { sardine.exists(match<String> { it.contains("notes-md") }) } returns true

        simulateUpload(
            sardine, "note-1", storageMutex, storageWrites,
            markdownExportEnabled = true,
            markdownDirExists = false
        )

        verify(exactly = 1) { sardine.exists(match<String> { it.contains("notes-md") }) }
    }

    // ═══════════════════════════════════════════════
    // ConcurrentHashMap E-Tag Safety
    // ═══════════════════════════════════════════════

    @Test
    fun `ConcurrentHashMap handles parallel etag writes safely`() = runTest {
        val etagUpdates = ConcurrentHashMap<String, String>()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)

        val noteIds = (1..20).map { "note-$it" }

        coroutineScope {
            noteIds.map { noteId ->
                async(dispatcher) {
                    etagUpdates["etag_json_$noteId"] = "\"etag-$noteId\""
                }
            }.awaitAll()
        }

        assertEquals(20, etagUpdates.size)
        noteIds.forEach { noteId ->
            assertEquals("\"etag-$noteId\"", etagUpdates["etag_json_$noteId"])
        }
    }
}
