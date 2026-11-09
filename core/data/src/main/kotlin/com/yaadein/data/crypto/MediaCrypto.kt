package com.yaadein.data.crypto

import java.io.Closeable
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec

/**
 * File encryption for photos, videos, thumbnails, voice notes and reels.
 *
 * **Format 2 (written since this version), authenticated:** `[magic "YDN2"][7-byte random file prefix]` followed by
 * 64 KiB chunks, each sealed with AES-256-GCM (16-byte tag). Chunk i uses the nonce `prefix ‖ i (4 bytes) ‖ last-flag`
 * and the header as associated data (the STREAM construction, as in backups). Any changed byte, swapped chunk or cut-off
 * end fails to decrypt, instead of silently returning damaged pixels. Chunks keep the file seekable, so a video can be
 * scrubbed without decrypting it all.
 *
 * **Format 1 (older files), read-only:** `[magic "YDN1"][16-byte IV][AES-256-CTR]`, integrity only via the SHA-256
 * recorded at import. Still opened transparently.
 */
class MediaCrypto(private val keyProvider: () -> SecretKey, private val random: SecureRandom = SecureRandom()) {
    /** Fixed key (tests, device tests). */
    constructor(key: SecretKey) : this({ key })

    // Read on every operation, not at construction: the key exists only once the vault is open
    // (VaultKeys.mediaKey throws VaultLockedException before that).
    private val key: SecretKey get() = keyProvider()

    /** Plaintext written to the returned stream is encrypted (format 2) into [out]. */
    fun encryptingStream(out: OutputStream): OutputStream = ChunkWriter(out)

