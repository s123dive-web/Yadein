package com.yaadein.data.crypto

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Thrown when vault keys are needed while the vault is still closed (PIN not entered since the app started). */
class VaultLockedException : IllegalStateException("The vault is locked")

/**
 * Holds the two random 256-bit keys that protect the vault: one for the SQLCipher database and one for media files.
 * The raw keys only ever live in memory.
 *
 * How they are stored ([protection]):
 * - [Protection.DEVICE]: wrapped by the Android Keystore key alone. Used before a PIN exists (onboarding), when the
 *   user chose "phone screen lock", and by installs made before PIN-bound keys existed. The app can open the vault
 *   without the user.
 * - [Protection.PIN]: `salt ‖ Keystore( AES-GCM( Argon2id(PIN, salt), dbKey ‖ mediaKey ) )`. Without the PIN the keys cannot
 *   be recovered, even by code running as the app; the Keystore layer forces any guessing to happen on this phone.
 *   An optional second copy is sealed with a biometric-bound Keystore key ([storeBiometricCopy]), so a fingerprint
 *   opens the vault too.
 *
 * Every change writes the new copy, checks it opens, and only then deletes the old one, so an interruption can never
 * leave the vault without a working copy.
 */
class VaultKeys(
    private val wrapper: KeyWrapper,
    private val store: SecretStore,
    private val kdf: PinHasher = Argon2PinHasher(),
    private val random: SecureRandom = SecureRandom(),
) {
    enum class Protection { NONE, DEVICE, PIN }

    @Volatile private var dbKey: ByteArray? = null
    @Volatile private var mediaKey: ByteArray? = null
    private val _open = MutableStateFlow(false)

    /** True once the keys are in memory for this process. */
    val openState: StateFlow<Boolean> = _open.asStateFlow()
    val isOpen: Boolean get() = dbKey != null && mediaKey != null

    val protection: Protection
        get() = when {
            store.getBytes(KEY_PIN_BLOB) != null -> Protection.PIN
            store.getBytes(KEY_DB) != null && store.getBytes(KEY_MEDIA) != null -> Protection.DEVICE
            else -> Protection.NONE
        }

    val isProvisioned: Boolean get() = protection != Protection.NONE

    val hasBiometricCopy: Boolean get() = store.getBytes(KEY_BIO_BLOB) != null

    /** True when the keys are in memory or can be loaded without the user. Background work checks this first. */
    fun canOpenWithoutUser(): Boolean = isOpen || protection != Protection.PIN

    /** Database key; throws [VaultLockedException] while closed. */
    fun databaseKey(): ByteArray = dbKey ?: autoOpenOrThrow().let { dbKey!! }

    /** Media key; throws [VaultLockedException] while closed. */
    fun mediaKey(): SecretKeySpec = SecretKeySpec(mediaKey ?: autoOpenOrThrow().let { mediaKey!! }, "AES")

    /** Opens without the user where that is allowed (no PIN protection). Generates the keys on first run. */
    @Synchronized
    fun openWithoutUser(): Boolean {
        if (isOpen) return true
        return when (protection) {
            Protection.PIN -> false
            Protection.DEVICE -> {
                setOpen(wrapper.unwrap(store.getBytes(KEY_DB)!!), wrapper.unwrap(store.getBytes(KEY_MEDIA)!!)); true
            }
            Protection.NONE -> {
                val db = ByteArray(KEY_BYTES).also(random::nextBytes)
                val media = ByteArray(KEY_BYTES).also(random::nextBytes)
                store.putBytes(KEY_DB, wrapper.wrap(db))
                store.putBytes(KEY_MEDIA, wrapper.wrap(media))
                setOpen(db, media); true
            }
        }
    }

    /** Tries the PIN. Returns false for a wrong PIN (or when the vault is not PIN-protected). */
    @Synchronized
    fun openWithPin(pin: String): Boolean {
        val record = store.getBytes(KEY_PIN_BLOB) ?: return false
        if (record.size <= SALT_BYTES) return false
        val keys = unsealWithPin(pin, record.copyOfRange(0, SALT_BYTES), record.copyOfRange(SALT_BYTES, record.size)) ?: return false
        setOpen(keys.copyOfRange(0, KEY_BYTES), keys.copyOfRange(KEY_BYTES, KEY_BYTES * 2))
        keys.fill(0)
        return true
    }

    /**
     * Binds the (open) vault to [pin]: writes the PIN copy, verifies it, then removes the device-only copy.
     * Also used to change the PIN; an existing biometric copy stays valid because the keys themselves do not change.
     */
    @Synchronized
    fun protectWithPin(pin: String) {
        val (db, media) = requireOpen()
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val blob = sealWithPin(pin, salt, db + media)
        // Verify before anything is removed.
        val check = unsealWithPin(pin, salt, blob) ?: error("PIN copy did not verify")
        check(check.contentEquals(db + media)) { "PIN copy did not verify" }
        check.fill(0)
        // Salt and sealed keys are one value, so a change is a single atomic write.
        store.putBytes(KEY_PIN_BLOB, salt + blob)
        store.remove(KEY_DB); store.remove(KEY_MEDIA)
    }

    /** Switches back to device-only protection ("use phone screen lock"). Requires the vault to be open. */
    @Synchronized
    fun removePinProtection() {
        val (db, media) = requireOpen()
        store.putBytes(KEY_DB, wrapper.wrap(db))
        store.putBytes(KEY_MEDIA, wrapper.wrap(media))
        check(wrapper.unwrap(store.getBytes(KEY_DB)!!).contentEquals(db)) { "device copy did not verify" }
        store.remove(KEY_PIN_BLOB)
        clearBiometricCopy()
    }

    /**
     * Seals the keys with [encryptCipher], an AES-GCM cipher from a biometric-bound Keystore key that the user has just
     * authenticated (BiometricPrompt CryptoObject).
     */
    @Synchronized
    fun storeBiometricCopy(encryptCipher: Cipher) {
        val (db, media) = requireOpen()
        val sealed = encryptCipher.doFinal(db + media)
        store.putBytes(KEY_BIO_BLOB, encryptCipher.iv + sealed)
    }

    /** IV the biometric decrypt cipher must be initialised with, or null when there is no biometric copy. */
    fun biometricIv(): ByteArray? = store.getBytes(KEY_BIO_BLOB)?.copyOfRange(0, GCM_IV_BYTES)

    /** Opens with a biometric-authenticated decrypt cipher (initialised with [biometricIv]). */
    @Synchronized
    fun openWithBiometric(decryptCipher: Cipher): Boolean {
        val blob = store.getBytes(KEY_BIO_BLOB) ?: return false
        val keys = runCatching { decryptCipher.doFinal(blob, GCM_IV_BYTES, blob.size - GCM_IV_BYTES) }.getOrNull() ?: return false
        if (keys.size != KEY_BYTES * 2) return false
        setOpen(keys.copyOfRange(0, KEY_BYTES), keys.copyOfRange(KEY_BYTES, KEY_BYTES * 2))
        keys.fill(0)
        return true
    }

    fun clearBiometricCopy() = store.remove(KEY_BIO_BLOB)

    /** Forgets everything: the Keystore key is deleted so wrapped blobs can never be opened again. */
    @Synchronized
    fun wipe() {
        dbKey?.fill(0); mediaKey?.fill(0)
        dbKey = null; mediaKey = null
        _open.value = false
        listOf(KEY_DB, KEY_MEDIA, KEY_PIN_BLOB, KEY_BIO_BLOB).forEach(store::remove)
        wrapper.destroy()
    }

    // ---- internals ---------------------------------------------------------------------------------------------

    /** Device-protected vaults open on demand (e.g. a worker in a fresh process); PIN-protected ones need the user. */
    private fun autoOpenOrThrow() {
        if (!openWithoutUser()) throw VaultLockedException()
    }

    private fun requireOpen(): Pair<ByteArray, ByteArray> {
        val db = dbKey; val media = mediaKey
        if (db == null || media == null) throw VaultLockedException()
        return db to media
    }

    private fun setOpen(db: ByteArray, media: ByteArray) {
        dbKey = db; mediaKey = media
        _open.value = true
    }

    private fun sealWithPin(pin: String, salt: ByteArray, plain: ByteArray): ByteArray {
        val kek = kdf.hash(pin, salt)
        try {
            val iv = ByteArray(GCM_IV_BYTES).also(random::nextBytes)
            val cipher = Cipher.getInstance(GCM)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(AAD)
            return wrapper.wrap(iv + cipher.doFinal(plain))
        } finally {
            kek.fill(0)
        }
    }

    private fun unsealWithPin(pin: String, salt: ByteArray, blob: ByteArray): ByteArray? {
        val inner = runCatching { wrapper.unwrap(blob) }.getOrNull() ?: return null
        if (inner.size <= GCM_IV_BYTES) return null
        val kek = kdf.hash(pin, salt)
        return try {
            val cipher = Cipher.getInstance(GCM)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(GCM_TAG_BITS, inner, 0, GCM_IV_BYTES))
            cipher.updateAAD(AAD)
            cipher.doFinal(inner, GCM_IV_BYTES, inner.size - GCM_IV_BYTES).takeIf { it.size == KEY_BYTES * 2 }
        } catch (_: AEADBadTagException) {
            null
        } catch (_: javax.crypto.BadPaddingException) {
            null
        } finally {
            kek.fill(0)
        }
    }

    companion object {
        const val KEY_BYTES = 32
        private const val SALT_BYTES = 16
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val GCM = "AES/GCM/NoPadding"
        private val AAD = "yaadein-vault-pin-v2".toByteArray()
        private const val KEY_DB = "vault.db.key"
        private const val KEY_MEDIA = "vault.media.key"
        private const val KEY_PIN_BLOB = "vault.pin.keys"
        private const val KEY_BIO_BLOB = "vault.bio.keys"
    }
}
