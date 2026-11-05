package com.yaadein.data.crypto

import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec

/**
 * Holds the two random 256-bit keys that protect the vault: one for the SQLCipher database and one
 * for media files. Both are generated once, wrapped by the [KeyWrapper] (Android Keystore) and
 * persisted in the [SecretStore]. The raw keys only ever live in memory.
 */
class VaultKeys(
    private val wrapper: KeyWrapper,
    private val store: SecretStore,
    private val random: SecureRandom = SecureRandom(),
) {
    @Volatile private var dbKey: ByteArray? = null
    @Volatile private var mediaKey: ByteArray? = null

    val isProvisioned: Boolean get() = store.getBytes(KEY_DB) != null && store.getBytes(KEY_MEDIA) != null

    @Synchronized
    fun databaseKey(): ByteArray = dbKey ?: load(KEY_DB).also { dbKey = it }

    @Synchronized
    fun mediaKey(): SecretKeySpec = SecretKeySpec(mediaKey ?: load(KEY_MEDIA).also { mediaKey = it }, "AES")

    private fun load(name: String): ByteArray {
        val wrapped = store.getBytes(name)
        if (wrapped != null) return wrapper.unwrap(wrapped)
        val fresh = ByteArray(KEY_BYTES).also(random::nextBytes)
        store.putBytes(name, wrapper.wrap(fresh))
        return fresh
    }

    /** Forgets everything: the Keystore key is deleted so wrapped blobs can never be opened again. */
    @Synchronized
    fun wipe() {
        dbKey?.fill(0); mediaKey?.fill(0)
        dbKey = null; mediaKey = null
        store.remove(KEY_DB); store.remove(KEY_MEDIA)
        wrapper.destroy()
    }

    companion object {
        const val KEY_BYTES = 32
        private const val KEY_DB = "vault.db.key"
        private const val KEY_MEDIA = "vault.media.key"
    }
}