    /** Sequential decryption of either format. Throws [IOException] if a format-2 file was tampered with or truncated. */
    fun decryptingStream(input: InputStream): InputStream {
        val magic = readExactly(input, 4)
        return when {
            magic.contentEquals(MAGIC_V2) -> ChunkReaderStream(input, magic + readExactly(input, PREFIX_BYTES))
            magic.contentEquals(MAGIC) -> {
                val iv = readExactly(input, IV_SIZE)
                val cipher = Cipher.getInstance(CTR).apply { init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv)) }
                javax.crypto.CipherInputStream(input, cipher)
            }
            else -> error("Not a Yaadein media file")
        }
    }

    /** Random-access reader over the plaintext of [file] (either format), for players that seek. */
    fun openReader(file: File): PlainReader {
        val raf = RandomAccessFile(file, "r")
        try {
            val magic = ByteArray(4).also { raf.readFully(it) }
            return when {
                magic.contentEquals(MAGIC_V2) -> {
                    val prefix = ByteArray(PREFIX_BYTES).also { raf.readFully(it) }
                    V2Reader(raf, magic + prefix)
                }
                magic.contentEquals(MAGIC) -> V1Reader(raf, ByteArray(IV_SIZE).also { raf.readFully(it) })
                else -> error("Not a Yaadein media file")
            }
        } catch (e: Exception) {
            raf.close()
            throw e
        }
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

    /** Plaintext length of an encrypted file of either format. */
    fun plainLength(file: File): Long {
        val magic = FileInputStream(file).use { runCatching { readExactly(it, 4) }.getOrNull() } ?: return 0L
        return if (magic.contentEquals(MAGIC_V2)) v2PlainLength(file.length()) else (file.length() - HEADER_SIZE).coerceAtLeast(0)
    }

    // ---- format 2 ------------------------------------------------------------------------------------------------

    private fun chunkCipher(mode: Int, header: ByteArray, index: Int, last: Boolean): Cipher {
        val nonce = ByteArray(12)
        System.arraycopy(header, 4, nonce, 0, PREFIX_BYTES)
        nonce[7] = (index ushr 24).toByte(); nonce[8] = (index ushr 16).toByte(); nonce[9] = (index ushr 8).toByte(); nonce[10] = index.toByte()
        nonce[11] = if (last) 1 else 0
        return Cipher.getInstance(GCM).apply {
            init(mode, key, GCMParameterSpec(TAG_BITS, nonce))
            updateAAD(header)
        }
    }

    private inner class ChunkWriter(private val out: OutputStream) : OutputStream() {
        private val header = MAGIC_V2 + ByteArray(PREFIX_BYTES).also(random::nextBytes)
        private val buf = ByteArray(CHUNK)
        private var filled = 0
        private var index = 0
        private var closed = false

        init { out.write(header) }

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            var o = off; var left = len
            while (left > 0) {
                // A full buffer is only sealed once more data arrives, so the final chunk is known to be the last.
                if (filled == CHUNK) seal(last = false)
                val n = minOf(left, CHUNK - filled)
                System.arraycopy(b, o, buf, filled, n)
                filled += n; o += n; left -= n
            }
        }

        private fun seal(last: Boolean) {
            out.write(chunkCipher(Cipher.ENCRYPT_MODE, header, index++, last).doFinal(buf, 0, filled))
            filled = 0
        }

        override fun flush() = out.flush()

        override fun close() {
            if (closed) return
            closed = true
            seal(last = true)
            out.close()
        }
    }

    private inner class ChunkReaderStream(input: InputStream, private val header: ByteArray) : InputStream() {
        private val src = java.io.PushbackInputStream(input, 1)
        private val sealedBuf = ByteArray(CHUNK + TAG_BYTES)
        private var chunk = ByteArray(0)
        private var pos = 0
        private var index = 0
        private var done = false

        private fun fill(): Boolean {
            if (done) return false
            var n = 0
            while (n < sealedBuf.size) {
                val r = src.read(sealedBuf, n, sealedBuf.size - n)
                if (r < 0) break
                n += r
            }
            if (n < TAG_BYTES) throw IOException("Encrypted media truncated")
            // It is the last chunk exactly when nothing follows it.
            val peek = src.read()
            val last = peek < 0
            if (!last) src.unread(peek)
            chunk = try {
                chunkCipher(Cipher.DECRYPT_MODE, header, index++, last).doFinal(sealedBuf, 0, n)
            } catch (e: Exception) {
                throw IOException("Encrypted media damaged or truncated", e)
            }
            pos = 0
            if (last) done = true
            return true
        }

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (pos >= chunk.size) if (!fill()) return -1
            val n = minOf(len, chunk.size - pos)
            System.arraycopy(chunk, pos, b, off, n)
            pos += n
            return n
        }

        override fun available(): Int = chunk.size - pos
        override fun close() = src.close()
    }

    private fun v2ChunkCount(fileLength: Long): Long {
        val body = fileLength - V2_HEADER
        if (body < TAG_BYTES) return 0
        return (body + CHUNK + TAG_BYTES - 1) / (CHUNK + TAG_BYTES)
    }

    private fun v2PlainLength(fileLength: Long): Long {
        val chunks = v2ChunkCount(fileLength)
        if (chunks == 0L) return 0L
        return (fileLength - V2_HEADER) - chunks * TAG_BYTES
    }

    /** Seekable plaintext view of a file. */
    interface PlainReader : Closeable {
        val length: Long
        /** Reads up to [len] bytes at [position]; returns -1 at the end. */
        fun read(position: Long, buffer: ByteArray, offset: Int, len: Int): Int
    }

    private inner class V2Reader(private val raf: RandomAccessFile, private val header: ByteArray) : PlainReader {
        private val fileLength = raf.length()
        private val chunks = v2ChunkCount(fileLength)
        override val length: Long = v2PlainLength(fileLength)
        private var cachedIndex = -1L
        private var cached = ByteArray(0)
        private val sealed = ByteArray(CHUNK + TAG_BYTES)

        override fun read(position: Long, buffer: ByteArray, offset: Int, len: Int): Int {
            if (position >= length) return -1
            val index = position / CHUNK
            if (index != cachedIndex) {
                val start = V2_HEADER + index * (CHUNK + TAG_BYTES)
                val size = minOf((CHUNK + TAG_BYTES).toLong(), fileLength - start).toInt()
                raf.seek(start)
                raf.readFully(sealed, 0, size)
                cached = try {
                    chunkCipher(Cipher.DECRYPT_MODE, header, index.toInt(), index == chunks - 1).doFinal(sealed, 0, size)
                } catch (e: Exception) {
                    throw IOException("Encrypted media damaged", e)
                }
                cachedIndex = index
            }
            val inChunk = (position - index * CHUNK).toInt()
            val n = minOf(len, cached.size - inChunk)
            System.arraycopy(cached, inChunk, buffer, offset, n)
            return n
        }

        override fun close() = raf.close()
    }

    private inner class V1Reader(private val raf: RandomAccessFile, private val iv: ByteArray) : PlainReader {
        override val length: Long = (raf.length() - HEADER_SIZE).coerceAtLeast(0)
        private var cipher: Cipher? = null
        private var next = -1L

        override fun read(position: Long, buffer: ByteArray, offset: Int, len: Int): Int {
            if (position >= length) return -1
            val n = minOf(len.toLong(), length - position).toInt()
            if (position != next || cipher == null) {
                cipher = cipherAt(iv, position)
                raf.seek(HEADER_SIZE + position)
            }
            raf.readFully(buffer, offset, n)
            val plain = cipher!!.update(buffer, offset, n)
            System.arraycopy(plain, 0, buffer, offset, plain.size)
            next = position + n
            return n
        }

        override fun close() = raf.close()
    }

    /** Format 1: cipher positioned at [plainOffset] bytes into the plaintext. */
    private fun cipherAt(iv: ByteArray, plainOffset: Long): Cipher {
        val block = plainOffset / BLOCK_SIZE
        val counterIv = iv.copyOf()
        var carry = block
        for (i in counterIv.indices.reversed()) {
            if (carry == 0L) break
            val sum = (counterIv[i].toLong() and 0xFF) + (carry and 0xFF)
            counterIv[i] = (sum and 0xFF).toByte()
            carry = (carry ushr 8) + (sum ushr 8)
        }
        val cipher = Cipher.getInstance(CTR)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(counterIv))
        val skip = (plainOffset % BLOCK_SIZE).toInt()
        if (skip > 0) cipher.update(ByteArray(skip))
        return cipher
    }

    private fun readExactly(input: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var read = 0
        while (read < n) {
            val r = input.read(buf, read, n - read)
            if (r < 0) throw EOFException("Encrypted media header truncated")
            read += r
        }
        return buf
    }

    companion object {
        val MAGIC = byteArrayOf('Y'.code.toByte(), 'D'.code.toByte(), 'N'.code.toByte(), '1'.code.toByte())
        val MAGIC_V2 = byteArrayOf('Y'.code.toByte(), 'D'.code.toByte(), 'N'.code.toByte(), '2'.code.toByte())
        const val IV_SIZE = 16
        const val HEADER_SIZE = 4 + IV_SIZE
        const val BLOCK_SIZE = 16
        const val CHUNK = 64 * 1024
        private const val PREFIX_BYTES = 7
        private const val V2_HEADER = 4 + PREFIX_BYTES
        private const val TAG_BYTES = 16
        private const val TAG_BITS = 128
        private const val BUFFER = 64 * 1024
        private const val CTR = "AES/CTR/NoPadding"
        private const val GCM = "AES/GCM/NoPadding"

        fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
    }
}
