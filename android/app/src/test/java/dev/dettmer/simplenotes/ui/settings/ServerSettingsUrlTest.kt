package dev.dettmer.simplenotes.ui.settings

import dev.dettmer.simplenotes.ui.settings.ServerSettings.Companion.extractHostFromUrl
import dev.dettmer.simplenotes.ui.settings.ServerSettings.Companion.isServerReallyChanged
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Wann ein geänderter Server den Bestätigungsdialog auslöst (v1.7.0-Regeln). */
class ServerSettingsUrlTest {
    @Test fun `setup, removal and cosmetic differences are no change`() {
        assertFalse("first setup", isServerReallyChanged("", "https://cloud.example.org"))
        assertFalse("removed", isServerReallyChanged("https://cloud.example.org", ""))
        assertFalse("protocol only", isServerReallyChanged("http://cloud.example.org", "https://cloud.example.org"))
        assertFalse("slash and case", isServerReallyChanged("https://Cloud.example.org/dav/", "https://cloud.example.org/dav"))
    }

    @Test fun `host, port or path differences are a change`() {
        assertTrue(isServerReallyChanged("https://a.example.org", "https://b.example.org"))
        assertTrue(isServerReallyChanged("https://a.example.org", "https://a.example.org:8443"))
        assertTrue(isServerReallyChanged("https://a.example.org/dav", "https://a.example.org/webdav"))
    }

    @Test fun `host extraction strips only the protocol`() {
        assertEquals("cloud.example.org/dav", extractHostFromUrl("https://cloud.example.org/dav"))
        assertEquals("10.0.2.2:8080", extractHostFromUrl("http://10.0.2.2:8080"))
        assertEquals("plain", extractHostFromUrl("plain"))
    }
}
