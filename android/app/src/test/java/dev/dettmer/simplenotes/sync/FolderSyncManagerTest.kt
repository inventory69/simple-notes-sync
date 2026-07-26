package dev.dettmer.simplenotes.sync

import android.content.Context
import android.content.SharedPreferences
import dev.dettmer.simplenotes.storage.FolderMeta
import dev.dettmer.simplenotes.storage.FolderStore
import dev.dettmer.simplenotes.sync.webdav.WebDavClient
import dev.dettmer.simplenotes.sync.webdav.WebDavException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FolderSyncManagerTest {
    private lateinit var manager: FolderSyncManager
    private lateinit var folderStore: FolderStore
    private lateinit var tmpDir: File

    private val prefsBacking = mutableMapOf<String, Any?>()

    @Before fun setUp() {
        tmpDir = Files.createTempDirectory("foldersync-test").toFile()
        prefsBacking.clear()
        // Echte Backing-Map: der ETag-Fast-Path liest und schreibt über die Prefs.
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putString(any(), any()) } answers { prefsBacking[firstArg()] = secondArg<String?>(); editor }
        every { editor.putBoolean(any(), any()) } answers { prefsBacking[firstArg()] = secondArg<Boolean>(); editor }
        every { editor.remove(any()) } answers { prefsBacking.remove(firstArg<String>()); editor }
        every { editor.putStringSet(any(), any()) } answers { prefsBacking[firstArg()] = secondArg<Set<String>?>(); editor }
        val prefs = mockk<SharedPreferences>(relaxed = true) {
            every { edit() } returns editor
            every { getString(any(), any()) } answers { prefsBacking[firstArg()] as? String ?: secondArg() }
            every { getBoolean(any(), any()) } answers { prefsBacking[firstArg()] as? Boolean ?: secondArg() }
            // Rückgabe muss mutable sein — mockks secondArg() castet sonst EmptySet auf MutableSet.
            @Suppress("UNCHECKED_CAST")
            every { getStringSet(any(), any()) } answers {
                (prefsBacking[firstArg<String>()] as? Set<String>)?.toMutableSet() ?: mutableSetOf()
            }
        }
        val context = mockk<Context> {
            every { filesDir } returns tmpDir
            every { getSharedPreferences(any(), any()) } returns prefs
        }
        folderStore = FolderStore(context)
        manager = FolderSyncManager(
            urlBuilder = mockk(relaxed = true),
            folderStore = folderStore,
            prefs = prefs
        )
    }

    @org.junit.After fun tearDown() {
        tmpDir.deleteRecursively()
    }

    // ── mergeByName: LWW ──────────────────────────────────────────────────

    @Test fun `mergeByName remote wins when higher updatedAt`() {
        val local = listOf(FolderMeta("A", updatedAt = 100L))
        val remote = listOf(FolderMeta("A", color = "#FF0000", updatedAt = 200L))
        val result = manager.mergeByName(local, remote)
        assertEquals(1, result.size)
        assertEquals("#FF0000", result[0].color)
    }

    @Test fun `mergeByName local wins when higher updatedAt`() {
        val local = listOf(FolderMeta("A", color = "#00FF00", updatedAt = 300L))
        val remote = listOf(FolderMeta("A", updatedAt = 100L))
        val result = manager.mergeByName(local, remote)
        assertEquals("#00FF00", result[0].color)
    }

    @Test fun `mergeByName equal updatedAt keeps local (first)`() {
        val local = listOf(FolderMeta("A", color = "#LOCAL", updatedAt = 100L))
        val remote = listOf(FolderMeta("A", color = "#REMOTE", updatedAt = 100L))
        val result = manager.mergeByName(local, remote)
        assertEquals("#LOCAL", result[0].color)
    }

    @Test fun `mergeByName union of both sides`() {
        val local = listOf(FolderMeta("A", updatedAt = 100L))
        val remote = listOf(FolderMeta("B", updatedAt = 100L))
        val result = manager.mergeByName(local, remote)
        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "A" })
        assertTrue(result.any { it.name == "B" })
    }

    @Test fun `mergeByName tombstone wins against active when higher updatedAt`() {
        val local = listOf(FolderMeta("A", deleted = false, updatedAt = 100L))
        val remote = listOf(FolderMeta("A", deleted = true, updatedAt = 200L))
        val result = manager.mergeByName(local, remote)
        assertTrue(result[0].deleted)
    }

    @Test fun `mergeByName active wins against tombstone when higher updatedAt`() {
        val local = listOf(FolderMeta("A", deleted = false, updatedAt = 300L))
        val remote = listOf(FolderMeta("A", deleted = true, updatedAt = 100L))
        val result = manager.mergeByName(local, remote)
        assertFalse(result[0].deleted)
    }

    @Test fun `mergeByName is case-insensitive`() {
        val local = listOf(FolderMeta("rezepte", updatedAt = 100L))
        val remote = listOf(FolderMeta("Rezepte", color = "#RED", updatedAt = 200L))
        val result = manager.mergeByName(local, remote)
        assertEquals(1, result.size)
        assertEquals("Rezepte", result[0].name)
        assertEquals("#RED", result[0].color)
    }

    @Test fun `mergeByName empty local returns remote`() {
        val remote = listOf(FolderMeta("X", updatedAt = 50L))
        val result = manager.mergeByName(emptyList(), remote)
        assertEquals(1, result.size)
        assertEquals("X", result[0].name)
    }

    @Test fun `mergeByName empty remote returns local`() {
        val local = listOf(FolderMeta("Y", updatedAt = 50L))
        val result = manager.mergeByName(local, emptyList())
        assertEquals(1, result.size)
        assertEquals("Y", result[0].name)
    }

    // ── Gson-Null-Korruption (Regression: NPE in name.lowercase()) ────────────

    /**
     * Gson umgeht Kotlin-Null-Safety: fehlt der `name`-Key, wird das non-null Feld mit `null` befüllt.
     * Vor dem Fix crashte mergeByName hier mit NPE und legte den gesamten Folder-Sync lahm.
     */
    private fun parseFolderMeta(json: String): List<FolderMeta> {
        val type = object : com.google.gson.reflect.TypeToken<List<FolderMeta>>() {}.type
        return com.google.gson.Gson().fromJson(json, type)
    }

    @Test fun `mergeByName drops Gson null-name entry and keeps valid color`() {
        val corrupt = parseFolderMeta("""[{"color":"#BADBAD","updatedAt":999}]""")
        val valid = listOf(FolderMeta("Rezepte", color = "#E6C9A8", updatedAt = 100L))
        val result = manager.mergeByName(corrupt, valid)
        assertEquals(1, result.size)
        assertEquals("Rezepte", result[0].name)
        assertEquals("#E6C9A8", result[0].color)
    }

    @Test fun `mergeByName survives null-name on both sides`() {
        val corrupt = parseFolderMeta("""[{"updatedAt":1},{"name":"","updatedAt":2}]""")
        val result = manager.mergeByName(corrupt, corrupt)
        assertTrue(result.isEmpty())
    }

    // ── sync() → Boolean ──────────────────────────────────────────────────

    private fun webDavWith(json: String): WebDavClient {
        val webdav = mockk<WebDavClient>()
        every { webdav.get(any<String>()) } returns json.byteInputStream()
        every { webdav.put(any(), any<ByteArray>(), any()) } returns null
        return webdav
    }

    @Test fun `sync returns true when remote brings newer color (empty-folder case)`() = runTest {
        folderStore.replaceMeta(listOf(FolderMeta("A", updatedAt = 100L)))
        val webdav = webDavWith("""[{"name":"A","color":"#FF0000","updatedAt":200}]""")
        assertTrue(manager.sync(webdav, "https://server/"))
    }

    @Test fun `sync returns false when merged equals local`() = runTest {
        folderStore.replaceMeta(listOf(FolderMeta("A", color = "#FF0000", updatedAt = 200L)))
        val webdav = webDavWith("""[{"name":"A","color":"#FF0000","updatedAt":200}]""")
        assertFalse(manager.sync(webdav, "https://server/"))
    }

    @Test fun `sync returns false when webdav throws`() = runTest {
        folderStore.replaceMeta(listOf(FolderMeta("A", updatedAt = 100L)))
        val webdav = mockk<WebDavClient>()
        every { webdav.get(any<String>()) } throws RuntimeException("network error")
        every { webdav.put(any(), any<ByteArray>(), any()) } throws RuntimeException("network error")
        assertFalse(manager.sync(webdav, "https://server/"))
    }

    /** folders.json wird direkt geGETtet — der 404-Fall läuft über den catch-all, kein HEAD davor. */
    @Test fun `sync downloads folders json without an exists probe`() = runTest {
        folderStore.replaceMeta(listOf(FolderMeta("A", updatedAt = 100L)))
        val webdav = webDavWith("""[{"name":"A","color":"#FF0000","updatedAt":200}]""")

        manager.sync(webdav, "https://server/")

        verify(exactly = 0) { webdav.exists(any()) }
        verify(exactly = 1) { webdav.get(any<String>()) }
    }

    @Test fun `sync treats a missing folders json as empty remote`() = runTest {
        folderStore.replaceMeta(listOf(FolderMeta("A", updatedAt = 100L)))
        val webdav = mockk<WebDavClient>()
        every { webdav.get(any<String>()) } throws WebDavException("not found", 404)
        every { webdav.put(any(), any<ByteArray>(), any()) } returns null

        assertFalse(manager.sync(webdav, "https://server/"))
        verify(exactly = 1) { webdav.put(any(), any<ByteArray>(), any()) }
    }

    // ── ETag-Fast-Path (v2.14.0) ──────────────────────────────────────────────

    private fun cacheEtag(value: String?) {
        prefsBacking[dev.dettmer.simplenotes.utils.Constants.KEY_FOLDERS_JSON_ETAG] = value
    }

    private fun cachedEtag(): String? =
        prefsBacking[dev.dettmer.simplenotes.utils.Constants.KEY_FOLDERS_JSON_ETAG] as? String

    @Test fun `sync skips the round-trip on an ETag match`() = runTest {
        folderStore.replaceMeta(listOf(FolderMeta("A", updatedAt = 100L)))
        cacheEtag("\"e1\"")
        val webdav = mockk<WebDavClient>()

        assertFalse(manager.sync(webdav, "https://server/", remoteEtag = "W/\"e1\"", allowSkip = true))

        verify(exactly = 0) { webdav.get(any<String>()) }
        verify(exactly = 0) { webdav.put(any(), any<ByteArray>(), any()) }
    }

    @Test fun `sync does not skip when allowSkip is false`() = runTest {
        folderStore.replaceMeta(listOf(FolderMeta("A", updatedAt = 100L)))
        cacheEtag("\"e1\"")
        val webdav = webDavWith("""[{"name":"A","updatedAt":100}]""")

        manager.sync(webdav, "https://server/", remoteEtag = "\"e1\"", allowSkip = false)

        verify(exactly = 1) { webdav.get(any<String>()) }
    }

    @Test fun `sync does not skip on an ETag difference`() = runTest {
        folderStore.replaceMeta(listOf(FolderMeta("A", updatedAt = 100L)))
        cacheEtag("\"e1\"")
        val webdav = webDavWith("""[{"name":"A","updatedAt":100}]""")

        manager.sync(webdav, "https://server/", remoteEtag = "\"e2\"", allowSkip = true)

        verify(exactly = 1) { webdav.get(any<String>()) }
    }

    /** Server ohne ETags (remoteEtag == null) dürfen nie skippen. */
    @Test fun `sync does not skip without a remote ETag`() = runTest {
        folderStore.replaceMeta(listOf(FolderMeta("A", updatedAt = 100L)))
        cacheEtag("\"e1\"")
        val webdav = webDavWith("""[{"name":"A","updatedAt":100}]""")

        manager.sync(webdav, "https://server/", remoteEtag = null, allowSkip = true)

        verify(exactly = 1) { webdav.get(any<String>()) }
    }

    @Test fun `sync does not skip while the dirty flag is set`() = runTest {
        folderStore.replaceMeta(listOf(FolderMeta("A", updatedAt = 100L)))
        cacheEtag("\"e1\"")
        prefsBacking[dev.dettmer.simplenotes.utils.Constants.KEY_FOLDERS_DIRTY] = true
        val webdav = webDavWith("""[{"name":"A","updatedAt":100}]""")

        manager.sync(webdav, "https://server/", remoteEtag = "\"e1\"", allowSkip = true)

        verify(exactly = 1) { webdav.get(any<String>()) }
    }

    @Test fun `sync does not skip while the server-removal queue is pending`() = runTest {
        folderStore.replaceMeta(listOf(FolderMeta("A", updatedAt = 100L)))
        folderStore.setServerRemovalQueue(setOf("A"))
        cacheEtag("\"e1\"")
        val webdav = webDavWith("""[{"name":"A","updatedAt":100}]""")

        manager.sync(webdav, "https://server/", remoteEtag = "\"e1\"", allowSkip = true)

        verify(exactly = 1) { webdav.get(any<String>()) }
    }

    @Test fun `a run without a PUT caches the remote ETag`() = runTest {
        folderStore.replaceMeta(listOf(FolderMeta("A", color = "#FF0000", updatedAt = 200L)))
        val webdav = webDavWith("""[{"name":"A","color":"#FF0000","updatedAt":200}]""")

        manager.sync(webdav, "https://server/", remoteEtag = "\"fresh\"", allowSkip = true)

        verify(exactly = 0) { webdav.put(any(), any<ByteArray>(), any()) }
        assertEquals("\"fresh\"", cachedEtag())
    }

    /** Nach einem PUT ist der neue Server-ETag unbekannt — der Key muss weg. */
    @Test fun `a run with a PUT clears the cached ETag`() = runTest {
        folderStore.replaceMeta(listOf(FolderMeta("B", updatedAt = 300L)))
        cacheEtag("\"stale\"")
        val webdav = webDavWith("""[{"name":"A","updatedAt":100}]""")

        manager.sync(webdav, "https://server/", remoteEtag = "\"other\"", allowSkip = true)

        verify(exactly = 1) { webdav.put(any(), any<ByteArray>(), any()) }
        assertNull(cachedEtag())
    }
}
