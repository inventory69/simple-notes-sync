package dev.dettmer.simplenotes.images

import android.graphics.Bitmap
import androidx.core.graphics.scale

/**
 * Geteilte Skalierungs-Rechnung für alle Decode-Pfade (Bild-Import, Widget).
 *
 * Zwei Schritte gehören immer zusammen: `inSampleSize` rastert nur in Zweierpotenzen und bringt
 * ein Bild deshalb nur grob in die Nähe der Zielkante — erst [downscaleIfNeeded] trifft sie exakt.
 * Wer nur den ersten Schritt macht, landet bei bis zu der vierfachen Pixelzahl.
 */

/** Standard-Android-Doku-Rezept: kleinste Power-of-2-`inSampleSize`, die auf ≤ [maxDimension] bringt. */
internal fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    var inSampleSize = 1
    if (height > maxDimension || width > maxDimension) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= maxDimension && halfWidth / inSampleSize >= maxDimension) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

/**
 * Zielmaße, wenn die längste Kante auf [maxDimension] begrenzt wird. Bereits kleine Bilder
 * kommen unverändert zurück. Reine Rechnung ohne `Bitmap` — damit JVM-testbar.
 */
internal fun scaledSize(width: Int, height: Int, maxDimension: Int): Pair<Int, Int> {
    val largestSide = maxOf(width, height)
    if (largestSide <= maxDimension) return width to height
    val scale = maxDimension.toFloat() / largestSide
    return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
}

/** inSampleSize rastert nur in Zweierpotenzen — ein Rest-Downscale bringt exakt auf [maxDimension]. */
internal fun Bitmap.downscaleIfNeeded(maxDimension: Int): Bitmap {
    val (newWidth, newHeight) = scaledSize(width, height, maxDimension)
    if (newWidth == width && newHeight == height) return this
    val scaled = scale(newWidth, newHeight)
    if (scaled !== this) recycle()
    return scaled
}
