package com.yaadein.data.lock

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yaadein.data.crypto.Argon2PinHasher
import com.yaadein.data.crypto.EncryptedPrefsSecretStore
import com.yaadein.data.crypto.KeystoreKeyWrapper
import com.yaadein.data.crypto.PinManager
import com.yaadein.data.crypto.PinResult
import com.yaadein.data.crypto.VaultKeys
import com.yaadein.data.crypto.VaultLockedException
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.db.DatabaseFactory
import com.yaadein.data.db.entity.PersonEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The upgrade path on real hardware crypto: an install from before PIN-bound keys (Keystore-only keys + PIN hash +
 * SQLCipher database) is migrated by the next correct PIN, after which a fresh process cannot open the database without
 * the PIN. Uses the real Android Keystore, EncryptedSharedPreferences, Argon2id and SQLCipher.
 *
 * ./gradlew :core:data:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class VaultMigrationDeviceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alias = "yaadein_test_master"
    private val prefs = "yaadein_test_secrets"

    private fun store() = EncryptedPrefsSecretStore(context, prefs)
    private fun keys() = VaultKeys(KeystoreKeyWrapper(alias), store(), Argon2PinHasher())
    private fun pins() = PinManager(store(), Argon2PinHasher())
    private fun session(k: VaultKeys = keys()) = VaultSession(k, pins()) { what, t -> throw AssertionError(what, t) }
    private fun db(k: VaultKeys): AppDatabase = DatabaseFactory.encrypted(context) { k.databaseKey() }

    @Before fun clean() = wipe()
    @After fun cleanUp() = wipe()

    private fun wipe() {
        runCatching { KeystoreKeyWrapper(alias).destroy() }
        context.deleteSharedPreferences(prefs)
        context.getDatabasePath(AppDatabase.NAME).let { listOf(it, java.io.File(it.path + "-wal"), java.io.File(it.path + "-shm")).forEach { f -> f.delete() } }
    }

    @Test
    fun legacyInstallMigratesAndThenNeedsThePin() = runBlocking {
        // 1. "Old build": device-wrapped keys, a person in the encrypted DB, a PIN hash.
        val legacy = keys()
        legacy.openWithoutUser()
        db(legacy).also { it.personDao().upsert(PersonEntity(0, "Aaji", null, "FAMILY", null, null, "", 0, 0)); it.close() }
        pins().setPin("246810")

        // 2. New build, first start: opens by itself, migration pending; the right PIN re-seals the keys.
        val k = keys()
        val s = session(k)
        s.start()
        assertEquals(VaultSession.State.Open(migrationPending = true), s.state.value)
        assertTrue(s.unlockWithPin("111111", false) {} is PinResult.Wrong)
        assertEquals(PinResult.Success, s.unlockWithPin("246810", false) {})
        assertEquals(VaultKeys.Protection.PIN, k.protection)

        // 3. Fresh process: locked; nothing can read the DB until the PIN is entered.
        val k2 = keys()
        val s2 = session(k2)
        s2.start()
        assertEquals(VaultSession.State.Locked, s2.state.value)
        try { k2.databaseKey(); fail("key must not be available before the PIN") } catch (_: VaultLockedException) { }
        assertEquals(PinResult.Success, s2.unlockWithPin("246810", false) {})
        val names = db(k2).let { val n = it.personDao().getAll().map { p -> p.name }; it.close(); n }
        assertEquals(listOf("Aaji"), names)
    }

    @Test
    fun freshInstallBindsTheFirstPin() = runBlocking {
        val s = session()
        s.start()
        assertEquals(VaultSession.State.Open(migrationPending = false), s.state.value)
        s.setPin("135790")
        val s2 = session()
        s2.start()
        assertEquals(VaultSession.State.Locked, s2.state.value)
        assertEquals(PinResult.Success, s2.unlockWithPin("135790", false) {})
    }
}
