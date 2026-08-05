package dev.dettmer.simplenotes.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteShareHelperTest {
    @Test fun `replaces image tags with placeholders at their position`() {
        val text = "Erster Absatz\n\n![|75%|right](.assets/a.webp)\n\nZweiter Absatz\n\n![](.assets/b.webp)"
        val result = NoteShareHelper.formatTextForShare(text) { alt ->
            if (alt.isBlank()) "[img]" else "[img $alt]"
        }
        assertEquals(
            "Erster Absatz\n\n[img]\n\nZweiter Absatz\n\n[img]",
            result
        )
    }

    @Test fun `keeps alt text but strips size and align tokens`() {
        val text = "![Sonnenuntergang|75%|right](.assets/a.webp)"
        val result = NoteShareHelper.formatTextForShare(text) { alt -> "[img $alt]" }
        assertEquals("[img Sonnenuntergang]", result)
    }

    @Test fun `text without images is unchanged apart from trim`() {
        val text = "  Ein Absatz ohne Bilder.  "
        val result = NoteShareHelper.formatTextForShare(text) { "[img]" }
        assertEquals("Ein Absatz ohne Bilder.", result)
    }
}
