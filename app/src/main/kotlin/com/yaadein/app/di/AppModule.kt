package com.yaadein.app.di

import android.content.Context
import com.yaadein.data.crypto.BiometricKeys
import com.yaadein.data.crypto.PinManager
import com.yaadein.data.crypto.VaultKeys
import com.yaadein.data.media.MediaStore
import com.yaadein.feature.lock.VaultWiper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.system.exitProcess

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /**
     * Wipe = delete the Keystore wrapping key (making DB + media unrecoverable), remove the files,
     * clear the PIN, then restart the process so no decrypted state survives in memory.
     */
    @Provides
    @Singleton
    fun vaultWiper(
        @ApplicationContext context: Context,
        keys: VaultKeys,
        mediaStore: dagger.Lazy<MediaStore>,
        pinManager: PinManager,
        biometricKeys: BiometricKeys,
    ): VaultWiper = VaultWiper {
        keys.wipe()
        biometricKeys.delete()
        mediaStore.get().wipeAll()
        pinManager.clear()
        context.getDatabasePath("yaadein.db").let { db ->
            db.delete()
            java.io.File(db.path + "-wal").delete()
            java.io.File(db.path + "-shm").delete()
        }
        context.filesDir.resolve("datastore").deleteRecursively()
        exitProcess(0)
    }
}
