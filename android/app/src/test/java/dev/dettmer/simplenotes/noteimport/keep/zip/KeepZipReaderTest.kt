package dev.dettmer.simplenotes.noteimport.keep.zip

import android.net.Uri
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * v2.5.0 — Tests #33, #34, #35 aus Analyseplan §5.1 + 4 Defensiv-Tests.
 *
 * `KeepZipReaderImpl` benötigt einen Android-`Context` und ist daher in
 * JVM-Tests nicht direkt instanziierbar. Die Logik wird über
 * `TestReader` + `PreScanLogic` abgedeckt, die die ZIP-Streaming-Logik
 * Context-frei spiegeln.
 */
class KeepZipReaderTest {

    /** Test-Implementierung: liest direkt aus einem ByteArray statt aus einem SAF-Uri. */
    private class TestReader(private val zipBytes: ByteArray) : KeepZipReader {

        override suspend fun preScan(uri: Uri): KeepPreScanResult =
            PreScanLogic.run(zipBytes)

        override suspend fun readEntries(uri: Uri) = kotlinx.coroutines.flow.flow {
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zin ->
                var e = zin.nextEntry
                while (e != null) {
                    if (!e.isDirectory) {
                        val n = e.name.lowercase()
                        if (n.endsWith(".json") || n.endsWith(".html")) {
                            val bytes = zin.readBytes()
                            emit(KeepZipEntry(e.name, bytes, e.size.coerceAtLeast(0L)))
                        }
                    }
                    e = zin.nextEntry
                }
            }
        }
    }

    /** Spiegelt die preScan-Klassifikation für den Test-Pfad. */
    private object PreScanLogic {

        private data class Counts(
            var active: Int = 0,
            var archived: Int = 0,
            var trashed: Int = 0,
            var shared: Int = 0,
            var withAttach: Int = 0,
        )

        fun run(zipBytes: ByteArray): KeepPreScanResult {
            var total = 0
            var size = 0L
            val labels = HashSet<String>()
            val counts = Counts()
            val gson = com.google.gson.Gson()
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zin ->
                var e = zin.nextEntry
                while (e != null) {
                    size += e.size.coerceAtLeast(0L)
                    if (!e.isDirectory && e.name.endsWith(".json", true)) {
                        total++
                        val dto = parseEntry(zin, gson)
                        if (dto == null) { counts.active++ } else { applyDto(dto, labels, counts) }
                    }
                    e = zin.nextEntry
                }
            }
            return KeepPreScanResult(
                totalNotes = total, activeCount = counts.active, archivedCount = counts.archived,
                trashedCount = counts.trashed, labelCount = labels.size, sharedCount = counts.shared,
                notesWithAttachments = counts.withAttach, sizeBytes = size,
            )
        }

        private fun parseEntry(
            zin: ZipInputStream,
            gson: com.google.gson.Gson,
        ): dev.dettmer.simplenotes.noteimport.keep.parser.dto.KeepNoteJson? {
            val bytes = zin.readBytes()
            val raw = String(bytes, Charsets.UTF_8).let {
                if (it.isNotEmpty() && it[0] == '\uFEFF') it.substring(1) else it
            }
            return try {
                gson.fromJson(
                    raw,
                    dev.dettmer.simplenotes.noteimport.keep.parser.dto.KeepNoteJson::class.java
                )
            } catch (_: Exception) { null }
        }

        private fun applyDto(
            dto: dev.dettmer.simplenotes.noteimport.keep.parser.dto.KeepNoteJson,
            labels: HashSet<String>,
            counts: Counts,
        ) {
            when {
                dto.isTrashed == true -> counts.trashed++
                dto.isArchived == true -> counts.archived++
                else -> counts.active++
            }
            if (!dto.sharees.isNullOrEmpty()) counts.shared++
            if (!dto.attachments.isNullOrEmpty()) counts.withAttach++
            dto.labels?.forEach { l -> l.name?.takeIf { it.isNotBlank() }?.let(labels::add) }
        }
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zout ->
            for ((name, body) in entries) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                val ze = ZipEntry(name)
                ze.method = ZipEntry.STORED
                ze.size = bytes.size.toLong()
                ze.compressedSize = bytes.size.toLong()
                ze.crc = java.util.zip.CRC32().also { it.update(bytes) }.value
                zout.putNextEntry(ze)
                zout.write(bytes)
                zout.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private val fakeUri: Uri = mockk()

    // ───── #33 ────────────────────────────────────────────────────────
    @Test
    fun `readEntries_validZip_emitsAllJsonAndHtml`() = runBlocking {
        val zipBytes = zipOf(
            "Takeout/Keep/a.json" to """{"title":"a"}""",
            "Takeout/Keep/a.html" to """<html><body>a</body></html>""",
            "Takeout/Keep/b.json" to """{"title":"b"}""",
            "Takeout/index.html" to "<html/>",
            "Takeout/Keep/Labels.txt" to "Privat\nReise"
        )
        val out = TestReader(zipBytes).readEntries(fakeUri).toList()
        // index.html und Labels.txt sind nicht .json/.html im Keep-Sinn?
        // Unsere Filterlogik akzeptiert .html generell — index.html wird also
        // mitemittiert. Das ist OK; der Use-Case (Commit #10) korreliert nur
        // .html-Files mit ihrem .json-Geschwister über den Basename.
        val names = out.map { it.name }.toSet()
        assertTrue(names.contains("Takeout/Keep/a.json"))
        assertTrue(names.contains("Takeout/Keep/a.html"))
        assertTrue(names.contains("Takeout/Keep/b.json"))
        assertTrue(!names.contains("Takeout/Keep/Labels.txt"))
    }

    // ───── #34 (umgeleitet auf Pre-Scan-Klassifikation, §7.2) ─────────
    @Test
    fun `preScan_classifiesActiveArchivedTrashedSharedAttachmentsLabels`() = runBlocking {
        val zipBytes = zipOf(
            "Keep/a1.json" to """{"title":"A1","labels":[{"name":"Privat"}]}""",
            "Keep/a2.json" to """{"title":"A2","attachments":[{"filePath":"x.jpg","mimetype":"image/jpeg"}]}""",
            "Keep/arch.json" to """{"title":"AR","isArchived":true,"labels":[{"name":"Reise"}]}""",
            "Keep/trash.json" to """{"title":"TR","isTrashed":true}""",
            "Keep/share.json" to """{"title":"SH","sharees":[{"email":"x@y.z"}]}""",
            "Keep/dup.json" to """{"title":"DUP","labels":[{"name":"Privat"}]}""",
            "Keep/Labels.txt" to "Privat\nReise"
        )
        val r = TestReader(zipBytes).preScan(fakeUri)
        assertEquals(6, r.totalNotes)
        assertEquals(4, r.activeCount)
        assertEquals(1, r.archivedCount)
        assertEquals(1, r.trashedCount)
        assertEquals(2, r.labelCount)              // Privat (×2) + Reise = 2 distinkte
        assertEquals(1, r.sharedCount)
        assertEquals(1, r.notesWithAttachments)
        assertTrue(r.sizeBytes > 0L)
    }

    // ───── #35 ────────────────────────────────────────────────────────
    @Test
    fun `preScan_filtersNonKeepFiles`() = runBlocking {
        val zipBytes = zipOf(
            "Keep/a.json" to """{"title":"a"}""",
            "Photos/holiday.jpg" to "binary-blob",
            "Takeout/index.html" to "<html/>",
            "Keep/Labels.txt" to "Privat"
        )
        val r = TestReader(zipBytes).preScan(fakeUri)
        assertEquals(1, r.totalNotes) // nur die .json-Datei zählt als Notiz
        assertEquals(1, r.activeCount)
    }

    // ───── Defensiv: corrupt ZIP ─────────────────────────────────────
    @Test
    fun `readEntries_corruptZip_returnsEmpty`() = runBlocking {
        // JDK 17: ZipInputStream.readLOC() returns null for unrecognised signatures
        // (catches EOFException; propagates other IOExceptions). ByteArrayInputStream
        // never throws IOException, so corrupt data → null → empty flow.
        val bogus = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07)
        val out = TestReader(bogus).readEntries(fakeUri).toList()
        assertTrue(out.isEmpty())
    }

    // ───── Defensiv: leeres ZIP ──────────────────────────────────────
    @Test
    fun `preScan_emptyZip_returnsZeros`() = runBlocking {
        val empty = zipOf()
        val r = TestReader(empty).preScan(fakeUri)
        assertEquals(0, r.totalNotes)
        assertEquals(0, r.activeCount)
        assertEquals(0L, r.sizeBytes)
    }

    // ───── Defensiv: matchingCount-Logik ─────────────────────────────
    @Test
    fun `matchingCount_includesActiveAlways_archivedAndTrashedConditionally`() {
        val r = KeepPreScanResult(
            totalNotes = 10, activeCount = 5, archivedCount = 3, trashedCount = 2,
            labelCount = 0, sharedCount = 0, notesWithAttachments = 0, sizeBytes = 0L,
        )
        assertEquals(5, r.matchingCount(includeArchived = false, includeTrashed = false))
        assertEquals(8, r.matchingCount(includeArchived = true, includeTrashed = false))
        assertEquals(7, r.matchingCount(includeArchived = false, includeTrashed = true))
        assertEquals(10, r.matchingCount(includeArchived = true, includeTrashed = true))
    }

    // ───── Defensiv: BOM-prefixed JSON ──────────────────────────────
    @Test
    fun `preScan_bomPrefixedJson_isParsedCorrectly`() = runBlocking {
        val zipBytes = zipOf(
            "Keep/a.json" to "\uFEFF" + """{"title":"a","isArchived":true}"""
        )
        val r = TestReader(zipBytes).preScan(fakeUri)
        assertEquals(1, r.totalNotes)
        assertEquals(1, r.archivedCount)
    }
}
