package com.yaadein.feature.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaadein.data.crypto.PinManager
import com.yaadein.data.crypto.PinResult
import com.yaadein.data.lock.AppLockState
import com.yaadein.data.lock.VaultSession
import com.yaadein.domain.repository.SettingsRepository
import com.yaadein.domain.settings.LockMethod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LockUiState(
    val pin: String = "",
    val checking: Boolean = false,
    val error: LockError? = null,
    val lockedForMs: Long = 0,
    val biometricAvailable: Boolean = false,
    val biometricEnabled: Boolean = true,
    val attemptsBeforeWipe: Int? = null,
    /** Older install: the next correct PIN re-seals the keys with it (fingerprint is not offered until then). */
    val migrationPending: Boolean = false,
    val lockMethod: LockMethod = LockMethod.PIN,
    /** PIN accepted and a fingerprint copy of the keys should be made before the app opens. */
    val enrollBiometric: Boolean = false,
    val biometricChanged: Boolean = false,
)

sealed interface LockError {
    data class WrongPin(val failures: Int) : LockError
    data object Wiped : LockError
}

/** Biometric prompts, implemented in the app module (Android APIs, Keystore, CryptoObject). */
interface BiometricLauncher {
    /** Authenticates and opens the vault with the fingerprint copy of its keys (or just unlocks when already open). */
    fun unlock(onSuccess: () -> Unit, onError: (BiometricError) -> Unit)
    /** Seals a fingerprint copy of the (open) vault keys. */
    fun enroll(onSuccess: () -> Unit, onError: (BiometricError) -> Unit)
}

enum class BiometricError { CANCELLED, CHANGED, UNAVAILABLE, FAILED }

/** The phone's own screen lock (PIN/pattern/password or biometrics), Android 11+. */
fun interface DeviceLockLauncher {
    fun unlock(onSuccess: () -> Unit, onError: (BiometricError) -> Unit)
}

@HiltViewModel
class LockViewModel @Inject constructor(
    private val pinManager: PinManager,
    private val session: VaultSession,
    private val lockState: AppLockState,
    private val settingsRepository: SettingsRepository,
    private val wiper: VaultWiper,
) : ViewModel() {
    private val _state = MutableStateFlow(LockUiState())
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            val migration = withContext(Dispatchers.IO) { (session.state.value as? VaultSession.State.Open)?.migrationPending == true }
            val canUseBiometric = withContext(Dispatchers.IO) { session.isOpen || session.hasBiometricCopy }
            val lockMs = withContext(Dispatchers.IO) { pinManager.remainingLockMs() }
            _state.update {
                it.copy(
                    biometricEnabled = settings.biometricEnabled && settings.lockMethod == LockMethod.PIN && canUseBiometric && !migration,
                    lockedForMs = lockMs,
                    migrationPending = migration,
                    lockMethod = settings.lockMethod,
                )
            }
            tickLockout()
        }
    }

    fun setBiometricAvailable(available: Boolean) = _state.update { it.copy(biometricAvailable = available) }

    fun onDigit(digit: Char) {
        val s = _state.value
        if (s.checking || s.lockedForMs > 0 || s.pin.length >= PinManager.PIN_LENGTH) return
        val pin = s.pin + digit
        _state.update { it.copy(pin = pin, error = null) }
        if (pin.length == PinManager.PIN_LENGTH) submit(pin)
    }

    fun onBackspace() = _state.update { if (it.checking) it else it.copy(pin = it.pin.dropLast(1), error = null) }

    fun onBiometricSuccess() = lockState.unlock()

    fun onBiometricError(error: BiometricError) {
        if (error == BiometricError.CHANGED) {
            viewModelScope.launch { settingsRepository.update { it.copy(biometricEnabled = false) } }
            _state.update { it.copy(biometricEnabled = false, biometricChanged = true) }
        }
    }

    fun onDeviceUnlocked() = lockState.unlock()

    /** After the fingerprint copy is made (or the user declined), open the app. */
    fun onEnrollFinished(enrolled: Boolean) {
        viewModelScope.launch {
            if (!enrolled) settingsRepository.update { it.copy(biometricEnabled = false) }
            _state.update { it.copy(enrollBiometric = false) }
            lockState.unlock()
        }
    }

    private fun submit(pin: String) {
        viewModelScope.launch {
            _state.update { it.copy(checking = true) }
            val wipe = settingsRepository.current().wipeAfterTenFailures
            val result = withContext(Dispatchers.Default) {
                session.unlockWithPin(pin, wipeEnabled = wipe, onWipe = { wiper.wipeEverything() })
            }
            when (result) {
                PinResult.Success -> {
                    val settings = settingsRepository.current()
                    val hasCopy = withContext(Dispatchers.IO) { session.hasBiometricCopy }
                    // Fingerprint was on but there is no fingerprint copy of the keys yet (just upgraded, or biometrics
                    // changed): make one now, while the user is here, before showing the app.
                    val enroll = settings.biometricEnabled && settings.lockMethod == LockMethod.PIN && !hasCopy && _state.value.biometricAvailable
                    _state.update { it.copy(pin = "", checking = false, migrationPending = false, enrollBiometric = enroll) }
                    if (!enroll) lockState.unlock()
                }
                is PinResult.Wrong -> {
                    _state.update {
                        it.copy(pin = "", checking = false, error = LockError.WrongPin(result.failures), lockedForMs = result.lockedForMs, attemptsBeforeWipe = result.attemptsBeforeWipe)
                    }
                    tickLockout()
                }
                is PinResult.LockedOut -> {
                    _state.update { it.copy(pin = "", checking = false, lockedForMs = result.remainingMs) }
                    tickLockout()
                }
                PinResult.Wiped -> _state.update { it.copy(pin = "", checking = false, error = LockError.Wiped) }
            }
        }
    }

    private fun tickLockout() {
        viewModelScope.launch {
            while (_state.value.lockedForMs > 0) {
                delay(1_000)
                _state.update { it.copy(lockedForMs = pinManager.remainingLockMs()) }
            }
        }
    }
}

/** Erases the vault after too many wrong PINs (bound in the app module). */
fun interface VaultWiper {
    fun wipeEverything()
}
