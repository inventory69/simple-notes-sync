package dev.dettmer.simplenotes.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 🆕 v2.17.0 (WP-3): [CredentialStore] ohne Gerät. Der [CredentialKeyProvider]-Seam ersetzt den
 * AndroidKeyStore durch einen `SecretKeySpec` — damit sind Umschlag, alle drei Migrationsquellen,
 * die Fallback-Verzweigung und die Zurückbeförderung auf der JVM prüfbar. Ungeprüft bleibt allein
 * das Holen des Keys aus dem KeyStore; das deckt der Gerätetest.
 */
class CredentialStoreTest {
    private val files = mutableMapOf<String, MutableMap<String, String?>>()
    private lateinit var context: Context

    private val testKey: SecretKey = SecretKeySpec(ByteArray(KEY_BYTES) { it.toByte() }, "AES")
    private var keyCalls = 0

    @Before fun setUp() {
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        every { context.getSharedPreferences(any(), any()) } answers { prefsFor(firstArg()) }
        every { context.deleteSharedPreferences(any()) } answers {
            files.remove(firstArg<String>())
            true
        }
        CredentialStore.resetCachesForTest()
        useKey { testKey }
    }

    @After fun tearDown() {
        CredentialStore.resetCachesForTest()
        unmockkAll()
    }

