package dev.dettmer.simplenotes.sync.webdav

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests für [PropfindParser] gegen echte Server-Antwortformen.
 *
 * Abgedeckt: Nextcloud/SabreDAV (`d:`), Apache mod_dav (`lp1:`, `D:`),
 * Jianguoyun (Namespace-Default ohne Prefix), fehlende Properties und
 * 404-`propstat`-Blöcke.
 */
class PropfindParserTest {
    private fun parse(xml: String) = PropfindParser.parse(xml.trimIndent().byteInputStream())

    // ═══════════════════════════════════════════════
    // Nextcloud / SabreDAV
    // ═══════════════════════════════════════════════

    private val nextcloudResponse = """
        <?xml version="1.0"?>
        <d:multistatus xmlns:d="DAV:" xmlns:s="http://sabredav.org/ns" xmlns:oc="http://owncloud.org/ns">
          <d:response>
            <d:href>/remote.php/dav/files/alice/notes/</d:href>
            <d:propstat>
              <d:prop>
                <d:getlastmodified>Mon, 21 Jul 2025 10:12:33 GMT</d:getlastmodified>
                <d:resourcetype><d:collection/></d:resourcetype>
                <d:getetag>&quot;68d7a1b2c3d4e&quot;</d:getetag>
              </d:prop>
              <d:status>HTTP/1.1 200 OK</d:status>
            </d:propstat>
            <d:propstat>
              <d:prop>
                <d:getcontentlength/>
                <d:getcontenttype/>
              </d:prop>
              <d:status>HTTP/1.1 404 Not Found</d:status>
            </d:propstat>
          </d:response>
          <d:response>
            <d:href>/remote.php/dav/files/alice/notes/abc-123.json</d:href>
            <d:propstat>
              <d:prop>
                <d:getlastmodified>Tue, 22 Jul 2025 08:00:00 GMT</d:getlastmodified>
                <d:getcontentlength>1234</d:getcontentlength>
                <d:resourcetype/>
                <d:getetag>&quot;abc123etag&quot;</d:getetag>
                <d:getcontenttype>application/json</d:getcontenttype>
              </d:prop>
              <d:status>HTTP/1.1 200 OK</d:status>
            </d:propstat>
          </d:response>
        </d:multistatus>
    """

    @Test fun `nextcloud multistatus yields collection and file`() {
        val resources = parse(nextcloudResponse)

        assertEquals(2, resources.size)
        assertTrue("collection must be recognised", resources[0].isDirectory)
        assertFalse("plain file must not be a collection", resources[1].isDirectory)
    }

    @Test fun `name and path are derived from href`() {
        val resources = parse(nextcloudResponse)

        assertEquals("notes", resources[0].name)
        assertEquals("/remote.php/dav/files/alice/notes/", resources[0].path)
        assertEquals("abc-123.json", resources[1].name)
    }

    @Test fun `contentLength and etag are parsed`() {
        val resources = parse(nextcloudResponse)

        assertEquals(1234L, resources[1].contentLength)
        assertEquals("\"abc123etag\"", resources[1].etag)
    }

    @Test fun `props from a 404 propstat block are ignored`() {
        val collection = parse(nextcloudResponse)[0]

        // getcontentlength stand nur im 404-Block → darf nicht übernommen werden.
        assertEquals(-1L, collection.contentLength)
    }

    @Test fun `rfc1123 lastmodified is parsed as GMT`() {
        val file = parse(nextcloudResponse)[1]

        val expected = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }.parse("2025-07-22 08:00:00")
        assertEquals(expected, file.modified)
    }

    // ═══════════════════════════════════════════════
    // Fremdserver-Formen
    // ═══════════════════════════════════════════════

    @Test fun `apache mod_dav prefixes are parsed namespace-tolerantly`() {
        val resources = parse(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <D:multistatus xmlns:D="DAV:" xmlns:ns0="DAV:">
              <D:response xmlns:lp1="DAV:" xmlns:lp2="http://apache.org/dav/props/">
                <D:href>/dav/notes/note.json</D:href>
                <D:propstat>
                  <D:prop>
                    <lp1:resourcetype/>
                    <lp1:getcontentlength>42</lp1:getcontentlength>
                    <lp1:getlastmodified>Wed, 23 Jul 2025 12:00:00 GMT</lp1:getlastmodified>
                    <lp1:getetag>"1a2b3c"</lp1:getetag>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
            </D:multistatus>
            """
        )

        assertEquals(1, resources.size)
        assertEquals("note.json", resources[0].name)
        assertEquals(42L, resources[0].contentLength)
        assertEquals("\"1a2b3c\"", resources[0].etag)
        assertFalse(resources[0].isDirectory)
    }

    @Test fun `default namespace without prefix is parsed`() {
        val resources = parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <multistatus xmlns="DAV:">
              <response>
                <href>/dav/notes/</href>
                <propstat>
                  <prop>
                    <resourcetype><collection/></resourcetype>
                  </prop>
                  <status>HTTP/1.1 200 OK</status>
                </propstat>
              </response>
            </multistatus>
            """
        )

        assertEquals(1, resources.size)
        assertTrue(resources[0].isDirectory)
        assertEquals("notes", resources[0].name)
    }

    @Test fun `httpd unix-directory contenttype also marks a collection`() {
        val resources = parse(
            """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/dav/sub/</d:href>
                <d:propstat>
                  <d:prop><d:getcontenttype>httpd/unix-directory</d:getcontenttype></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
            """
        )

        assertTrue(resources[0].isDirectory)
    }

    // ═══════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════

    @Test fun `percent-encoded href yields a decoded name`() {
        val resources = parse(
            """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/dav/notes/Test%20neu/</d:href>
                <d:propstat>
                  <d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
            """
        )

        assertEquals("Test neu", resources[0].name)
        assertEquals("/dav/notes/Test neu/", resources[0].path)
    }

    @Test fun `missing props fall back to null and minus one`() {
        val resources = parse(
            """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/dav/notes/bare.json</d:href>
                <d:propstat>
                  <d:prop><d:resourcetype/></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
            """
        )

        assertNull(resources[0].modified)
        assertNull(resources[0].etag)
        assertEquals(-1L, resources[0].contentLength)
    }

    @Test fun `unparsable lastmodified yields null instead of throwing`() {
        val resources = parse(
            """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/dav/notes/x.json</d:href>
                <d:propstat>
                  <d:prop><d:getlastmodified>not a date at all</d:getlastmodified></d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
            """
        )

        assertEquals(1, resources.size)
        assertNull(resources[0].modified)
    }

    @Test fun `entry with an invalid href is skipped, the rest survives`() {
        val resources = parse(
            """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/dav/notes/bad file.json</d:href>
                <d:propstat><d:prop/><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
              <d:response>
                <d:href>/dav/notes/good.json</d:href>
                <d:propstat><d:prop/><d:status>HTTP/1.1 200 OK</d:status></d:propstat>
              </d:response>
            </d:multistatus>
            """
        )

        assertEquals(1, resources.size)
        assertEquals("good.json", resources[0].name)
    }

    @Test fun `empty multistatus yields an empty list`() {
        assertTrue(
            parse("""<?xml version="1.0"?><d:multistatus xmlns:d="DAV:"/>""").isEmpty()
        )
    }
}
