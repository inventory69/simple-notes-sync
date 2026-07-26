package dev.dettmer.simplenotes.sync

import android.content.Context
import android.content.SharedPreferences
import dev.dettmer.simplenotes.storage.AssetStore
import dev.dettmer.simplenotes.sync.webdav.WebDavClient
import dev.dettmer.simplenotes.sync.webdav.WebDavResource
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AssetSyncManagerTest {
    private lateinit var tmpDir: File
    private lateinit var assetStore: AssetStore
    private lateinit var prefs: SharedPreferences
    private lateinit var manager: AssetSyncManager

    @Before fun setUp() {
        tmpDir = Files.createTempDirectory("asset-sync-test").toFile()
        val cache = File(tmpDir, "cache").apply { mkdirs() }
        val context = mockk<Context> {
            every { filesDir } returns tmpDir
            every { cacheDir } returns cache
        }
        assetStore = AssetStore(context, freeBytes = { Long.MAX_VALUE / 2 })
        prefs = mockk(relaxed = true) {
            every { getInt(any(), any()) } returns 3
        }
        manager = AssetSyncManager(
            prefs = prefs,
            assetStore = assetStore,
            urlBuilder = SyncUrlBuilder(prefs),
            connectionManager = mockk(relaxed = true),
            ioDispatcher = Dispatchers.Unconfined,
            mimeForExtension = { "image/webp" }
        )
    }

    @After fun tearDown() {
        tmpDir.deleteRecursively()
    }

    // WebDavResource ist eine reine Data-Class → echtes Objekt statt Mock.
    private fun davResource(name: String, modifiedMs: Long? = null): WebDavResource = WebDavResource(
        href = java.net.URI("/notes-assets/$name"),
        modified = modifiedMs?.let { java.util.Date(it) },
        contentLength = 0L,
        isDirectory = false,
        etag = null
    )

    @Test fun `uploadMissing only uploads referenced assets that exist locally but not on server`() = runBlocking {
        assetStore.saveAssetAs("bytes-a".toByteArray(), "a.webp")
        assetStore.saveAssetAs("bytes-b".toByteArray(), "b.webp")
        // "c.webp" is referenced but never saved locally — must be skipped, nothing to upload.

        val webdav = mockk<WebDavClient>(relaxed = true)
        val uploadedUrl = slot<String>()
        every { webdav.put(capture(uploadedUrl), any<ByteArray>(), any()) } returns null

        val uploaded = manager.uploadMissing(
            webdav,
            "http://server/notes",
            referenced = setOf("a.webp", "b.webp", "c.webp"),
            serverAssets = mapOf("b.webp" to davResource("b.webp")) // b already on server
        )

        assertEquals(1, uploaded)
        assertTrue(uploadedUrl.captured.endsWith("a.webp"))
    }

    @Test fun `downloadMissing only downloads referenced assets on server but missing locally`() = runBlocking {
        assetStore.saveAssetAs("already here".toByteArray(), "have.webp")

        val webdav = mockk<WebDavClient>(relaxed = true)
        every { webdav.get(any()) } returns ByteArrayInputStream("downloaded content".toByteArray())

        val downloaded = manager.downloadMissing(
            webdav,
            "http://server/notes",
            referenced = setOf("have.webp", "missing.webp", "not-on-server.webp"),
            serverAssets = mapOf(
                "have.webp" to davResource("have.webp"),
                "missing.webp" to davResource("missing.webp")
            )
        )

        assertEquals(1, downloaded)
        assertTrue(assetStore.getAssetFile("missing.webp").exists())
        assertEquals("downloaded content", assetStore.getAssetFile("missing.webp").readText())
        assertEquals("already here", assetStore.getAssetFile("have.webp").readText()) // untouched
        assertTrue(!assetStore.getAssetFile("not-on-server.webp").exists())
    }

    @Test fun `garbageCollect deletes local and remote orphans but keeps referenced assets`() = runBlocking {
        val old = System.currentTimeMillis() - AssetGc.GRACE_PERIOD_MS - 1000L
        val keptFile = assetStore.getAssetFile("kept.webp")
        val orphanFile = assetStore.getAssetFile("orphan.webp")
        runBlocking {
            assetStore.saveAssetAs("kept".toByteArray(), "kept.webp")
            assetStore.saveAssetAs("orphan".toByteArray(), "orphan.webp")
        }
        keptFile.setLastModified(old)
        orphanFile.setLastModified(old)

        val webdav = mockk<WebDavClient>(relaxed = true)
        val deletedUrls = slot<String>()
        every { webdav.delete(capture(deletedUrls)) } just Runs

        val note = dev.dettmer.simplenotes.models.Note(
            id = "n1",
            title = "t",
            content = "![](.assets/kept.webp)",
            deviceId = "dev"
        )

        manager.garbageCollect(
            webdav,
            "http://server/notes",
            allNotes = listOf(note),
            serverAssets = mapOf("orphan.webp" to davResource("orphan.webp", modifiedMs = old)),
            allowRemoteSweep = true
        )

        assertTrue(keptFile.exists())
        assertTrue(!orphanFile.exists())
        assertTrue(deletedUrls.captured.endsWith("orphan.webp"))
    }

    @Test fun `garbageCollect skips remote sweep when guard disallows it`() = runBlocking {
        val old = System.currentTimeMillis() - AssetGc.GRACE_PERIOD_MS - 1000L
        assetStore.saveAssetAs("orphan".toByteArray(), "orphan.webp")
        assetStore.getAssetFile("orphan.webp").setLastModified(old)

        val webdav = mockk<WebDavClient>(relaxed = true)

        manager.garbageCollect(
            webdav,
            "http://server/notes",
            allNotes = emptyList(),
            serverAssets = mapOf("orphan.webp" to davResource("orphan.webp", modifiedMs = old)),
            allowRemoteSweep = false
        )

        verify(exactly = 0) { webdav.delete(any()) }
    }
}
