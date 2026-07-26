package dev.dettmer.simplenotes.sync.webdav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🆕 v2.14.0: Derselbe Server liefert denselben ETag als PUT-Header und im PROPFIND-`getetag`
 * in unterschiedlicher Schreibweise. Ohne den toleranten Vergleich würde jede Notiz beim
 * nächsten Sync unnötig neu geladen.
 */
class EtagsMatchTest {
    @Test fun `weak and strong form of the same etag match`() {
        assertTrue(etagsMatch("W/\"abc123\"", "\"abc123\""))
    }

    @Test fun `quoted and unquoted form match`() {
        assertTrue(etagsMatch("\"abc123\"", "abc123"))
    }

    @Test fun `surrounding whitespace is ignored`() {
        assertTrue(etagsMatch(" \"abc\" ", "abc"))
    }

    @Test fun `different etags do not match`() {
        assertFalse(etagsMatch("\"abc\"", "\"def\""))
    }

    @Test fun `null never matches`() {
        assertFalse(etagsMatch(null, "\"abc\""))
        assertFalse(etagsMatch("\"abc\"", null))
        assertFalse(etagsMatch(null, null))
    }
}
