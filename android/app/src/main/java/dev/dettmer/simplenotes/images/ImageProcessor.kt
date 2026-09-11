package dev.dettmer.simplenotes.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Kompressionsmodus für neu eingefügte Bilder (persistiert als String in Settings). */
enum class ImageCompressionMode { COMPRESSED, LOSSLESS, ORIGINAL }

data class ProcessedImage(val bytes: ByteArray, val ext: String)

/**
 * Wandelt ein per Photo-Picker gewähltes Bild in speicherfertige Bytes um.
 * Nie das Original in voller Auflösung decodieren (E6) — bounds-only-Decode
 * bestimmt zuerst die Ziel-`inSampleSize`, EXIF-Rotation wird vor dem Re-Encode
 * angewendet (E7), Re-Encode strippt dabei automatisch EXIF/GPS-Metadaten.
 */
class ImageProcessor(private val context: Context) {
    companion object {
        private const val MAX_DIMENSION = 1920
        private const val QUALITY_COMPRESSED = 80
        private const val QUALITY_LOSSLESS = 100
        private const val FALLBACK_EXT = "jpg"
    }

    suspend fun process(uri: Uri, mode: ImageCompressionMode): ProcessedImage = withContext(Dispatchers.IO) {
        if (mode == ImageCompressionMode.ORIGINAL) {
            return@withContext ProcessedImage(readBytes(uri), extFromUri(uri))
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }

        val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)
        val sampled = openStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: throw IOException("Cannot decode image: $uri")

        val oriented = applyExifOrientation(uri, sampled)
        val bitmap = oriented.downscaleIfNeeded(MAX_DIMENSION)

        val format = webpFormat(lossless = mode == ImageCompressionMode.LOSSLESS)
        val quality = if (mode == ImageCompressionMode.LOSSLESS) QUALITY_LOSSLESS else QUALITY_COMPRESSED
        val out = ByteArrayOutputStream()
        bitmap.compress(format, quality, out)
        bitmap.recycle()

        ProcessedImage(out.toByteArray(), "webp")
    }

    private fun openStream(uri: Uri) = context.contentResolver.openInputStream(uri)
        ?: throw IOException("Cannot open image: $uri")

    private fun readBytes(uri: Uri): ByteArray = openStream(uri).use { it.readBytes() }

    private fun extFromUri(uri: Uri): String {
        val mime = context.contentResolver.getType(uri)
        return mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: FALLBACK_EXT
    }

    private fun applyExifOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = openStream(uri).use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        return applyExifOrientation(bitmap, orientation)
    }

    @Suppress("DEPRECATION")
    private fun webpFormat(lossless: Boolean): Bitmap.CompressFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (lossless) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY
    } else {
        Bitmap.CompressFormat.WEBP
    }
}
