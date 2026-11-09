package com.yaadein.data.crypto

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import com.yaadein.domain.usecase.LockoutPolicy
import java.security.SecureRandom

/** Argon2id hashing / key derivation. Abstracted so tests can use a fast fake. */
interface PinHasher {
    fun hash(pin: String, salt: ByteArray): ByteArray
}

class Argon2PinHasher : PinHasher {
    private val argon2 by lazy { Argon2Kt() }
    override fun hash(pin: String, salt: ByteArray): ByteArray =
        argon2.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = pin.toByteArray(Charsets.UTF_8),
            salt = salt,
            tCostInIterations = T_COST,
            mCostInKibibyte = M_COST_KIB,
            parallelism = PARALLELISM,
            hashLengthInBytes = 32,
        ).rawHashAsByteArray()

    companion object {
        const val T_COST = 3
        const val M_COST_KIB = 65_536
        const val PARALLELISM = 2
    }
}

sealed interface PinResult {
    data object Success : PinResult
    data class Wrong(val failures: Int, val lockedForMs: Long, val attemptsBeforeWipe: Int?) : PinResult
    data class LockedOut(val remainingMs: Long) : PinResult
    data object Wiped : PinResult
}

/**
 * Enforces the wrong-PIN lockout policy and keeps the legacy PIN hash.
 *
 * Since the vault became PIN-bound ([VaultKeys.openWithPin]) the PIN is checked by trying to open the vault; the
 * Argon2id hash is only consulted for installs that have not been upgraded yet ([verify]'s `check`).
 *
 * The lockout deadline is measured on the monotonic clock (time since boot), which the user cannot change, so moving
 * the phone's date forward no longer skips the delay. After a reboot the monotonic clock restarts, so the full delay
 * starts again from the moment the app next asks.
 */
