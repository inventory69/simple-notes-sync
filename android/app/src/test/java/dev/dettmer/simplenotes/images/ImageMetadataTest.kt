package dev.dettmer.simplenotes.images

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageMetadataTest {
    private val noExif = ImageMetadata(widthPx = 100, heightPx = 200, fileSizeBytes = 1024L)

    @Test
    fun `no metadata hides info button`() {
        assertFalse(shouldShowImageInfo(null, "jpg"))
    }

    @Test
    fun `metadata without exif still shows info button`() {
        assertTrue(shouldShowImageInfo(noExif, "jpg"))
    }

    @Test
    fun `re-encoded webp hides info button even with metadata`() {
        assertFalse(shouldShowImageInfo(noExif, "webp"))
        assertFalse(shouldShowImageInfo(noExif, "WEBP"))
    }
}
