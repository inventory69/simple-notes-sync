package dev.dettmer.simplenotes.sync

import android.content.Context
import dev.dettmer.simplenotes.R
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.FakeSharedPreferences
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import javax.net.ssl.SSLException
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * 🆕 v2.21.0: Self-signed-Server, dem die Plattform nicht vertraut. Ohne Pin abgelehnt, mit dem
 * passenden Pin angenommen (auch bei fremdem Hostnamen), mit einem fremden Pin wieder abgelehnt.
 */
class TrustedCertificateTest {
    // SAN passt bewusst nicht zu localhost, wie bei vielen Self-signed-Zertifikaten
    private val serverCert = HeldCertificate.Builder().commonName("nas.invalid").addSubjectAlternativeName("nas.invalid").build()
    private val server = MockWebServer()
    private val prefs = FakeSharedPreferences()
    private val context: Context = mockk(relaxed = true) {
        every { getString(any()) } answers { "R:${firstArg<Int>()}" }
    }

    @Before fun setUp() {
        server.useHttps(HandshakeCertificates.Builder().heldCertificate(serverCert).build().sslSocketFactory(), false)
        server.start()
        prefs.edit().putString(Constants.KEY_SERVER_URL, server.url("/").toString()).apply()
    }

    @After fun tearDown() {
        ConnectionManager.closeIdleConnections()
        server.shutdown()
    }

    private val url get() = server.url("/").toString()

    private fun head() = Request.Builder().url(server.url("/")).head().build()

    private fun expectSslFailure(): IOException {
        server.enqueue(MockResponse())
        try {
            TrustedCertificate.probeClient(prefs, url).newCall(head()).execute().close()
        } catch (e: IOException) {
            return e
        }
        fail("TLS handshake should have failed")
        throw AssertionError()
    }

    @Test fun `without pin the certificate is rejected and mapped to untrusted`() {
        val e = expectSslFailure()

        assertEquals(SslKind.UNTRUSTED, SyncExceptionMapper.sslKind(e))
        // auch hinter einer umhüllenden IOException
        assertEquals(SslKind.UNTRUSTED, SyncExceptionMapper.sslKind(IOException("wrapped", e)))
        assertEquals("R:${R.string.sync_error_ssl_untrusted}", SyncExceptionMapper(context).mapToUserMessage(e))
    }

    @Test fun `matching pin is accepted despite the wrong hostname`() {
        TrustedCertificate.trust(prefs, url, TrustedCertificate.fingerprint(serverCert.certificate))
        server.enqueue(MockResponse())

        val code = TrustedCertificate.probeClient(prefs, url).newCall(head()).execute().use { it.code }

        assertEquals(200, code)
    }

    @Test fun `authenticated client carries the pin as well`() {
        val pin = TrustedCertificate.fingerprint(serverCert.certificate)
        server.enqueue(MockResponse())

        val client = ConnectionManager.buildHttpClient(5_000L, "user", "pw", pin)
        val code = client.newCall(head()).execute().use { it.code }

        assertEquals(200, code)
    }

    @Test fun `foreign pin is rejected`() {
        val other = HeldCertificate.Builder().commonName("other").build()
        TrustedCertificate.trust(prefs, url, TrustedCertificate.fingerprint(other.certificate))

        assertEquals(SslKind.UNTRUSTED, SyncExceptionMapper.sslKind(expectSslFailure()))
    }

    @Test fun `fetchLeaf returns the server fingerprint in openssl format`() {
        val info = TrustedCertificate.fetchLeaf(server.url("/").toString(), prefs)

        assertNotNull(info)
        assertEquals(TrustedCertificate.fingerprint(serverCert.certificate), info!!.sha256)
        assertTrue(info.sha256.matches(Regex("([0-9A-F]{2}:){31}[0-9A-F]{2}")))
        assertTrue(info.subject.contains("nas.invalid"))
    }

    @Test fun `gate check reports TlsError without pin and Reachable with it`() = runTest {
        val gate = SyncGateChecker(context, prefs, UnconfinedTestDispatcher(testScheduler))
        server.enqueue(MockResponse())
        assertEquals(Reachability.TlsError(SslKind.UNTRUSTED), gate.checkReachability())

        TrustedCertificate.trust(prefs, url, TrustedCertificate.fingerprint(serverCert.certificate))
        server.enqueue(MockResponse())
        assertEquals(Reachability.Reachable, gate.checkReachability())
    }

