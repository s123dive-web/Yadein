package com.yaadein.data.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.yaadein.data.crypto.MediaCrypto
import com.yaadein.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.RandomAccessFile
import javax.crypto.Cipher

/**
 * Media3 DataSource that decrypts vault files on the fly. URIs look like `yaadein://media/{id}`.
 * AES-CTR lets us seek: we position the file at HEADER + offset and initialise the cipher counter
 * at the matching block, so ExoPlayer can scrub a 200 MB video without decrypting it all.
 */
@UnstableApi
class EncryptedDataSource(private val db: AppDatabase, private val mediaStore: MediaStore) : BaseDataSource(false) {
    private var file: RandomAccessFile? = null
    private var cipher: Cipher? = null
    private var remaining = 0L
    private var uri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val id = dataSpec.uri.lastPathSegment?.toLongOrNull() ?: throw java.io.IOException("Bad media uri ${dataSpec.uri}")
        val media = runBlocking { db.mediaDao().get(id) } ?: throw java.io.FileNotFoundException("media $id")
        val f = mediaStore.file(media.encryptedPath)
        val iv = mediaStore.crypto.readIv(f)
        val plainLength = mediaStore.crypto.plainLength(f)
        val position = dataSpec.position
        if (position > plainLength) throw java.io.EOFException()
        val raf = RandomAccessFile(f, "r")
        raf.seek(MediaCrypto.HEADER_SIZE + position)
        file = raf
        cipher = mediaStore.crypto.cipherAt(iv, position)
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

    class Factory(private val db: AppDatabase, private val mediaStore: MediaStore) : DataSource.Factory {
        override fun createDataSource(): DataSource = EncryptedDataSource(db, mediaStore)
    }

    companion object {
        const val SCHEME = "yaadein"
        fun uriFor(mediaId: Long): Uri = Uri.parse("$SCHEME://media/$mediaId")
    }
}
