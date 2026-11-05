package com.yaadein.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Wraps and unwraps small secrets (DB key, media key) with a hardware-backed key. */
interface KeyWrapper {
    fun wrap(plain: ByteArray): ByteArray
    fun unwrap(wrapped: ByteArray): ByteArray
    /** Removes the wrapping key: everything wrapped with it becomes unrecoverable (used by "wipe"). */
    fun destroy()
}

/** Android Keystore AES-256-GCM implementation. Output layout: [12-byte IV][ciphertext+tag]. */
class KeystoreKeyWrapper(private val alias: String = DEFAULT_ALIAS) : KeyWrapper {
    private val keyStore: KeyStore by lazy { KeyStore.getInstance(PROVIDER).apply { load(null) } }

    private fun key(): SecretKey {
        (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    override fun wrap(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val out = cipher.doFinal(plain)
        return iv + out
    }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        require(wrapped.size > IV_SIZE) { "Wrapped blob too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = wrapped.copyOfRange(0, IV_SIZE)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(wrapped, IV_SIZE, wrapped.size - IV_SIZE)
    }

    override fun destroy() {
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    companion object {
        const val DEFAULT_ALIAS = "yaadein_master"
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_BITS = 128
    }
}
