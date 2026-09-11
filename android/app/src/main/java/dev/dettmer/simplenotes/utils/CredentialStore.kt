package dev.dettmer.simplenotes.utils

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import java.security.GeneralSecurityException
import javax.crypto.SecretKey

/**
 * 🔐 v2.17.0 (WP-3): WebDAV-Zugangsdaten liegen als `IV‖Ciphertext` unter einem AES-256-GCM-Key aus
 * dem AndroidKeyStore — ohne `androidx.security:security-crypto` (2025 deprecated) und ohne
 * Tink-Keyset. Vorgänger war `EncryptedSharedPreferences` (v2.3.0–v2.16.1).
 *
 * Lesereihenfolge: verschlüsselte Datei → Fallback-Datei → alte Klartext-Prefs.
 * Geschrieben wird immer verschlüsselt. Scheitert der KeyStore auch im zweiten Anlauf, landen die
 * Werte in `simple_notes_credentials_fallback.xml` — einer eigenen Datei, die aus Cloud-Backup und
 * Device-Transfer ausgeschlossen ist. Vorher ging das in die regulären Settings-Prefs, die
 * gesichert werden; das Passwort konnte so im Klartext das Gerät verlassen.
 * [setCredentials] meldet diesen Downgrade nach oben, damit er sichtbar wird.
 *
 * Audit: E-01
 */
object CredentialStore {
    private const val TAG = "CredentialStore"

    /** Verschlüsselte Werte (v2.17.0+). */
    private const val PREFS_NAME = "simple_notes_credentials"

    /** Klartext-Notnagel bei defektem KeyStore — backup-/transfer-ausgeschlossen. */
    private const val FALLBACK_PREFS_NAME = "simple_notes_credentials_fallback"

    /** Tink-Store bis v2.16.1. Nur noch Migrationsquelle, verschwindet mit security-crypto in v2.20.0. */
    private const val TINK_PREFS_NAME = "simple_notes_secure_prefs"

    @VisibleForTesting
    internal var keyProvider: CredentialKeyProvider = androidKeyStoreKeyProvider

    /** Migration/Zurückbeförderung läuft einmal pro Prozess — sie schreibt, also nicht bei jedem Lesen. */
    @Volatile private var migrated = false

    /**
     * Der KeyStore hat in diesem Prozess eine Exception geworfen. Ohne dieses Flag wurde bei jedem
     * Credential-Zugriff neu angeklopft: auf einem Gerät mit kaputtem Keyset ~15-35 ms pro Aufruf
     * und damit 40-69 % der gesamten Sync-Dauer (v2.15.0-Vorfall, 10 Zugriffe pro Sync).
     * Gilt nur für diesen Prozess — der nächste Start bewertet neu und befördert die
     * Fallback-Werte zurück in den verschlüsselten Store.
     */
    @Volatile private var keyUnavailable = false

    fun getUsername(context: Context): String? = credentials(context)?.first

    fun getPassword(context: Context): String? = credentials(context)?.second

    fun hasCredentials(context: Context): Boolean {
        val (username, password) = credentials(context) ?: return false
        return username.isNotBlank() && password.isNotBlank()
    }

    /**
     * @return `true` wenn verschlüsselt gespeichert, `false` beim Klartext-Fallback. Der Rückgabewert
     *   ist das Signal für die Oberfläche (Snackbar + Warnzeile in den Server-Einstellungen) —
     *   vorher stand dieser Downgrade nur in `Logger.w`.
     */
    fun setCredentials(context: Context, username: String, password: String): Boolean {
        val app = context.applicationContext
        if (writeEncrypted(app, username, password)) {
            clearLegacySources(app)
            migrated = true
            return true
        }
        Logger.w(TAG, "⚠️ KeyStore unavailable — storing credentials unencrypted in fallback prefs")
        writePlain(app, FALLBACK_PREFS_NAME, username, password)
        return false
    }

    fun clearCredentials(context: Context) {
        val app = context.applicationContext
        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { clear() }
        clearLegacySources(app)
    }

