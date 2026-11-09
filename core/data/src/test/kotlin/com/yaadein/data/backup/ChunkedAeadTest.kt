package com.yaadein.data.backup

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

class ChunkedAeadTest {
    private val key = SecretKeySpec(ByteArray(32) { 7 }, "AES")
    private val otherKey = SecretKeySpec(ByteArray(32) { 8 }, "AES")
    private val prefix = ByteArray(ChunkedAead.PREFIX_BYTES) { it.toByte() }
    private val aad = "header".toByteArray()

    private fun seal(plain: ByteArray, writeSize: Int = 1000): ByteArray {
        val out = ByteArrayOutputStream()
        ChunkedAeadOutputStream(out, key, prefix, aad).use { s ->
            var off = 0
            while (off < plain.size) { val n = minOf(writeSize, plain.size - off); s.write(plain, off, n); off += n }
        }
        return out.toByteArray()
    }

    private fun open(sealed: ByteArray, k: SecretKeySpec = key): ByteArray =
        ChunkedAeadInputStream(ByteArrayInputStream(sealed), k, prefix, aad).use { it.readBytes() }

    @Test
    fun `round trips at every chunk boundary`() {
        val c = ChunkedAead.CHUNK
        for (size in listOf(0, 1, c - 1, c, c + 1, 2 * c, 3 * c + 17)) {
            val plain = Random(size).nextBytes(size)
            assertArrayEquals(plain, open(seal(plain)), "size $size")
            assertArrayEquals(plain, open(seal(plain, writeSize = c + 3)), "size $size, large writes")
        }
    }

    @Test
    fun `memory stays bounded because output is produced chunk by chunk`() {
        val out = ByteArrayOutputStream()
        val stream = ChunkedAeadOutputStream(out, key, prefix, aad)
        stream.write(ByteArray(ChunkedAead.CHUNK * 4))
        // Three full chunks are already sealed and written before close; only the last waits.
        assertTrue(out.size() >= 3 * (ChunkedAead.CHUNK + ChunkedAead.TAG_BYTES))
        stream.close()
    }

    @Test
    fun `wrong key fails at once as a wrong password`() {
        val sealed = seal(Random(1).nextBytes(ChunkedAead.CHUNK * 2))
        assertThrows<BackupManager.WrongPasswordException> { open(sealed, otherKey) }
    }

    @Test
    fun `dropping the last chunk is detected`() {
        val sealed = seal(Random(2).nextBytes(ChunkedAead.CHUNK * 2 + 10))
        val cut = sealed.copyOf(2 * (ChunkedAead.CHUNK + ChunkedAead.TAG_BYTES))
        assertThrows<IOException> { open(cut) }
    }

    @Test
    fun `a flipped byte is detected`() {
        val sealed = seal(Random(3).nextBytes(ChunkedAead.CHUNK * 2))
        sealed[ChunkedAead.CHUNK + ChunkedAead.TAG_BYTES + 5] = (sealed[ChunkedAead.CHUNK + ChunkedAead.TAG_BYTES + 5].toInt() xor 1).toByte()
        assertThrows<IOException> { open(sealed) }
    }
}
