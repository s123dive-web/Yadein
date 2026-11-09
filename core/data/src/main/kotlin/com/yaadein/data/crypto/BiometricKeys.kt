package com.yaadein.data.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * A Keystore AES key that can only be used right after a strong (class 3) biometric authentication, and that Android
 * destroys when a new fingerprint or face is enrolled. It seals a second copy of the vault keys
 * ([VaultKeys.storeBiometricCopy]) so a fingerprint opens the vault without the PIN.
 *
 * Ciphers returned here must be passed to BiometricPrompt as a CryptoObject; they work only after it succeeds.
 * [decryptCipher] throws [android.security.keystore.KeyPermanentlyInvalidatedException] once biometrics changed.
 */
class BiometricKeys(private val alias: String = DEFAULT_ALIAS) {
    private val keyStore: KeyStore by lazy { KeyStore.getInstance(PROVIDER).apply { load(null) } }

    fun encryptCipher(): Cipher {
        delete() // always a fresh key for a fresh enrollment
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, createKey()) }
    }

    fun decryptCipher(iv: ByteArray): Cipher {
        val key = (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: throw android.security.keystore.KeyPermanentlyInvalidatedException("biometric key missing")
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv)) }
    }

    fun delete() {
        runCatching { if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias) }
    }

    private fun createKey(): SecretKey {
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).run {
            init(builder.build())
            generateKey()
        }
    }

    companion object {
        const val DEFAULT_ALIAS = "yaadein_biometric"
        private const val PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
    }
}
