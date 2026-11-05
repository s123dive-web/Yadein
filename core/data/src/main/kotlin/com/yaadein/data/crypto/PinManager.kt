package com.yaadein.data.crypto

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import com.yaadein.domain.usecase.LockoutPolicy
import java.security.SecureRandom

/** Argon2id hashing. Abstracted so tests can use a fast fake. */
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
 * Stores the PIN as Argon2id(salt, pin) in the secret store and enforces the lockout policy.
 * The number of consecutive failures and the lockout deadline are persisted so restarting the app
 * does not reset the delay.
 */
class PinManager(
    private val store: SecretStore,
    private val hasher: PinHasher = Argon2PinHasher(),
    private val random: SecureRandom = SecureRandom(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val isConfigured: Boolean get() = store.getBytes(KEY_HASH) != null

    fun failures(): Int = store.getInt(KEY_FAILURES, 0)

    /** Milliseconds until the user may try again; 0 when not locked. */
    fun remainingLockMs(): Long = (store.getLong(KEY_LOCK_UNTIL, 0L) - clock()).coerceAtLeast(0L)

    fun setPin(pin: String) {
        require(isValidPin(pin)) { "PIN must be 6 digits" }
        val salt = ByteArray(16).also(random::nextBytes)
        store.putBytes(KEY_SALT, salt)
        store.putBytes(KEY_HASH, hasher.hash(pin, salt))
        store.putInt(KEY_FAILURES, 0)
        store.putLong(KEY_LOCK_UNTIL, 0L)
    }

    fun verify(pin: String, wipeEnabled: Boolean, onWipe: () -> Unit): PinResult {
        val remaining = remainingLockMs()
        if (remaining > 0) return PinResult.LockedOut(remaining)
        val salt = store.getBytes(KEY_SALT) ?: return PinResult.Wrong(0, 0, null)
        val expected = store.getBytes(KEY_HASH) ?: return PinResult.Wrong(0, 0, null)
        val actual = hasher.hash(pin, salt)
        if (constantTimeEquals(expected, actual)) {
            store.putInt(KEY_FAILURES, 0)
            store.putLong(KEY_LOCK_UNTIL, 0L)
            return PinResult.Success
        }
        val failures = failures() + 1
        store.putInt(KEY_FAILURES, failures)
        if (LockoutPolicy.shouldWipe(failures, wipeEnabled)) {
            onWipe()
            return PinResult.Wiped
        }
        val delay = LockoutPolicy.delayAfterFailures(failures)
        if (delay > 0) store.putLong(KEY_LOCK_UNTIL, clock() + delay)
        return PinResult.Wrong(
            failures = failures,
            lockedForMs = delay,
            attemptsBeforeWipe = if (wipeEnabled) LockoutPolicy.attemptsBeforeWipe(failures) else null,
        )
    }

    fun clear() {
        store.remove(KEY_SALT); store.remove(KEY_HASH); store.remove(KEY_FAILURES); store.remove(KEY_LOCK_UNTIL)
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }

    companion object {
        const val PIN_LENGTH = 6
        fun isValidPin(pin: String) = pin.length == PIN_LENGTH && pin.all { it.isDigit() }
        private const val KEY_SALT = "pin.salt"
        private const val KEY_HASH = "pin.hash"
        private const val KEY_FAILURES = "pin.failures"
        private const val KEY_LOCK_UNTIL = "pin.lockUntil"
    }
}
