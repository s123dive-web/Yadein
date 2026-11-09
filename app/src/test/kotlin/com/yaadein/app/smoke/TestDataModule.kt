package com.yaadein.app.smoke

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import com.yaadein.data.backup.BackupManager
import com.yaadein.data.crypto.BiometricKeys
import com.yaadein.data.crypto.KeyWrapper
import com.yaadein.data.crypto.MediaCrypto
import com.yaadein.data.crypto.PinHasher
import com.yaadein.data.crypto.PinManager
import com.yaadein.data.crypto.SecretStore
import com.yaadein.data.crypto.InMemorySecretStore
import com.yaadein.data.crypto.VaultKeys
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.db.DatabaseFactory
import com.yaadein.data.di.DataModule
import com.yaadein.data.media.MediaImporter
import com.yaadein.data.media.MediaStore
import com.yaadein.data.media.VideoOptimizer
import com.yaadein.data.polish.NoSmartPolish
import com.yaadein.data.polish.SmartPolishProvider
import com.yaadein.data.repository.MemoryRepositoryImpl
import com.yaadein.data.settings.DataStoreSettingsRepository
import com.yaadein.data.settings.EncryptedSettingsSerializer
import com.yaadein.domain.repository.SettingsRepository
import com.yaadein.domain.settings.UserSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.io.File
import java.security.MessageDigest
import javax.inject.Singleton

/** JVM stand-in for the Keystore: reversible XOR so wrap != plain. */
class XorKeyWrapper : KeyWrapper {
    override fun wrap(plain: ByteArray): ByteArray = byteArrayOf(0x42) + plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    override fun unwrap(wrapped: ByteArray): ByteArray = wrapped.drop(1).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    override fun destroy() = Unit
}

/** Replaces the production data module so the whole app runs on Robolectric without Keystore or SQLCipher. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DataModule::class])
object TestDataModule {
    @Provides @Singleton fun clock(): () -> Long = { System.currentTimeMillis() }
    @Provides @Singleton fun keyWrapper(): KeyWrapper = XorKeyWrapper()
    @Provides @Singleton fun secretStore(): SecretStore = InMemorySecretStore()
    private val fastKdf = object : PinHasher {
        override fun hash(pin: String, salt: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(salt + pin.toByteArray())
    }
    @Provides @Singleton fun vaultKeys(wrapper: KeyWrapper, store: SecretStore): VaultKeys = VaultKeys(wrapper, store, fastKdf)
    @Provides @Singleton fun biometricKeys(): BiometricKeys = BiometricKeys()
    @Provides @Singleton fun pinManager(store: SecretStore): PinManager = PinManager(store, fastKdf)
    @Provides @Singleton fun database(@ApplicationContext context: Context): AppDatabase = DatabaseFactory.plainForTests(context)
    @Provides @Singleton fun mediaCrypto(keys: VaultKeys): MediaCrypto = MediaCrypto({ keys.mediaKey() })
    @Provides @Singleton fun mediaStore(@ApplicationContext context: Context, crypto: MediaCrypto): MediaStore = MediaStore(context, crypto)
    @Provides @Singleton fun videoOptimizer(): VideoOptimizer = VideoOptimizer { _, _ -> null }
    @Provides @Singleton fun mediaImporter(@ApplicationContext context: Context, db: AppDatabase, mediaStore: MediaStore, memoryRepository: MemoryRepositoryImpl, optimizer: VideoOptimizer, clock: () -> Long): MediaImporter =
        MediaImporter(context, db, mediaStore, memoryRepository, optimizer, clock)
    @Provides @Singleton fun smartPolish(): SmartPolishProvider = NoSmartPolish
    @Provides @Singleton fun backupManager(@ApplicationContext context: Context, db: AppDatabase, mediaStore: MediaStore, memoryRepository: MemoryRepositoryImpl): BackupManager =
        BackupManager(context, db, mediaStore, memoryRepository)
    @Provides @Singleton fun settingsDataStore(@ApplicationContext context: Context, wrapper: KeyWrapper): DataStore<UserSettings> =
        DataStoreFactory.create(serializer = EncryptedSettingsSerializer(wrapper), produceFile = { File(context.cacheDir, "settings-test-${System.nanoTime()}.enc") })
    @Provides @Singleton fun settingsRepository(store: DataStore<UserSettings>): SettingsRepository = DataStoreSettingsRepository(store)
}
