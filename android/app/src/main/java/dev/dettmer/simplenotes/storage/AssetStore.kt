package dev.dettmer.simplenotes.storage

import android.content.Context
import android.os.StatFs
import dev.dettmer.simplenotes.utils.Logger
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Content-addressed Ablage für Bild-Attachments. Dateiname = SHA-256(bytes)[0:16] + Extension
 * → Dedup gratis, idempotente Uploads, keine Konflikte (siehe Plan "Bild-Attachments" E3).
 */
class AssetStore(
    private val context: Context,
    // ponytail: injectable so tests avoid mocking the Android StatFs constructor
    private val freeBytes: (path: String) -> Long = { StatFs(it).availableBytes }
) {
    companion object {
        private const val TAG = "AssetStore"
        private const val HASH_HEX_LENGTH = 16

        // Kein halb geschriebenes Asset soll je als "existiert" zählen — Faktor 2 für
        // Temp-Datei + finale Datei während des atomaren renameTo.
        private const val FREE_SPACE_SAFETY_FACTOR = 2
    }

    private val assetsDir: File = File(context.filesDir, "assets").apply {
        if (!exists()) mkdirs()
    }

    suspend fun saveAsset(bytes: ByteArray, ext: String): String = withContext(Dispatchers.IO) {
        val name = "${hashName(bytes)}.$ext"
        writeAtomically(bytes, File(assetsDir, name))
        name
    }

    /**
     * Sync-Download: der Server-Dateiname ist bereits bekannt (content-addressed, immutable) —
     * kein Re-Hash nötig, nur der atomare Write.
     */
    suspend fun saveAssetAs(bytes: ByteArray, name: String): Unit = withContext(Dispatchers.IO) {
        writeAtomically(bytes, File(assetsDir, name))
    }

    private fun writeAtomically(bytes: ByteArray, target: File) {
        if (target.exists()) return
        checkFreeSpace(bytes.size.toLong())

        val temp = File.createTempFile("asset_", ".tmp", context.cacheDir)
        try {
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) {
                throw IOException("Atomic rename failed: ${temp.path} -> ${target.path}")
            }
        } finally {
            temp.delete()
        }
        Logger.d(TAG, "💾 Saved asset: ${target.name} (${bytes.size} bytes)")
    }

    fun getAssetFile(name: String): File = File(assetsDir, name)

    fun listAssets(): List<File> = assetsDir.listFiles()?.toList().orEmpty()

    fun deleteAsset(name: String): Boolean = File(assetsDir, name).delete()

    private fun checkFreeSpace(byteSize: Long) {
        val required = byteSize * FREE_SPACE_SAFETY_FACTOR
        val free = freeBytes(assetsDir.path)
        if (free < required) {
            throw IOException("Not enough free space: need $required bytes, have $free")
        }
    }

    private fun hashName(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }.take(HASH_HEX_LENGTH)
    }
}
