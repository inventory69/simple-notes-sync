package dev.dettmer.simplenotes.backup

import dev.dettmer.simplenotes.utils.Logger
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 🔐 v1.7.0: Encryption Manager for Backup Files
 * 
 * Provides AES-256-GCM encryption for local backups with:
 * - Password-based encryption (PBKDF2 key derivation)
 * - Random salt + IV for each encryption
 * - GCM authentication tag for integrity
 * - Simple file format: [MAGIC][VERSION][SALT][IV][ENCRYPTED_DATA]
 */
class EncryptionManager {
    
    companion object {
        private const val TAG = "EncryptionManager"
        
        // File format constants
        private const val MAGIC = "SNE1"  // Simple Notes Encrypted v1
        private const val VERSION: Byte = 1
        private const val MAGIC_BYTES = 4
        private const val VERSION_BYTES = 1
        private const val SALT_LENGTH = 32  // 256 bits
        private const val IV_LENGTH = 12    // 96 bits (recommended for GCM)
        private const val HEADER_LENGTH = MAGIC_BYTES + VERSION_BYTES + SALT_LENGTH + IV_LENGTH  // 49 bytes
        
        // Encryption constants
        private const val KEY_LENGTH = 256  // AES-256
        private const val GCM_TAG_LENGTH = 128  // 128 bits
        private const val PBKDF2_ITERATIONS = 100_000  // OWASP recommendation
        
        // Algorithm names
        private const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding"
    }
    
    /**
     * Encrypt data with password
     * 
     * @param data Plaintext data to encrypt
     * @param password User password
     * @return Encrypted byte array with header [MAGIC][VERSION][SALT][IV][CIPHERTEXT]
     */
    fun encrypt(data: ByteArray, password: String): ByteArray {
        Logger.d(TAG, "🔐 Encrypting ${data.size} bytes...")
        
        // Generate random salt and IV
        val salt = ByteArray(SALT_LENGTH)
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().apply {
            nextBytes(salt)
            nextBytes(iv)
        }
        
        // Derive encryption key from password
        val key = deriveKey(password, salt)
        
        // Encrypt data
        val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
        val secretKey = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        val ciphertext = cipher.doFinal(data)
        
        // Build encrypted file: MAGIC + VERSION + SALT + IV + CIPHERTEXT
        val result = ByteBuffer.allocate(HEADER_LENGTH + ciphertext.size).apply {
            put(MAGIC.toByteArray(StandardCharsets.US_ASCII))
            put(VERSION)
            put(salt)
            put(iv)
            put(ciphertext)
        }.array()
        
        Logger.d(TAG, "✅ Encryption successful: ${result.size} bytes (header: $HEADER_LENGTH, ciphertext: ${ciphertext.size})")
        return result
    }
    
    /**
     * Decrypt data with password
     * 
     * @param encryptedData Encrypted byte array (with header)
     * @param password User password
     * @return Decrypted plaintext
     * @throws EncryptionException if decryption fails (wrong password, corrupted data, etc.)
     */
    @Suppress("ThrowsCount") // Multiple validation steps require separate throws
    fun decrypt(encryptedData: ByteArray, password: String): ByteArray {
        Logger.d(TAG, "🔓 Decrypting ${encryptedData.size} bytes...")
        
        // Validate minimum size
        if (encryptedData.size < HEADER_LENGTH) {
            throw EncryptionException("File too small: ${encryptedData.size} bytes (expected at least $HEADER_LENGTH)")
        }
        
        // Parse header
        val buffer = ByteBuffer.wrap(encryptedData)
        
        // Verify magic bytes
        val magic = ByteArray(MAGIC_BYTES)
        buffer.get(magic)
        val magicString = String(magic, StandardCharsets.US_ASCII)
        if (magicString != MAGIC) {
            throw EncryptionException("Invalid file format: expected '$MAGIC', got '$magicString'")
        }
        
        // Check version
        val version = buffer.get()
        if (version != VERSION) {
            throw EncryptionException("Unsupported version: $version (expected $VERSION)")
        }
        
        // Extract salt and IV
        val salt = ByteArray(SALT_LENGTH)
        val iv = ByteArray(IV_LENGTH)
        buffer.get(salt)
        buffer.get(iv)
        
        // Extract ciphertext
        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)
        
        // Derive key from password
        val key = deriveKey(password, salt)
        
        // Decrypt
        return try {
            val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
            val secretKey = SecretKeySpec(key, "AES")
            val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            val plaintext = cipher.doFinal(ciphertext)
            
            Logger.d(TAG, "✅ Decryption successful: ${plaintext.size} bytes")
            plaintext
        } catch (e: Exception) {
            Logger.e(TAG, "Decryption failed", e)
            throw EncryptionException("Decryption failed: ${e.message}. Wrong password?", e)
        }
    }
    
    /**
     * Derive 256-bit encryption key from password using PBKDF2
     */
    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
        return factory.generateSecret(spec).encoded
    }
    
    /**
     * Check if data is encrypted (starts with magic bytes)
     */
    fun isEncrypted(data: ByteArray): Boolean {
        if (data.size < MAGIC_BYTES) return false
        val magic = data.sliceArray(0 until MAGIC_BYTES)
        return String(magic, StandardCharsets.US_ASCII) == MAGIC
    }
}

/**
 * Exception thrown when encryption/decryption fails
 */
class EncryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)