    /**
     * Liegen die Zugangsdaten gerade unverschlüsselt auf der Platte? Treibt die dauerhafte
     * Warnzeile in den Server-Einstellungen.
     */
    fun isStoredUnencrypted(context: Context): Boolean {
        val app = context.applicationContext
        return readPlain(app, FALLBACK_PREFS_NAME) != null ||
            readPlain(app, Constants.PREFS_NAME_LEGACY) != null
    }

    /**
     * Holt die Zugangsdaten aus der letzten noch erreichbaren Quelle in den neuen Store und befördert
     * dabei auch Fallback-Werte zurück. Aufhänger: `SimpleNotesApplication.onCreate()`.
     * Scheitert der KeyStore, wird **nichts** gelöscht und der nächste Start versucht es erneut.
     */
    fun migrateIfNeeded(context: Context) {
        if (migrated) return
        val app = context.applicationContext
        synchronized(this) {
            if (migrated) return
            if (readEncrypted(app) != null) {
                migrated = true
                return
            }
            val legacy = legacyCredentials(app)
            if (legacy == null) {
                migrated = true
                return
            }
            if (writeEncrypted(app, legacy.first, legacy.second)) {
                clearLegacySources(app)
                migrated = true
                Logger.d(TAG, "✅ Credentials migrated to AndroidKeyStore-encrypted prefs")
            } else {
                Logger.w(TAG, "⚠️ KeyStore unavailable — credentials left in place, retrying on next start")
            }
        }
    }

    private fun credentials(context: Context): Pair<String, String>? {
        val app = context.applicationContext
        migrateIfNeeded(app)
        return readEncrypted(app)
            ?: readPlain(app, FALLBACK_PREFS_NAME)
            ?: readPlain(app, Constants.PREFS_NAME_LEGACY)
    }

    /**
     * Migrationsquellen in der Reihenfolge „zuletzt geschrieben zuerst". Ein Klartext-Eintrag
     * existiert nur, weil ein Schreiben in den sicheren Store scheiterte — er ist damit jünger als
     * das, was im Tink-Store liegt.
     */
    private fun legacyCredentials(app: Context): Pair<String, String>? =
        readPlain(app, FALLBACK_PREFS_NAME)
            ?: readPlain(app, Constants.PREFS_NAME_LEGACY)
            ?: tinkCredentials(app)

    @Suppress("DEPRECATION") // security-crypto ist nur noch Migrationsquelle — raus in v2.20.0.
    private fun tinkCredentials(app: Context): Pair<String, String>? {
        // Ohne vorhandene Datei keinen Tink-Store anfassen: EncryptedSharedPreferences.create()
        // würde auf einer Frischinstallation Keyset + MasterKey erzeugen, die nie jemand braucht.
        if (app.getSharedPreferences(TINK_PREFS_NAME, Context.MODE_PRIVATE).all.isEmpty()) return null
        return try {
            // Voll qualifiziert statt importiert: Kotlin meldet die Deprecation sonst am Import,
            // und dort greift das @Suppress dieser Funktion nicht.
            val masterKey = androidx.security.crypto.MasterKey.Builder(app)
                .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                app,
                TINK_PREFS_NAME,
                masterKey,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            val username = prefs.getString(Constants.KEY_USERNAME, null) ?: return null
            val password = prefs.getString(Constants.KEY_PASSWORD, null) ?: return null
            username to password
        } catch (e: Exception) {
            // Keyset unlesbar — typisch nach einem Gerätewechsel: die Prefs-Datei wird mitkopiert,
            // der nicht exportierbare MasterKey nicht. Hier ist nichts zu retten; die Werte kommen
            // aus einer der anderen Quellen oder sind ohnehin weg.
            Logger.w(TAG, "⚠️ Legacy secure prefs unreadable (${e.javaClass.simpleName}: ${e.message})")
            null
        }
    }

