package dev.dettmer.simplenotes.utils

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 🔐 v2.17.0 (WP-3): Test-Seam für [CredentialStore]. Liefert den AES-256-GCM-Schlüssel, unter dem
 * Username und Passwort in den Prefs liegen. Produktion = AndroidKeyStore; Tests injizieren einen
 * `SecretKeySpec` und prüfen Umschlag, Migration, Fallback und Zurückbeförderung auf der JVM.
 * Ungetestet bleibt allein das Holen des Keys aus dem KeyStore — das deckt der Gerätetest.
 */
internal fun interface CredentialKeyProvider {
    /**
     * @param create nur auf dem Schreibpfad `true`. Beim Lesen darf nie ein Schlüssel entstehen:
     *   `SettingsViewModel` liest die Credentials in den StateFlow-Initializern, also bei
     *   VM-Konstruktion auf dem Main-Thread.
     */
    fun secretKey(create: Boolean): SecretKey?
}

private const val TAG = "CredentialStore"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_BITS = 128
private const val GCM_IV_BYTES = 12
private const val AES_KEY_BITS = 256

internal const val CREDENTIAL_KEY_ALIAS = "credential_master_key"

/** Roher AES-256-GCM-Key im AndroidKeyStore — kein Tink, keine Keyset-Datei. */
internal val androidKeyStoreKeyProvider = CredentialKeyProvider { create ->
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    val existing = keyStore.getKey(CREDENTIAL_KEY_ALIAS, null) as? SecretKey
    when {
        existing != null -> existing
        !create -> null
        else -> KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    CREDENTIAL_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(AES_KEY_BITS)
                    // Bewusst KEINE User-Auth-Bindung: der Hintergrund-Sync muss auf einem
                    // gesperrten Gerät laufen.
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
    }
}

/** Verwirft den KeyStore-Eintrag. Ein roher Key hat keine Keyset-Datei — mehr ist nicht zu löschen. */
internal fun deleteCredentialKey() {
    try {
        KeyStore.getInstance(ANDROID_KEYSTORE)
            .apply { load(null) }
            .deleteEntry(CREDENTIAL_KEY_ALIAS)
    } catch (e: Exception) {
        // Kein Key vorhanden ist der Normalfall (Frischinstallation, zurückgespieltes Gerät) —
        // das Neuanlegen darunter funktioniert trotzdem.
        Logger.d(TAG, "Credential key not removed (${e.javaClass.simpleName}) — continuing")
    }
}

/**
 * Umschlag `IV‖Ciphertext`, hex-kodiert. Vorlage: `backup/EncryptionManager`.
 * Hex statt Base64, weil `android.util.Base64` im JVM-Unit-Test nur ein Stub ist — die paar
 * zusätzlichen Bytes pro Credential sind billiger als ein ungetesteter Umschlag.
 */
internal fun encryptCredential(key: SecretKey, plain: String): String {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, key)
    val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
    return (cipher.iv + ciphertext).joinToString("") { "%02x".format(it) }
}

internal fun decryptCredential(key: SecretKey, stored: String): String {
    val bytes = stored.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, bytes, 0, GCM_IV_BYTES))
    return String(cipher.doFinal(bytes, GCM_IV_BYTES, bytes.size - GCM_IV_BYTES), Charsets.UTF_8)
}
