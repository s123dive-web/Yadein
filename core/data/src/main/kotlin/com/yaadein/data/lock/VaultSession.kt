package com.yaadein.data.lock

import android.content.Context
import com.yaadein.data.crash.CrashLog
import dagger.hilt.android.qualifiers.ApplicationContext
import com.yaadein.data.crypto.PinManager
import com.yaadein.data.crypto.PinResult
import com.yaadein.data.crypto.VaultKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the vault keys are in memory for this process, and the only place that opens, re-binds or migrates them.
 *
 * - PIN-protected vault, fresh process: [State.Locked] until the PIN (or a fingerprint) opens it. Nothing that reads the
 *   database is composed before that.
 * - Device-protected vault (onboarding, "phone screen lock", or an install from before PIN-bound keys): opened at
 *   start. An older install with a PIN reports [State.Open] with `migrationPending`; the next correct PIN re-seals the
 *   keys with it ([unlockWithPin]).
 */
@Singleton
class VaultSession(
    private val keys: VaultKeys,
    private val pins: PinManager,
    private val report: (String, Throwable) -> Unit,
) {
    @Inject constructor(keys: VaultKeys, pins: PinManager, @ApplicationContext context: Context) :
        this(keys, pins, { what, t -> CrashLog.recordNonFatal(context, what, t) })

    sealed interface State {
        data object Checking : State
        data object Locked : State
        data class Open(val migrationPending: Boolean) : State
    }

    private val _state = MutableStateFlow<State>(State.Checking)
    val state: StateFlow<State> = _state.asStateFlow()

    val isOpen: Boolean get() = keys.isOpen
    val hasBiometricCopy: Boolean get() = keys.hasBiometricCopy

    /** Decides the start state (Keystore + EncryptedSharedPreferences work, so off the main thread). Idempotent. */
    suspend fun start() = withContext(Dispatchers.IO) {
        if (_state.value != State.Checking) return@withContext
        _state.value = when {
            keys.isOpen -> State.Open(migrationPending = pins.hasLegacyHash)
            keys.protection == VaultKeys.Protection.PIN -> State.Locked
            else -> runCatching { keys.openWithoutUser() }.fold(
                onSuccess = { State.Open(migrationPending = pins.hasLegacyHash) },
                onFailure = { report("vault open", it); State.Locked },
            )
        }
    }

    /** Suspends until the vault is open (e.g. to recover interrupted imports after the first unlock). */
    suspend fun awaitOpen() { state.first { it is State.Open } }

    /** Checks [pin] under the lockout policy; a correct PIN opens the vault (and finishes a pending migration). */
    fun unlockWithPin(pin: String, wipeEnabled: Boolean, onWipe: () -> Unit): PinResult {
        val result = pins.verify(pin, wipeEnabled, onWipe) { candidate ->
            when (keys.protection) {
                VaultKeys.Protection.PIN -> keys.openWithPin(candidate)
                VaultKeys.Protection.DEVICE -> pins.matchesLegacyHash(candidate) && run {
                    migrateToPin(candidate)
                    true
                }
                VaultKeys.Protection.NONE -> false
            }
        }
        if (result == PinResult.Success) _state.value = State.Open(migrationPending = pins.hasLegacyHash)
        return result
    }

    /** First PIN, or a changed PIN. The vault must be open (the user is past onboarding or inside Settings). */
    fun setPin(pin: String) {
        require(PinManager.isValidPin(pin))
        keys.openWithoutUser() // no-op when already open; provisions keys on a fresh install
        keys.protectWithPin(pin)
        pins.markVaultBound()
        _state.value = State.Open(migrationPending = false)
    }

    /** "Use phone screen lock": keys go back to device-only protection and the PIN is forgotten. */
    fun useDeviceLock() {
        keys.removePinProtection()
        pins.clear()
        _state.value = State.Open(migrationPending = false)
    }

    /** Biometric unlock succeeded with [decryptCipher] (or plain auth when the vault is already open). */
    fun unlockWithBiometric(decryptCipher: javax.crypto.Cipher?): Boolean {
        val opened = keys.isOpen || (decryptCipher != null && keys.openWithBiometric(decryptCipher))
        if (opened) _state.value = State.Open(migrationPending = pins.hasLegacyHash)
        return opened
    }

    fun biometricIv(): ByteArray? = keys.biometricIv()
    fun storeBiometricCopy(encryptCipher: javax.crypto.Cipher) = keys.storeBiometricCopy(encryptCipher)
    fun clearBiometricCopy() = keys.clearBiometricCopy()

    private fun migrateToPin(pin: String) {
        runCatching {
            keys.openWithoutUser()
            keys.protectWithPin(pin)
            pins.markVaultBound()
        }.onFailure { report("vault PIN migration", it) }
    }
}
