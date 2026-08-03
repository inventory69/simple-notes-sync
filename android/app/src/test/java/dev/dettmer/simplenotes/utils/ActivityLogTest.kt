package dev.dettmer.simplenotes.utils

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.FileWriter
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ActivityLogTest {
    private lateinit var tmpDir: File
    private lateinit var context: Context

    @Before
    fun setUp() {
        tmpDir = Files.createTempDirectory("activity-log-test").toFile()
        context = mockk<Context>()
        every { context.filesDir } returns tmpDir
        every { context.applicationContext } returns context
        ActivityLog.init(context)
    }

    @After
    fun tearDown() {
        tmpDir.deleteRecursively()
    }

    @Test
    fun `log writes a parsable JSONL line`() {
        ActivityLog.log(ActivityLog.Op.TRASH, ActivityLog.Src.LOCAL, id = "n1", title = "Einkaufsliste", folder = "Haushalt")

        val entries = ActivityLog.readTail(context, maxLines = 100)
        assertEquals(1, entries.size)
        val entry = entries.first()
        assertEquals(ActivityLog.Op.TRASH, entry.op)
        assertEquals(ActivityLog.Src.LOCAL, entry.src)
        assertEquals("n1", entry.id)
        assertEquals("Einkaufsliste", entry.title)
        assertEquals("Haushalt", entry.folder)
    }

    @Test
    fun `folder omitted from JSON round-trips as null (root note)`() {
        ActivityLog.log(ActivityLog.Op.CREATE, ActivityLog.Src.LOCAL, id = "n1", title = "Root note", folder = null)
        val entry = ActivityLog.readTail(context, maxLines = 100).first()
        assertNull(entry.folder)
    }

    @Test
    fun `corrupt line is skipped, valid lines around it survive`() {
        val file = File(tmpDir, ActivityLog.FILE_NAME)
        FileWriter(file, true).use {
            it.write(ActivityLog.serialize(listOf(sampleEntry(ts = 1L))))
            it.write("{not valid json\n")
            it.write(ActivityLog.serialize(listOf(sampleEntry(ts = 2L))))
        }

        val entries = ActivityLog.readTail(context, maxLines = 100)
        assertEquals(2, entries.size)
        assertEquals(listOf(1L, 2L), entries.map { it.ts })
    }

    @Test
    fun `blank line is skipped without error`() {
        val file = File(tmpDir, ActivityLog.FILE_NAME)
        file.writeText("\n" + ActivityLog.serialize(listOf(sampleEntry(ts = 5L))) + "\n")

        val entries = ActivityLog.readTail(context, maxLines = 100)
        assertEquals(1, entries.size)
    }

    @Test
    fun `rotation renames the main file to backup once size threshold is exceeded`() {
        // Titel wird auf 200 Zeichen gekappt (TITLE_MAX_LENGTH) → ~300 Bytes/Zeile.
        // MAX_FILE_BYTES ist 4 MB, also reichlich Zeilen schreiben, um sicher drüber zu kommen.
        val longTitle = "x".repeat(500)
        repeat(20_000) { i ->
            ActivityLog.log(ActivityLog.Op.EDIT, ActivityLog.Src.LOCAL, id = "n$i", title = longTitle)
        }

        val backup = File(tmpDir, ActivityLog.FILE_NAME_BAK)
        assertTrue("expected rotation backup to exist after exceeding size threshold", backup.exists())
    }

    @Test
    fun `readTail returns the most recent entries without loading the whole file`() {
        repeat(10) { i ->
            ActivityLog.log(ActivityLog.Op.EDIT, ActivityLog.Src.LOCAL, id = "n$i", title = "note$i")
        }

        val tail = ActivityLog.readTail(context, maxLines = 3)
        assertEquals(3, tail.size)
        // Tail-Read liefert älteste-zuerst innerhalb des gelesenen Fensters (letzte 3 geschriebenen).
        assertEquals(listOf("n7", "n8", "n9"), tail.map { it.id })
    }

    @Test
    fun `clearLocal removes file and backup`() {
        ActivityLog.log(ActivityLog.Op.TRASH, ActivityLog.Src.LOCAL, id = "n1")

        assertTrue(ActivityLog.clearLocal(context))

        assertNull(ActivityLog.getLogFile(context))
    }

    @Test
    fun `trigger round-trips, legacy lines and unknown trigger tokens degrade to null (not dropped)`() {
        ActivityLog.log(ActivityLog.Op.SYNC_OK, ActivityLog.Src.LOCAL, trigger = ActivityLog.Trigger.WIFI_CONNECT)
        val logged = ActivityLog.readTail(context, maxLines = 100).first()
        assertEquals(ActivityLog.Trigger.WIFI_CONNECT, logged.trigger)

        val legacyLine = """{"v":1,"ts":10,"op":"SYNC_OK","src":"LOCAL","dev":"d"}"""
        val legacy = ActivityLog.parseLine(legacyLine)
        assertNotNull(legacy)
        assertNull(legacy?.trigger)

        // Ein Token aus einem neueren Build (oder Desktop-Client) darf die Zeile nicht verwerfen.
        val futureLine = """{"v":1,"ts":11,"op":"SYNC_OK","src":"LOCAL","dev":"d","trigger":"FROM_THE_FUTURE"}"""
        val future = ActivityLog.parseLine(futureLine)
        assertNotNull(future)
        assertNull(future?.trigger)
    }

    private fun sampleEntry(ts: Long) = ActivityLog.Entry(
        ts = ts,
        op = ActivityLog.Op.SYNC_OK,
        src = ActivityLog.Src.LOCAL,
        dev = "Test Device"
    )
}
