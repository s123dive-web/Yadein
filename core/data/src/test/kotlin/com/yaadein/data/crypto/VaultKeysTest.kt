package com.yaadein.data.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Reversible stand-in for the Keystore: prefixes and XORs so wrap != plain. */
class FakeKeyWrapper : KeyWrapper {
    var destroyed = false
    override fun wrap(plain: ByteArray): ByteArray = byteArrayOf(0x42) + plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    override fun unwrap(wrapped: ByteArray): ByteArray {
        check(!destroyed) { "key destroyed" }
        require(wrapped[0] == 0x42.toByte())
        return wrapped.drop(1).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    }
    override fun destroy() { destroyed = true }
}

/** Fast deterministic KDF in place of Argon2id. */
class FakeKdf : PinHasher {
    override fun hash(pin: String, salt: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(salt + pin.toByteArray())
}

class VaultKeysTest {
    private val store = InMemorySecretStore()
    private val wrapper = FakeKeyWrapper()
    private fun keys() = VaultKeys(wrapper, store, FakeKdf())

    @Test
    fun `keys are generated once and persisted wrapped`() {
        val keys = keys()
        assertFalse(keys.isProvisioned)
        val db1 = keys.databaseKey()
        val media1 = keys.mediaKey().encoded
        assertTrue(keys.isProvisioned)
        assertEquals(VaultKeys.Protection.DEVICE, keys.protection)
        assertEquals(32, db1.size)
        assertFalse(db1.contentEquals(media1))
        assertFalse(store.getBytes("vault.db.key")!!.contentEquals(db1))
        val again = keys()
        assertArrayEquals(db1, again.databaseKey())
        assertArrayEquals(media1, again.mediaKey().encoded)
    }

    @Test
    fun `binding to a pin removes the device copy and only the pin opens it in a new process`() {
        val first = keys()
        first.openWithoutUser()
        val db = first.databaseKey().copyOf()
        first.protectWithPin("123456")
        assertEquals(VaultKeys.Protection.PIN, first.protection)
        assertNull(store.getBytes("vault.db.key"))
        assertNull(store.getBytes("vault.media.key"))

        val fresh = keys()
        assertFalse(fresh.isOpen)
        assertFalse(fresh.openWithoutUser())
        assertThrows(VaultLockedException::class.java) { fresh.databaseKey() }
        assertFalse(fresh.openWithPin("654321"))
        assertFalse(fresh.isOpen)
        assertTrue(fresh.openWithPin("123456"))
        assertArrayEquals(db, fresh.databaseKey())
    }

    @Test
    fun `changing the pin keeps the same keys`() {
        val keys = keys()
        keys.openWithoutUser()
        val media = keys.mediaKey().encoded
        keys.protectWithPin("111111")
        keys.protectWithPin("222222")
        val fresh = keys()
        assertFalse(fresh.openWithPin("111111"))
        assertTrue(fresh.openWithPin("222222"))
        assertArrayEquals(media, fresh.mediaKey().encoded)
    }

    @Test
    fun `switching to device lock restores the device copy`() {
        val keys = keys()
        keys.openWithoutUser()
        val db = keys.databaseKey().copyOf()
        keys.protectWithPin("123456")
        keys.removePinProtection()
        assertEquals(VaultKeys.Protection.DEVICE, keys.protection)
        val fresh = keys()
        assertTrue(fresh.openWithoutUser())
        assertArrayEquals(db, fresh.databaseKey())
    }

    @Test
    fun `pin binding needs the vault open`() {
        assertThrows(VaultLockedException::class.java) { keys().protectWithPin("123456") }
    }

    @Test
    fun `a biometric copy opens the vault with the right cipher only`() {
        val keys = keys()
        keys.openWithoutUser()
        val db = keys.databaseKey().copyOf()
        keys.protectWithPin("123456")
        val bioKey = SecretKeySpec(ByteArray(32) { 7 }, "AES")
        keys.storeBiometricCopy(Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, bioKey) })
        assertTrue(keys.hasBiometricCopy)

        val fresh = keys()
        val iv = fresh.biometricIv()!!
        val wrong = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, SecretKeySpec(ByteArray(32) { 8 }, "AES"), GCMParameterSpec(128, iv)) }
        assertFalse(fresh.openWithBiometric(wrong))
        val right = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, bioKey, GCMParameterSpec(128, iv)) }
        assertTrue(fresh.openWithBiometric(right))
        assertArrayEquals(db, fresh.databaseKey())
    }

    @Test
    fun `wipe destroys the wrapping key and forgets the blobs`() {
        val keys = keys()
        keys.openWithoutUser()
        keys.protectWithPin("123456")
        keys.wipe()
        assertTrue(wrapper.destroyed)
        assertFalse(keys.isProvisioned)
        assertFalse(keys.isOpen)
    }
}
