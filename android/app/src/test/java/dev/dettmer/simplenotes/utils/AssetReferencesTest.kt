package dev.dettmer.simplenotes.utils

import dev.dettmer.simplenotes.models.Note
import org.junit.Assert.assertEquals
import org.junit.Test

class AssetReferencesTest {
    private fun note(content: String, trashedAt: Long? = null, archivedAt: Long? = null) = Note(
        title = "t",
        content = content,
        deviceId = "d",
        trashedAt = trashedAt,
        archivedAt = archivedAt
    )

    @Test fun `extracts single reference`() {
        assertEquals(
            setOf("abc123.webp"),
            AssetReferences.extractAssetNames("text ![desc](.assets/abc123.webp) more")
        )
    }

    @Test fun `extracts multiple references`() {
        assertEquals(
            setOf("a1.webp", "b2.png"),
            AssetReferences.extractAssetNames("![](.assets/a1.webp)\n\n![x](.assets/b2.png)")
        )
    }

    @Test fun `alt text with brackets is not supported by design`() {
        // Regex-Alt-Text erlaubt keine "]" — bewusste Einschränkung, deckt den Alltagsfall ab.
        assertEquals(
            emptySet<String>(),
            AssetReferences.extractAssetNames("![a[b]](.assets/x.webp)")
        )
    }

    @Test fun `no match for http links`() {
        assertEquals(
            emptySet<String>(),
            AssetReferences.extractAssetNames("![desc](http://example.com/img.png)")
        )
    }

    @Test fun `no match for plain link without image syntax`() {
        assertEquals(
            emptySet<String>(),
            AssetReferences.extractAssetNames("[desc](.assets/x.webp)")
        )
    }

    @Test fun `extractAllReferenced includes trash and archive`() {
        val notes = listOf(
            note("![](.assets/active.webp)"),
            note("![](.assets/trashed.webp)", trashedAt = 1L),
            note("![](.assets/archived.webp)", archivedAt = 1L)
        )
        assertEquals(
            setOf("active.webp", "trashed.webp", "archived.webp"),
            AssetReferences.extractAllReferenced(notes)
        )
    }
}
