package dev.dettmer.simplenotes.storage

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AssetStoreTest {
    private lateinit var tmpDir: File
    private lateinit var store: AssetStore

    @Before fun setUp() {
        tmpDir = Files.createTempDirectory("asset-store-test").toFile()
        val cache = File(tmpDir, "cache").apply { mkdirs() }
        val context = mockk<Context> {
            every { filesDir } returns tmpDir
            every { cacheDir } returns cache
        }
        store = AssetStore(context, freeBytes = { Long.MAX_VALUE / 2 })
    }

    @After fun tearDown() {
        tmpDir.deleteRecursively()
    }

    @Test fun `saveAsset writes file named by content hash`() = runBlocking {
        val name = store.saveAsset("hello".toByteArray(), "webp")
        assertTrue(name.endsWith(".webp"))
        assertTrue(store.getAssetFile(name).exists())
        assertEquals("hello", store.getAssetFile(name).readText())
    }

    @Test fun `saveAsset dedups identical content to the same name`() = runBlocking {
        val name1 = store.saveAsset("same bytes".toByteArray(), "webp")
        val name2 = store.saveAsset("same bytes".toByteArray(), "webp")
        assertEquals(name1, name2)
        assertEquals(1, store.listAssets().size)
    }

    @Test fun `different content yields different names`() = runBlocking {
        val name1 = store.saveAsset("a".toByteArray(), "webp")
        val name2 = store.saveAsset("b".toByteArray(), "webp")
        assertTrue(name1 != name2)
    }

    @Test fun `saveAssetAs writes bytes under the given name without re-hashing`() = runBlocking {
        store.saveAssetAs("server bytes".toByteArray(), "knownname.webp")
        val file = store.getAssetFile("knownname.webp")
        assertTrue(file.exists())
        assertEquals("server bytes", file.readText())
    }

    @Test fun `saveAssetAs is a no-op if the file already exists`() = runBlocking {
        store.saveAssetAs("first".toByteArray(), "x.webp")
        store.saveAssetAs("second".toByteArray(), "x.webp")
        assertEquals("first", store.getAssetFile("x.webp").readText())
    }

    @Test fun `deleteAsset removes the file`() = runBlocking {
        val name = store.saveAsset("to-delete".toByteArray(), "webp")
        assertTrue(store.deleteAsset(name))
        assertFalse(store.getAssetFile(name).exists())
    }

    @Test fun `saveAsset throws when not enough free space`() = runBlocking {
        val tightStore = AssetStore(
            mockk<Context> {
                every { filesDir } returns tmpDir
                every { cacheDir } returns File(tmpDir, "cache")
            },
            freeBytes = { 0L }
        )
        try {
            tightStore.saveAsset("too big".toByteArray(), "webp")
            org.junit.Assert.fail("expected IOException")
        } catch (_: java.io.IOException) {
            // expected
        }
    }
}
