package com.yaadein.feature.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yaadein.core.ui.theme.Yaadein
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.yaadein.core.ui.R as CoreR

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Ready(val versionName: String) : UpdateUiState
    data object NeedsPermission : UpdateUiState
    data object Installing : UpdateUiState
    data class UpToDate(val installedVersion: String, val fileVersion: String, val fileIsOlder: Boolean) : UpdateUiState
    data object NotThisApp : UpdateUiState
    data object DifferentSignature : UpdateUiState
    data class Failed(val message: String?) : UpdateUiState
}

@HiltViewModel
class UpdateViewModel @Inject constructor(@ApplicationContext context: Context) : ViewModel() {
    private val updater = AppUpdater(context)
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()
    val installedVersion: String = runCatching { updater.installed().versionName }.getOrDefault("?")

    init {
        viewModelScope.launch {
            UpdateEvents.events.collect { e -> if (e is UpdateEvents.Event.Failed) _state.value = UpdateUiState.Failed(e.message) }
        }
    }

    fun onFilePicked(uri: Uri) {
        UpdateEvents.post(UpdateEvents.Event.Idle)
        viewModelScope.launch {
            _state.value = UpdateUiState.Checking
            _state.value = when (val d = runCatching { updater.inspect(uri) }.getOrElse { UpdateDecision.Unreadable }) {
                is UpdateDecision.Install -> UpdateUiState.Ready(d.newVersionName)
                is UpdateDecision.AlreadyUpToDate -> UpdateUiState.UpToDate(d.installedVersionName, d.fileVersionName, d.fileIsOlder)
                UpdateDecision.NotThisApp -> UpdateUiState.NotThisApp
                UpdateDecision.DifferentSignature -> UpdateUiState.DifferentSignature
                UpdateDecision.Unreadable -> UpdateUiState.Failed(null)
            }
        }
    }

    /** Returns the settings intent to show when Android still needs the "Install unknown apps" permission. */
    fun install(): android.content.Intent? {
        if (!updater.canInstall()) { _state.value = UpdateUiState.NeedsPermission; return updater.permissionSettingsIntent() }
        viewModelScope.launch {
            _state.value = UpdateUiState.Installing
            runCatching { updater.install() }.onFailure { _state.value = UpdateUiState.Failed(it.message) }
        }
        return null
    }

    /** Back from the permission screen: continue if it was granted. */
    fun onResumeAfterPermission() { if (_state.value == UpdateUiState.NeedsPermission && updater.canInstall()) install() }

    fun reset() { _state.value = UpdateUiState.Idle }
}

@Composable
fun UpdateRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // "*/*" as well: some file managers label APKs as plain binary files.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::onFilePicked) }
    androidx.lifecycle.compose.LifecycleResumeEffect(state) {
        viewModel.onResumeAfterPermission()
        onPauseOrDispose { }
    }
    UpdateScreen(
        state = state,
        installedVersion = viewModel.installedVersion,
        onBack = onBack,
        onChoose = { pick.launch(arrayOf(APK_MIME, "*/*")) },
        onInstall = { viewModel.install()?.let { runCatching { context.startActivity(it) } } },
        onReset = viewModel::reset,
        modifier = modifier,
    )
}

private const val APK_MIME = "application/vnd.android.package-archive"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    state: UpdateUiState,
    installedVersion: String,
    onBack: () -> Unit,
    onChoose: () -> Unit,
    onInstall: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_update), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(CoreR.string.action_back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.gutter), verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            Text(stringResource(R.string.update_current_version, installedVersion), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.update_intro), style = MaterialTheme.typography.bodyLarge)
            when (state) {
                UpdateUiState.Idle -> Unit
                UpdateUiState.Checking -> Progress(stringResource(R.string.update_checking))
                UpdateUiState.Installing -> Progress(stringResource(R.string.update_installing))
                is UpdateUiState.Ready -> Note(
                    title = stringResource(R.string.update_ready_title, state.versionName),
                    text = stringResource(R.string.update_ready_text),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) { Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_install)) } }
                UpdateUiState.NeedsPermission -> Note(
                    title = stringResource(R.string.update_permission_title),
                    text = stringResource(R.string.update_permission_text),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) { Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_install)) } }
                is UpdateUiState.UpToDate -> Note(
                    title = stringResource(R.string.update_up_to_date_title),
                    text = stringResource(if (state.fileIsOlder) R.string.update_file_older else R.string.update_file_same, state.fileVersion, state.installedVersion),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) { OutlinedButton(onClick = onReset) { Text(stringResource(CoreR.string.action_done)) } }
                UpdateUiState.NotThisApp -> Note(stringResource(R.string.update_not_this_app_title), stringResource(R.string.update_not_this_app_text), MaterialTheme.colorScheme.errorContainer) {
                    OutlinedButton(onClick = onReset) { Text(stringResource(CoreR.string.action_done)) }
                }
                UpdateUiState.DifferentSignature -> Note(stringResource(R.string.update_signature_title), stringResource(R.string.update_signature_text), MaterialTheme.colorScheme.errorContainer) {
                    OutlinedButton(onClick = onReset) { Text(stringResource(CoreR.string.action_done)) }
                }
                is UpdateUiState.Failed -> Note(stringResource(R.string.update_failed_title), state.message ?: stringResource(R.string.update_unreadable), MaterialTheme.colorScheme.errorContainer) {
                    OutlinedButton(onClick = onReset) { Text(stringResource(CoreR.string.action_done)) }
                }
            }
            Button(onClick = onChoose, enabled = state !is UpdateUiState.Checking && state !is UpdateUiState.Installing, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.update_choose))
            }
            Text(stringResource(R.string.update_data_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(spacing.xxxl))
        }
    }
}

@Composable
private fun Progress(text: String) {
    Surface(shape = RoundedCornerShape(Yaadein.radius.lg), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Yaadein.spacing.lg), verticalArrangement = Arrangement.spacedBy(Yaadein.spacing.sm)) {
            Text(text, style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Note(title: String, text: String, color: Color, action: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(Yaadein.radius.lg), color = color, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Yaadein.spacing.lg), verticalArrangement = Arrangement.spacedBy(Yaadein.spacing.sm)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            action()
        }
    }
}
