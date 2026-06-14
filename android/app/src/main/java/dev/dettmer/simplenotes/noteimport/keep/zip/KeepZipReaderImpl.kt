package dev.dettmer.simplenotes.noteimport.keep.zip

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import dev.dettmer.simplenotes.noteimport.keep.parser.dto.KeepNoteJson
import dev.dettmer.simplenotes.utils.Logger
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * v2.5.0 — `ZipInputStream`-basierte Implementierung.
 *
 * SAF-Quelle: `Uri` wird über `ContentResolver.openInputStream()`
 * geöffnet; `use {}` schließt den Stream auch im Fehlerfall.
 *
 * **Streaming**: `flow { … }` emittiert pro JSON/HTML-Entry; die Bytes
 * werden direkt aus dem `ZipInputStream` gelesen (keine Zwischendatei).
 */
class KeepZipReaderImpl(private val context: Context) : KeepZipReader {
    override suspend fun preScan(uri: Uri): KeepPreScanResult {
        return try {
            openZip(uri).use { zin -> scanZipStats(zin) }
        } catch (e: IOException) {
            Logger.w(TAG, "preScan IO error: ${e.message}")
            throw e
        } catch (e: Exception) {
            Logger.w(TAG, "preScan failed: ${e.message}")
            throw e
        }
    }

    private fun scanZipStats(zin: ZipInputStream): KeepPreScanResult {
        var total = 0
        var active = 0
        var archived = 0
        var trashed = 0
        var shared = 0
        var withAttachments = 0
        var sizeBytes = 0L
        val labels = HashSet<String>()
        val gson = Gson()

        var entry = nextEntrySafe(zin)
        while (entry != null) {
            sizeBytes += entry.size.coerceAtLeast(0L)
            if (isKeepJsonEntry(entry)) {
                total++
                val bytes = readBytes(zin)
                val dto = parseDtoSafe(gson, bytes, entry.name)
                if (dto != null) {
                    when {
                        dto.isTrashed == true -> trashed++
                        dto.isArchived == true -> archived++
                        else -> active++
                    }
                    if (!dto.sharees.isNullOrEmpty()) shared++
                    if (!dto.attachments.isNullOrEmpty()) withAttachments++
                    dto.labels?.forEach { l ->
                        l.name?.takeIf { it.isNotBlank() }?.let(labels::add)
                    }
                } else {
                    // unparsbare JSONs zählen als active (worst-case Anzeige);
                    // der Use-Case wird sie später als failed markieren.
                    active++
                }
            } else {
                zin.closeEntry()
            }
            entry = nextEntrySafe(zin)
        }

        return KeepPreScanResult(
            totalNotes = total,
            activeCount = active,
            archivedCount = archived,
            trashedCount = trashed,
            labelCount = labels.size,
            sharedCount = shared,
            notesWithAttachments = withAttachments,
            sizeBytes = sizeBytes
        )
    }

    override suspend fun readEntries(uri: Uri): Flow<KeepZipEntry> = flow {
        try {
            openZip(uri).use { zin ->
                var entry = nextEntrySafe(zin)
                while (entry != null) {
                    if (isKeepRelevantEntry(entry)) {
                        val bytes = readBytes(zin)
                        emit(
                            KeepZipEntry(
                                name = entry.name,
                                bytes = bytes,
                                sizeBytes = entry.size.coerceAtLeast(0L)
                            )
                        )
                    } else {
                        zin.closeEntry()
                    }
                    entry = nextEntrySafe(zin)
                }
            }
        } catch (e: IOException) {
            Logger.w(TAG, "readEntries IO error: ${e.message}")
            throw e
        } catch (e: Exception) {
            Logger.w(TAG, "readEntries failed: ${e.message}")
            throw e
        }
    }.flowOn(Dispatchers.IO)

    // ───── Helpers ─────────────────────────────────────────────────────

    private fun openZip(uri: Uri): ZipInputStream {
        val raw: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("ContentResolver returned null InputStream for $uri")
        return ZipInputStream(BufferedInputStream(raw))
    }

    /**
     * `ZipInputStream.nextEntry` kann bei korrupten Archiven werfen.
     * Wir loggen und propagieren — der Use-Case zeigt den Fatal-Pfad.
     */
    private fun nextEntrySafe(zin: ZipInputStream): ZipEntry? = try {
        zin.nextEntry
    } catch (e: IOException) {
        Logger.w(TAG, "ZIP corrupt while iterating entries: ${e.message}")
        throw e
    }

    private fun readBytes(zin: ZipInputStream): ByteArray {
        // ZipInputStream.readBytes() ist seit JDK 11 verfügbar.
        return zin.readBytes()
    }

    private fun parseDtoSafe(gson: Gson, bytes: ByteArray, name: String): KeepNoteJson? {
        val raw = String(bytes, Charsets.UTF_8).let {
            if (it.isNotEmpty() && it[0] == '\uFEFF') it.substring(1) else it
        }
        if (raw.isBlank()) {
            Logger.w(TAG, "Empty JSON entry '$name' during preScan")
            return null
        }
        return try {
            gson.fromJson(raw, KeepNoteJson::class.java)
        } catch (e: JsonSyntaxException) {
            Logger.w(TAG, "preScan: invalid JSON in '$name': ${e.message}")
            null
        } catch (e: Exception) {
            Logger.w(TAG, "preScan: unexpected error in '$name': ${e.message}")
            null
        }
    }

    private fun isKeepJsonEntry(entry: ZipEntry): Boolean {
        if (entry.isDirectory) return false
        val name = entry.name
        if (!name.endsWith(".json", ignoreCase = true)) return false
        // Akzeptiert Pfade wie "Takeout/Keep/foo.json" UND
        // "Keep/foo.json" UND "foo.json" (Tests pumpen flache ZIPs).
        // Ausschluss: typische Takeout-Meta-JSONs (z.B. "archive_browser.html"
        // ist HTML, also bereits gefiltert — keine Sonder-Behandlung nötig).
        return true
    }

    private fun isKeepRelevantEntry(entry: ZipEntry): Boolean {
        if (entry.isDirectory) return false
        val name = entry.name.lowercase()
        return name.endsWith(".json") || name.endsWith(".html")
    }

    companion object {
        private const val TAG = "KeepZipReader"
    }
}
