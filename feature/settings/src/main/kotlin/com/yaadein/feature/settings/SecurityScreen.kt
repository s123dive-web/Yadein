package com.yaadein.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.PhonelinkLock
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaadein.data.crypto.BiometricKeys
import com.yaadein.data.lock.VaultSession
import com.yaadein.domain.repository.SettingsRepository
import com.yaadein.domain.settings.LockMethod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.settings.LockTimeout
import com.yaadein.domain.settings.UserSettings
import com.yaadein.core.ui.R as CoreR

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val session: VaultSession,
    private val biometricKeys: BiometricKeys,
) : ViewModel() {
    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())

    fun update(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    /** After a successful enrollment prompt (fingerprint copy stored) or a refusal. */
    fun biometricEnrolled(ok: Boolean) = update { it.copy(biometricEnabled = ok) }

    fun disableBiometric() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { session.clearBiometricCopy(); biometricKeys.delete() }
            settingsRepository.update { it.copy(biometricEnabled = false) }
        }
    }

    /** Re-seals the keys device-only and forgets the PIN (the user confirmed with the phone's screen lock). */
    fun useDeviceLock() {
        viewModelScope.launch {
            withContext(Dispatchers.Default) { session.useDeviceLock(); biometricKeys.delete() }
            settingsRepository.update { it.copy(lockMethod = LockMethod.DEVICE, biometricEnabled = false, wipeAfterTenFailures = false) }
        }
    }
}

@Composable
fun SecurityRoute(
    biometricAvailable: Boolean,
    onBack: () -> Unit,
    onChangePin: () -> Unit,
    modifier: Modifier = Modifier,
    /** Shows the fingerprint prompt that seals a fingerprint copy of the keys; null when biometrics are unavailable. */
    enrollBiometric: ((done: (Boolean) -> Unit) -> Unit)? = null,
    /** Confirms with the phone's screen lock; null on Android 10 and older or without a screen lock. */
    confirmDeviceLock: ((done: (Boolean) -> Unit) -> Unit)? = null,
    viewModel: SecurityViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var confirmSwitch by remember { mutableStateOf(false) }
    SecurityScreen(
        settings = settings, biometricAvailable = biometricAvailable && enrollBiometric != null, onBack = onBack, onChangePin = onChangePin,
        onBiometric = { on ->
            if (on) enrollBiometric?.invoke { ok -> viewModel.biometricEnrolled(ok) } else viewModel.disableBiometric()
        },
        onTimeout = { viewModel.update { s -> s.copy(lockTimeout = it) } },
        onSecureScreen = { viewModel.update { s -> s.copy(secureScreen = it) } },
        onWipe = { viewModel.update { s -> s.copy(wipeAfterTenFailures = it) } },
        onUseDeviceLock = confirmDeviceLock?.let { { confirmSwitch = true } },
        modifier = modifier,
    )
    if (confirmSwitch) {
        AlertDialog(
            onDismissRequest = { confirmSwitch = false },
            title = { Text(stringResource(R.string.security_device_confirm_title)) },
            text = { Text(stringResource(R.string.security_device_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSwitch = false
                    confirmDeviceLock?.invoke { ok -> if (ok) viewModel.useDeviceLock() }
                }) { Text(stringResource(R.string.security_switch)) }
            },
            dismissButton = { TextButton(onClick = { confirmSwitch = false }) { Text(stringResource(CoreR.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    settings: UserSettings,
    biometricAvailable: Boolean,
    onBack: () -> Unit,
    onChangePin: () -> Unit,
    onBiometric: (Boolean) -> Unit,
    onTimeout: (LockTimeout) -> Unit,
    onSecureScreen: (Boolean) -> Unit,
    onWipe: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onUseDeviceLock: (() -> Unit)? = null,
) {
    val spacing = Yaadein.spacing
    val pinMode = settings.lockMethod == LockMethod.PIN
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_security), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(CoreR.string.action_back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        Column(modifier = Modifier.padding(horizontal = spacing.gutter, vertical = spacing.sm)) {
            Text(stringResource(R.string.security_lock_method), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(if (pinMode) R.string.security_lock_pin else R.string.security_lock_device),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (pinMode) {
            SettingsRow(icon = Icons.Rounded.Password, title = stringResource(R.string.security_change_pin), subtitle = stringResource(R.string.security_change_pin_help), onClick = onChangePin)
            SettingsSwitchRow(icon = Icons.Rounded.Fingerprint, title = stringResource(R.string.security_biometric), subtitle = stringResource(if (biometricAvailable) R.string.security_biometric_help else R.string.security_biometric_unavailable), checked = settings.biometricEnabled && biometricAvailable, onCheckedChange = onBiometric, enabled = biometricAvailable)
            if (onUseDeviceLock != null) {
                SettingsRow(icon = Icons.Rounded.PhonelinkLock, title = stringResource(R.string.security_use_device_lock), subtitle = stringResource(R.string.security_use_device_lock_help), onClick = onUseDeviceLock)
            }
        } else {
            SettingsRow(icon = Icons.Rounded.Password, title = stringResource(R.string.security_set_pin), subtitle = stringResource(R.string.security_set_pin_help), onClick = onChangePin)
        }
        Column(modifier = Modifier.padding(horizontal = spacing.gutter, vertical = spacing.md)) {
            Text(stringResource(R.string.security_timeout), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.security_timeout_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(spacing.sm))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                LockTimeout.entries.forEach { t ->
                    FilterChip(
                        selected = settings.lockTimeout == t,
                        onClick = { onTimeout(t) },
                        label = {
                            Text(
                                when (t) {
                                    LockTimeout.IMMEDIATELY -> stringResource(R.string.timeout_immediately)
                                    LockTimeout.THIRTY_SECONDS -> stringResource(R.string.timeout_30s)
                                    LockTimeout.ONE_MINUTE -> stringResource(R.string.timeout_1m)
                                    LockTimeout.FIVE_MINUTES -> stringResource(R.string.timeout_5m)
                                },
                            )
                        },
                    )
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = spacing.gutter))
        SettingsSwitchRow(icon = Icons.Rounded.Screenshot, title = stringResource(R.string.security_secure_screen), subtitle = stringResource(R.string.security_secure_screen_help), checked = settings.secureScreen, onCheckedChange = onSecureScreen)
        if (pinMode) {
            SettingsSwitchRow(icon = Icons.Rounded.DeleteForever, title = stringResource(R.string.security_wipe), subtitle = stringResource(R.string.security_wipe_help), checked = settings.wipeAfterTenFailures, onCheckedChange = onWipe)
        }
    }
}
