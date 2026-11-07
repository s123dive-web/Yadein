package com.yaadein.data.media

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.reels.EncryptedFileDataSource
import kotlinx.coroutines.runBlocking

/**
 * Media3 DataSource that decrypts vault photos and videos on the fly. URIs look like `yaadein://media/{id}`; the id is
 * looked up in the database and the file is read through [EncryptedFileDataSource] (seekable, authenticated).
 */
@UnstableApi
object EncryptedDataSource {
    const val SCHEME = "yaadein"
    fun uriFor(mediaId: Long): Uri = Uri.parse("$SCHEME://media/$mediaId")

    class Factory(private val db: AppDatabase, private val mediaStore: MediaStore) : DataSource.Factory {
        override fun createDataSource(): DataSource = EncryptedFileDataSource(mediaStore.crypto) { uri ->
            val id = uri.lastPathSegment?.toLongOrNull() ?: throw java.io.IOException("Bad media uri $uri")
            // Called on ExoPlayer's loading thread, never the main thread.
            val media = runBlocking { db.mediaDao().get(id) } ?: throw java.io.FileNotFoundException("media $id")
            mediaStore.file(media.encryptedPath)
        }
    }
}
