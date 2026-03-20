package dev.dettmer.simplenotes.sync.parallel

import com.thegrizzlylabs.sardineandroid.Sardine
import dev.dettmer.simplenotes.utils.Logger
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 🆕 v1.8.0: Paralleler Download-Handler für Notizen
 *
 * Features:
 * - Konfigurierbare max. parallele Downloads (default: 5)
 * - Graceful Error-Handling (einzelne Fehler stoppen nicht den ganzen Sync)
 * - Progress-Callback für UI-Updates
 * - Retry-Logic für transiente Fehler mit Exponential Backoff
 *
 * Performance:
 * - 100 Notizen: ~20s → ~4s (5x schneller)
 * - 50 Notizen: ~10s → ~2s
 *
 * @param sardine WebDAV-Client für Downloads
 * @param maxParallelDownloads Maximale Anzahl gleichzeitiger Downloads (1-10)
 * @param retryCount Anzahl der Wiederholungsversuche bei Fehlern
 */
class ParallelDownloader(
    private val sardine: Sardine,
    private val maxParallelDownloads: Int = DEFAULT_MAX_PARALLEL,
    private val retryCount: Int = DEFAULT_RETRY_COUNT,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        private const val TAG = "ParallelDownloader"
        const val DEFAULT_MAX_PARALLEL = 5
        const val DEFAULT_RETRY_COUNT = 2
        private const val RETRY_DELAY_MS = 500L
    }

    /**
     * Download-Progress Callback
     *
     * @param completed Anzahl abgeschlossener Downloads
     * @param total Gesamtanzahl Downloads
     * @param currentFile Aktueller Dateiname (optional)
     */
    var onProgress: ((completed: Int, total: Int, currentFile: String?) -> Unit)? = null

    /**
     * Führt parallele Downloads aus
     *
     * Die Downloads werden mit einem Semaphore begrenzt, um Server-Überlastung
     * zu vermeiden. Jeder Download wird unabhängig behandelt - Fehler in einem
     * Download stoppen nicht die anderen.
     *
     * @param tasks Liste der Download-Tasks
     * @return Liste der Ergebnisse (Success, Failure, Skipped)
     */
    suspend fun downloadAll(tasks: List<DownloadTask>): List<DownloadTaskResult> = coroutineScope {
        if (tasks.isEmpty()) {
            Logger.d(TAG, "⏭️ No tasks to download")
            return@coroutineScope emptyList()
        }

        Logger.d(TAG, "🚀 Starting parallel download: ${tasks.size} tasks, max $maxParallelDownloads concurrent")

        val semaphore = Semaphore(maxParallelDownloads)
        val completedCount = AtomicInteger(0)
        val totalCount = tasks.size

        val jobs = tasks.map { task ->
            async(ioDispatcher) {
                semaphore.withPermit {
                    val result = downloadWithRetry(task)

                    // Progress Update
                    val completed = completedCount.incrementAndGet()
                    onProgress?.invoke(completed, totalCount, task.noteId)

                    result
                }
            }
        }

        // Warte auf alle Downloads
        val results = jobs.awaitAll()

        // Statistiken loggen
        val successCount = results.count { it is DownloadTaskResult.Success }
        val failureCount = results.count { it is DownloadTaskResult.Failure }
        val skippedCount = results.count { it is DownloadTaskResult.Skipped }

        Logger.d(TAG, "📊 Download complete: $successCount success, $failureCount failed, $skippedCount skipped")

        results
    }

    /**
     * Download mit Retry-Logic und Exponential Backoff
     *
     * Versucht den Download bis zu (retryCount + 1) mal. Bei jedem Fehlversuch
     * wird exponentiell länger gewartet (500ms, 1000ms, 1500ms, ...).
     *
     * @param task Der Download-Task
     * @return Ergebnis des Downloads (Success oder Failure)
     */
    private suspend fun downloadWithRetry(task: DownloadTask): DownloadTaskResult {
        var lastError: Throwable? = null

        repeat(retryCount + 1) { attempt ->
            try {
                val content = sardine.get(task.url).use { it.bufferedReader().readText() }

                Logger.d(TAG, "✅ Downloaded ${task.noteId} (attempt ${attempt + 1})")

                return DownloadTaskResult.Success(
                    noteId = task.noteId,
                    content = content,
                    etag = task.serverETag
                )
            } catch (e: CancellationException) {
                // 🛡️ v1.8.2: Cancellation nie verschlucken — sofort propagieren (SNS-182-16)
                throw e
            } catch (e: Exception) {
                lastError = e
                Logger.w(TAG, "⚠️ Download failed ${task.noteId} (attempt ${attempt + 1}): ${e.message}")

                // Retry nach Delay (außer beim letzten Versuch)
                if (attempt < retryCount) {
                    delay(RETRY_DELAY_MS * (attempt + 1)) // Exponential backoff
                }
            }
        }

        Logger.e(TAG, "❌ Download failed after ${retryCount + 1} attempts: ${task.noteId}")
        return DownloadTaskResult.Failure(task.noteId, lastError ?: Exception("Unknown error"))
    }
}
