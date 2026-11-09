package com.yaadein.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.core.ui.util.formatBytes
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.media.MediaStore
import com.yaadein.data.media.VaultChecker
import com.yaadein.data.media.VaultReport
import com.yaadein.domain.repository.SettingsRepository
import com.yaadein.domain.settings.UserSettings
import com.yaadein.feature.lock.VaultWiper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import com.yaadein.core.ui.R as CoreR

data class StorageUiState(
    val settings: UserSettings = UserSettings(),
    val mediaBytes: Long = 0,
    val databaseBytes: Long = 0,
    val photoCount: Int = 0,
    val videoCount: Int = 0,
    val freeBytes: Long = 0,
    val loading: Boolean = true,
    /** Vault check: null = not run; progress while running. */
    val check: VaultCheckState = VaultCheckState.Idle,
)

sealed interface VaultCheckState {
    data object Idle : VaultCheckState
    data class Running(val done: Int, val total: Int) : VaultCheckState
    data class Done(val report: VaultReport) : VaultCheckState
}

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val db: AppDatabase,
    private val mediaStore: MediaStore,
    private val wiper: VaultWiper,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val checker: VaultChecker,
) : ViewModel() {
    private val usage = MutableStateFlow(StorageUiState())

    val state: StateFlow<StorageUiState> = combine(settingsRepository.settings, usage, db.mediaDao().observeCountOfKind("PHOTO"), db.mediaDao().observeCountOfKind("VIDEO")) { s, u, p, v ->
        u.copy(settings = s, photoCount = p, videoCount = v)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StorageUiState())

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val media = withContext(Dispatchers.IO) { mediaStore.usedBytes() }
            val dbFile = context.getDatabasePath(AppDatabase.NAME)
            val dbBytes = withContext(Dispatchers.IO) { listOf(dbFile, File(dbFile.path + "-wal"), File(dbFile.path + "-shm")).filter { it.exists() }.sumOf { it.length() } }
            val free = withContext(Dispatchers.IO) { context.filesDir.usableSpace }
            usage.value = usage.value.copy(mediaBytes = media, databaseBytes = dbBytes, freeBytes = free, loading = false)
        }
    }

    fun checkVault() {
        if (usage.value.check is VaultCheckState.Running) return
        viewModelScope.launch {
            usage.value = usage.value.copy(check = VaultCheckState.Running(0, 0))
            val report = checker.check { done, total -> usage.value = usage.value.copy(check = VaultCheckState.Running(done, total)) }
            usage.value = usage.value.copy(check = VaultCheckState.Done(report))
        }
    }

    fun setOptimize(v: Boolean) { viewModelScope.launch { settingsRepository.update { it.copy(storeOptimizedCopies = v) } } }
    fun eraseEverything() { viewModelScope.launch(Dispatchers.IO) { wiper.wipeEverything() } }
}

@Composable
fun StorageRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: StorageViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    StorageScreen(state = state, onBack = onBack, onOptimize = viewModel::setOptimize, onErase = viewModel::eraseEverything, onCheck = viewModel::checkVault, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageScreen(state: StorageUiState, onBack: () -> Unit, onOptimize: (Boolean) -> Unit, onErase: () -> Unit, modifier: Modifier = Modifier, onCheck: () -> Unit = {}) {
    val spacing = Yaadein.spacing
    var confirmErase by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_storage), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(CoreR.string.action_back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.gutter), verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            Surface(shape = RoundedCornerShape(Yaadein.radius.lg), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    val total = state.mediaBytes + state.databaseBytes
                    Text(stringResource(R.string.storage_used, formatBytes(total)), style = MaterialTheme.typography.headlineSmall)
                    val fraction = if (state.freeBytes + total > 0) total.toFloat() / (state.freeBytes + total) else 0f
                    LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.storage_free, formatBytes(state.freeBytes)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(pluralStringResource(R.plurals.storage_photos, state.photoCount, state.photoCount) + " · " + pluralStringResource(R.plurals.storage_videos, state.videoCount, state.videoCount))
                        Text(formatBytes(state.mediaBytes), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.storage_database))
                        Text(formatBytes(state.databaseBytes), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            SettingsSwitchRow(icon = null, title = stringResource(R.string.storage_optimize), subtitle = stringResource(R.string.storage_optimize_help), checked = state.settings.storeOptimizedCopies, onCheckedChange = onOptimize)
            HorizontalDivider()
            Text(stringResource(R.string.storage_check), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.storage_check_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (val c = state.check) {
                VaultCheckState.Idle -> OutlinedButton(onClick = onCheck) { Text(stringResource(R.string.storage_check_run)) }
                is VaultCheckState.Running -> {
                    LinearProgressIndicator(progress = { if (c.total > 0) c.done.toFloat() / c.total else 0f }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.storage_check_progress, c.done, c.total), style = MaterialTheme.typography.bodyMedium)
                }
                is VaultCheckState.Done -> {
                    val r = c.report
                    Text(
                        if (r.healthy) pluralStringResource(R.plurals.storage_check_ok, r.checked, r.checked)
                        else stringResource(R.string.storage_check_problems, r.damaged + r.missing, r.affectedMemoryIds.size),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (r.healthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onCheck) { Text(stringResource(R.string.storage_check_again)) }
                }
            }
            HorizontalDivider()
            Text(stringResource(R.string.storage_danger), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.storage_erase_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { confirmErase = true }, colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.storage_erase)) }
            Spacer(Modifier.height(spacing.xxxl))
        }
    }
    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text(stringResource(R.string.storage_erase)) },
            text = { Text(stringResource(R.string.storage_erase_confirm)) },
            confirmButton = { TextButton(onClick = { confirmErase = false; onErase() }) { Text(stringResource(R.string.storage_erase_yes)) } },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text(stringResource(CoreR.string.action_cancel)) } },
        )
    }
}
