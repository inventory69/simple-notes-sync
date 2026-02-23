package dev.dettmer.simplenotes.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dev.dettmer.simplenotes.utils.Logger
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * 🆕 v1.9.0 (F08): Manages image files in app-internal storage.
 *
 * Images are stored in `filesDir/images/` with UUID filenames.
 * The custom URI scheme `simplenotes://images/<uuid>.<ext>` is used
 * in note content to reference images.
 */
class ImageStorage(private val context: Context) {

    companion object {
        private const val TAG = "ImageStorage"
        private const val IMAGES_DIR = "images"
        const val SCHEME = "simplenotes"
        const val HOST = "images"

        /** Maximum image dimension (width or height) after downscaling. */
        private const val MAX_IMAGE_DIMENSION = 1920

        /** JPEG compression quality (0-100). */
        private const val JPEG_QUALITY = 85

        private const val HALVE = 2
    }

    private val imagesDir: File = File(context.filesDir, IMAGES_DIR).apply {
        if (!exists()) mkdirs()
    }

    /**
     * Import an image from a content URI (e.g., from Photo Picker).
     * The image is downscaled if necessary and saved to internal storage.
     *
     * @param contentUri The content:// URI from the picker.
     * @return The internal reference path (e.g., "simplenotes://images/uuid.jpg"),
     *         or null if import failed.
     */
    fun importImage(contentUri: Uri): String? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(contentUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            if (options.outWidth <= 0 || options.outHeight <= 0) {
                Logger.e(TAG, "Failed to decode image dimensions from $contentUri")
                return null
            }

            options.inSampleSize = calculateInSampleSize(options)
            options.inJustDecodeBounds = false

            val bitmap = context.contentResolver.openInputStream(contentUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: run {
                Logger.e(TAG, "Failed to open input stream for $contentUri")
                return null
            }

            val mimeType = context.contentResolver.getType(contentUri)
            val isPng = mimeType == "image/png"
            val extension = if (isPng) "png" else "jpg"
            val format = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG

            val fileName = "${UUID.randomUUID()}.$extension"
            val outputFile = File(imagesDir, fileName)

            outputFile.outputStream().use { out ->
                bitmap.compress(format, JPEG_QUALITY, out)
            }
            bitmap.recycle()

            val internalPath = "$SCHEME://$HOST/$fileName"
            Logger.d(TAG, "Image imported: $contentUri → $internalPath (${outputFile.length()} bytes)")
            internalPath
        } catch (e: IOException) {
            Logger.e(TAG, "Failed to import image: ${e.message}")
            null
        } catch (e: SecurityException) {
            Logger.e(TAG, "Permission denied reading image: ${e.message}")
            null
        }
    }

    /**
     * Load a Bitmap from an internal image path.
     *
     * @param internalPath e.g., "simplenotes://images/uuid.jpg"
     * @return Decoded Bitmap, or null if file not found/corrupt.
     */
    fun loadBitmap(internalPath: String): Bitmap? {
        val file = resolveImage(internalPath) ?: return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to decode bitmap: ${e.message}")
            null
        }
    }

    /**
     * Resolve an internal image path to a File.
     */
    fun resolveImage(internalPath: String): File? {
        val fileName = extractFileName(internalPath) ?: return null
        val file = File(imagesDir, fileName)
        return if (file.exists()) file else null
    }

    private fun extractFileName(internalPath: String): String? {
        val prefix = "$SCHEME://$HOST/"
        return if (internalPath.startsWith(prefix)) internalPath.removePrefix(prefix) else null
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > MAX_IMAGE_DIMENSION || width > MAX_IMAGE_DIMENSION) {
            val halfHeight = height / HALVE
            val halfWidth = width / HALVE
            while (halfHeight / inSampleSize >= MAX_IMAGE_DIMENSION &&
                halfWidth / inSampleSize >= MAX_IMAGE_DIMENSION) {
                inSampleSize *= HALVE
            }
        }
        return inSampleSize
    }
}
