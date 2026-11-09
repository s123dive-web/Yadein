package com.yaadein.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import com.yaadein.data.backup.BackupManager
import com.yaadein.data.crypto.Argon2PinHasher
import com.yaadein.data.crypto.BiometricKeys
import com.yaadein.data.crypto.EncryptedPrefsSecretStore
import com.yaadein.data.crypto.KeyWrapper
import com.yaadein.data.crypto.KeystoreKeyWrapper
import com.yaadein.data.crypto.MediaCrypto
import com.yaadein.data.crypto.PinManager
import com.yaadein.data.crypto.SecretStore
import com.yaadein.data.crypto.VaultKeys
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.db.DatabaseFactory
import com.yaadein.data.media.MediaImporter
import com.yaadein.data.media.MediaStore
import com.yaadein.data.media.TransformerVideoOptimizer
import com.yaadein.data.media.VideoOptimizer
import com.yaadein.data.polish.GeminiNanoPolish
import com.yaadein.data.polish.SmartPolishProvider
import com.yaadein.data.repository.MemoryRepositoryImpl
import com.yaadein.data.repository.GroupRepositoryImpl
import com.yaadein.data.repository.MediaRepositoryImpl
import com.yaadein.data.repository.PersonRepositoryImpl
import com.yaadein.data.settings.DataStoreSettingsRepository
import com.yaadein.data.settings.EncryptedSettingsSerializer
import com.yaadein.domain.repository.GroupRepository
import com.yaadein.domain.repository.MediaRepository
import com.yaadein.domain.repository.MemoryRepository
import com.yaadein.domain.repository.PersonRepository
import com.yaadein.domain.repository.SettingsRepository
import com.yaadein.domain.settings.UserSettings
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Clock

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun clock(): () -> Long = { System.currentTimeMillis() }

    @Provides
    @Singleton
    fun keyWrapper(): KeyWrapper = KeystoreKeyWrapper()

    @Provides
    @Singleton
    fun secretStore(@ApplicationContext context: Context): SecretStore = EncryptedPrefsSecretStore(context)

    @Provides
    @Singleton
    fun vaultKeys(wrapper: KeyWrapper, store: SecretStore): VaultKeys = VaultKeys(wrapper, store, Argon2PinHasher())

    @Provides
    @Singleton
    fun biometricKeys(): BiometricKeys = BiometricKeys()

    @Provides
    @Singleton
    fun pinManager(@ApplicationContext context: Context, store: SecretStore): PinManager = PinManager(
        store = store,
        hasher = Argon2PinHasher(),
        elapsed = { android.os.SystemClock.elapsedRealtime() },
        bootId = { android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, 0) },
    )

    /** Built without the key: SQLCipher asks for it on the first query, which only happens once the vault is open. */
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context, keys: VaultKeys): AppDatabase =
        DatabaseFactory.encrypted(context) { keys.databaseKey() }

    @Provides
    @Singleton
    fun mediaCrypto(keys: VaultKeys): MediaCrypto = MediaCrypto({ keys.mediaKey() })

    @Provides
    @Singleton
    fun mediaStore(@ApplicationContext context: Context, crypto: MediaCrypto): MediaStore = MediaStore(context, crypto)

    @Provides
    @Singleton
    fun videoOptimizer(@ApplicationContext context: Context): VideoOptimizer = TransformerVideoOptimizer(context)

    @Provides
    @Singleton
    fun mediaImporter(
        @ApplicationContext context: Context,
        db: AppDatabase,
        mediaStore: MediaStore,
        memoryRepository: MemoryRepositoryImpl,
        optimizer: VideoOptimizer,
        clock: () -> Long,
    ): MediaImporter = MediaImporter(context, db, mediaStore, memoryRepository, optimizer, clock)

    @Provides
    @Singleton
    fun smartPolish(): SmartPolishProvider = GeminiNanoPolish()

    /** Face scan after each import (Settings → Face suggestions); the suggester itself is constructor-injected. */
    @Provides
    @Singleton
    fun faceIndexer(suggester: com.yaadein.data.faces.FaceSuggester): com.yaadein.data.media.FaceIndexer = com.yaadein.data.media.FaceIndexer { suggester.scan(it) }

    @Provides
    @Singleton
    fun backupManager(@ApplicationContext context: Context, db: AppDatabase, mediaStore: MediaStore, memoryRepository: MemoryRepositoryImpl): BackupManager =
        BackupManager(context, db, mediaStore, memoryRepository)

    @Provides
    @Singleton
    fun settingsDataStore(@ApplicationContext context: Context, wrapper: KeyWrapper): DataStore<UserSettings> =
        DataStoreFactory.create(
            serializer = EncryptedSettingsSerializer(wrapper),
            produceFile = { context.dataStoreFile("settings.enc") },
        )

    @Provides
    @Singleton
    fun settingsRepository(store: DataStore<UserSettings>): SettingsRepository = DataStoreSettingsRepository(store)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun memoryRepository(impl: MemoryRepositoryImpl): MemoryRepository
    @Binds abstract fun personRepository(impl: PersonRepositoryImpl): PersonRepository
    @Binds abstract fun groupRepository(impl: GroupRepositoryImpl): GroupRepository
    @Binds abstract fun mediaRepository(impl: MediaRepositoryImpl): MediaRepository
    @Binds abstract fun captureStatsRepository(impl: com.yaadein.data.repository.CaptureStatsRepositoryImpl): com.yaadein.domain.repository.CaptureStatsRepository
    @Binds abstract fun importProgress(impl: com.yaadein.data.media.ImportCoordinator): com.yaadein.domain.repository.ImportProgressSource
}
