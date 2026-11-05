package com.yaadein.feature.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.settings.UserSettings
import com.yaadein.feature.relive.notify.OnThisDayWorker
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.yaadein.core.ui.R as CoreR

@Composable
fun NotificationsRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { viewModel.update { it.copy(onThisDayEnabled = true) }; OnThisDayWorker.schedule(context, settings.onThisDayMinuteOfDay) }
    }
    NotificationsScreen(
        settings = settings,
        onBack = onBack,
        onOnThisDay = { enabled ->
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !OnThisDayWorker.canNotify(context)) {
                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.update { it.copy(onThisDayEnabled = enabled) }
                if (enabled) OnThisDayWorker.schedule(context, settings.onThisDayMinuteOfDay) else OnThisDayWorker.cancel(context)
            }
        },
        onTime = { minute ->
            viewModel.update { it.copy(onThisDayMinuteOfDay = minute) }
            if (settings.onThisDayEnabled) OnThisDayWorker.schedule(context, minute)
        },
        onShake = { viewModel.update { s -> s.copy(shakeForMemory = it) } },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(settings: UserSettings, onBack: () -> Unit, onOnThisDay: (Boolean) -> Unit, onTime: (Int) -> Unit, onShake: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    var pickTime by remember { mutableStateOf(false) }
    val time = LocalTime.of(settings.onThisDayMinuteOfDay / 60, settings.onThisDayMinuteOfDay % 60)
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_notifications), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(CoreR.string.action_back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        SettingsSwitchRow(icon = Icons.Rounded.WbSunny, title = stringResource(R.string.notif_on_this_day), subtitle = stringResource(R.string.notif_on_this_day_help), checked = settings.onThisDayEnabled, onCheckedChange = onOnThisDay)
        SettingsRow(icon = Icons.Rounded.Schedule, title = stringResource(R.string.notif_time), subtitle = time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))) { pickTime = true }
        SettingsSwitchRow(icon = Icons.Rounded.Vibration, title = stringResource(R.string.notif_shake), subtitle = stringResource(R.string.notif_shake_help), checked = settings.shakeForMemory, onCheckedChange = onShake)
        Text(stringResource(R.string.notif_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Yaadein.spacing.gutter))
    }
    if (pickTime) {
        val state = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text(stringResource(R.string.notif_time)) },
            text = { TimePicker(state = state) },
            confirmButton = { TextButton(onClick = { onTime(state.hour * 60 + state.minute); pickTime = false }) { Text(stringResource(CoreR.string.action_done)) } },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(CoreR.string.action_cancel)) } },
        )
    }
}
