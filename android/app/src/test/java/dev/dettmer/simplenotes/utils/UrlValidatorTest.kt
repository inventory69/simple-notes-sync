package dev.dettmer.simplenotes.utils

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit-Tests für UrlValidator.isLocalUrl().
 *
 * Hinweis: validateHttpUrl() benötigt Android Context und kann hier nicht getestet werden.
 * isLocalUrl() ist pure Logik und vollständig testbar.
 */
class UrlValidatorTest {

    // ═══════════════════════════════════════════════
    // Localhost
    // ═══════════════════════════════════════════════

    @Test
    fun `localhost is local`() {
        assertTrue(UrlValidator.isLocalUrl("http://localhost"))
        assertTrue(UrlValidator.isLocalUrl("http://localhost:8080"))
        assertTrue(UrlValidator.isLocalUrl("https://localhost/path"))
    }

    @Test
    fun `127_0_0_1 is local`() {
        assertTrue(UrlValidator.isLocalUrl("http://127.0.0.1"))
        assertTrue(UrlValidator.isLocalUrl("http://127.0.0.1:443"))
        assertTrue(UrlValidator.isLocalUrl("https://127.0.0.1/webdav"))
    }

    @Test
    fun `127_x_x_x range is local`() {
        assertTrue(UrlValidator.isLocalUrl("http://127.1.2.3"))
        assertTrue(UrlValidator.isLocalUrl("http://127.255.255.255"))
    }

    // ═══════════════════════════════════════════════
    // RFC 1918 Class A (10.0.0.0/8)
    // ═══════════════════════════════════════════════

    @Test
    fun `10_x_x_x is local`() {
        assertTrue(UrlValidator.isLocalUrl("http://10.0.0.1"))
        assertTrue(UrlValidator.isLocalUrl("http://10.0.1.1:8080"))
        assertTrue(UrlValidator.isLocalUrl("http://10.255.255.255"))
    }

    // ═══════════════════════════════════════════════
    // RFC 1918 Class B (172.16.0.0/12)
    // ═══════════════════════════════════════════════

    @Test
    fun `172_16_x_x to 172_31_x_x is local`() {
        assertTrue(UrlValidator.isLocalUrl("http://172.16.0.1"))
        assertTrue(UrlValidator.isLocalUrl("http://172.20.1.1"))
        assertTrue(UrlValidator.isLocalUrl("http://172.31.255.255"))
    }

    @Test
    fun `172_15_x_x is NOT local`() {
        assertFalse(UrlValidator.isLocalUrl("http://172.15.0.1"))
    }

    @Test
    fun `172_32_x_x is NOT local`() {
        assertFalse(UrlValidator.isLocalUrl("http://172.32.0.1"))
    }

    // ═══════════════════════════════════════════════
    // RFC 1918 Class C (192.168.0.0/16)
    // ═══════════════════════════════════════════════

    @Test
    fun `192_168_x_x is local`() {
        assertTrue(UrlValidator.isLocalUrl("http://192.168.0.1"))
        assertTrue(UrlValidator.isLocalUrl("http://192.168.1.100:5005"))
        assertTrue(UrlValidator.isLocalUrl("http://192.168.255.255"))
    }

    @Test
    fun `192_169_0_1 is NOT local`() {
        assertFalse(UrlValidator.isLocalUrl("http://192.169.0.1"))
    }

    // ═══════════════════════════════════════════════
    // mDNS .local domains
    // ═══════════════════════════════════════════════

    @Test
    fun `dot_local domains are local`() {
        assertTrue(UrlValidator.isLocalUrl("http://mynas.local"))
        assertTrue(UrlValidator.isLocalUrl("http://raspberry.local:8080"))
        assertTrue(UrlValidator.isLocalUrl("https://server.local/webdav"))
    }

    // ═══════════════════════════════════════════════
    // Public / Non-Local
    // ═══════════════════════════════════════════════

    @Test
    fun `public IP is NOT local`() {
        assertFalse(UrlValidator.isLocalUrl("http://8.8.8.8"))
        assertFalse(UrlValidator.isLocalUrl("http://1.1.1.1"))
        assertFalse(UrlValidator.isLocalUrl("http://203.0.113.1"))
    }

    @Test
    fun `public domain is NOT local`() {
        assertFalse(UrlValidator.isLocalUrl("https://example.com"))
        assertFalse(UrlValidator.isLocalUrl("https://nextcloud.example.com"))
    }

    // ═══════════════════════════════════════════════
    // Edge Cases
    // ═══════════════════════════════════════════════

    @Test
    fun `invalid URL returns false`() {
        assertFalse(UrlValidator.isLocalUrl("not-a-url"))
        assertFalse(UrlValidator.isLocalUrl(""))
    }

    @Test
    fun `IP with octets above 255 returns false`() {
        assertFalse(UrlValidator.isLocalUrl("http://192.168.1.256"))
        assertFalse(UrlValidator.isLocalUrl("http://10.0.0.999"))
    }
}
