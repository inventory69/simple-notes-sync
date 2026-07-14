package dev.dettmer.simplenotes.images

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

private const val ROTATE_90 = 90f
private const val ROTATE_180 = 180f
private const val ROTATE_270 = 270f

/** Liest die EXIF-Orientation einer Datei; `ORIENTATION_NORMAL` falls keine vorhanden/lesbar. */
fun readExifOrientation(file: File): Int =
    ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

/** True für Orientations, die Breite/Höhe vertauschen (90°/270°-Rotationen, Transpose/Transverse). */
fun orientationSwapsAxes(orientation: Int): Boolean = orientation in setOf(
    ExifInterface.ORIENTATION_ROTATE_90,
    ExifInterface.ORIENTATION_ROTATE_270,
    ExifInterface.ORIENTATION_TRANSPOSE,
    ExifInterface.ORIENTATION_TRANSVERSE
)

/** Bakt [orientation] als Pixel-Transformation in [bitmap] ein; recycelt die Quelle bei Änderung. */
fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
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
