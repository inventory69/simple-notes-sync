package dev.dettmer.simplenotes.images

import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import dev.dettmer.simplenotes.utils.Logger
import java.io.File
import java.io.IOException

private const val TAG = "ImageMetadata"

/** EXIF-Metadaten eines Bild-Assets. `null`-Felder werden im Info-Dialog übersprungen. */
data class ImageMetadata(
    val widthPx: Int,
    val heightPx: Int,
    val fileSizeBytes: Long,
    val dateTaken: String? = null,
    val cameraMake: String? = null,
    val cameraModel: String? = null,
    val iso: Int? = null,
    val exposureTime: String? = null,
    val focalLengthMm: Double? = null,
    val gps: Pair<Double, Double>? = null
)

/**
 * Info-Button nur für lesbare, nicht re-encodete (Original-Qualität) Bilder — EXIF-Felder sind
 * dabei egal, [ImageInfoDialog][dev.dettmer.simplenotes.markdown.ImageInfoDialog] blendet fehlende
 * Felder ohnehin einzeln aus. Re-encodete WebPs (Compressed/Lossless) strippen EXIF beim
 * Re-Encode und bleiben deshalb über die Dateiendung ausgeschlossen.
 */
fun shouldShowImageInfo(metadata: ImageMetadata?, fileExtension: String): Boolean =
    metadata != null && !fileExtension.equals("webp", ignoreCase = true)

private const val FAST_SHUTTER_THRESHOLD_S = 1.0

/**
 * Liest Dimensionen (bounds-only-Decode, kein Full-Bitmap) + EXIF-Tags aus [file]. Blocking IO —
 * Aufrufer dispatcht auf IO. `null` bei IOException (fehlende/kaputte Datei).
 */
fun readImageMetadata(file: File): ImageMetadata? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)

    val exif = ExifInterface(file.path)
    val exposureSeconds = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, -1.0)
    val focalLength = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, -1.0)
    val iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, -1)

    ImageMetadata(
        widthPx = bounds.outWidth,
        heightPx = bounds.outHeight,
        fileSizeBytes = file.length(),
        dateTaken = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
        cameraMake = exif.getAttribute(ExifInterface.TAG_MAKE),
        cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL),
        iso = iso.takeIf { it > 0 },
        exposureTime = exposureSeconds.takeIf { it > 0 }?.let(::formatExposureTime),
        focalLengthMm = focalLength.takeIf { it > 0 },
        gps = exif.latLong?.let { it[0] to it[1] }
    )
} catch (e: IOException) {
    Logger.d(TAG, "Failed to read image metadata: ${e.message}")
    null
}

private fun formatExposureTime(seconds: Double): String =
    if (seconds >= FAST_SHUTTER_THRESHOLD_S) {
        "%.1f s".format(seconds)
    } else {
        "1/${Math.round(1.0 / seconds)} s"
    }
