package com.yaadein.data.reels

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.yaadein.data.crypto.MediaCrypto
import java.io.File
import java.io.RandomAccessFile
import javax.crypto.Cipher

/**
 * Media3 DataSource that plays an encrypted vault file (same format as media: magic + IV + AES-CTR) chosen by
 * [resolve]. Seeking positions the cipher counter at the right block, so nothing is decrypted to disk.
 */
@UnstableApi
class EncryptedFileDataSource(private val crypto: MediaCrypto, private val resolve: (Uri) -> File?) : BaseDataSource(false) {
    private var file: RandomAccessFile? = null
    private var cipher: Cipher? = null
    private var remaining = 0L
    private var uri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val f = resolve(dataSpec.uri)?.takeIf { it.exists() } ?: throw java.io.FileNotFoundException(dataSpec.uri.toString())
        val iv = crypto.readIv(f)
        val plainLength = crypto.plainLength(f)
        val position = dataSpec.position
        if (position > plainLength) throw java.io.EOFException()
        val raf = RandomAccessFile(f, "r")
        raf.seek(MediaCrypto.HEADER_SIZE + position)
        file = raf
        cipher = crypto.cipherAt(iv, position)
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) minOf(dataSpec.length, plainLength - position) else plainLength - position
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val toRead = minOf(length.toLong(), remaining).toInt()
        val raf = file ?: throw java.io.IOException("not open")
        val encrypted = ByteArray(toRead)
        val n = raf.read(encrypted, 0, toRead)
        if (n <= 0) return C.RESULT_END_OF_INPUT
        val out = cipher!!.update(encrypted, 0, n)
        System.arraycopy(out, 0, buffer, offset, n)
        remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        file?.close(); file = null; cipher = null
        if (uri != null) { uri = null; transferEnded() }
    }

    class Factory(private val crypto: MediaCrypto, private val resolve: (Uri) -> File?) : DataSource.Factory {
        override fun createDataSource(): DataSource = EncryptedFileDataSource(crypto, resolve)
    }
}
