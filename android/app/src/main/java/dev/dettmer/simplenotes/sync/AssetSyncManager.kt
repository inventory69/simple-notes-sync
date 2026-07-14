package dev.dettmer.simplenotes.sync

import android.content.SharedPreferences
import android.webkit.MimeTypeMap
import com.thegrizzlylabs.sardineandroid.DavResource
import com.thegrizzlylabs.sardineandroid.Sardine
import dev.dettmer.simplenotes.models.Note
import dev.dettmer.simplenotes.storage.AssetStore
import dev.dettmer.simplenotes.utils.AssetReferences
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 🆕 Bild-Attachments: Sync-Gegenstück zu [dev.dettmer.simplenotes.storage.AssetStore].
 * Verantwortlich für Upload/Download/GC von Bild-Assets im Geschwister-Ordner `-assets/`
 * (siehe [SyncUrlBuilder.getAssetsUrl]). Assets sind content-addressed und immutable — es gibt
 * keine Konflikte, kein Deletion-Tracking, nur Mark-and-Sweep-GC (siehe [AssetGc]).
 */
internal class AssetSyncManager(
    private val prefs: SharedPreferences,
    private val assetStore: AssetStore,
    private val urlBuilder: SyncUrlBuilder,
    private val connectionManager: ConnectionManager,
    private val ioDispatcher: CoroutineDispatcher,
    // ponytail: injectable so tests avoid the Android MimeTypeMap stub (returns null under plain unit tests)
    private val mimeForExtension: (String) -> String? = { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
) {
    companion object {
        private const val TAG = "AssetSyncManager"
        private const val RETRY_COUNT = 2
        private const val RETRY_DELAY_MS = 500L
        private const val FALLBACK_MIME = "application/octet-stream"
    }

    fun ensureAssetsDirectoryExists(sardine: Sardine, serverUrl: String) {
        if (connectionManager.assetsDirEnsured) return
        try {
            val url = urlBuilder.getAssetsUrl(serverUrl)
            val dirExists = try {
                sardine.exists(url)
            } catch (e: IOException) {
                Logger.w(TAG, "⚠️ -assets/ exists() check failed: ${e.message}, trying list()")
                try {
                    sardine.list(url)
                    true
                } catch (_: IOException) {
                    false
                }
            }
            if (!dirExists) {
                sardine.createDirectory(url)
                Logger.d(TAG, "📁 Created -assets/ directory")
            }
            connectionManager.assetsDirEnsured = true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to ensure -assets/ directory: ${e.message}")
        }
    }

    /** Ein PROPFIND für alle drei Diffs (Upload, Download, GC) — siehe Plan-Vorgabe. */
    fun listServerAssets(sardine: Sardine, serverUrl: String): Map<String, DavResource> = try {
        sardine.list(urlBuilder.getAssetsUrl(serverUrl))
            .filterNot { it.isDirectory }
            .associateBy { it.name }
    } catch (e: Exception) {
        Logger.w(TAG, "⚠️ listServerAssets failed: ${e.message}")
        emptyMap()
    }

    /** Lokal vorhandene, referenzierte Assets hochladen, die auf dem Server fehlen. */
    suspend fun uploadMissing(
        sardine: Sardine,
        serverUrl: String,
        referenced: Set<String>,
        serverAssets: Map<String, DavResource>
    ): Int {
        val toUpload = referenced.filter { it !in serverAssets && assetStore.getAssetFile(it).exists() }
        if (toUpload.isEmpty()) return 0
        Logger.d(TAG, "🚀 Uploading ${toUpload.size} asset(s)")

        val successCount = AtomicInteger(0)
        runParallel(toUpload) { name ->
            if (putWithRetry(sardine, serverUrl, name)) successCount.incrementAndGet()
        }
        return successCount.get()
    }

    /** Referenzierte Assets herunterladen, die lokal fehlen, aber auf dem Server liegen. */
    suspend fun downloadMissing(
        sardine: Sardine,
        serverUrl: String,
        referenced: Set<String>,
        serverAssets: Map<String, DavResource>
    ): Int {
        val toDownload = referenced.filter { it in serverAssets && !assetStore.getAssetFile(it).exists() }
        if (toDownload.isEmpty()) return 0
        Logger.d(TAG, "🚀 Downloading ${toDownload.size} asset(s)")

        val successCount = AtomicInteger(0)
        runParallel(toDownload) { name ->
            if (getWithRetry(sardine, serverUrl, name)) successCount.incrementAndGet()
        }
        return successCount.get()
    }

    /**
     * Mark-and-Sweep-GC. [allowRemoteSweep] muss der Aufrufer analog `ALL_DELETED_GUARD_THRESHOLD`
     * auf `false` setzen, wenn die Notizliste leer war oder die Download-Phase fehlschlug.
     */
    suspend fun garbageCollect(
        sardine: Sardine,
        serverUrl: String,
        allNotes: List<Note>,
        serverAssets: Map<String, DavResource>,
        allowRemoteSweep: Boolean
    ) {
        val referenced = AssetReferences.extractAllReferenced(allNotes)
        val localMtimes = assetStore.listAssets().associate { it.name to it.lastModified() }
        val serverMtimes = serverAssets.mapValues { (_, res) -> res.modified?.time }
        val targets = AssetGc.computeTargets(
            referenced = referenced,
            localMtimes = localMtimes,
            serverMtimes = serverMtimes,
            now = System.currentTimeMillis(),
            allowRemoteSweep = allowRemoteSweep
        )

        targets.localToDelete.forEach { name ->
            if (assetStore.deleteAsset(name)) Logger.d(TAG, "🗑️ GC: deleted local orphan asset $name")
        }
        targets.remoteToDelete.forEach { name ->
            try {
                sardine.delete(urlBuilder.getAssetUrl(serverUrl, name))
                Logger.d(TAG, "🗑️ GC: deleted remote orphan asset $name")
            } catch (e: IOException) {
                Logger.w(TAG, "⚠️ GC: failed to delete remote asset $name: ${e.message}")
            }
        }
    }

    private suspend fun runParallel(names: List<String>, action: suspend (String) -> Unit) {
        val maxParallel = prefs.getInt(
            Constants.KEY_MAX_PARALLEL_CONNECTIONS,
            Constants.DEFAULT_MAX_PARALLEL_CONNECTIONS
        ).coerceAtMost(Constants.MAX_PARALLEL_UPLOADS_CAP)
        val semaphore = Semaphore(maxParallel)
        coroutineScope {
            names.map { name ->
                async(ioDispatcher) {
                    semaphore.withPermit { action(name) }
                }
            }.awaitAll()
        }
    }

    private suspend fun putWithRetry(sardine: Sardine, serverUrl: String, name: String): Boolean {
        val file = assetStore.getAssetFile(name)
        val url = urlBuilder.getAssetUrl(serverUrl, name)
        val mime = mimeForExtension(file.extension) ?: FALLBACK_MIME

        repeat(RETRY_COUNT + 1) { attempt ->
            try {
                sardine.put(url, file.readBytes(), mime)
                Logger.d(TAG, "📤 Uploaded asset: $name")
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(TAG, "⚠️ Asset upload failed $name (attempt ${attempt + 1}): ${e.message}")
                if (attempt < RETRY_COUNT) delay(RETRY_DELAY_MS * (attempt + 1))
            }
        }
        Logger.e(TAG, "❌ Asset upload failed after retries: $name")
        return false
    }

    /** Binärer Download-Zwilling zu [dev.dettmer.simplenotes.sync.parallel.ParallelDownloader] —
     * dessen `readText()` würde Bilddaten korrumpieren. */
    private suspend fun getWithRetry(sardine: Sardine, serverUrl: String, name: String): Boolean {
        val url = urlBuilder.getAssetUrl(serverUrl, name)

        repeat(RETRY_COUNT + 1) { attempt ->
            try {
                val bytes = sardine.get(url).use { it.readBytes() }
                assetStore.saveAssetAs(bytes, name)
                Logger.d(TAG, "📥 Downloaded asset: $name")
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(TAG, "⚠️ Asset download failed $name (attempt ${attempt + 1}): ${e.message}")
                if (attempt < RETRY_COUNT) delay(RETRY_DELAY_MS * (attempt + 1))
            }
        }
        Logger.e(TAG, "❌ Asset download failed after retries: $name")
        return false
    }
}
