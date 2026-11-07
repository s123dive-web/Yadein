package com.yaadein.data.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class MediaCryptoTest {
    private val key = SecretKeySpec(ByteArray(32) { (it * 7).toByte() }, "AES")
    private val crypto = MediaCrypto(key)

    @TempDir lateinit var dir: File

    private fun random(n: Int) = ByteArray(n).also { SecureRandom().nextBytes(it) }

    @Test
    fun `round trip preserves bytes and reports plaintext sha256`() {
        for (size in listOf(0, 1, MediaCrypto.CHUNK - 1, MediaCrypto.CHUNK, MediaCrypto.CHUNK + 1, 3 * MediaCrypto.CHUNK, 1_000_003)) {
            val plain = random(size)
            val target = File(dir, "a$size.enc")
            val sha = crypto.encryptBytes(plain, target)
            assertEquals(MediaCrypto.sha256(plain), sha)
            assertEquals(size.toLong(), crypto.plainLength(target), "length for $size")
            assertArrayEquals(plain, crypto.decryptToBytes(target), "bytes for $size")
            assertEquals(sha, crypto.sha256OfDecrypted(target))
        }
    }

    @Test
    fun `new files use the authenticated format and differ between files`() {
        val plain = "the same secret bytes, twice".toByteArray()
        val a = File(dir, "a.enc"); val b = File(dir, "b.enc")
        crypto.encryptBytes(plain, a); crypto.encryptBytes(plain, b)
        assertArrayEquals(MediaCrypto.MAGIC_V2, a.readBytes().copyOfRange(0, 4))
        assertTrue(!a.readBytes().contentEquals(b.readBytes()))
    }

    @Test
    fun `random access reads any range across chunk boundaries`() {
        val plain = random(5 * MediaCrypto.CHUNK + 777)
        val target = File(dir, "c.enc")
        crypto.encryptBytes(plain, target)
        crypto.openReader(target).use { r ->
            assertEquals(plain.size.toLong(), r.length)
            for (offset in listOf(0L, 12_345L, MediaCrypto.CHUNK - 10L, 4L * MediaCrypto.CHUNK + 5, plain.size - 3L)) {
                val want = minOf(20_000, plain.size - offset.toInt())
                val out = ByteArray(want)
                var got = 0
                while (got < want) {
                    val n = r.read(offset + got, out, got, want - got)
                    assertTrue(n > 0)
                    got += n
                }
                assertArrayEquals(plain.copyOfRange(offset.toInt(), offset.toInt() + want), out, "offset $offset")
            }
            assertEquals(-1, r.read(plain.size.toLong(), ByteArray(4), 0, 4))
        }
    }

    @Test
    fun `a changed byte is detected`() {
        val target = File(dir, "d.enc")
        crypto.encryptBytes(random(200_000), target)
        val bytes = target.readBytes(); bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 1).toByte(); target.writeBytes(bytes)
        assertThrows(IOException::class.java) { crypto.decryptToBytes(target) }
        crypto.openReader(target).use { r -> assertThrows(IOException::class.java) { r.read(r.length / 2, ByteArray(10), 0, 10) } }
    }

    @Test
    fun `a file cut at a chunk boundary is detected`() {
        val target = File(dir, "t.enc")
        crypto.encryptBytes(random(3 * MediaCrypto.CHUNK + 100), target)
        val cut = 4 + 7 + 2 * (MediaCrypto.CHUNK + 16)
        target.writeBytes(target.readBytes().copyOf(cut))
        assertThrows(IOException::class.java) { crypto.decryptToBytes(target) }
    }

    @Test
    fun `wrong key cannot read`() {
        val target = File(dir, "w.enc")
        crypto.encryptBytes(ByteArray(4096) { it.toByte() }, target)
        val other = MediaCrypto(SecretKeySpec(ByteArray(32) { 1 }, "AES"))
        assertThrows(IOException::class.java) { other.decryptToBytes(target) }
    }

    @Test
    fun `older CTR files still open, sequentially and with random access`() {
        val plain = random(150_000)
        val iv = random(16)
        val c = Cipher.getInstance("AES/CTR/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv)) }
        val legacy = File(dir, "v1.enc").apply { writeBytes(MediaCrypto.MAGIC + iv + c.doFinal(plain)) }
        assertEquals(plain.size.toLong(), crypto.plainLength(legacy))
        assertArrayEquals(plain, crypto.decryptToBytes(legacy))
        crypto.openReader(legacy).use { r ->
            val out = ByteArray(1000)
            assertEquals(1000, r.read(70_001, out, 0, 1000))
            assertArrayEquals(plain.copyOfRange(70_001, 71_001), out)
        }
    }

    @Test
    fun `rejects files without the magic header`() {
        val bogus = File(dir, "e.enc").apply { writeBytes(ByteArray(64)) }
        assertThrows(IllegalStateException::class.java) { crypto.decryptToBytes(bogus) }
    }
}