    /** SharedPreferences-Fake mit echter Backing-Map pro Dateiname — die Dateitrennung ist hier der Prüfgegenstand. */
    private fun prefsFor(name: String): SharedPreferences {
        val backing = files.getOrPut(name) { mutableMapOf() }
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putString(any(), any()) } answers {
            backing[firstArg()] = secondArg<String?>()
            editor
        }
        every { editor.remove(any()) } answers {
            backing.remove(firstArg<String>())
            editor
        }
        every { editor.clear() } answers {
            backing.clear()
            editor
        }
        return mockk<SharedPreferences>(relaxed = true).also { p ->
            every { p.edit() } returns editor
            every { p.getString(any(), any()) } answers { backing[firstArg()] ?: secondArg() }
            every { p.all } answers { backing.toMutableMap() }
        }
    }

    private fun useKey(provider: (Boolean) -> SecretKey?) {
        CredentialStore.keyProvider = CredentialKeyProvider { create ->
            keyCalls++
            provider(create)
        }
    }

    private fun encryptedFile() = files[PREFS_ENCRYPTED].orEmpty()

    private fun fallbackFile() = files[PREFS_FALLBACK].orEmpty()

    private fun legacyFile() = files[Constants.PREFS_NAME].orEmpty()

    // ── Umschlag ────────────────────────────────────────────────────────────────────────────

    @Test fun `stored credentials come back and never touch disk in clear text`() {
        assertTrue(CredentialStore.setCredentials(context, "alice", "s3cret"))

        assertEquals("alice", CredentialStore.getUsername(context))
        assertEquals("s3cret", CredentialStore.getPassword(context))
        assertTrue(CredentialStore.hasCredentials(context))
        assertFalse(encryptedFile().values.any { it.orEmpty().contains("s3cret") })
    }

    @Test fun `the same value encrypts to a different ciphertext every time`() {
        val first = encryptCredential(testKey, "s3cret")
        val second = encryptCredential(testKey, "s3cret")

        assertNotEquals(first, second)
        assertEquals("s3cret", decryptCredential(testKey, first))
        assertEquals("s3cret", decryptCredential(testKey, second))
    }

    @Test fun `a fresh install asks the key store for nothing`() {
        assertNull(CredentialStore.getUsername(context))

        assertEquals(0, keyCalls)
    }

    // ── Migration ───────────────────────────────────────────────────────────────────────────

    @Test fun `migrates the clear text credentials left behind by a broken keyset`() {
        files[Constants.PREFS_NAME] = mutableMapOf("username" to "bob", "password" to "pw")

        assertEquals("bob", CredentialStore.getUsername(context))

        assertNull(legacyFile()["username"])
        assertNull(legacyFile()["password"])
        assertNotNull(encryptedFile()["username"])
    }

    @Test fun `migrates the legacy Tink store and removes it`() {
        files[PREFS_TINK] = mutableMapOf("__tink_keyset__" to "blob")
        stubTinkStore("carol", "pw")

        assertEquals("carol", CredentialStore.getUsername(context))

        assertTrue(files[PREFS_TINK].isNullOrEmpty())
        assertNotNull(encryptedFile()["password"])
    }

    @Test fun `an unavailable key deletes nothing and leaves the values readable`() {
        files[Constants.PREFS_NAME] = mutableMapOf("username" to "bob", "password" to "pw")
        useKey { null }

        assertEquals("bob", CredentialStore.getUsername(context))

        assertEquals("bob", legacyFile()["username"])
        assertTrue(encryptedFile().isEmpty())
    }

    // ── Fallback ────────────────────────────────────────────────────────────────────────────

    @Test fun `a dead key is discarded once and the retry succeeds`() {
        var thrown = false
        useKey {
            if (!thrown) {
                thrown = true
                throw GeneralSecurityException("key material gone")
            }
            testKey
        }

        assertTrue(CredentialStore.setCredentials(context, "alice", "s3cret"))
        assertTrue(fallbackFile().isEmpty())
        assertEquals("s3cret", CredentialStore.getPassword(context))
    }

    @Test fun `any other key store failure falls back to its own file and reports the downgrade`() {
        useKey { throw IOException("keystore busy") }

        assertFalse(CredentialStore.setCredentials(context, "alice", "s3cret"))

        assertEquals("s3cret", fallbackFile()["password"])
        assertTrue(encryptedFile().isEmpty())
        assertTrue(CredentialStore.isStoredUnencrypted(context))
        // Der Wert bleibt trotz Downgrade lesbar — sonst stünde der Sync still.
        assertEquals("alice", CredentialStore.getUsername(context))
    }

    @Test fun `a failing key store is asked once per process, not once per access`() {
        useKey { throw IOException("keystore busy") }
        CredentialStore.setCredentials(context, "alice", "s3cret")
        val afterWrite = keyCalls

        repeat(TEN_ACCESSES) { CredentialStore.getUsername(context) }

        assertEquals(afterWrite, keyCalls)
    }

    @Test fun `the next process start promotes the fallback back into the encrypted store`() {
        useKey { throw IOException("keystore busy") }
        CredentialStore.setCredentials(context, "alice", "s3cret")

        // Neuer Prozess: keyUnavailable ist weg, der KeyStore ist wieder ansprechbar.
        CredentialStore.resetCachesForTest()
        useKey { testKey }
        CredentialStore.migrateIfNeeded(context)

        assertTrue(files[PREFS_FALLBACK].isNullOrEmpty())
        assertFalse(CredentialStore.isStoredUnencrypted(context))
        assertEquals("s3cret", CredentialStore.getPassword(context))
    }

    @Test fun `clearing removes every copy`() {
        files[Constants.PREFS_NAME] = mutableMapOf("username" to "bob", "password" to "pw")
        CredentialStore.setCredentials(context, "alice", "s3cret")

        CredentialStore.clearCredentials(context)

        assertFalse(CredentialStore.hasCredentials(context))
        assertTrue(encryptedFile().isEmpty())
        assertTrue(legacyFile().isEmpty())
        assertTrue(files[PREFS_FALLBACK].isNullOrEmpty())
    }

    @Suppress("DEPRECATION") // security-crypto ist nur noch Migrationsquelle — raus in v2.20.0.
    private fun stubTinkStore(username: String, password: String) {
        val tinkPrefs = mockk<SharedPreferences>(relaxed = true)
        every { tinkPrefs.getString(Constants.KEY_USERNAME, any()) } returns username
        every { tinkPrefs.getString(Constants.KEY_PASSWORD, any()) } returns password
        mockkConstructor(MasterKey.Builder::class)
        every { anyConstructed<MasterKey.Builder>().setKeyScheme(any()) } answers { self as MasterKey.Builder }
        every { anyConstructed<MasterKey.Builder>().build() } returns mockk(relaxed = true)
        mockkStatic(EncryptedSharedPreferences::class)
        every {
            EncryptedSharedPreferences.create(any<Context>(), any<String>(), any<MasterKey>(), any(), any())
        } returns tinkPrefs
    }

    private companion object {
        const val PREFS_ENCRYPTED = "simple_notes_credentials"
        const val PREFS_FALLBACK = "simple_notes_credentials_fallback"
        const val PREFS_TINK = "simple_notes_secure_prefs"
        const val KEY_BYTES = 32
        const val TEN_ACCESSES = 10
    }
}
