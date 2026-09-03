@file:Suppress("DEPRECATION") // EncryptedSharedPreferences: Required for Android 7+ support.
// Migration to DataStore Encrypted is only viable when minimum API level > 21.
// See: https://developer.android.com/topic/security/data-security

package dev.dettmer.simplenotes.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.GeneralSecurityException
import java.security.KeyStore

/**
 * 🔐 v2.3.0: Secure credential storage using EncryptedSharedPreferences.
 *
 * WebDAV credentials (username, password) are stored in AES256-GCM encrypted prefs.
 * Falls back to regular SharedPreferences if the Android Keystore is unavailable
 * (e.g. on broken devices). Migration from unencrypted prefs happens in
 * SimpleNotesApplication.onCreate().
 *
 * Audit: E-01
 */
object CredentialStore {
    private const val PREFS_NAME = "simple_notes_secure_prefs"
    private const val TAG = "CredentialStore"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    @Volatile private var securePrefs: SharedPreferences? = null

    /**
     * Merkt sich, dass der KeyStore dauerhaft nicht nutzbar ist. Ohne dieses Flag wurde bei
     * jedem Credential-Zugriff ein neuer `EncryptedSharedPreferences.create()`-Versuch
     * gestartet, weil nur der Erfolgsfall gecached war. Auf einem Gerät mit kaputtem Keyset
     * kostete das ~15-35 ms pro Aufruf und damit 40-69 % der gesamten Sync-Dauer (10 Zugriffe
     * pro Sync).
     */
    @Volatile private var secureUnavailable = false

    fun getSecurePrefs(context: Context): SharedPreferences? {
        securePrefs?.let { return it }
        if (secureUnavailable) return null
        return synchronized(this) {
            securePrefs ?: if (secureUnavailable) {
                null
            } else {
                createSecurePrefs(context.applicationContext).also {
                    securePrefs = it
                    secureUnavailable = it == null
                }
            }
        }
    }

    private fun createSecurePrefs(appContext: Context): SharedPreferences? {
        return try {
            buildSecurePrefs(appContext)
        } catch (e: GeneralSecurityException) {
            // Das Keyset lässt sich mit dem vorhandenen MasterKey nicht mehr entschlüsseln. Typisch
            // nach einem Gerätewechsel: die Prefs-Datei wird mitkopiert, der AndroidKeyStore-Key
            // nicht - der ist nicht exportierbar. Der Zustand heilt nicht von selbst, einmal
            // wegwerfen und neu anlegen ist die einzige Reparatur. Verloren geht dabei nichts, was
            // noch lesbar wäre: die Credentials liegen dann im Fallback (reguläre Prefs) und werden
            // von SimpleNotesApplication.migrateCredentialsToEncryptedPrefs() neu verschlüsselt.
            Logger.w(
                TAG,
                "⚠️ Secure prefs unreadable (${e.javaClass.simpleName}: ${e.message}) — recreating keyset"
            )
            recreateSecurePrefs(appContext)
        } catch (e: Exception) {
            // Alles andere ist ein Momentzustand, kein totes Keyset: voller Speicher (IOException),
            // KeyStore gerade nicht ansprechbar. Nach abgeschlossener Migration ist der
            // verschlüsselte Store die EINZIGE Kopie der Credentials — hier zu löschen würde das
            // Passwort vernichten. Also nur für diesen Prozess aufgeben; der nächste App-Start
            // versucht es erneut.
            Logger.e(
                TAG,
                "⚠️ Secure prefs unavailable (${e.javaClass.simpleName}: ${e.message}) — keyset kept"
            )
            null
        }
    }

    /** Verwirft Keyset + MasterKey und legt den Store genau einmal neu an. */
    private fun recreateSecurePrefs(appContext: Context): SharedPreferences? {
        return try {
            appContext.deleteSharedPreferences(PREFS_NAME)
            deleteMasterKey()
            buildSecurePrefs(appContext)
        } catch (e: Exception) {
            Logger.e(
                TAG,
                "⚠️ Failed to create EncryptedSharedPreferences (KeyStore issue): " +
                    "${e.javaClass.simpleName}: ${e.message}"
            )
            null
        }
    }

    private fun buildSecurePrefs(appContext: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun deleteMasterKey() {
        try {
            KeyStore.getInstance(ANDROID_KEYSTORE)
                .apply { load(null) }
                .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        } catch (e: Exception) {
            // Kein MasterKey vorhanden ist der Normalfall auf einem frisch zurückgespielten
            // Gerät — das Neuanlegen darunter funktioniert trotzdem.
            Logger.d(TAG, "MasterKey not removed (${e.javaClass.simpleName}) — continuing")
        }
    }

    /**
     * Returns the username. Reads from encrypted prefs first; falls back to
     * regular prefs during the migration window (before migration completes).
     */
    fun getUsername(context: Context): String? {
        val secureValue = getSecurePrefs(context)?.getString(Constants.KEY_USERNAME, null)
        if (secureValue != null) return secureValue
        // Fallback: regular prefs (pre-migration or KeyStore unavailable)
        return context.applicationContext
            .getSharedPreferences(Constants.PREFS_NAME_LEGACY, Context.MODE_PRIVATE)
            .getString(Constants.KEY_USERNAME, null)
    }

    /**
     * Returns the password. Reads from encrypted prefs first; falls back to
     * regular prefs during the migration window.
     */
    fun getPassword(context: Context): String? {
        val secureValue = getSecurePrefs(context)?.getString(Constants.KEY_PASSWORD, null)
        if (secureValue != null) return secureValue
        // Fallback: regular prefs (pre-migration or KeyStore unavailable)
        return context.applicationContext
            .getSharedPreferences(Constants.PREFS_NAME_LEGACY, Context.MODE_PRIVATE)
            .getString(Constants.KEY_PASSWORD, null)
    }

    fun setCredentials(context: Context, username: String, password: String) {
        val prefs = getSecurePrefs(context)
        if (prefs != null) {
            prefs.edit {
                putString(Constants.KEY_USERNAME, username)
                putString(Constants.KEY_PASSWORD, password)
            }
        } else {
            // Fallback: write to regular prefs if KeyStore unavailable
            Logger.w(TAG, "⚠️ KeyStore unavailable — storing credentials in regular prefs (fallback)")
            context.applicationContext
                .getSharedPreferences(Constants.PREFS_NAME_LEGACY, Context.MODE_PRIVATE)
                .edit {
                    putString(Constants.KEY_USERNAME, username)
                    putString(Constants.KEY_PASSWORD, password)
                }
        }
    }

    fun clearCredentials(context: Context) {
        getSecurePrefs(context)?.edit {
            remove(Constants.KEY_USERNAME)
            remove(Constants.KEY_PASSWORD)
        }
        // Also clear from legacy prefs in case they were not yet migrated
        context.applicationContext
            .getSharedPreferences(Constants.PREFS_NAME_LEGACY, Context.MODE_PRIVATE)
            .edit {
                remove(Constants.KEY_USERNAME)
                remove(Constants.KEY_PASSWORD)
            }
    }

    fun hasCredentials(context: Context): Boolean {
        return !getUsername(context).isNullOrBlank() && !getPassword(context).isNullOrBlank()
    }
}
