package com.yaadein.data.backup

import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Streaming authenticated encryption for backups: the plaintext is cut into [CHUNK] byte pieces and
 * each piece is sealed with AES-256-GCM on its own (the STREAM construction).
 *
 * Why: Android's Conscrypt provider cannot stream a single GCM operation. It buffers the whole input
 * until `doFinal`, so one GCM pass over a multi-hundred-megabyte backup ran out of memory and left a
 * file with only the header in it.
 *
 * Nonce for chunk i = 7-byte random prefix ‖ i as 4-byte big-endian ‖ last-chunk flag (1 byte).
 * The header bytes are authenticated as associated data. The counter stops chunks from being
 * reordered and the flag stops the file from being cut short without detection.
 */
internal object ChunkedAead {
    const val CHUNK = 64 * 1024
    const val TAG_BYTES = 16
    const val PREFIX_BYTES = 7

    fun nonce(prefix: ByteArray, index: Int, last: Boolean): ByteArray =
        ByteBuffer.allocate(12).put(prefix).putInt(index).put(if (last) 1 else 0).array()

    fun cipher(mode: Int, key: SecretKey, prefix: ByteArray, index: Int, last: Boolean, aad: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(TAG_BYTES * 8, nonce(prefix, index, last)))
            updateAAD(aad)
        }
}

/** Encrypts everything written to it; [close] seals the final chunk. Closes [out] on close. */
internal class ChunkedAeadOutputStream(
    out: OutputStream,
    private val key: SecretKey,
    private val prefix: ByteArray,
    private val aad: ByteArray,
) : FilterOutputStream(out) {
    private val buffer = ByteArray(ChunkedAead.CHUNK)
    private var filled = 0
    private var index = 0
    private var closed = false

    override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

    override fun write(b: ByteArray, off: Int, len: Int) {
        var o = off
        var remaining = len
        while (remaining > 0) {
            // Only seal a full chunk once more data arrives, so the last chunk is always flagged as last.
            if (filled == buffer.size) seal(last = false)
            val n = minOf(remaining, buffer.size - filled)
            System.arraycopy(b, o, buffer, filled, n)
            filled += n; o += n; remaining -= n
        }
    }

    private fun seal(last: Boolean) {
        val sealed = ChunkedAead.cipher(Cipher.ENCRYPT_MODE, key, prefix, index, last, aad).doFinal(buffer, 0, filled)
        out.write(sealed)
        index++
        filled = 0
    }

    override fun flush() = out.flush()

    override fun close() {
        if (closed) return
        closed = true
        try { seal(last = true); out.flush() } finally { out.close() }
    }
}

/**
 * Decrypts a [ChunkedAeadOutputStream] stream. The first chunk is opened in the constructor so a
 * wrong password fails immediately with [BackupManager.WrongPasswordException].
 */
internal class ChunkedAeadInputStream(
    private val input: InputStream,
    private val key: SecretKey,
    private val prefix: ByteArray,
    private val aad: ByteArray,
) : InputStream() {
    private val sealed = ByteArray(ChunkedAead.CHUNK + ChunkedAead.TAG_BYTES)
    private var plain = ByteArray(0)
    private var pos = 0
    private var index = 0
    private var finished = false
    private var lookahead = -2 // -2 = nothing read ahead yet

    init {
        try {
            nextChunk()
        } catch (e: IOException) {
            if (e.cause is AEADBadTagException || index == 0) throw BackupManager.WrongPasswordException()
            throw e
        }
    }

    private fun readByte(): Int {
        if (lookahead != -2) return lookahead.also { lookahead = -2 }
        return input.read()
    }

    private fun nextChunk() {
        var n = 0
        while (n < sealed.size) {
            if (n == 0 && lookahead != -2) {
                val b = readByte()
                if (b < 0) break
                sealed[n++] = b.toByte()
                continue
            }
            val r = input.read(sealed, n, sealed.size - n)
            if (r < 0) break
            n += r
        }
        // The chunk is the last one exactly when nothing follows it.
        val last = if (n < sealed.size) true else input.read().also { lookahead = it } < 0
        if (n < ChunkedAead.TAG_BYTES) throw IOException("backup truncated")
        plain = try {
            ChunkedAead.cipher(Cipher.DECRYPT_MODE, key, prefix, index, last, aad).doFinal(sealed, 0, n)
        } catch (e: AEADBadTagException) {
            throw IOException("backup chunk $index failed authentication", e)
        }
        pos = 0
        index++
        if (last) finished = true
    }

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        while (pos >= plain.size) {
            if (finished) return -1
            nextChunk()
        }
        val n = minOf(len, plain.size - pos)
        System.arraycopy(plain, pos, b, off, n)
        pos += n
        return n
    }

    override fun available(): Int = plain.size - pos

    override fun close() = input.close()
}
