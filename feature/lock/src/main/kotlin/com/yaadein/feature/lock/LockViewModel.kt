package com.yaadein.feature.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaadein.data.crypto.PinManager
import com.yaadein.data.crypto.PinResult
import com.yaadein.data.lock.AppLockState
import com.yaadein.domain.repository.SettingsRepository
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
)

sealed interface LockError {
    data class WrongPin(val failures: Int) : LockError
    data object Wiped : LockError
}

/** Thin interface so the ViewModel can request a biometric prompt without touching Android APIs. */
fun interface BiometricLauncher {
    fun launch(onSuccess: () -> Unit, onError: (String?) -> Unit)
}

@HiltViewModel
class LockViewModel @Inject constructor(
    private val pinManager: PinManager,
    private val lockState: AppLockState,
    private val settingsRepository: SettingsRepository,
    private val wiper: VaultWiper,
) : ViewModel() {
    private val _state = MutableStateFlow(LockUiState())
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            _state.update { it.copy(biometricEnabled = settings.biometricEnabled, lockedForMs = pinManager.remainingLockMs()) }
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

    private fun submit(pin: String) {
        viewModelScope.launch {
            _state.update { it.copy(checking = true) }
            val wipe = settingsRepository.current().wipeAfterTenFailures
            val result = withContext(Dispatchers.Default) {
                pinManager.verify(pin, wipeEnabled = wipe, onWipe = { wiper.wipeEverything() })
            }
            when (result) {
                PinResult.Success -> {
                    _state.update { it.copy(pin = "", checking = false) }
                    lockState.unlock()
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
