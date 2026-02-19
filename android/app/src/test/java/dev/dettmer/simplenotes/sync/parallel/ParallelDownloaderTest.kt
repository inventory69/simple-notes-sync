package dev.dettmer.simplenotes.sync.parallel

import com.thegrizzlylabs.sardineandroid.DavResource
import com.thegrizzlylabs.sardineandroid.Sardine
import io.mockk.*
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Unit-Tests für ParallelDownloader mit MockK.
 *
 * Testet die echte Download-Logik mit gemocktem Sardine-Client.
 */
class ParallelDownloaderTest {

    private fun mockSardine(): Sardine = mockk(relaxed = true)

    private fun createTask(noteId: String, url: String = "https://server/notes/$noteId.json") =
        DownloadTask(
            noteId = noteId,
            url = url,
            resource = mockk<DavResource>(relaxed = true),
            serverETag = "etag-$noteId",
            serverModified = System.currentTimeMillis()
        )

    // ═══════════════════════════════════════════════
    // Leere Task-Liste
    // ═══════════════════════════════════════════════

    @Test
    fun `downloadAll with empty list returns empty results`() = runTest {
        val sardine = mockSardine()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val downloader = ParallelDownloader(sardine, ioDispatcher = dispatcher)

        val results = downloader.downloadAll(emptyList())
        assertTrue(results.isEmpty())
    }

    // ═══════════════════════════════════════════════
    // Erfolgreiche Downloads
    // ═══════════════════════════════════════════════

    @Test
    fun `downloadAll single task succeeds`() = runTest {
        val sardine = mockSardine()
        val content = """{"id":"note-1","title":"Test"}"""
        every { sardine.get(any()) } returns ByteArrayInputStream(content.toByteArray())

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val downloader = ParallelDownloader(
            sardine,
            maxParallelDownloads = 2,
            retryCount = 0,
            ioDispatcher = dispatcher
        )
        val tasks = listOf(createTask("note-1"))
        val results = downloader.downloadAll(tasks)

        assertEquals(1, results.size)
        assertTrue(results[0] is DownloadTaskResult.Success)
        val success = results[0] as DownloadTaskResult.Success
        assertEquals("note-1", success.noteId)
        assertEquals(content, success.content)
        assertEquals("etag-note-1", success.etag)
    }

    @Test
    fun `downloadAll multiple tasks all succeed`() = runTest {
        val sardine = mockSardine()
        every { sardine.get(any()) } answers {
            val url = firstArg<String>()
            val noteId = url.substringAfterLast("/").removeSuffix(".json")
            ByteArrayInputStream("""{"id":"$noteId"}""".toByteArray())
        }

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val downloader = ParallelDownloader(
            sardine,
            maxParallelDownloads = 3,
            retryCount = 0,
            ioDispatcher = dispatcher
        )
        val tasks = (1..5).map { createTask("note-$it") }
        val results = downloader.downloadAll(tasks)

        assertEquals(5, results.size)
        assertEquals(5, results.count { it is DownloadTaskResult.Success })
    }

    // ═══════════════════════════════════════════════
    // Fehlerbehandlung
    // ═══════════════════════════════════════════════

    @Test
    fun `downloadAll single task failure returns Failure`() = runTest {
        val sardine = mockSardine()
        every { sardine.get(any()) } throws IOException("Connection refused")

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val downloader = ParallelDownloader(
            sardine,
            maxParallelDownloads = 2,
            retryCount = 0,
            ioDispatcher = dispatcher
        )
        val tasks = listOf(createTask("note-fail"))
        val results = downloader.downloadAll(tasks)

        assertEquals(1, results.size)
        assertTrue(results[0] is DownloadTaskResult.Failure)
        val failure = results[0] as DownloadTaskResult.Failure
        assertEquals("note-fail", failure.noteId)
        assertTrue(failure.error is IOException)
    }

