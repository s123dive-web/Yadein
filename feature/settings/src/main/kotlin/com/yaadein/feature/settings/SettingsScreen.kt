package com.yaadein.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.settings.UserSettings

enum class SettingsPage { APPEARANCE, SECURITY, STORAGE, BACKUP, NOTIFICATIONS, FACES, PRIVACY, ABOUT, CRASH_LOG, UPDATE }

@Composable
fun SettingsRoute(
    onOpen: (SettingsPage) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    showUpdate: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsScreen(settings = settings, onToggleEasyMode = { viewModel.update { s -> s.copy(easyMode = it) } }, onOpen = onOpen, contentPadding = contentPadding, modifier = modifier, showUpdate = showUpdate)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    onToggleEasyMode: (Boolean) -> Unit,
    onOpen: (SettingsPage) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** Sideloaded (debug) builds only: Play-distributed apps may not update themselves. */
    showUpdate: Boolean = false,
) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = contentPadding.calculateBottomPadding() + spacing.xxxl)) {
            SettingsSwitchRow(
                icon = Icons.Rounded.Accessibility,
                title = stringResource(R.string.settings_easy_mode),
                subtitle = stringResource(R.string.settings_easy_mode_subtitle),
                checked = settings.easyMode,
                onCheckedChange = onToggleEasyMode,
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = spacing.gutter))
            SettingsRow(Icons.Rounded.Palette, stringResource(R.string.settings_appearance), stringResource(R.string.settings_appearance_subtitle)) { onOpen(SettingsPage.APPEARANCE) }
            SettingsRow(Icons.Rounded.Lock, stringResource(R.string.settings_security), stringResource(R.string.settings_security_subtitle)) { onOpen(SettingsPage.SECURITY) }
            SettingsRow(Icons.Rounded.Notifications, stringResource(R.string.settings_notifications), stringResource(R.string.settings_notifications_subtitle)) { onOpen(SettingsPage.NOTIFICATIONS) }
            SettingsRow(Icons.Rounded.Face, stringResource(R.string.settings_faces), stringResource(R.string.settings_faces_subtitle)) { onOpen(SettingsPage.FACES) }
            SettingsRow(Icons.Rounded.Storage, stringResource(R.string.settings_storage), stringResource(R.string.settings_storage_subtitle)) { onOpen(SettingsPage.STORAGE) }
            SettingsRow(Icons.Rounded.Backup, stringResource(R.string.settings_backup), stringResource(R.string.settings_backup_subtitle)) { onOpen(SettingsPage.BACKUP) }
            HorizontalDivider(modifier = Modifier.padding(horizontal = spacing.gutter))
            SettingsRow(Icons.Rounded.PrivacyTip, stringResource(R.string.settings_privacy), stringResource(R.string.settings_privacy_subtitle)) { onOpen(SettingsPage.PRIVACY) }
            SettingsRow(Icons.Rounded.BugReport, stringResource(R.string.settings_crash_log), stringResource(R.string.settings_crash_log_subtitle)) { onOpen(SettingsPage.CRASH_LOG) }
            if (showUpdate) {
                SettingsRow(Icons.Rounded.SystemUpdate, stringResource(R.string.settings_update), stringResource(R.string.settings_update_subtitle)) { onOpen(SettingsPage.UPDATE) }
            }
            SettingsRow(Icons.Rounded.Info, stringResource(R.string.settings_about), stringResource(R.string.settings_about_subtitle)) { onOpen(SettingsPage.ABOUT) }
        }
    }
}

@Composable
fun SettingsRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    val spacing = Yaadein.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Yaadein.sizes.touchTarget + spacing.lg)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = spacing.gutter, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Yaadein.sizes.icon))
        Spacer(Modifier.width(spacing.lg))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(imageVector = Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingsSwitchRow(icon: ImageVector?, title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val spacing = Yaadein.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Yaadein.sizes.touchTarget + spacing.lg)
            .clickable(enabled = enabled, onClick = { onCheckedChange(!checked) }, role = Role.Switch)
            .semantics { toggleableState = if (checked) ToggleableState.On else ToggleableState.Off }
            .padding(horizontal = spacing.gutter, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Yaadein.sizes.icon))
            Spacer(Modifier.width(spacing.lg))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(spacing.md))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
