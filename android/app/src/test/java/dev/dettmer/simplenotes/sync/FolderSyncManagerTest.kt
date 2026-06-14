package dev.dettmer.simplenotes.sync

import android.content.Context
import android.content.SharedPreferences
import com.thegrizzlylabs.sardineandroid.Sardine
import dev.dettmer.simplenotes.storage.FolderMeta
import dev.dettmer.simplenotes.storage.FolderStore
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FolderSyncManagerTest {
    private lateinit var manager: FolderSyncManager
    private lateinit var folderStore: FolderStore
    private lateinit var tmpDir: File

    @Before fun setUp() {
        tmpDir = Files.createTempDirectory("foldersync-test").toFile()
        val prefs = mockk<SharedPreferences>(relaxed = true) {
            every { edit() } returns mockk(relaxed = true)
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

    private fun sardineWith(json: String): Sardine {
        val sardine = mockk<Sardine>()
        every { sardine.exists(any()) } returns true
        every { sardine.get(any<String>()) } returns json.byteInputStream()
        every { sardine.put(any(), any<ByteArray>(), any()) } just Runs
        return sardine
    }

    @Test fun `sync returns true when remote brings newer color (empty-folder case)`() = runTest {
        folderStore.replaceMeta(listOf(FolderMeta("A", updatedAt = 100L)))
        val sardine = sardineWith("""[{"name":"A","color":"#FF0000","updatedAt":200}]""")
        assertTrue(manager.sync(sardine, "https://server/"))
    }

    @Test fun `sync returns false when merged equals local`() = runTest {
        folderStore.replaceMeta(listOf(FolderMeta("A", color = "#FF0000", updatedAt = 200L)))
        val sardine = sardineWith("""[{"name":"A","color":"#FF0000","updatedAt":200}]""")
        assertFalse(manager.sync(sardine, "https://server/"))
    }

    @Test fun `sync returns false when sardine throws`() = runTest {
        folderStore.replaceMeta(listOf(FolderMeta("A", updatedAt = 100L)))
        val sardine = mockk<Sardine> { every { exists(any()) } throws RuntimeException("network error") }
        assertFalse(manager.sync(sardine, "https://server/"))
    }
}
