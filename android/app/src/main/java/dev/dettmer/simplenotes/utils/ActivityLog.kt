package dev.dettmer.simplenotes.utils

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileWriter
import java.io.RandomAccessFile
import org.json.JSONObject

/**
 * Menschenlesbares Aktivitätsprotokoll — Issue #128 Teil 3. Format-Spec:
 * `project-docs/simple-notes-sync/activity-log-format.md`.
 *
 * Anders als [SyncDebugLogger] ist dieses Protokoll **immer aktiv**: ein Protokoll, das man
 * erst einschalten muss, ist genau dann aus, wenn man es braucht.
 *
 * Eine Zeile = ein JSON-Objekt (JSONL), append-only. Rotation per Rename auf Backup (wie
 * [SyncDebugLogger]), NICHT per Read-All/Rewrite-All — siehe der historische O(n²)-Bug in
 * [Logger.trimLogFile], den dieser Ansatz bewusst vermeidet.
 *
 * Das Protokoll bleibt auf dem Gerät. Es gibt keinen Server-Sync: jedes Gerät führt sein
 * eigenes Protokoll, und wer es weitergeben will, teilt es bewusst über den Teilen-Button
 * (anonymisiert durch [LogAnonymizer]). Aufbewahrung = Rotation nach [MAX_FILE_BYTES], eine
 * Backup-Generation.
 */
object ActivityLog {
    enum class Op {
        CREATE,
        EDIT,
        TRASH,
        RESTORE,
        PURGE,
        UPLOAD,
        DOWNLOAD,
        CONFLICT,
        FOLDER_DELETE,
        SYNC_OK,
        SYNC_FAIL,
        DELETION_SKIPPED
    }

    enum class Src { LOCAL, REMOTE }

    data class Entry(
        val v: Int = 1,
        val ts: Long,
        val op: Op,
        val src: Src,
        val id: String? = null,
        val title: String? = null,
        val folder: String? = null,
        val dev: String,
        val why: String? = null,
        val err: String? = null
    )

    const val FILE_NAME = "activity.jsonl"
    const val FILE_NAME_BAK = "activity.jsonl.1"

    private const val MAX_FILE_BYTES = 4L * 1024L * 1024L // lokales Safety-Net, s. Klassenkommentar
    private const val TAIL_CHUNK_BYTES = 64L * 1024L
    private const val TAIL_CHUNK_GROWTH_FACTOR = 4L
    private const val TITLE_MAX_LENGTH = 200
    private const val ERR_MAX_LENGTH = 200
    private const val TAG = "ActivityLog"

    private val fileLock = Any()

    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Menschenlesbarer Gerätename, z. B. "Google Pixel 10". Kein separates Namensfeld nötig. */
    fun deviceDisplayName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    fun log(
        op: Op,
        src: Src,
        id: String? = null,
        title: String? = null,
        folder: String? = null,
        why: String? = null,
        err: String? = null
    ) {
        val ctx = appContext ?: return
        val entry = Entry(
            ts = System.currentTimeMillis(),
            op = op,
            src = src,
            id = id,
            title = title?.take(TITLE_MAX_LENGTH),
            folder = folder,
            dev = deviceDisplayName(),
            why = why,
            err = err?.take(ERR_MAX_LENGTH)
        )
        synchronized(fileLock) {
            try {
                val file = File(ctx.filesDir, FILE_NAME)
                FileWriter(file, true).use { it.write(entry.toJson().toString() + "\n") }
                rotateIfNeeded(ctx, file)
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to write activity.jsonl: ${e.message}")
            }
        }
    }

    /** Lokale Log-Datei, oder `null` wenn sie (noch) nicht existiert. */
    fun getLogFile(context: Context): File? {
        val f = File(context.filesDir, FILE_NAME)
        return if (f.exists()) f else null
    }

