package dev.dettmer.simplenotes.sync

import android.content.Context
import android.content.SharedPreferences
import dev.dettmer.simplenotes.utils.Constants
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 🆕 v2.14.0: Die Dir-"verifiziert"-Flags liegen in den Prefs und hängen am Fingerprint
 * `serverUrl|syncFolder|username`. Sie überleben [ConnectionManager.clearSession] und
 * invalidieren sich bei jeder Config-Änderung selbst — ohne expliziten Reset-Hook.
 */
class ConnectionManagerDirFlagsTest {
    private val backing = mutableMapOf<String, Any?>()
    private lateinit var prefs: SharedPreferences
    private lateinit var context: Context
    private var username: String? = "alice"

    @Before fun setUp() {
        prefs = fakePrefs()
        context = mockk(relaxed = true)
        backing[Constants.KEY_SERVER_URL] = "https://server/"
        backing[Constants.KEY_SYNC_FOLDER_NAME] = "notes"
        mockkObject(dev.dettmer.simplenotes.utils.CredentialStore)
        every { dev.dettmer.simplenotes.utils.CredentialStore.getUsername(any()) } answers { username }
    }

    @After fun tearDown() = unmockkObject(dev.dettmer.simplenotes.utils.CredentialStore)

    /** SharedPreferences-Fake mit echter Backing-Map — Prefs-Persistenz ist hier der Prüfgegenstand. */
    private fun fakePrefs(): SharedPreferences {
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { editor.putBoolean(any(), any()) } answers {
            backing[firstArg()] = secondArg<Boolean>()
            editor
        }
        every { editor.putString(any(), any()) } answers {
            backing[firstArg()] = secondArg<String?>()
            editor
        }
        return mockk<SharedPreferences>(relaxed = true).also { p ->
            every { p.edit() } returns editor
            every { p.getBoolean(any(), any()) } answers { backing[firstArg()] as? Boolean ?: secondArg() }
            every { p.getString(any(), any()) } answers { backing[firstArg()] as? String ?: secondArg() }
        }
    }

    private fun manager() = ConnectionManager(context, prefs)

    @Test fun `an ensured flag survives clearSession`() {
        val cm = manager()
        cm.notesDirEnsured = true

        cm.clearSession()

        assertTrue(manager().notesDirEnsured)
    }

    @Test fun `changing the sync folder invalidates the flags`() {
        manager().notesDirEnsured = true

        backing[Constants.KEY_SYNC_FOLDER_NAME] = "notizen"

        assertFalse(manager().notesDirEnsured)
    }

    @Test fun `changing the username invalidates the flags`() {
        manager().markdownDirEnsured = true

        username = "bob"

        assertFalse(manager().markdownDirEnsured)
    }

    @Test fun `changing the server url invalidates the flags`() {
        manager().assetsDirEnsured = true

        backing[Constants.KEY_SERVER_URL] = "https://other/"

        assertFalse(manager().assetsDirEnsured)
    }

    @Test fun `setting a flag to false clears it`() {
        val cm = manager()
        cm.notesDirEnsured = true
        cm.notesDirEnsured = false

        assertFalse(cm.notesDirEnsured)
    }

    @Test fun `the three flags are independent`() {
        val cm = manager()
        cm.notesDirEnsured = true

        assertTrue(cm.notesDirEnsured)
        assertFalse(cm.markdownDirEnsured)
        assertFalse(cm.assetsDirEnsured)
    }
}