class PinManager(
    private val store: SecretStore,
    private val hasher: PinHasher = Argon2PinHasher(),
    private val random: SecureRandom = SecureRandom(),
    /** Milliseconds since boot (SystemClock.elapsedRealtime). */
    private val elapsed: () -> Long = { System.nanoTime() / 1_000_000 },
    /** Changes on every reboot (Settings.Global.BOOT_COUNT). */
    private val bootId: () -> Int = { 0 },
) {
    /** A PIN exists, either as the legacy hash or as the key that opens the vault. */
    val isConfigured: Boolean get() = store.getBytes(KEY_HASH) != null || store.getInt(KEY_VAULT_PIN, 0) == 1

    /** True when the PIN hash is the only proof (vault not yet upgraded to PIN-bound keys). */
    val hasLegacyHash: Boolean get() = store.getBytes(KEY_HASH) != null

    fun failures(): Int = store.getInt(KEY_FAILURES, 0)

    /** Milliseconds until the user may try again; 0 when not locked. */
    @Synchronized
    fun remainingLockMs(): Long {
        migrateWallClockLock()
        val duration = store.getLong(KEY_LOCK_DURATION, 0L)
        if (duration <= 0L) return 0L
        val now = elapsed()
        if (store.getInt(KEY_LOCK_BOOT, Int.MIN_VALUE) != bootId()) {
            // Rebooted since the lock started: the monotonic clock restarted, so the full delay applies from now.
            store.putInt(KEY_LOCK_BOOT, bootId())
            store.putLong(KEY_LOCK_SET_AT, now)
            return duration
        }
        val since = now - store.getLong(KEY_LOCK_SET_AT, now)
        if (since < 0L) { store.putLong(KEY_LOCK_SET_AT, now); return duration }
        val remaining = duration - since
        if (remaining <= 0L) { store.putLong(KEY_LOCK_DURATION, 0L); return 0L }
        return remaining
    }

    /** Stores the legacy verifier hash. New installs also mark the PIN as vault-bound via [markVaultBound]. */
    fun setPin(pin: String) {
        require(isValidPin(pin)) { "PIN must be 6 digits" }
        val salt = ByteArray(16).also(random::nextBytes)
        store.putBytes(KEY_SALT, salt)
        store.putBytes(KEY_HASH, hasher.hash(pin, salt))
        resetFailures()
    }

    /** The vault keys are now wrapped with the PIN; the legacy hash is no longer needed and is removed. */
    fun markVaultBound() {
        store.putInt(KEY_VAULT_PIN, 1)
        store.remove(KEY_SALT)
        store.remove(KEY_HASH)
    }

    fun matchesLegacyHash(pin: String): Boolean {
        val salt = store.getBytes(KEY_SALT) ?: return false
        val expected = store.getBytes(KEY_HASH) ?: return false
        return constantTimeEquals(expected, hasher.hash(pin, salt))
    }

    /**
     * Runs [check] (opening the vault, or the legacy hash) under the lockout policy. [check] is only called when the
     * user is allowed to try.
     */
    @Synchronized
    fun verify(pin: String, wipeEnabled: Boolean, onWipe: () -> Unit, check: (String) -> Boolean): PinResult {
        val remaining = remainingLockMs()
        if (remaining > 0) return PinResult.LockedOut(remaining)
        if (!isConfigured) return PinResult.Wrong(0, 0, null)
        if (check(pin)) {
            resetFailures()
            return PinResult.Success
        }
        val failures = failures() + 1
        store.putInt(KEY_FAILURES, failures)
        if (LockoutPolicy.shouldWipe(failures, wipeEnabled)) {
            onWipe()
            return PinResult.Wiped
        }
        val delay = LockoutPolicy.delayAfterFailures(failures)
        if (delay > 0) {
            store.putLong(KEY_LOCK_DURATION, delay)
            store.putLong(KEY_LOCK_SET_AT, elapsed())
            store.putInt(KEY_LOCK_BOOT, bootId())
        }
        return PinResult.Wrong(
            failures = failures,
            lockedForMs = delay,
            attemptsBeforeWipe = if (wipeEnabled) LockoutPolicy.attemptsBeforeWipe(failures) else null,
        )
    }

    // Kept for source compatibility with callers that pass only the wipe callback.
    fun verify(pin: String, wipeEnabled: Boolean, onWipe: () -> Unit): PinResult = verify(pin, wipeEnabled, onWipe, ::matchesLegacyHash)

    fun clear() {
        listOf(KEY_SALT, KEY_HASH, KEY_FAILURES, KEY_LOCK_UNTIL_WALL, KEY_LOCK_DURATION, KEY_LOCK_SET_AT, KEY_LOCK_BOOT, KEY_VAULT_PIN)
            .forEach(store::remove)
    }

    private fun resetFailures() {
        store.putInt(KEY_FAILURES, 0)
        store.putLong(KEY_LOCK_DURATION, 0L)
        store.remove(KEY_LOCK_UNTIL_WALL)
    }

    /** Older builds stored a wall-clock deadline; carry a pending lock over once as a monotonic one. */
    private fun migrateWallClockLock() {
        val until = store.getLong(KEY_LOCK_UNTIL_WALL, 0L)
        if (until <= 0L) return
        store.remove(KEY_LOCK_UNTIL_WALL)
        val left = until - System.currentTimeMillis()
        if (left > 0L) {
            store.putLong(KEY_LOCK_DURATION, left.coerceAtMost(MAX_LOCK_MS))
            store.putLong(KEY_LOCK_SET_AT, elapsed())
            store.putInt(KEY_LOCK_BOOT, bootId())
        }
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    companion object {
        const val PIN_LENGTH = 6
        private const val MAX_LOCK_MS = 3_600_000L
        fun isValidPin(pin: String) = pin.length == PIN_LENGTH && pin.all { it.isDigit() }
        private const val KEY_SALT = "pin.salt"
        private const val KEY_HASH = "pin.hash"
        private const val KEY_FAILURES = "pin.failures"
        private const val KEY_LOCK_UNTIL_WALL = "pin.lockUntil"
        private const val KEY_LOCK_DURATION = "pin.lockDurationMs"
        private const val KEY_LOCK_SET_AT = "pin.lockSetAtElapsed"
        private const val KEY_LOCK_BOOT = "pin.lockBoot"
        private const val KEY_VAULT_PIN = "pin.vaultBound"
    }
}
