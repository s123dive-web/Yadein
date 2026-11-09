package com.yaadein.app.media

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.yaadein.core.ui.media.EncryptedImage
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.media.MediaStore
import okio.Buffer
import okio.FileSystem

/** Decrypts a media file (thumbnail or full) into memory for Coil. Runs on Coil's IO dispatcher. */
class EncryptedImageFetcher(
    private val model: EncryptedImage,
    private val options: Options,
    private val db: AppDatabase,
    private val mediaStore: MediaStore,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val media = db.mediaDao().get(model.mediaId) ?: return null
        val path = if (model.thumbnail) media.thumbPath else media.encryptedPath
        val file = mediaStore.file(path)
        if (!file.exists()) return null
        val bytes = mediaStore.crypto.decryptToBytes(file)
        val buffer = Buffer().write(bytes)
        return SourceFetchResult(
            source = ImageSource(buffer, FileSystem.SYSTEM),
            mimeType = if (media.kind == "VIDEO" && !model.thumbnail) "video/mp4" else "image/jpeg",
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val db: AppDatabase, private val mediaStore: MediaStore) : Fetcher.Factory<EncryptedImage> {
        override fun create(data: EncryptedImage, options: Options, imageLoader: ImageLoader): Fetcher =
            EncryptedImageFetcher(data, options, db, mediaStore)
    }
}

class EncryptedImageKeyer : Keyer<EncryptedImage> {
    override fun key(data: EncryptedImage, options: Options): String = data.cacheKey
}
