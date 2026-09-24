package dev.dettmer.simplenotes.backup

import dev.dettmer.simplenotes.utils.Logger
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.US_ASCII
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 🔐 Verschlüsselung lokaler Backups: AES-256-GCM, Schlüssel per PBKDF2-HMAC-SHA256 aus dem Passwort.
 *
 * **SNE2** (seit v2.19.0, wird geschrieben) arbeitet blockweise, damit weder Verschlüsseln noch
 * Entschlüsseln die ganze Datei im Speicher braucht. Conscrypts GCM puffert beim Verschlüsseln den
 * kompletten Klartext, ein Backup mit vielen Bildern lief damit in den `OutOfMemoryError`.
 * ```
 * Header (47 Byte): "SNE2" · PBKDF2-Iterationen u32 BE · Salt (32) · Nonce-Präfix (7)
 * Chunks:           GCM-Ciphertext ‖ Tag (16), Klartext je 64 KiB
 *                   Nonce = Präfix ‖ Chunk-Index u32 BE ‖ Letzt-Flag (1 Byte), AAD = Header
 * ```
 * Alle Chunks außer dem letzten tragen genau 64 KiB, der letzte 0 bis 64 KiB mit Flag 1, es gibt
 * mindestens einen (STREAM-Konstruktion wie bei Tink). Abgeschnittene, vertauschte und angehängte
 * Chunks fallen so ebenso auf wie ein geänderter Header.
 *
 * **SNE1** (v1.7.0 bis v2.18.x, nur noch gelesen): "SNE1" · Version (1) · Salt (32) · IV (12) ·
 * ein GCM-Block über den ganzen Klartext, 100 000 Iterationen.
 *
 * @param iterations PBKDF2-Iterationen für neue Backups. Nur Tests setzen weniger.
 */
class EncryptionManager(private val iterations: Int = DEFAULT_ITERATIONS) {
    companion object {
        private const val TAG = "EncryptionManager"

        private const val MAGIC_LENGTH = 4
        private val MAGIC_V1 = "SNE1".toByteArray(US_ASCII)
        private val MAGIC_V2 = "SNE2".toByteArray(US_ASCII)
        private const val SALT_LENGTH = 32 // 256 bits
        private const val KEY_LENGTH = 256 // AES-256
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_LENGTH = 16

        // SNE1 (Altbestand)
        private const val V1_VERSION: Byte = 1
        private const val V1_IV_LENGTH = 12
        private const val V1_HEADER_REST = 1 + SALT_LENGTH + V1_IV_LENGTH // nach der Magic
        private const val V1_ITERATIONS = 100_000

        // SNE2
        private const val DEFAULT_ITERATIONS = 600_000 // wie im E2EE-Plan
        private const val MAX_ITERATIONS = 10_000_000L // präparierte Datei soll die App nicht minutenlang rechnen lassen
        private const val ITERATIONS_LENGTH = 4
        private const val NONCE_PREFIX_LENGTH = 7
        private const val NONCE_LENGTH = 12
        private const val HEADER_LENGTH = MAGIC_LENGTH + ITERATIONS_LENGTH + SALT_LENGTH + NONCE_PREFIX_LENGTH
        internal const val CHUNK_SIZE = 64 * 1024

        private const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ENCRYPTION_ALGORITHM = "AES/GCM/NoPadding"
    }

    /**
     * Verschlüsselt alles, was in den zurückgegebenen Stream geschrieben wird, als SNE2 nach [out].
     * Erst `close()` schreibt den letzten Chunk und schließt [out].
     */
    fun encryptingStream(out: OutputStream, password: String): OutputStream {
        val salt = randomBytes(SALT_LENGTH)
        val header = ByteBuffer.allocate(HEADER_LENGTH)
            .put(MAGIC_V2)
            .putInt(iterations)
            .put(salt)
            .put(randomBytes(NONCE_PREFIX_LENGTH))
            .array()
        val chunks = ChunkCipher(deriveKey(password, salt, iterations), header)
        out.write(header)
        return EncryptingStream(out, chunks)
    }

    /**
     * Entschlüsselt SNE2 blockweise, SNE1 komplett im Speicher (Altbestand). Ein falsches Passwort
     * oder eine veränderte Datei wirft beim Lesen [EncryptionException].
     */
    fun decryptingStream(input: InputStream, password: String): InputStream {
        val magic = readExactly(input, MAGIC_LENGTH)
        return when {
            magic.contentEquals(MAGIC_V2) -> {
                val header = magic + readExactly(input, HEADER_LENGTH - MAGIC_LENGTH)
                val fields = ByteBuffer.wrap(header, MAGIC_LENGTH, HEADER_LENGTH - MAGIC_LENGTH)
                val iterations = fields.int.toUInt().toLong()
                if (iterations !in 1..MAX_ITERATIONS) {
                    throw EncryptionException("Unsupported PBKDF2 iteration count: $iterations")
                }
                val salt = ByteArray(SALT_LENGTH).also { fields.get(it) }
                DecryptingStream(input, ChunkCipher(deriveKey(password, salt, iterations.toInt()), header))
            }
            magic.contentEquals(MAGIC_V1) -> ByteArrayInputStream(decryptV1(input.readBytes(), password))
            else -> throw EncryptionException("Invalid file format: ${String(magic, US_ASCII)}")
        }
    }