    /**
     * Liest die letzten [maxLines] vollständigen Zeilen (neueste zuletzt), ohne die Datei
     * komplett zu laden — Tail-Read per [RandomAccessFile], wächst in 64-KB-Schritten bis genug
     * Zeilen gefunden sind oder der Dateianfang erreicht ist. Fällt bei Bedarf auf das
     * Rotations-Backup zurück.
     *
     * Rotation während des Lesens: die potenziell abgeschnittene erste Zeile eines Chunks wird
     * verworfen (defensiv) statt geparst.
     */
    fun readTail(context: Context, maxLines: Int): List<Entry> {
        val main = File(context.filesDir, FILE_NAME)
        val fromMain = if (main.exists()) tailLines(main, maxLines).mapNotNull(::parseLine) else emptyList()
        if (fromMain.size >= maxLines) return fromMain
        val backup = File(context.filesDir, FILE_NAME_BAK)
        if (!backup.exists()) return fromMain
        val remaining = maxLines - fromMain.size
        val fromBackup = tailLines(backup, remaining).mapNotNull(::parseLine)
        return fromBackup + fromMain
    }

    /** Löscht Log-Datei + Backup. Idempotent (true auch wenn nichts existierte). */
    fun clearLocal(context: Context): Boolean {
        synchronized(fileLock) {
            return try {
                File(context.filesDir, FILE_NAME).let { if (it.exists()) it.delete() }
                File(context.filesDir, FILE_NAME_BAK).let { if (it.exists()) it.delete() }
                true
            } catch (e: Exception) {
                Logger.w(TAG, "Failed to clear activity.jsonl: ${e.message}")
                false
            }
        }
    }

    /** Serialisiert [entries] als JSONL-Text — Gegenstück zu [parseLine], für Tests und Export. */
    fun serialize(entries: List<Entry>): String =
        entries.joinToString("") { it.toJson().toString() + "\n" }

    fun parseLine(line: String): Entry? {
        if (line.isBlank()) return null
        return try {
            val o = JSONObject(line)
            Entry(
                v = o.optInt("v", 1),
                ts = o.getLong("ts"),
                op = Op.valueOf(o.getString("op")),
                src = Src.valueOf(o.getString("src")),
                id = o.optString("id").ifEmpty { null },
                title = o.optString("title").ifEmpty { null },
                folder = o.optString("folder").ifEmpty { null },
                dev = o.optString("dev", "?"),
                why = o.optString("why").ifEmpty { null },
                err = o.optString("err").ifEmpty { null }
            )
        } catch (e: Exception) {
            Logger.w(TAG, "Skipping corrupt activity log line: ${e.message}")
            null
        }
    }

    // ───────── private helpers ─────────

    private fun Entry.toJson(): JSONObject = JSONObject().apply {
        put("v", v)
        put("ts", ts)
        put("op", op.name)
        put("src", src.name)
        id?.let { put("id", it) }
        title?.let { put("title", it) }
        folder?.let { put("folder", it) }
        put("dev", dev)
        why?.let { put("why", it) }
        err?.let { put("err", it) }
    }

    private fun rotateIfNeeded(ctx: Context, file: File) {
        try {
            if (file.length() > MAX_FILE_BYTES) {
                val backup = File(ctx.filesDir, FILE_NAME_BAK)
                if (backup.exists()) backup.delete()
                file.renameTo(backup)
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to rotate activity.jsonl: ${e.message}")
        }
    }

    private fun tailLines(file: File, minLines: Int): List<String> {
        try {
            RandomAccessFile(file, "r").use { raf ->
                val length = raf.length()
                if (length == 0L) return emptyList()
                var chunk = TAIL_CHUNK_BYTES
                while (true) {
                    val start = maxOf(0L, length - chunk)
                    raf.seek(start)
                    val bytes = ByteArray((length - start).toInt())
                    raf.readFully(bytes)
                    var lines = String(bytes, Charsets.UTF_8).split("\n").filter { it.isNotBlank() }
                    // Chunk-Anfang kann eine abgeschnittene Zeile sein (bzw. bei Rotation während
                    // des Lesens sogar Datenmüll) — defensiv verwerfen, außer wir sind am Dateianfang.
                    if (start > 0 && lines.isNotEmpty()) lines = lines.drop(1)
                    if (lines.size >= minLines || start == 0L) {
                        return lines.takeLast(minLines)
                    }
                    chunk *= TAIL_CHUNK_GROWTH_FACTOR
                }
            }
        } catch (e: Exception) {
            Logger.w(TAG, "Failed tail-read of ${file.name}: ${e.message}")
            return emptyList()
        }
    }
}
