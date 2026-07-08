package dev.dettmer.simplenotes.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.core.graphics.scale
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
        private const val ROTATE_90 = 90f
        private const val ROTATE_180 = 180f
        private const val ROTATE_270 = 270f
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
        val bitmap = downscaleIfNeeded(oriented, MAX_DIMENSION)

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

    /** Standard-Android-Doku-Rezept: kleinste Power-of-2-`inSampleSize`, die auf ≤ [maxDimension] bringt. */
    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var inSampleSize = 1
        if (height > maxDimension || width > maxDimension) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while (halfHeight / inSampleSize >= maxDimension && halfWidth / inSampleSize >= maxDimension) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun applyExifOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = openStream(uri).use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(ROTATE_90)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(ROTATE_180)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(ROTATE_270)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(ROTATE_90)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(ROTATE_270)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    /** inSampleSize rastert nur in Zweierpotenzen — ein Rest-Downscale bringt exakt auf [maxDimension]. */
    private fun downscaleIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largestSide = maxOf(bitmap.width, bitmap.height)
        if (largestSide <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / largestSide
        val newWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = bitmap.scale(newWidth, newHeight)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    @Suppress("DEPRECATION")
    private fun webpFormat(lossless: Boolean): Bitmap.CompressFormat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        if (lossless) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP_LOSSY
    } else {
        Bitmap.CompressFormat.WEBP
    }
}
