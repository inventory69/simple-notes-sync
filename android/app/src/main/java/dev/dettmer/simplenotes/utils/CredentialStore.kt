@file:Suppress("DEPRECATION") // EncryptedSharedPreferences: Required for Android 7+ support.
// Migration to DataStore Encrypted is only viable when minimum API level > 21.
// See: https://developer.android.com/topic/security/data-security

package dev.dettmer.simplenotes.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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

    @Volatile private var securePrefs: SharedPreferences? = null

    fun getSecurePrefs(context: Context): SharedPreferences? {
        return securePrefs ?: synchronized(this) {
            securePrefs ?: createSecurePrefs(context.applicationContext).also { securePrefs = it }
        }
    }

    private fun createSecurePrefs(appContext: Context): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Logger.e(TAG, "⚠️ Failed to create EncryptedSharedPreferences (KeyStore issue): ${e.message}")
            null
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
