package com.yaadein.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.core.ui.theme.paletteFor
import com.yaadein.domain.settings.AppTheme
import com.yaadein.domain.settings.DarkMode
import com.yaadein.domain.settings.UserSettings
import com.yaadein.core.ui.R as CoreR

@Composable
fun AppearanceRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    AppearanceScreen(
        settings = settings,
        onTheme = { t -> viewModel.update { it.copy(theme = t) } },
        onDarkMode = { d -> viewModel.update { it.copy(darkMode = d) } },
        onEasyMode = { e -> viewModel.update { it.copy(easyMode = e) } },
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun themeName(theme: AppTheme): String = when (theme) {
    AppTheme.WARM_ALBUM -> stringResource(R.string.theme_warm_album)
    AppTheme.MIDNIGHT -> stringResource(R.string.theme_midnight)
    AppTheme.MONSOON -> stringResource(R.string.theme_monsoon)
    AppTheme.FESTIVE -> stringResource(R.string.theme_festive)
    AppTheme.MINIMAL_MONO -> stringResource(R.string.theme_minimal_mono)
    AppTheme.DYNAMIC -> stringResource(R.string.theme_dynamic)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceScreen(
    settings: UserSettings,
    onTheme: (AppTheme) -> Unit,
    onDarkMode: (DarkMode) -> Unit,
    onEasyMode: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_appearance), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(CoreR.string.action_back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = spacing.gutter, vertical = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(stringResource(R.string.appearance_dark_mode), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(spacing.sm))
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        DarkMode.entries.forEachIndexed { i, mode ->
                            SegmentedButton(
                                selected = settings.darkMode == mode,
                                onClick = { onDarkMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = DarkMode.entries.size),
                            ) {
                                Text(
                                    when (mode) {
                                        DarkMode.SYSTEM -> stringResource(R.string.dark_mode_system)
                                        DarkMode.LIGHT -> stringResource(R.string.dark_mode_light)
                                        DarkMode.DARK -> stringResource(R.string.dark_mode_dark)
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(spacing.lg))
                    Text(stringResource(R.string.appearance_theme), style = MaterialTheme.typography.titleMedium)
                }
            }
            items(AppTheme.entries, key = { it.name }) { theme ->
                ThemeSwatch(theme = theme, selected = settings.theme == theme, onClick = { onTheme(theme) })
            }
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Column {
                    Spacer(Modifier.height(spacing.md))
                    SettingsSwitchRow(
                        icon = null,
                        title = stringResource(R.string.settings_easy_mode),
                        subtitle = stringResource(R.string.settings_easy_mode_subtitle),
                        checked = settings.easyMode,
                        onCheckedChange = onEasyMode,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatch(theme: AppTheme, selected: Boolean, onClick: () -> Unit) {
    val spacing = Yaadein.spacing
    val radius = Yaadein.radius
    val palette = paletteFor(theme)
    val light = palette.light
    val dark = palette.dark
    val name = themeName(theme)
    val dynamic = theme == AppTheme.DYNAMIC
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(radius.lg))
            .border(if (selected) 3.dp else 1.dp, borderColor, RoundedCornerShape(radius.lg))
            .clickable(onClick = onClick, role = Role.RadioButton)
            .semantics { this.selected = selected; contentDescription = name }
            .padding(spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            val colors = if (dynamic) {
                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.surface)
            } else {
                listOf(light.primary, light.secondary, light.surface, dark.surface)
            }
            colors.forEach { c ->
                Box(Modifier.size(28.dp).clip(CircleShape).background(c).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
            }
        }
        Spacer(Modifier.height(spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Yaadein.sizes.iconSm))
            Text(text = name, style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified)
        }
    }
}
