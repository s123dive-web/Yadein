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
import com.yaadein.data.lock.VaultSession
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

    // Lazy so the encrypted database, vault keys and importer are not built on the main thread
    // while the process is starting (Keystore + EncryptedSharedPreferences work is slow on low-end phones).
    @Inject lateinit var database: dagger.Lazy<AppDatabase>
    @Inject lateinit var mediaStore: dagger.Lazy<MediaStore>
    @Inject lateinit var importer: dagger.Lazy<MediaImporter>
    @Inject lateinit var vaultSession: VaultSession

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        com.yaadein.data.crash.CrashLog.install(this, BuildConfig.VERSION_NAME)
        // Finish or discard imports interrupted by process death; prune orphan files. A PIN-protected vault cannot be
        // read before the user unlocks it, so this waits for the first unlock.
        appScope.launch {
            vaultSession.start()
            vaultSession.awaitOpen()
            runCatching { importer.get().recoverPending() }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(EncryptedImageKeyer())
                add(EncryptedImageFetcher.Factory(database.get(), mediaStore.get()))
            }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.2).build() }
            // No disk cache: decrypted pixels must never be written to disk in the clear.
            .diskCache(null as DiskCache?)
            .crossfade(true)
            .build()
}
