package dev.dettmer.simplenotes.sync

import dev.dettmer.simplenotes.sync.webdav.WebDavResource
import java.net.URI
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🆕 v2.14.0: [WebDavSyncService.isDeletableEmptyDir] entscheidet, ob der Ordner-Cleanup ein
 * DELETE absetzt. Ein Beta-Log (mailbox.org) zeigte `DELETE failed: 404` für Verzeichnisse, die
 * es nie gab — `listOrNull` liefert dort `null`, was fälschlich als "leer" galt.
 */
class WebDavSyncServiceFolderCleanupTest {
    private val dirUrl = "https://dav.example.org/notes/simplenotessync/Test%20neu/"

    private fun resource(path: String, isDirectory: Boolean = false) = WebDavResource(
        href = URI(path),
        modified = null,
        contentLength = if (isDirectory) -1L else 42L,
        isDirectory = isDirectory,
        etag = null
    )

    @Test fun `missing directory is not deletable`() {
        assertFalse(WebDavSyncService.isDeletableEmptyDir(dirUrl, null))
    }

    @Test fun `directory containing only itself is deletable`() {
        val self = resource("/notes/simplenotessync/Test%20neu/", isDirectory = true)

        assertTrue(WebDavSyncService.isDeletableEmptyDir(dirUrl, listOf(self)))
    }

    /** Der Self-Eintrag wird über den dekodierten Pfad erkannt, nicht über den rohen href. */
    @Test fun `self entry is matched despite percent encoding`() {
        val selfWithoutSlash = resource("/notes/simplenotessync/Test%20neu", isDirectory = true)

        assertTrue(WebDavSyncService.isDeletableEmptyDir(dirUrl, listOf(selfWithoutSlash)))
    }

    @Test fun `directory with a child note is kept`() {
        val self = resource("/notes/simplenotessync/Test%20neu/", isDirectory = true)
        val child = resource("/notes/simplenotessync/Test%20neu/abc.json")

        assertFalse(WebDavSyncService.isDeletableEmptyDir(dirUrl, listOf(self, child)))
    }

    /** Server, der den Self-Eintrag weglässt: eine wirklich leere Liste bleibt löschbar. */
    @Test fun `empty listing is deletable`() {
        assertTrue(WebDavSyncService.isDeletableEmptyDir(dirUrl, emptyList()))
    }
}