    private fun readEncrypted(app: Context): Pair<String, String>? {
        if (keyUnavailable) return null
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedUser = prefs.getString(Constants.KEY_USERNAME, null) ?: return null
        val storedPass = prefs.getString(Constants.KEY_PASSWORD, null) ?: return null
        return try {
            // create = false: erst nachsehen, ob überhaupt etwas da ist. Auf einer
            // Frischinstallation wird der KeyStore so gar nicht erst angefasst.
            val key = keyProvider.secretKey(create = false) ?: return null
            decryptCredential(key, storedUser) to decryptCredential(key, storedPass)
        } catch (e: Exception) {
            Logger.e(TAG, "⚠️ Credential decryption failed (${e.javaClass.simpleName}: ${e.message})")
            null
        }
    }

    private fun writeEncrypted(app: Context, username: String, password: String): Boolean {
        if (keyUnavailable) return false
        return try {
            val key = keyProvider.secretKey(create = true) ?: return false
            storeAndVerify(app, key, username, password)
        } catch (e: GeneralSecurityException) {
            // Material ist tot: einmal verwerfen und neu anlegen (Heal-Pfad aus v2.15.0, ohne dessen
            // Keyset-Löschung — ein roher Key hat keine Keyset-Datei).
            Logger.w(TAG, "⚠️ Credential key unusable (${e.javaClass.simpleName}: ${e.message}) — recreating")
            retryWithFreshKey(app, username, password)
        } catch (e: Exception) {
            // Momentzustand (voller Speicher, KeyStore gerade nicht ansprechbar), kein totes
            // Material. Nach der Migration ist dieser Store die einzige Kopie — hier zu löschen
            // würde das Passwort vernichten. Also nur für diesen Prozess aufgeben.
            Logger.e(TAG, "⚠️ Credential key unavailable (${e.javaClass.simpleName}: ${e.message}) — kept")
            keyUnavailable = true
            false
        }
    }

    private fun retryWithFreshKey(app: Context, username: String, password: String): Boolean {
        return try {
            deleteCredentialKey()
            val key = keyProvider.secretKey(create = true) ?: return false
            storeAndVerify(app, key, username, password)
        } catch (e: Exception) {
            Logger.e(TAG, "⚠️ Credential key still unusable after retry (${e.javaClass.simpleName}: ${e.message})")
            keyUnavailable = true
            false
        }
    }

    /** Schreibt und liest zur Kontrolle zurück — erst danach dürfen die alten Quellen weg. */
    private fun storeAndVerify(app: Context, key: SecretKey, username: String, password: String): Boolean {
        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(Constants.KEY_USERNAME, encryptCredential(key, username))
            putString(Constants.KEY_PASSWORD, encryptCredential(key, password))
        }
        return readEncrypted(app) == (username to password)
    }

    private fun readPlain(app: Context, name: String): Pair<String, String>? {
        val prefs = app.getSharedPreferences(name, Context.MODE_PRIVATE)
        val username = prefs.getString(Constants.KEY_USERNAME, null) ?: return null
        val password = prefs.getString(Constants.KEY_PASSWORD, null) ?: return null
        return username to password
    }

    private fun writePlain(app: Context, name: String, username: String, password: String) {
        app.getSharedPreferences(name, Context.MODE_PRIVATE).edit {
            putString(Constants.KEY_USERNAME, username)
            putString(Constants.KEY_PASSWORD, password)
        }
    }

    /**
     * Tink-Store, Fallback-Datei und die Credential-Einträge in den regulären Prefs.
     * Der Tink-MasterKey-Alias bleibt stehen — ihn zu löschen bringt nichts und ist ein
     * zusätzliches Risiko; er geht in v2.20.0 mit der Dependency.
     */
    private fun clearLegacySources(app: Context) {
        app.deleteSharedPreferences(TINK_PREFS_NAME)
        app.deleteSharedPreferences(FALLBACK_PREFS_NAME)
        app.getSharedPreferences(Constants.PREFS_NAME_LEGACY, Context.MODE_PRIVATE).edit {
            remove(Constants.KEY_USERNAME)
            remove(Constants.KEY_PASSWORD)
        }
    }

    @VisibleForTesting
    internal fun resetCachesForTest() {
        migrated = false
        keyUnavailable = false
        keyProvider = androidKeyStoreKeyProvider
    }
}
