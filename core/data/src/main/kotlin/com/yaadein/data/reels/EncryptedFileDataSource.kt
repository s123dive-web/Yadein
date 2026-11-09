package com.yaadein.data.reels

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.yaadein.data.crypto.MediaCrypto
import java.io.File

/**
 * Media3 DataSource that plays an encrypted vault file (either media format, see [MediaCrypto]) chosen by [resolve].
 * Reads go through [MediaCrypto.PlainReader], which decrypts only the chunk being played, so seeking is cheap and
 * nothing is written to disk in the clear. A tampered chunk fails the read instead of playing garbage.
 */
@UnstableApi
class EncryptedFileDataSource(private val crypto: MediaCrypto, private val resolve: (Uri) -> File?) : BaseDataSource(false) {
    private var reader: MediaCrypto.PlainReader? = null
    private var position = 0L
    private var remaining = 0L
    private var uri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val f = resolve(dataSpec.uri)?.takeIf { it.exists() } ?: throw java.io.FileNotFoundException(dataSpec.uri.toString())
        val r = crypto.openReader(f)
        if (dataSpec.position > r.length) { r.close(); throw java.io.EOFException() }
        reader = r
        position = dataSpec.position
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) minOf(dataSpec.length, r.length - position) else r.length - position
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val r = reader ?: throw java.io.IOException("not open")
        val n = r.read(position, buffer, offset, minOf(length.toLong(), remaining).toInt())
        if (n <= 0) return C.RESULT_END_OF_INPUT
        position += n
        remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        reader?.close(); reader = null
        if (uri != null) { uri = null; transferEnded() }
    }

    class Factory(private val crypto: MediaCrypto, private val resolve: (Uri) -> File?) : DataSource.Factory {
        override fun createDataSource(): DataSource = EncryptedFileDataSource(crypto, resolve)
    }
}
