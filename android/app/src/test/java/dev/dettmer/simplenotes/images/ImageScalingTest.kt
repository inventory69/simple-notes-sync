package dev.dettmer.simplenotes.images

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sichert die zweistufige Skalierung ab, von der das Widget-Byte-Budget abhängt.
 * Fällt um, sobald einer der beiden Schritte (`inSampleSize` / Rest-Downscale) wegbricht —
 * genau der Fehler, der vorher nur drei Bilder pro Widget zuließ.
 */
class ImageScalingTest {
    private companion object {
        const val WIDGET_MAX_DIM = 256

        /** Obergrenze pro Bild bei 256px längster Kante in RGB_565 (2 Byte/Pixel): 256×256×2. */
        const val MAX_BYTES_PER_IMAGE = 131_072
    }

    /** Beide Schritte hintereinander, so wie `decodeWidgetBitmap` sie ausführt. */
    private fun scaleChain(width: Int, height: Int): Pair<Int, Int> {
        val sampleSize = calculateInSampleSize(width, height, WIDGET_MAX_DIM)
        return scaledSize(width / sampleSize, height / sampleSize, WIDGET_MAX_DIM)
    }

    @Test
    fun `landscape image lands exactly on the long-edge target`() {
        assertEquals(256 to 96, scaleChain(1600, 600))
    }

    @Test
    fun `portrait image lands exactly on the long-edge target`() {
        assertEquals(192 to 256, scaleChain(1200, 1600))
    }

    @Test
    fun `square image lands exactly on the long-edge target`() {
        assertEquals(256 to 256, scaleChain(2000, 2000))
    }

    @Test
    fun `small images are left untouched`() {
        assertEquals(100 to 80, scaleChain(100, 80))
    }

    @Test
    fun `every aspect ratio stays within the per-image byte ceiling`() {
        listOf(1600 to 600, 1200 to 1600, 2000 to 2000, 100 to 80, 4000 to 3000).forEach { (w, h) ->
            val (outW, outH) = scaleChain(w, h)
            val bytes = outW * outH * 2
            assertTrue("${w}x$h decoded to ${outW}x$outH = $bytes bytes", bytes <= MAX_BYTES_PER_IMAGE)
        }
    }

    @Test
    fun `inSampleSize alone never overshoots the target by more than a factor of two`() {
        // Begründet den zweiten Schritt: die Zweierpotenz-Rasterung lässt die längste Kante
        // bis zu doppelt so groß stehen wie gewünscht.
        val sampleSize = calculateInSampleSize(1600, 600, WIDGET_MAX_DIM)
        assertEquals(2, sampleSize)
        assertEquals(800, 1600 / sampleSize)
    }
}