    /** Beginnt [data] mit einer Backup-Magic (SNE1 oder SNE2)? */
    fun isEncrypted(data: ByteArray): Boolean {
        if (data.size < MAGIC_LENGTH) return false
        val magic = data.copyOf(MAGIC_LENGTH)
        return magic.contentEquals(MAGIC_V1) || magic.contentEquals(MAGIC_V2)
    }

    /** SNE1 ohne Magic: Version · Salt · IV · Ciphertext. */
    private fun decryptV1(data: ByteArray, password: String): ByteArray {
        if (data.size < V1_HEADER_REST) throw EncryptionException("File too small: ${data.size + MAGIC_LENGTH} bytes")
        if (data[0] != V1_VERSION) throw EncryptionException("Unsupported version: ${data[0]} (expected $V1_VERSION)")
        val key = deriveKey(password, data.copyOfRange(1, 1 + SALT_LENGTH), V1_ITERATIONS)
        val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
        return authenticated {
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, data, 1 + SALT_LENGTH, V1_IV_LENGTH))
            cipher.doFinal(data, V1_HEADER_REST, data.size - V1_HEADER_REST)
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val start = System.currentTimeMillis()
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH)
        val key = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM).generateSecret(spec).encoded
        Logger.d(TAG, "🔑 PBKDF2 ($iterations iterations) took ${System.currentTimeMillis() - start} ms")
        return SecretKeySpec(key, "AES")
    }

    private fun randomBytes(size: Int) = ByteArray(size).also { SecureRandom().nextBytes(it) }

    private fun readExactly(input: InputStream, size: Int): ByteArray {
        val bytes = readUpTo(input, size)
        if (bytes.size < size) throw EncryptionException("File too small: header incomplete")
        return bytes
    }

    /** GCM je Chunk: Nonce aus Präfix, Index und Letzt-Flag, der Header als AAD. */
    private class ChunkCipher(private val key: SecretKeySpec, private val header: ByteArray) {
        private val cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM)
        private var index = 0

        fun seal(plain: ByteArray, length: Int, last: Boolean): ByteArray {
            init(Cipher.ENCRYPT_MODE, last)
            return cipher.doFinal(plain, 0, length)
        }

        fun open(sealed: ByteArray, last: Boolean): ByteArray = authenticated {
            init(Cipher.DECRYPT_MODE, last)
            cipher.doFinal(sealed)
        }

        private fun init(mode: Int, last: Boolean) {
            val nonce = ByteBuffer.allocate(NONCE_LENGTH)
                .put(header, HEADER_LENGTH - NONCE_PREFIX_LENGTH, NONCE_PREFIX_LENGTH)
                .putInt(index++)
                .put((if (last) 1 else 0).toByte())
                .array()
            cipher.init(mode, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(header)
        }
    }

    /** Hält höchstens einen Chunk. Ein voller geht erst raus, wenn weitere Daten folgen. */
    private class EncryptingStream(private val out: OutputStream, private val chunks: ChunkCipher) : OutputStream() {
        private val buffer = ByteArray(CHUNK_SIZE)
        private var filled = 0
        private var closed = false

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            var pos = off
            while (pos < off + len) {
                if (filled == CHUNK_SIZE) {
                    out.write(chunks.seal(buffer, filled, last = false))
                    filled = 0
                }
                val n = minOf(CHUNK_SIZE - filled, off + len - pos)
                System.arraycopy(b, pos, buffer, filled, n)
                filled += n
                pos += n
            }
        }

        override fun flush() = out.flush()

        override fun close() {
            if (closed) return
            closed = true
            out.use { it.write(chunks.seal(buffer, filled, last = true)) }
        }
    }

    /** Liest einen Chunk voraus: nur so ist erkennbar, welcher der letzte ist. */
    private class DecryptingStream(private val input: InputStream, private val chunks: ChunkCipher) : InputStream() {
        private var next = readUpTo(input, CHUNK_SIZE + GCM_TAG_LENGTH)
        private var plain = ByteArray(0)
        private var pos = 0
        private var done = false

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) == -1) -1 else one[0].toUByte().toInt()
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (pos == plain.size) {
                if (done) return -1
                val current = next
                next = if (current.size == CHUNK_SIZE + GCM_TAG_LENGTH) readUpTo(input, current.size) else ByteArray(0)
                done = next.isEmpty()
                plain = chunks.open(current, last = done)
                pos = 0
            }
            val n = minOf(len, plain.size - pos)
            System.arraycopy(plain, pos, b, off, n)
            pos += n
            return n
        }

        override fun close() = input.close()
    }
}

/** Liest bis zu [size] Bytes, weniger nur am Dateiende. */
private fun readUpTo(input: InputStream, size: Int): ByteArray {
    val bytes = ByteArray(size)
    var n = 0
    while (n < size) {
        val read = input.read(bytes, n, size - n)
        if (read == -1) return bytes.copyOf(n)
        n += read
    }
    return bytes
}

private inline fun <T> authenticated(block: () -> T): T = try {
    block()
} catch (e: GeneralSecurityException) {
    Logger.w("EncryptionManager", "Decryption failed: ${e.message}")
    throw EncryptionException("Decryption failed: ${e.message}. Wrong password?", e)
}

/**
 * Exception thrown when encryption/decryption fails
 */
class EncryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)