    @Test
    fun `downloadAll mixed success and failure`() = runTest {
        val sardine = mockSardine()
        every { sardine.get(match { it.contains("note-1") }) } returns
            ByteArrayInputStream("""{"id":"note-1"}""".toByteArray())
        every { sardine.get(match { it.contains("note-2") }) } throws IOException("Timeout")
        every { sardine.get(match { it.contains("note-3") }) } returns
            ByteArrayInputStream("""{"id":"note-3"}""".toByteArray())

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val downloader = ParallelDownloader(
            sardine,
            maxParallelDownloads = 3,
            retryCount = 0,
            ioDispatcher = dispatcher
        )
        val tasks = listOf(createTask("note-1"), createTask("note-2"), createTask("note-3"))
        val results = downloader.downloadAll(tasks)

        assertEquals(3, results.size)
        assertEquals(2, results.count { it is DownloadTaskResult.Success })
        assertEquals(1, results.count { it is DownloadTaskResult.Failure })
    }

    // ═══════════════════════════════════════════════
    // Retry-Logik
    // ═══════════════════════════════════════════════

    @Test
    fun `downloadAll retries on failure and succeeds`() = runTest {
        val sardine = mockSardine()
        var callCount = 0
        every { sardine.get(any()) } answers {
            callCount++
            if (callCount == 1) {
                throw IOException("First attempt fails")
            }
            ByteArrayInputStream("""{"id":"note-retry"}""".toByteArray())
        }

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val downloader = ParallelDownloader(
            sardine,
            maxParallelDownloads = 1,
            retryCount = 2,
            ioDispatcher = dispatcher
        )
        val tasks = listOf(createTask("note-retry"))
        val results = downloader.downloadAll(tasks)

        assertEquals(1, results.size)
        assertTrue("Should succeed after retry", results[0] is DownloadTaskResult.Success)
        assertTrue("Should have called get at least twice", callCount >= 2)
    }

    @Test
    fun `downloadAll fails after all retries exhausted`() = runTest {
        val sardine = mockSardine()
        every { sardine.get(any()) } throws IOException("Persistent failure")

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val downloader = ParallelDownloader(
            sardine,
            maxParallelDownloads = 1,
            retryCount = 2,
            ioDispatcher = dispatcher
        )
        val tasks = listOf(createTask("note-persistent-fail"))
        val results = downloader.downloadAll(tasks)

        assertEquals(1, results.size)
        assertTrue("Should fail after all retries", results[0] is DownloadTaskResult.Failure)

        // Sardine.get() should be called retryCount + 1 times
        verify(exactly = 3) { sardine.get(any()) }
    }

    // ═══════════════════════════════════════════════
    // Progress Callback
    // ═══════════════════════════════════════════════

    @Test
    fun `progress callback is called for each task`() = runTest {
        val sardine = mockSardine()
        every { sardine.get(any()) } returns ByteArrayInputStream("{}".toByteArray())

        val progressCalls = mutableListOf<Triple<Int, Int, String?>>()

        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val downloader = ParallelDownloader(
            sardine,
            maxParallelDownloads = 1,
            retryCount = 0,
            ioDispatcher = dispatcher
        )
        downloader.onProgress = { completed, total, currentFile ->
            progressCalls.add(Triple(completed, total, currentFile))
        }

        val tasks = listOf(createTask("note-1"), createTask("note-2"), createTask("note-3"))
        downloader.downloadAll(tasks)

        assertEquals(3, progressCalls.size)
        // All calls should have total = 3
        assertTrue(progressCalls.all { it.second == 3 })
    }

    // ═══════════════════════════════════════════════
    // Konstanten
    // ═══════════════════════════════════════════════

    @Test
    fun `default constants are correct`() {
        assertEquals(5, ParallelDownloader.DEFAULT_MAX_PARALLEL)
        assertEquals(2, ParallelDownloader.DEFAULT_RETRY_COUNT)
    }
}
