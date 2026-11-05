package com.yaadein.data.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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

class VaultKeysTest {
    @Test
    fun `keys are generated once and persisted wrapped`() {
        val store = InMemorySecretStore()
        val wrapper = FakeKeyWrapper()
        val keys = VaultKeys(wrapper, store)
        assertFalse(keys.isProvisioned)
        val db1 = keys.databaseKey()
        val media1 = keys.mediaKey().encoded
        assertTrue(keys.isProvisioned)
        assertEquals(32, db1.size)
        assertFalse(db1.contentEquals(media1))
        // Stored blobs are wrapped, not raw.
        assertFalse(store.getBytes("vault.db.key")!!.contentEquals(db1))
        // A second instance over the same store yields the same keys.
        val again = VaultKeys(wrapper, store)
        assertArrayEquals(db1, again.databaseKey())
        assertArrayEquals(media1, again.mediaKey().encoded)
    }

    @Test
    fun `wipe destroys the wrapping key and forgets the blobs`() {
        val store = InMemorySecretStore()
        val wrapper = FakeKeyWrapper()
        val keys = VaultKeys(wrapper, store)
        keys.databaseKey()
        keys.wipe()
        assertTrue(wrapper.destroyed)
        assertFalse(keys.isProvisioned)
    }
}
