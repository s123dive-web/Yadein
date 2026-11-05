package com.yaadein.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.yaadein.app.media.EncryptedImageFetcher
import com.yaadein.app.media.EncryptedImageKeyer
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.media.MediaStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class YaadeinApplication : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var database: AppDatabase
    @Inject lateinit var mediaStore: MediaStore

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(EncryptedImageKeyer())
                add(EncryptedImageFetcher.Factory(database, mediaStore))
            }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.2).build() }
            // No disk cache: decrypted pixels must never be written to disk in the clear.
            .diskCache(null as DiskCache?)
            .crossfade(true)
            .build()
}
