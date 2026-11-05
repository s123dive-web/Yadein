package com.yaadein.data.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileInputStream
import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec

class MediaCryptoTest {
    private val key = SecretKeySpec(ByteArray(32) { (it * 7).toByte() }, "AES")
    private val crypto = MediaCrypto(key)

    @TempDir lateinit var dir: File

    @Test
    fun `round trip preserves bytes and reports plaintext sha256`() {
        val plain = ByteArray(1_000_003).also { SecureRandom().nextBytes(it) }
        val target = File(dir, "a.enc")
        val sha = crypto.encryptBytes(plain, target)
        assertEquals(MediaCrypto.sha256(plain), sha)
        assertEquals(MediaCrypto.HEADER_SIZE + plain.size.toLong(), target.length())
        assertEquals(plain.size.toLong(), crypto.plainLength(target))
        assertArrayEquals(plain, crypto.decryptToBytes(target))
        assertEquals(sha, crypto.sha256OfDecrypted(target))
    }

    @Test
    fun `ciphertext differs from plaintext and between files`() {
        val plain = "the same secret bytes, twice".toByteArray()
        val a = File(dir, "a.enc"); val b = File(dir, "b.enc")
        crypto.encryptBytes(plain, a); crypto.encryptBytes(plain, b)
        val ca = a.readBytes().drop(MediaCrypto.HEADER_SIZE); val cb = b.readBytes().drop(MediaCrypto.HEADER_SIZE)
        assert(ca != cb) { "random IV must make ciphertext differ" }
        assert(ca != plain.toList())
    }

    @Test
    fun `random access cipher decrypts from an arbitrary offset`() {
        val plain = ByteArray(70_000) { (it % 251).toByte() }
        val target = File(dir, "c.enc")
        crypto.encryptBytes(plain, target)
        val iv = crypto.readIv(target)
        val offset = 12_345L
        val cipher = crypto.cipherAt(iv, offset)
        val raw = FileInputStream(target).use { it.readBytes() }
        val cipherStart = MediaCrypto.HEADER_SIZE + offset.toInt()
        val chunk = raw.copyOfRange(cipherStart, cipherStart + 5_000)
        val decrypted = cipher.update(chunk)
        assertArrayEquals(plain.copyOfRange(offset.toInt(), offset.toInt() + 5_000), decrypted)
    }

    @Test
    fun `wrong key fails integrity check`() {
        val plain = ByteArray(4096) { it.toByte() }
        val target = File(dir, "d.enc")
        val sha = crypto.encryptBytes(plain, target)
        val other = MediaCrypto(SecretKeySpec(ByteArray(32) { 1 }, "AES"))
        assert(other.sha256OfDecrypted(target) != sha)
    }

    @Test
    fun `rejects files without the magic header`() {
        val bogus = File(dir, "e.enc").apply { writeBytes(ByteArray(64)) }
        assertThrows(IllegalStateException::class.java) { crypto.decryptToBytes(bogus) }
    }
}
