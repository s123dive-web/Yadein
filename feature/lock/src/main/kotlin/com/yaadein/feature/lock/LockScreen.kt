package com.yaadein.feature.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Button
import com.yaadein.domain.settings.LockMethod
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaadein.core.ui.illustrations.Illustration
import com.yaadein.core.ui.illustrations.IllustrationKind
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.data.crypto.PinManager

@Composable
fun LockRoute(
    biometricLauncher: BiometricLauncher?,
    modifier: Modifier = Modifier,
    deviceLockLauncher: DeviceLockLauncher? = null,
    viewModel: LockViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(biometricLauncher) { viewModel.setBiometricAvailable(biometricLauncher != null) }
    val launchBiometric: (() -> Unit)? = biometricLauncher?.let { launcher ->
        { launcher.unlock(onSuccess = viewModel::onBiometricSuccess, onError = viewModel::onBiometricError) }
    }
    // Offer biometrics automatically when the lock screen appears.
    LaunchedEffect(launchBiometric, state.biometricEnabled, state.lockMethod) {
        if (state.lockMethod == LockMethod.PIN && state.biometricEnabled && launchBiometric != null && state.lockedForMs == 0L) launchBiometric()
    }
    // PIN accepted, fingerprint wanted but not yet sealed: ask once, then open the app either way.
    LaunchedEffect(state.enrollBiometric) {
        if (state.enrollBiometric) {
            val launcher = biometricLauncher
            if (launcher == null) viewModel.onEnrollFinished(false)
            else launcher.enroll(onSuccess = { viewModel.onEnrollFinished(true) }, onError = { viewModel.onEnrollFinished(false) })
        }
    }
    var deviceUnavailable by remember { mutableStateOf(false) }
    val launchDevice: () -> Unit = {
        if (deviceLockLauncher == null) deviceUnavailable = true
        else deviceLockLauncher.unlock(onSuccess = viewModel::onDeviceUnlocked, onError = { if (it == BiometricError.UNAVAILABLE) deviceUnavailable = true })
    }
    LaunchedEffect(state.lockMethod) { if (state.lockMethod == LockMethod.DEVICE) launchDevice() }
    if (state.lockMethod == LockMethod.DEVICE) {
        DeviceLockScreen(unavailable = deviceUnavailable, onUnlock = launchDevice, onOpenAnyway = viewModel::onDeviceUnlocked, modifier = modifier)
        return
    }
    LockScreen(
        state = state,
        onDigit = viewModel::onDigit,
        onBackspace = viewModel::onBackspace,
        onBiometric = launchBiometric,
        modifier = modifier,
        subtitle = when {
            state.migrationPending -> stringResource(R.string.lock_migration_note)
            state.biometricChanged -> stringResource(R.string.lock_biometric_changed)
            else -> stringResource(R.string.lock_subtitle)
        },
    )
}

/** Lock screen for "phone screen lock" mode: one button that opens the system credential prompt. */
@Composable
fun DeviceLockScreen(unavailable: Boolean, onUnlock: () -> Unit, onOpenAnyway: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = Yaadein.spacing
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().padding(spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Illustration(kind = IllustrationKind.LOCK_HEART, modifier = Modifier.size(Yaadein.sizes.illustration * 0.7f))
            Spacer(Modifier.height(spacing.lg))
            Text(text = stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = stringResource(if (unavailable) R.string.lock_device_unavailable else R.string.lock_device_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(spacing.xl))
            if (unavailable) {
                Button(onClick = onOpenAnyway, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.lock_device_open_anyway)) }
            } else {
                Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.lock_device_button)) }
            }
        }
    }
}

@Composable
fun LockScreen(
    state: LockUiState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: (() -> Unit)?,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.lock_title),
    subtitle: String = stringResource(R.string.lock_subtitle),
) {
    val spacing = Yaadein.spacing
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Illustration(kind = IllustrationKind.LOCK_HEART, modifier = Modifier.size(Yaadein.sizes.illustration * 0.7f))
            Spacer(Modifier.height(spacing.lg))
            Text(text = title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(spacing.xs))
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(spacing.xl))
            PinDots(count = state.pin.length, error = state.error is LockError.WrongPin)
            Spacer(Modifier.height(spacing.md))
            StatusLine(state)
            Spacer(Modifier.height(spacing.lg))
            PinPad(
                enabled = !state.checking && state.lockedForMs == 0L,
                onDigit = onDigit,
                onBackspace = onBackspace,
                onBiometric = if (state.biometricEnabled) onBiometric else null,
            )
        }
    }
}

@Composable
private fun StatusLine(state: LockUiState) {
    val text = when {
        state.error is LockError.Wiped -> stringResource(R.string.lock_wiped)
        state.lockedForMs > 0 -> {
            val seconds = ((state.lockedForMs + 999) / 1000).toInt()
            if (seconds >= 60) stringResource(R.string.lock_wait_minutes, (seconds + 59) / 60) else stringResource(R.string.lock_wait_seconds, seconds)
        }
        state.error is LockError.WrongPin -> {
            val remaining = state.attemptsBeforeWipe
            if (remaining != null) pluralStringResource(R.plurals.lock_wrong_pin_wipe, remaining, remaining) else stringResource(R.string.lock_wrong_pin)
        }
        else -> ""
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        modifier = Modifier.height(40.dp),
    )
}

@Composable
fun PinDots(count: Int, error: Boolean, total: Int = PinManager.PIN_LENGTH) {
    val spacing = Yaadein.spacing
    val description = stringResource(R.string.lock_pin_entered, count, total)
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), modifier = Modifier.semantics { contentDescription = description }) {
        repeat(total) { i ->
            val filled = i < count
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            error -> MaterialTheme.colorScheme.error
                            filled -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    ),
            )
        }
    }
}

@Composable
fun PinPad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: (() -> Unit)?,
) {
    val spacing = Yaadein.spacing
    val keySize = Yaadein.sizes.touchTarget + spacing.xl
    val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'))
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth()) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
                row.forEach { d -> PinKey(label = d.toString(), enabled = enabled, onClick = { onDigit(d) }, size = keySize) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(keySize), contentAlignment = Alignment.Center) {
                if (onBiometric != null) {
                    IconButton(onClick = onBiometric, enabled = enabled, modifier = Modifier.size(keySize)) {
                        Icon(Icons.Rounded.Fingerprint, contentDescription = stringResource(R.string.lock_use_biometric), modifier = Modifier.size(Yaadein.sizes.iconLg), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            PinKey(label = "0", enabled = enabled, onClick = { onDigit('0') }, size = keySize)
            IconButton(onClick = onBackspace, enabled = enabled, modifier = Modifier.size(keySize)) {
                Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = stringResource(R.string.lock_backspace), modifier = Modifier.size(Yaadein.sizes.icon))
            }
        }
    }
}

@Composable
private fun PinKey(label: String, enabled: Boolean, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = label, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
