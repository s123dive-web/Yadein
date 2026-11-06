package com.yaadein.feature.settings

import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.data.lock.AppLockState
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
    /** Android's one-time "Install unknown apps" permission is missing; [pendingVersion] is a checked file waiting for it. */
    data class NeedsPermission(val pendingVersion: String?) : UpdateUiState
    data object Checking : UpdateUiState
    data class Installing(val versionName: String) : UpdateUiState
    data class UpToDate(val installedVersion: String, val fileVersion: String, val fileIsOlder: Boolean) : UpdateUiState
    data object NotThisApp : UpdateUiState
    data object DifferentSignature : UpdateUiState
    data class Failed(val message: String?) : UpdateUiState
}

/**
 * One straight flow: (permission once) → pick the APK → checked → installs straight away → Android's confirmation
 * opens over Yaadein (skipped from the second update on Android 12+) → Yaadein closes, updates and reopens.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val lockState: AppLockState,
) : ViewModel() {
    private val updater = AppUpdater(context)
    private val _state = MutableStateFlow<UpdateUiState>(if (updater.canInstall()) UpdateUiState.Idle else UpdateUiState.NeedsPermission(null))
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()
    private val _confirm = MutableStateFlow<Intent?>(null)
    /** Android's confirmation screen, started by the Update screen so it opens over Yaadein. */
    val confirm: StateFlow<Intent?> = _confirm.asStateFlow()
    val installedVersion: String = runCatching { updater.installed().versionName }.getOrDefault("?")
    private var pendingVersion: String? = null

    init {
        viewModelScope.launch {
            UpdateEvents.events.collect { e ->
                when (e) {
                    is UpdateEvents.Event.Failed -> { _state.value = UpdateUiState.Failed(e.message); UpdateEvents.post(UpdateEvents.Event.Idle) }
                    is UpdateEvents.Event.ConfirmNeeded -> { _confirm.value = e.intent; UpdateEvents.post(UpdateEvents.Event.Idle) }
                    UpdateEvents.Event.Idle -> Unit
                }
            }
        }
        // Pick up a file checked before Android closed the app (e.g. while on the permission screen).
        viewModelScope.launch {
            val resumed = runCatching { updater.resumeCandidate() }.getOrNull() as? UpdateDecision.Install ?: return@launch
            pendingVersion = resumed.newVersionName
            if (updater.canInstall()) install(resumed.newVersionName) else _state.value = UpdateUiState.NeedsPermission(resumed.newVersionName)
        }
    }

    /** Called right before the app opens a system screen (picker, permission, installer): no PIN on the way back. */
    fun leavingForSystemScreen() = lockState.allowExternalScreen(System.currentTimeMillis())

    fun permissionIntent(): Intent = updater.permissionSettingsIntent()

    fun onFilePicked(uri: Uri) {
        viewModelScope.launch {
            _state.value = UpdateUiState.Checking
            _state.value = when (val d = runCatching { updater.inspect(uri) }.getOrElse { UpdateDecision.Unreadable }) {
                is UpdateDecision.Install -> {
                    pendingVersion = d.newVersionName
                    if (updater.canInstall()) { install(d.newVersionName); return@launch } else UpdateUiState.NeedsPermission(d.newVersionName)
                }
                is UpdateDecision.AlreadyUpToDate -> UpdateUiState.UpToDate(d.installedVersionName, d.fileVersionName, d.fileIsOlder)
                UpdateDecision.NotThisApp -> UpdateUiState.NotThisApp
                UpdateDecision.DifferentSignature -> UpdateUiState.DifferentSignature
                UpdateDecision.Unreadable -> UpdateUiState.Failed(null)
            }
        }
    }

    /** Back on screen (e.g. from the permission page): continue where the flow stopped. */
    fun onResume() {
        val s = _state.value
        if (s is UpdateUiState.NeedsPermission && updater.canInstall()) {
            val version = s.pendingVersion ?: pendingVersion
            if (version != null) install(version) else _state.value = UpdateUiState.Idle
        }
    }

    fun confirmShown() { _confirm.value = null }

    private fun install(version: String) {
        _state.value = UpdateUiState.Installing(version)
        viewModelScope.launch {
            runCatching { updater.install() }.onFailure { _state.value = UpdateUiState.Failed(it.message) }
        }
    }

    fun reset() {
        _state.value = if (updater.canInstall()) UpdateUiState.Idle else UpdateUiState.NeedsPermission(null)
    }
}

@Composable
fun UpdateRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: UpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val confirm by viewModel.confirm.collectAsStateWithLifecycle()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::onFilePicked) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.onResume() }
    // Started from this activity (not a receiver with NEW_TASK), so Android's dialog opens on top of Yaadein.
    val confirmLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    LaunchedEffect(confirm) {
        confirm?.let { intent ->
            viewModel.confirmShown()
            viewModel.leavingForSystemScreen()
            runCatching { confirmLauncher.launch(intent) }
        }
    }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    UpdateScreen(
        state = state,
        installedVersion = viewModel.installedVersion,
        onBack = onBack,
        // "*/*" too: some file managers label APKs as plain binary files.
        onChoose = { viewModel.leavingForSystemScreen(); pick.launch(arrayOf(APK_MIME, "*/*")) },
        onAllow = {
            viewModel.leavingForSystemScreen()
            runCatching { permission.launch(viewModel.permissionIntent().apply { removeFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
        },
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
    onAllow: () -> Unit,
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
            val busy = state is UpdateUiState.Checking || state is UpdateUiState.Installing
            when (state) {
                UpdateUiState.Idle -> Unit
                UpdateUiState.Checking -> Progress(stringResource(R.string.update_checking), null)
                is UpdateUiState.Installing -> Progress(stringResource(R.string.update_installing_version, state.versionName), stringResource(R.string.update_installing_help))
                is UpdateUiState.NeedsPermission -> Note(
                    title = stringResource(R.string.update_permission_title),
                    text = stringResource(R.string.update_permission_text),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) { Button(onClick = onAllow, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_allow)) } }
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
            val waitingForPermission = state is UpdateUiState.NeedsPermission
            Button(onClick = onChoose, enabled = !busy && !waitingForPermission, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.update_choose))
            }
            Text(stringResource(R.string.update_data_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(spacing.xxxl))
        }
    }
}

@Composable
private fun Progress(text: String, help: String?) {
    Surface(shape = RoundedCornerShape(Yaadein.radius.lg), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Yaadein.spacing.lg), verticalArrangement = Arrangement.spacedBy(Yaadein.spacing.sm)) {
            Text(text, style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (help != null) Text(help, style = MaterialTheme.typography.bodyMedium)
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
