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
import com.yaadein.data.media.MediaImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.yaadein.data.media.MediaStore
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class YaadeinApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    @Inject lateinit var database: AppDatabase
    @Inject lateinit var mediaStore: MediaStore
    @Inject lateinit var importer: MediaImporter

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Finish or discard imports interrupted by process death; prune orphan files.
        appScope.launch { runCatching { importer.recoverPending() } }
    }

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
