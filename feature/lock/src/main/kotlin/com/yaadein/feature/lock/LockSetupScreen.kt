package com.yaadein.feature.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.data.crypto.PinManager
import com.yaadein.data.lock.AppLockState
import com.yaadein.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class SetupStep { CHOOSE, CONFIRM, BIOMETRIC }

data class LockSetupUiState(
    val step: SetupStep = SetupStep.CHOOSE,
    val pin: String = "",
    val firstPin: String = "",
    val mismatch: Boolean = false,
    val saving: Boolean = false,
    val useBiometric: Boolean = true,
    val done: Boolean = false,
)

@HiltViewModel
class LockSetupViewModel @Inject constructor(
    private val pinManager: PinManager,
    private val settingsRepository: SettingsRepository,
    private val lockState: AppLockState,
) : ViewModel() {
    private val _state = MutableStateFlow(LockSetupUiState())
    val state: StateFlow<LockSetupUiState> = _state.asStateFlow()

    fun onDigit(d: Char) {
        val s = _state.value
        if (s.saving || s.pin.length >= PinManager.PIN_LENGTH) return
        val pin = s.pin + d
        _state.update { it.copy(pin = pin, mismatch = false) }
        if (pin.length == PinManager.PIN_LENGTH) advance(pin)
    }

    fun onBackspace() = _state.update { it.copy(pin = it.pin.dropLast(1), mismatch = false) }

    fun setBiometric(enabled: Boolean) = _state.update { it.copy(useBiometric = enabled) }

    private fun advance(pin: String) {
        when (_state.value.step) {
            SetupStep.CHOOSE -> _state.update { it.copy(step = SetupStep.CONFIRM, firstPin = pin, pin = "") }
            SetupStep.CONFIRM -> {
                if (pin == _state.value.firstPin) {
                    viewModelScope.launch {
                        _state.update { it.copy(saving = true) }
                        withContext(Dispatchers.Default) { pinManager.setPin(pin) }
                        // The user has just proven the PIN: unlock before the overlay could appear
                        // (it shows as soon as pinConfigured becomes true while the vault is locked).
                        lockState.unlock()
                        settingsRepository.update { it.copy(pinConfigured = true) }
                        _state.update { it.copy(saving = false, pin = "", step = SetupStep.BIOMETRIC) }
                    }
                } else {
                    _state.update { it.copy(step = SetupStep.CHOOSE, firstPin = "", pin = "", mismatch = true) }
                }
            }
            SetupStep.BIOMETRIC -> Unit
        }
    }

    fun finish(biometricAvailable: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(biometricEnabled = biometricAvailable && _state.value.useBiometric) }
            lockState.unlock()
            _state.update { it.copy(done = true) }
        }
    }
}

@Composable
fun LockSetupRoute(
    biometricAvailable: Boolean,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LockSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.done) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onDone() }
    }
    LockSetupScreen(
        state = state,
        biometricAvailable = biometricAvailable,
        onDigit = viewModel::onDigit,
        onBackspace = viewModel::onBackspace,
        onBiometricToggle = viewModel::setBiometric,
        onFinish = { viewModel.finish(biometricAvailable) },
        modifier = modifier,
    )
}

@Composable
fun LockSetupScreen(
    state: LockSetupUiState,
    biometricAvailable: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onBiometricToggle: (Boolean) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = Yaadein.spacing
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (state.step) {
                SetupStep.CHOOSE, SetupStep.CONFIRM -> {
                    Text(
                        text = stringResource(if (state.step == SetupStep.CHOOSE) R.string.setup_choose_title else R.string.setup_confirm_title),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(spacing.sm))
                    Text(
                        text = stringResource(R.string.setup_pin_help),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(spacing.xl))
                    PinDots(count = state.pin.length, error = state.mismatch)
                    Spacer(Modifier.height(spacing.md))
                    Text(
                        text = if (state.mismatch) stringResource(R.string.setup_mismatch) else "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.height(24.dp),
                    )
                    Spacer(Modifier.height(spacing.lg))
                    PinPad(enabled = !state.saving, onDigit = onDigit, onBackspace = onBackspace, onBiometric = null)
                }
                SetupStep.BIOMETRIC -> {
                    Text(text = stringResource(R.string.setup_biometric_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(spacing.sm))
                    Text(
                        text = stringResource(if (biometricAvailable) R.string.setup_biometric_help else R.string.setup_biometric_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(spacing.xl))
                    if (biometricAvailable) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg)) {
                            Text(text = stringResource(R.string.setup_biometric_switch), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Switch(checked = state.useBiometric, onCheckedChange = onBiometricToggle)
                        }
                        Spacer(Modifier.height(spacing.xl))
                    }
                    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.setup_finish)) }
                }
            }
        }
    }
}
