package com.yaadein.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yaadein.core.ui.illustrations.Illustration
import com.yaadein.core.ui.illustrations.IllustrationKind
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.data.seed.DemoSeeder
import com.yaadein.domain.repository.MemoryRepository
import com.yaadein.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.yaadein.core.ui.R as CoreR

data class AboutUiState(val memoryCount: Int = -1, val seeding: Boolean = false, val seeded: Boolean = false)

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val seeder: DemoSeeder,
    private val memoryRepository: MemoryRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(AboutUiState())
    val state: StateFlow<AboutUiState> = _state.asStateFlow()

    init { refresh() }

    private fun refresh() {
        viewModelScope.launch { _state.update { it.copy(memoryCount = memoryRepository.count()) } }
    }

    fun loadDemo() {
        viewModelScope.launch {
            _state.update { it.copy(seeding = true) }
            seeder.seed()
            settingsRepository.update { it.copy(demoMode = true) }
            _state.update { it.copy(seeding = false, seeded = true) }
            refresh()
        }
    }

    fun loadLarge() {
        viewModelScope.launch {
            _state.update { it.copy(seeding = true) }
            seeder.seedLarge(2000)
            _state.update { it.copy(seeding = false) }
            refresh()
        }
    }
}

@Composable
fun AboutRoute(onBack: () -> Unit, versionName: String, debugBuild: Boolean, modifier: Modifier = Modifier, onOpenAppInfo: () -> Unit = {}, viewModel: AboutViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AboutScreen(state = state, versionName = versionName, debugBuild = debugBuild, onBack = onBack, onLoadDemo = viewModel::loadDemo, onLoadLarge = viewModel::loadLarge, modifier = modifier, onOpenAppInfo = onOpenAppInfo)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    state: AboutUiState,
    versionName: String,
    debugBuild: Boolean,
    onBack: () -> Unit,
    onLoadDemo: () -> Unit,
    onLoadLarge: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenAppInfo: () -> Unit = {},
) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_about), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(CoreR.string.action_back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Illustration(kind = IllustrationKind.PHOTO_STACK, modifier = Modifier.size(Yaadein.sizes.illustration * 0.8f))
            Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(stringResource(R.string.about_version, versionName), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(CoreR.string.app_credit), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onOpenAppInfo, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.app_info_open)) }
            Text(stringResource(R.string.about_offline), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.about_fonts), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text(stringResource(R.string.about_demo_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.about_demo_help), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.seeding) {
                CircularProgressIndicator()
            } else {
                Button(onClick = onLoadDemo, enabled = state.memoryCount == 0, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (state.memoryCount == 0) R.string.about_demo_load else R.string.about_demo_already))
                }
                if (debugBuild) {
                    OutlinedButton(onClick = onLoadLarge, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_demo_large)) }
                }
            }
            Spacer(Modifier.height(spacing.xxxl))
        }
    }
}
