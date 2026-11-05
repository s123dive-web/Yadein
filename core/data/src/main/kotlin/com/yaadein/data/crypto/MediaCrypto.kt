package com.yaadein.data.crypto

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

/**
 * File encryption for photos, videos and thumbnails.
 *
 * Layout: [4-byte magic "YDN1"][16-byte IV][AES-256-CTR ciphertext]. CTR mode keeps the file
 * seekable so ExoPlayer can scrub a video without decrypting the whole file; integrity is covered
 * by a SHA-256 of the plaintext stored in the database and verified when the file is imported.
 */
class MediaCrypto(private val key: SecretKey, private val random: SecureRandom = SecureRandom()) {

    fun encryptingStream(out: OutputStream): OutputStream {
        val iv = ByteArray(IV_SIZE).also(random::nextBytes)
        out.write(MAGIC)
        out.write(iv)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
        return CipherOutputStream(out, cipher)
    }

    fun decryptingStream(input: InputStream): InputStream {
        val header = ByteArray(HEADER_SIZE)
        var read = 0
        while (read < HEADER_SIZE) {
            val n = input.read(header, read, HEADER_SIZE - read)
            if (n < 0) error("Encrypted media header truncated")
            read += n
        }
        check(header.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Not a Yaadein media file" }
        val iv = header.copyOfRange(MAGIC.size, HEADER_SIZE)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
        return CipherInputStream(input, cipher)
    }

    /** Cipher positioned at [plainOffset] bytes into the plaintext, for random-access reads. */
    fun cipherAt(iv: ByteArray, plainOffset: Long): Cipher {
        val block = plainOffset / BLOCK_SIZE
        val counterIv = iv.copyOf()
        // Add `block` to the big-endian 128-bit counter.
        var carry = block
        for (i in counterIv.indices.reversed()) {
            if (carry == 0L) break
            val sum = (counterIv[i].toLong() and 0xFF) + (carry and 0xFF)
            counterIv[i] = (sum and 0xFF).toByte()
            carry = (carry ushr 8) + (sum ushr 8)
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(counterIv))
        // Discard the bytes inside the first partial block.
        val skip = (plainOffset % BLOCK_SIZE).toInt()
        if (skip > 0) cipher.update(ByteArray(skip))
        return cipher
    }

    fun readIv(file: File): ByteArray = FileInputStream(file).use { input ->
        val header = ByteArray(HEADER_SIZE)
        var read = 0
        while (read < HEADER_SIZE) {
            val n = input.read(header, read, HEADER_SIZE - read)
            if (n < 0) error("Encrypted media header truncated")
            read += n
        }
        check(header.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Not a Yaadein media file" }
        header.copyOfRange(MAGIC.size, HEADER_SIZE)
    }

    /** Encrypts [plain] into [target] while computing the plaintext SHA-256. Returns the hex digest. */
    fun encryptFile(plain: InputStream, target: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileOutputStream(target).use { raw ->
            encryptingStream(raw).use { enc ->
                val buf = ByteArray(BUFFER)
                while (true) {
                    val n = plain.read(buf)
                    if (n < 0) break
                    digest.update(buf, 0, n)
                    enc.write(buf, 0, n)
                }
            }
        }
        return digest.digest().toHex()
    }

    fun encryptBytes(plain: ByteArray, target: File): String = encryptFile(plain.inputStream(), target)

    fun decryptToBytes(file: File): ByteArray = decryptingStream(FileInputStream(file)).use { it.readBytes() }

    /** Streams the decrypted content and returns its SHA-256; used to verify a file after import. */
    fun sha256OfDecrypted(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        decryptingStream(FileInputStream(file)).use { input ->
            val buf = ByteArray(BUFFER)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().toHex()
    }

    /** Plaintext length of an encrypted file (CTR mode has no padding). */
    fun plainLength(file: File): Long = (file.length() - HEADER_SIZE).coerceAtLeast(0)

    companion object {
        val MAGIC = byteArrayOf('Y'.code.toByte(), 'D'.code.toByte(), 'N'.code.toByte(), '1'.code.toByte())
        const val IV_SIZE = 16
        const val HEADER_SIZE = 4 + IV_SIZE
        const val BLOCK_SIZE = 16
        private const val BUFFER = 64 * 1024
        private const val TRANSFORMATION = "AES/CTR/NoPadding"

        fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
    }
}
