package dev.dettmer.simplenotes.sync

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PendingServerDeletionsTest {
    private lateinit var tmpDir: File
    private lateinit var store: PendingServerDeletions

    @Before fun setUp() {
        tmpDir = Files.createTempDirectory("pending-del-test").toFile()
        val context = mockk<Context> { every { filesDir } returns tmpDir }
        store = PendingServerDeletions(context)
    }

    @After fun tearDown() { tmpDir.deleteRecursively() }

    @Test fun `add and getAll preserves folderName`() = runBlocking {
        store.add(listOf(
            PendingServerDeletions.PendingDeletion("id1", "Rezepte"),
            PendingServerDeletions.PendingDeletion("id2", null)
        ))
        val all = store.getAll().associateBy { it.id }
        assertEquals("Rezepte", all["id1"]!!.folderName)
        assertNull(all["id2"]!!.folderName)
    }

    @Test fun `remove by id`() = runBlocking {
        store.add(listOf(PendingServerDeletions.PendingDeletion("id1", "A"),
                         PendingServerDeletions.PendingDeletion("id2", "B")))
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
}
