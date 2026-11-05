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

@Composable
fun SecurityRoute(biometricAvailable: Boolean, onBack: () -> Unit, onChangePin: () -> Unit, modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SecurityScreen(
        settings = settings, biometricAvailable = biometricAvailable, onBack = onBack, onChangePin = onChangePin,
        onBiometric = { viewModel.update { s -> s.copy(biometricEnabled = it) } },
        onTimeout = { viewModel.update { s -> s.copy(lockTimeout = it) } },
        onSecureScreen = { viewModel.update { s -> s.copy(secureScreen = it) } },
        onWipe = { viewModel.update { s -> s.copy(wipeAfterTenFailures = it) } },
        modifier = modifier,
    )
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
) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_security), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(CoreR.string.action_back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        SettingsRow(icon = Icons.Rounded.Password, title = stringResource(R.string.security_change_pin), subtitle = stringResource(R.string.security_change_pin_help), onClick = onChangePin)
        SettingsSwitchRow(icon = Icons.Rounded.Fingerprint, title = stringResource(R.string.security_biometric), subtitle = stringResource(if (biometricAvailable) R.string.security_biometric_help else R.string.security_biometric_unavailable), checked = settings.biometricEnabled && biometricAvailable, onCheckedChange = onBiometric, enabled = biometricAvailable)
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
        SettingsSwitchRow(icon = Icons.Rounded.DeleteForever, title = stringResource(R.string.security_wipe), subtitle = stringResource(R.string.security_wipe_help), checked = settings.wipeAfterTenFailures, onCheckedChange = onWipe)
    }
}
