package dev.dettmer.simplenotes.utils

import dev.dettmer.simplenotes.backup.EncryptionException
import dev.dettmer.simplenotes.backup.EncryptionManager
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.random.Random
import kotlin.text.Charsets.UTF_8
import org.junit.Assert.*
import org.junit.Test

/**
 * 🔒 Backup-Verschlüsselung: SNE2 (blockweise, wird geschrieben) und SNE1 (Altbestand, nur gelesen).
 */
class EncryptionManagerTest {
    // Wenige Iterationen, sonst rechnet jeder Test Sekunden an PBKDF2.
    private val encryptionManager = EncryptionManager(iterations = 1_000)

    private val chunk = EncryptionManager.CHUNK_SIZE
    private val header = 47
    private val sealedChunk = chunk + 16

    private fun encrypt(data: ByteArray, password: String = "pw"): ByteArray {
        val out = ByteArrayOutputStream()
        encryptionManager.encryptingStream(out, password).use { stream ->
            // Ungerade Häppchen, damit Chunk-Grenzen mitten in einen write() fallen.
            for (i in data.indices step 1000) stream.write(data, i, minOf(1000, data.size - i))
        }
        return out.toByteArray()
    }

    private fun decrypt(file: ByteArray, password: String = "pw"): ByteArray =
        encryptionManager.decryptingStream(file.inputStream(), password).use { it.readBytes() }

    private fun assertRejected(file: ByteArray, password: String = "pw") {
        assertThrows(EncryptionException::class.java) { decrypt(file, password) }
    }

    /** Drei Chunks: zwei volle und ein kurzer letzter. */
    private val threeChunks = Random(1).nextBytes(2 * chunk + 5)

    @Test
    fun `roundtrip across chunk boundaries`() {
        for (size in listOf(0, 1, chunk, chunk + 1, 2 * chunk + 5)) {
            val data = Random(size).nextBytes(size)
            assertArrayEquals("size $size", data, decrypt(encrypt(data)))
        }
    }

    @Test
    fun `file is SNE2 header plus one tag per chunk`() {
        val file = encrypt(threeChunks)

        assertEquals("SNE2", String(file.copyOf(4), UTF_8))
        assertEquals(header + threeChunks.size + 3 * 16, file.size)
        assertEquals(header + 16, encrypt(ByteArray(0)).size)
    }

    @Test
    fun `wrong password throws EncryptionException`() {
        assertRejected(encrypt(threeChunks, "correct"), "wrong")
    }

    @Test
    fun `flipped byte is rejected`() {
        val file = encrypt(threeChunks)
        file[header + chunk + 10] = (file[header + chunk + 10].toInt() xor 1).toByte()
        assertRejected(file)
    }

    @Test
    fun `missing last chunk is rejected`() {
        assertRejected(encrypt(threeChunks).copyOf(header + 2 * sealedChunk))
    }

    @Test
    fun `swapped chunks are rejected`() {
        val file = encrypt(threeChunks)
        val first = file.copyOfRange(header, header + sealedChunk)
        file.copyInto(file, header, header + sealedChunk, header + 2 * sealedChunk)
        first.copyInto(file, header + sealedChunk)
        assertRejected(file)
    }

    @Test
    fun `changed iteration count in header is rejected`() {
        val file = encrypt(threeChunks)
        file[7]++ // u32 BE ab Byte 4: 1000 → 1001
        assertRejected(file)
    }

    @Test
    fun `appended bytes are rejected`() {
        assertRejected(encrypt(threeChunks) + byteArrayOf(0))
        assertRejected(encrypt(ByteArray(chunk)) + ByteArray(sealedChunk))
    }

    @Test
    fun `header only without any chunk is rejected`() {
        assertRejected(encrypt(ByteArray(0)).copyOf(header))
    }

    @Test(timeout = 5_000)
    fun `iteration count above the limit is rejected without deriving a key`() {
        val file = "SNE2".toByteArray(UTF_8) + byteArrayOf(-1, -1, -1, -1) + ByteArray(header - 8 + sealedChunk)
        assertRejected(file)
    }

    @Test
    fun `legacy SNE1 backup still decrypts`() {
        // Erzeugt mit EncryptionManager.encrypt aus v2.18.1 (100 000 Iterationen).
        val sne1 = Base64.getDecoder().decode(
            "U05FMQGfZ1XqTzrm4NgRXS5I21/ZJnImS2I6Am4+CHE4yvD1YSG5GmYDrndDH1CmQFmVOelFigLcDq28RB5LsdnjufQosviVsH9KzEmdw78mSiu0sSE0"
        )

        assertEquals("""{"legacy":"SNE1 äö"}""", String(decrypt(sne1, "legacy-pw"), UTF_8))
        assertRejected(sne1, "wrong")
    }

    @Test
    fun `isEncrypted recognizes both formats only`() {
        assertTrue(encryptionManager.isEncrypted(encrypt(ByteArray(3))))
        assertTrue(encryptionManager.isEncrypted("SNE1".toByteArray(UTF_8)))
        assertFalse(encryptionManager.isEncrypted("{\"notes\":[]}".toByteArray(UTF_8)))
        assertFalse(encryptionManager.isEncrypted("SNE".toByteArray(UTF_8)))
    }

    @Test
    fun `plain JSON is not decrypted`() {
        assertRejected("{\"notes\":[]}".toByteArray(UTF_8))
    }
}