    @Test fun `pin is bound to its host and port`() {
        val pin = TrustedCertificate.fingerprint(serverCert.certificate)
        val other = "https://other.invalid:8443/dav/"
        TrustedCertificate.trust(prefs, url, pin)

        assertNull(TrustedCertificate.pin(prefs, other))
        // gleicher Host, anderer Pfad: gleicher Pin
        assertEquals(pin, TrustedCertificate.pin(prefs, server.url("/dav/sub/").toString()))
        // Default-Port aufgelöst
        TrustedCertificate.trust(prefs, "https://nas.invalid/", pin)
        assertEquals(pin, TrustedCertificate.pin(prefs, "https://nas.invalid:443/x/"))

        // Vertrauen für einen zweiten Host lässt den Pin des ersten stehen
        TrustedCertificate.trust(prefs, other, "AA:BB")
        assertEquals(pin, TrustedCertificate.pin(prefs, url))
        server.enqueue(MockResponse())
        assertEquals(200, TrustedCertificate.probeClient(prefs, url).newCall(head()).execute().use { it.code })
    }

    @Test fun `pin of another host does not open this one`() {
        TrustedCertificate.trust(prefs, "https://other.invalid/", TrustedCertificate.fingerprint(serverCert.certificate))

        assertEquals(SslKind.UNTRUSTED, SyncExceptionMapper.sslKind(expectSslFailure()))
    }

    @Test fun `ssl kinds from the cause chain`() {
        assertNull(SyncExceptionMapper.sslKind(IOException("plain")))
        assertEquals(SslKind.HOSTNAME, SyncExceptionMapper.sslKind(javax.net.ssl.SSLPeerUnverifiedException("host")))
        val expired = javax.net.ssl.SSLHandshakeException("expired").apply {
            initCause(java.security.cert.CertificateExpiredException("expired"))
        }
        assertEquals(SslKind.EXPIRED, SyncExceptionMapper.sslKind(expired))
        // Verbindungsabbruch ist ein Netzfehler, kein Zertifikatsproblem
        assertNull(SyncExceptionMapper.sslKind(SSLException("reset")))
        assertNull(SyncExceptionMapper.sslKind(IOException("wrapped", SSLException("reset"))))
        val untrusted = javax.net.ssl.SSLHandshakeException("untrusted").apply {
            initCause(java.security.cert.CertPathValidatorException("no anchor"))
        }
        assertEquals(SslKind.UNTRUSTED, SyncExceptionMapper.sslKind(untrusted))
        assertEquals(SslKind.OTHER, SyncExceptionMapper.sslKind(javax.net.ssl.SSLHandshakeException("protocol")))
        assertEquals(
            "R:${R.string.sync_error_ssl_hostname}",
            SyncExceptionMapper(context).mapToUserMessage(javax.net.ssl.SSLPeerUnverifiedException("host"))
        )
    }
}

/** Verbindungstest gegen den Self-signed-Server: Ergebnis trägt das Zertifikat für den Dialog. */
class TrustedCertificateConnectionTest {
    private val h = SyncHarness()
    private val serverCert = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()

    @After fun tearDown() {
        ConnectionManager.closeIdleConnections()
        h.close()
    }

    @Test fun `testConnection offers the certificate, then succeeds with the pin`() = runTest {
        h.server.useHttps(HandshakeCertificates.Builder().heldCertificate(serverCert).build().sslSocketFactory(), false)
        h.prefs.edit().putString(Constants.KEY_SERVER_URL, h.server.url("/").toString()).apply()
        val service = h.service(UnconfinedTestDispatcher(testScheduler))

        val first = service.testConnection()

        assertTrue(!first.isSuccess)
        assertEquals("R:${R.string.sync_error_ssl_untrusted}", first.errorMessage)
        assertEquals(TrustedCertificate.fingerprint(serverCert.certificate), first.untrustedCertificate?.sha256)

        assertEquals(h.server.url("/").toString(), first.untrustedCertificate!!.url)
        TrustedCertificate.trust(h.prefs, first.untrustedCertificate!!.url, first.untrustedCertificate!!.sha256)
        val second = h.service(UnconfinedTestDispatcher(testScheduler)).testConnection()

        assertTrue(second.errorMessage, second.isSuccess)
        assertNull(second.untrustedCertificate)
    }

    // testConnection selbst kommt auf der JVM nicht bis zum Hostnamen (Plattform-Truststore kennt die
    // Root nicht). Belegt wird die Einordnung, an der der Dialog hängt: HOSTNAME ist nicht UNTRUSTED.
    @Test fun `CA-valid certificate with a wrong hostname maps to HOSTNAME`() = runTest {
        val root = HeldCertificate.Builder().certificateAuthority(0).build()
        val leaf = HeldCertificate.Builder().signedBy(root).addSubjectAlternativeName("nas.invalid").build()
        h.server.useHttps(HandshakeCertificates.Builder().heldCertificate(leaf).build().sslSocketFactory(), false)
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(root.certificate).build()
        val client = okhttp3.OkHttpClient.Builder()
            .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
            .build()

        val e = runCatching { client.newCall(Request.Builder().url(h.server.url("/")).head().build()).execute() }
            .exceptionOrNull()

        assertEquals(SslKind.HOSTNAME, e?.let { SyncExceptionMapper.sslKind(it) })
    }
}
