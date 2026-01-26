package dev.dettmer.simplenotes.utils

import dev.dettmer.simplenotes.backup.EncryptionException
import dev.dettmer.simplenotes.backup.EncryptionManager
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom
import kotlin.text.Charsets.UTF_8

/**
 * 🔒 v1.7.0: Tests for Local Backup Encryption
 */
class EncryptionManagerTest {
    
    private val encryptionManager = EncryptionManager()
    
    @Test
    fun `encrypt and decrypt roundtrip preserves data`() {
        val originalData = "This is a test backup with UTF-8: äöü 🔒".toByteArray(UTF_8)
        val password = "TestPassword123"
        
        val encrypted = encryptionManager.encrypt(originalData, password)
        val decrypted = encryptionManager.decrypt(encrypted, password)
        
        assertArrayEquals(originalData, decrypted)
    }
    
    @Test
    fun `encrypted data has correct header format`() {
        val data = "Test data".toByteArray(UTF_8)
        val password = "password123"
        
        val encrypted = encryptionManager.encrypt(data, password)
        
        // Check magic bytes "SNE1"
        val magic = encrypted.copyOfRange(0, 4)
        assertArrayEquals("SNE1".toByteArray(UTF_8), magic)
        
        // Check version (1 byte = 0x01)
        assertEquals(1, encrypted[4].toInt())
        
        // Check minimum size: magic(4) + version(1) + salt(32) + iv(12) + ciphertext + tag(16)
        assertTrue("Encrypted data too small: ${encrypted.size}", encrypted.size >= 4 + 1 + 32 + 12 + 16)
    }
    
    @Test
    fun `isEncrypted returns true for encrypted data`() {
        val data = "Test".toByteArray(UTF_8)
        val encrypted = encryptionManager.encrypt(data, "password")
        
        assertTrue(encryptionManager.isEncrypted(encrypted))
    }
    
    @Test
    fun `isEncrypted returns false for plaintext data`() {
        val plaintext = "This is not encrypted".toByteArray(UTF_8)
        
        assertFalse(encryptionManager.isEncrypted(plaintext))
    }
    
    @Test
    fun `isEncrypted returns false for short data`() {
        val shortData = "SNE".toByteArray(UTF_8)  // Less than 4 bytes
        
        assertFalse(encryptionManager.isEncrypted(shortData))
    }
    
    @Test
    fun `isEncrypted returns false for wrong magic bytes`() {
        val wrongMagic = "FAKE1234567890".toByteArray(UTF_8)
        
        assertFalse(encryptionManager.isEncrypted(wrongMagic))
    }
    
    @Test
    fun `decrypt with wrong password throws EncryptionException`() {
        val data = "Sensitive data".toByteArray(UTF_8)
        val correctPassword = "correct123"
        val wrongPassword = "wrong123"
        
        val encrypted = encryptionManager.encrypt(data, correctPassword)
        
        val exception = assertThrows(EncryptionException::class.java) {
            encryptionManager.decrypt(encrypted, wrongPassword)
        }
        
        assertTrue(exception.message?.contains("Decryption failed") == true)
    }
    
    @Test
    fun `decrypt corrupted data throws EncryptionException`() {
        val data = "Test".toByteArray(UTF_8)
        val encrypted = encryptionManager.encrypt(data, "password")
        
        // Corrupt the ciphertext (skip header: 4 + 1 + 32 + 12 = 49 bytes)
        val corrupted = encrypted.copyOf()
        if (corrupted.size > 50) {
            corrupted[50] = (corrupted[50] + 1).toByte()  // Flip one bit
        }
        
        assertThrows(EncryptionException::class.java) {
            encryptionManager.decrypt(corrupted, "password")
        }
    }
    
    @Test
    fun `decrypt data with invalid header throws EncryptionException`() {
        val invalidData = "This is not encrypted at all".toByteArray(UTF_8)
        
        assertThrows(EncryptionException::class.java) {
            encryptionManager.decrypt(invalidData, "password")
        }
    }
    
    @Test
    fun `decrypt truncated data throws EncryptionException`() {
        val data = "Test".toByteArray(UTF_8)
        val encrypted = encryptionManager.encrypt(data, "password")
        
        // Truncate to only header
        val truncated = encrypted.copyOfRange(0, 20)
        
        assertThrows(EncryptionException::class.java) {
            encryptionManager.decrypt(truncated, "password")
        }
    }
    
    @Test
    fun `encrypt with different passwords produces different ciphertexts`() {
        val data = "Same data".toByteArray(UTF_8)
        
        val encrypted1 = encryptionManager.encrypt(data, "password1")
        val encrypted2 = encryptionManager.encrypt(data, "password2")
        
        // Different passwords should produce different ciphertexts
        assertFalse(encrypted1.contentEquals(encrypted2))
    }
    
    @Test
    fun `encrypt same data twice produces different ciphertexts (different IV)`() {
        val data = "Same data".toByteArray(UTF_8)
        val password = "same-password"
        
        val encrypted1 = encryptionManager.encrypt(data, password)
        val encrypted2 = encryptionManager.encrypt(data, password)
        
        // Different IVs should produce different ciphertexts
        assertFalse(encrypted1.contentEquals(encrypted2))
        
        // But both should decrypt to same original data
        val decrypted1 = encryptionManager.decrypt(encrypted1, password)
        val decrypted2 = encryptionManager.decrypt(encrypted2, password)
        assertArrayEquals(decrypted1, decrypted2)
    }
    
    @Test
    fun `encrypt large data (1MB) succeeds`() {
        val random = SecureRandom()
        val largeData = ByteArray(1024 * 1024)  // 1 MB
        random.nextBytes(largeData)
        val password = "password123"
        
        val encrypted = encryptionManager.encrypt(largeData, password)
        val decrypted = encryptionManager.decrypt(encrypted, password)
        
        assertArrayEquals(largeData, decrypted)
    }
    
    @Test
    fun `encrypt empty data succeeds`() {
        val emptyData = ByteArray(0)
        val password = "password"
        
        val encrypted = encryptionManager.encrypt(emptyData, password)
        val decrypted = encryptionManager.decrypt(encrypted, password)
        
        assertArrayEquals(emptyData, decrypted)
    }
    
    @Test
    fun `encrypt with empty password succeeds but is unsafe`() {
        val data = "Test".toByteArray(UTF_8)
        
        // Crypto library accepts empty passwords (UI prevents this with validation)
        val encrypted = encryptionManager.encrypt(data, "")
        val decrypted = encryptionManager.decrypt(encrypted, "")
        
        assertArrayEquals(data, decrypted)
        assertTrue("Empty password should still produce encrypted data", encrypted.size > data.size)
    }
    
    @Test
    fun `decrypt with unsupported version throws EncryptionException`() {
        val data = "Test".toByteArray(UTF_8)
        val encrypted = encryptionManager.encrypt(data, "password")
        
        // Change version byte to unsupported value (99)
        val invalidVersion = encrypted.copyOf()
        invalidVersion[4] = 99.toByte()
        
        assertThrows(EncryptionException::class.java) {
            encryptionManager.decrypt(invalidVersion, "password")
        }
    }
}
