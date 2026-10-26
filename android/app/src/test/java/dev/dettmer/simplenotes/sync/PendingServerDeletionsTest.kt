package dev.dettmer.simplenotes.sync

import android.content.Context
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.FakeSharedPreferences
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PendingServerDeletionsTest {
    private lateinit var tmpDir: File
    private lateinit var store: PendingServerDeletions
    private val prefs = FakeSharedPreferences()
    private var now = 1_000_000L

    @Before fun setUp() {
        tmpDir = Files.createTempDirectory("pending-del-test").toFile()
        val context = mockk<Context> {
            every { filesDir } returns tmpDir
            every { getSharedPreferences(any(), any()) } returns prefs
        }
        prefs.edit()
            .putString(Constants.KEY_SERVER_URL, "https://dav.example/")
            .putString(Constants.KEY_SYNC_FOLDER_NAME, "notes")
            .apply()
        store = PendingServerDeletions(context, nowMs = { now })
    }

    private fun switchFolder(name: String) = prefs.edit().putString(Constants.KEY_SYNC_FOLDER_NAME, name).apply()

    @After fun tearDown() {
        tmpDir.deleteRecursively()
    }

    @Test fun `add and getAll preserves folderName`() = runBlocking {
        store.add(
            listOf(
                PendingServerDeletions.PendingDeletion("id1", "Rezepte"),
                PendingServerDeletions.PendingDeletion("id2", null)
            )
        )
        val all = store.getAll().associateBy { it.id }
        assertEquals("Rezepte", all["id1"]!!.folderName)
        assertNull(all["id2"]!!.folderName)
    }

    @Test fun `remove by id`() = runBlocking {
        store.add(
            listOf(
                PendingServerDeletions.PendingDeletion("id1", "A"),
                PendingServerDeletions.PendingDeletion("id2", "B")
            )
        )
        store.remove(listOf("id1"))
        assertEquals(listOf("id2"), store.getAll().map { it.id })
    }

    @Test fun `legacy string array format reads as null folder`() = runBlocking {
        File(tmpDir, PendingServerDeletions.FILENAME).writeText("""["legacy-1","legacy-2"]""")
        val all = store.getAll()
        assertEquals(2, all.size)
        assertNull(all.first().folderName)
        assertEquals(setOf("legacy-1", "legacy-2"), all.map { it.id }.toSet())
    }

    @Test fun `isMove defaults to false and roundtrips`() = runBlocking {
        store.add(
            listOf(
                PendingServerDeletions.PendingDeletion("mv", "A", isMove = true),
                PendingServerDeletions.PendingDeletion("del", "B")
            )
        )
        val all = store.getAll().associateBy { it.id }
        assertEquals(true, all["mv"]!!.isMove)
        assertEquals(false, all["del"]!!.isMove)
    }

    @Test fun `legacy entries read as non-move`() = runBlocking {
        File(tmpDir, PendingServerDeletions.FILENAME)
            .writeText("""[{"id":"old","folderName":"A"}]""")
        assertEquals(false, store.getAll().first().isMove)
    }

    @Test fun `real delete wins over move on id collision`() = runBlocking {
        store.add(listOf(PendingServerDeletions.PendingDeletion("x", "A", isMove = true)))
        store.add(listOf(PendingServerDeletions.PendingDeletion("x", "A", isMove = false)))
        val entries = store.getAll().filter { it.id == "x" }
        assertEquals(1, entries.size)
        assertEquals(false, entries.first().isMove)
    }

    @Test fun `real delete wins over move within a single add`() = runBlocking {
        store.add(
            listOf(
                PendingServerDeletions.PendingDeletion("x", "A", isMove = true),
                PendingServerDeletions.PendingDeletion("x", "A", isMove = false)
            )
        )
        val entries = store.getAll().filter { it.id == "x" }
        assertEquals(1, entries.size)
        assertEquals(false, entries.first().isMove)
    }

    @Test fun `add tags the current target and time`() = runBlocking {
        store.add(listOf(PendingServerDeletions.PendingDeletion("id1", "A")))
        val entry = store.getAll().single()
        assertEquals("https://dav.example/notes/", entry.target)
        assertEquals(now, entry.queuedAt)
    }

    @Test fun `entries of another target wait and come back`() = runBlocking {
        store.add(listOf(PendingServerDeletions.PendingDeletion("id1")))
        switchFolder("other")
        assertEquals(emptyList<PendingServerDeletions.PendingDeletion>(), store.getForCurrentTarget())
        assertEquals(1, store.getAll().size)

        switchFolder("notes")
        assertEquals(listOf("id1"), store.getForCurrentTarget().map { it.id })
    }

    @Test fun `legacy entries are pinned to the target active on first load`() = runBlocking {
        File(tmpDir, PendingServerDeletions.FILENAME).writeText("""[{"id":"old","folderName":"A"}]""")
        assertEquals(listOf("old"), store.getForCurrentTarget().map { it.id })

        switchFolder("other")
        assertEquals(emptyList<PendingServerDeletions.PendingDeletion>(), store.getForCurrentTarget())
    }

    @Test fun `foreign entries expire after the trash retention, current ones never`() = runBlocking {
        store.add(listOf(PendingServerDeletions.PendingDeletion("foreign")))
        switchFolder("other")
        store.add(listOf(PendingServerDeletions.PendingDeletion("mine")))

        now += Constants.TRASH_RETENTION_MS
        assertEquals(listOf("mine"), store.getAll().map { it.id })
    }

    @Test fun `same id for two targets - removing for the current one keeps the other`() = runBlocking {
        store.add(listOf(PendingServerDeletions.PendingDeletion("x")))
        switchFolder("other")
        store.add(listOf(PendingServerDeletions.PendingDeletion("x")))
        assertEquals(2, store.getAll().size)

        store.remove(listOf("x"), onlyCurrentTarget = true)
        assertEquals(listOf("https://dav.example/notes/"), store.getAll().map { it.target })
    }
}
