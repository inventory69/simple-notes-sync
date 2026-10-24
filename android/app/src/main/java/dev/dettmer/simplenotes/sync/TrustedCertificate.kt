package dev.dettmer.simplenotes.sync

import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.dettmer.simplenotes.utils.Constants
import dev.dettmer.simplenotes.utils.Logger
import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** Zertifikat für den Vertrauens-Dialog. Fingerabdruck wie `openssl x509 -noout -fingerprint -sha256`. */
data class CertificateInfo(
    val subject: String,
    val issuer: String,
    val notBefore: Long,
    val notAfter: Long,
    val sha256: String,
    val url: String // geprüfte Server-URL, der Pin gilt für ihren Host
)

/**
 * 🆕 v2.21.0: Vertrauen in genau ein Server-Zertifikat (Self-signed), gespeichert als SHA-256 des
 * Leafs. Kein „SSL ignorieren“: Zuerst prüft die Plattform (Network Security Config, System- und
 * User-CAs), erst wenn sie ablehnt, zählt der Pin. Gesetzt wird er nur über den Verbindungstest.
 * Ein Pin je `host:port`: Vertrauen beim Testen einer neuen URL lässt den Pin des alten Servers stehen.
 */
object TrustedCertificate {
    private const val TAG = "TrustedCertificate"

    // HttpUrl löst den Default-Port auf, https://nas/ und https://nas:443/ teilen sich den Pin
    private fun key(url: String?): String? =
        url?.toHttpUrlOrNull()?.let { "${Constants.KEY_TRUSTED_CERT_PREFIX}${it.host}:${it.port}" }

    fun pin(prefs: SharedPreferences, url: String?): String? = key(url)?.let { prefs.getString(it, null) }

    fun trust(prefs: SharedPreferences, url: String, sha256: String) {
        val key = key(url) ?: return
        prefs.edit { putString(key, sha256) }
    }

    /** Ohne Pin bleibt der Builder unverändert. */
    fun apply(builder: OkHttpClient.Builder, pin: String?): OkHttpClient.Builder {
        if (pin == null) return builder
        val trustManager = PinningTrustManager(platformTrustManager(), pin)
        val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
        // Self-signed-Zertifikate haben oft kein passendes SAN: dasselbe Leaf darf auch hier durch.
        val standard = builder.build().hostnameVerifier
        return builder
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { host, session -> standard.verify(host, session) || leafMatches(session, pin) }
    }

    /**
     * Für unauthentifizierte Probes (Gate-HEAD, Statuskarte, [fetchLeaf]): geteilter Pool, Timeout aus
     * den Einstellungen, `Connection: close` für HEAD (v2.20.2) und der Pin des Hosts von [url].
     */
    fun probeClient(prefs: SharedPreferences, url: String?): OkHttpClient =
        apply(probeBuilder(prefs), pin(prefs, url)).build()

    private fun probeBuilder(prefs: SharedPreferences): OkHttpClient.Builder {
        val timeoutMs = ConnectionManager.getTimeoutMs(prefs)
        return OkHttpClient.Builder()
            .connectionPool(ConnectionManager.sharedPool)
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .addNetworkInterceptor(ConnectionManager.closeAfterHead)
    }

    /**
     * Liest das Leaf-Zertifikat des Servers, ohne ihm zu vertrauen: Der TrustManager merkt sich die
     * Kette und lehnt immer ab. Blockiert, also nur im IO-Kontext aufrufen.
     */
    fun fetchLeaf(url: String, prefs: SharedPreferences): CertificateInfo? {
        val recorder = RecordingTrustManager()
        val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(recorder), null) }
        val client = probeBuilder(prefs).sslSocketFactory(sslContext.socketFactory, recorder).build()
        try {
            client.newCall(Request.Builder().url(url).head().build()).execute().close()
        } catch (e: Exception) {
            Logger.d(TAG, "Certificate probe ended as expected: ${e.javaClass.simpleName}")
        }
        return recorder.leaf?.let { leaf ->
            CertificateInfo(
                subject = leaf.subjectX500Principal.name,
                issuer = leaf.issuerX500Principal.name,
                notBefore = leaf.notBefore.time,
                notAfter = leaf.notAfter.time,
                sha256 = fingerprint(leaf),
                url = url
            )
        }
    }

    fun fingerprint(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(":") { "%02X".format(it) }

    private fun matches(cert: X509Certificate?, pin: String) = cert != null && fingerprint(cert).equals(pin, true)

    private fun leafMatches(session: SSLSession, pin: String): Boolean = try {
        matches(session.peerCertificates.firstOrNull() as? X509Certificate, pin)
    } catch (e: Exception) {
        Logger.d(TAG, "No peer certificate for hostname check: ${e.message}")
        false
    }

    /**
     * Die 1-Argument-Init ist auf Android NSC-fähig. Mit Hostname weitergereicht, weil Androids
     * NSC-TrustManager die 2-Argument-Prüfung ablehnt, sobald es eine `<domain-config>` gibt.
     */
    private fun platformTrustManager(): X509ExtendedTrustManager =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers.filterIsInstance<X509ExtendedTrustManager>().first()

    // Bewusst eigener TrustManager: Plattform zuerst, danach nur exakt das gepinnte Leaf
    @SuppressLint("CustomX509TrustManager")
    private class PinningTrustManager(
        private val platform: X509ExtendedTrustManager,
        private val pin: String
    ) : X509ExtendedTrustManager() {
        private inline fun orPin(chain: Array<X509Certificate>, check: () -> Unit) {
            try {
                check()
            } catch (e: CertificateException) {
                if (!matches(chain.firstOrNull(), pin)) throw e
                Logger.d(TAG, "Platform rejected certificate, accepted by trusted fingerprint")
            }
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) =
            orPin(chain) { platform.checkServerTrusted(chain, authType) }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) =
            orPin(chain) { platform.checkServerTrusted(chain, authType, socket) }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) =
            orPin(chain) { platform.checkServerTrusted(chain, authType, engine) }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
            platform.checkClientTrusted(chain, authType)

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) =
            platform.checkClientTrusted(chain, authType, socket)

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) =
            platform.checkClientTrusted(chain, authType, engine)

        override fun getAcceptedIssuers(): Array<X509Certificate> = platform.acceptedIssuers
    }

    // Vertraut nie, liest nur die Kette für den Dialog aus
    @SuppressLint("CustomX509TrustManager")
    private class RecordingTrustManager : X509TrustManager {
        @Volatile var leaf: X509Certificate? = null

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            leaf = chain.firstOrNull()
            throw CertificateException("Recording only")
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
            throw CertificateException("Recording only")

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
