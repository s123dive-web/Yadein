package com.yaadein.feature.settings

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.data.backup.BackupManager
import androidx.compose.material3.FilledTonalButton
import androidx.work.WorkInfo
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import com.yaadein.domain.settings.UserSettings
import com.yaadein.domain.repository.SettingsRepository
import com.yaadein.data.backup.AutoBackup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import com.yaadein.core.ui.R as CoreR

sealed interface BackupStatus {
    data object Idle : BackupStatus
    data class Working(val done: Int, val total: Int, val restoring: Boolean) : BackupStatus
    data class Exported(val uri: Uri) : BackupStatus
    data class Peeked(val uri: Uri, val manifest: BackupManager.Manifest, val password: String) : BackupStatus
    data object Restored : BackupStatus
    data class PackAdded(val memories: Int, val newPeople: Int) : BackupStatus
    data class Error(val wrongPassword: Boolean, val message: String?) : BackupStatus
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backup: BackupManager,
    private val autoBackup: AutoBackup,
    private val settingsRepository: SettingsRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {
    private val _status = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val status: StateFlow<BackupStatus> = _status.asStateFlow()
    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())
    /** True while an automatic backup is running. */
    val autoRunning: StateFlow<Boolean> = AutoBackupWorker.observe(context)
        .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Sets up weekly backups to [uri] with [password] and runs the first one now. */
    fun enableAuto(uri: Uri, password: String) {
        viewModelScope.launch {
            _status.value = BackupStatus.Working(0, 0, false)
            runCatching { autoBackup.configure(uri, password.toCharArray()) }
                .onSuccess {
                    settingsRepository.update { it.copy(autoBackupEnabled = true, autoBackupUri = uri.toString(), autoBackupFailed = false, lastBackupAtMillis = 0L) }
                    _status.value = BackupStatus.Idle
                    AutoBackupWorker.enqueue(context, now = true)
                }
                .onFailure { _status.value = BackupStatus.Error(false, it.message) }
        }
    }

    fun backUpNow() = AutoBackupWorker.enqueue(context, now = true)

    fun disableAuto() {
        viewModelScope.launch {
            val uri = settingsRepository.current().autoBackupUri?.let(Uri::parse)
            autoBackup.clear(uri)
            settingsRepository.update { it.copy(autoBackupEnabled = false, autoBackupUri = null, autoBackupFailed = false) }
        }
    }

    fun export(uri: Uri, password: String) {
        viewModelScope.launch {
            _status.value = BackupStatus.Working(0, 0, false)
            runCatching { backup.export(uri, password.toCharArray()) { p -> if (p is BackupManager.Progress.Working) _status.value = BackupStatus.Working(p.done, p.total, false) } }
                .onSuccess {
                    settingsRepository.update { s -> s.copy(lastBackupAtMillis = System.currentTimeMillis()) }
                    _status.value = BackupStatus.Exported(uri)
                }
                .onFailure { _status.value = BackupStatus.Error(false, it.message) }
        }
    }

    fun peek(uri: Uri, password: String) {
        viewModelScope.launch {
            _status.value = BackupStatus.Working(0, 0, true)
            runCatching { backup.peek(uri, password.toCharArray()) }
                .onSuccess { _status.value = BackupStatus.Peeked(uri, it, password) }
                .onFailure { _status.value = BackupStatus.Error(isWrongPassword(it), it.message) }
        }
    }

    fun restore(uri: Uri, password: String) {
        viewModelScope.launch {
            _status.value = BackupStatus.Working(0, 0, true)
            runCatching { backup.restore(uri, password.toCharArray()) { p -> if (p is BackupManager.Progress.Working) _status.value = BackupStatus.Working(p.done, p.total, true) } }
                .onSuccess { _status.value = BackupStatus.Restored }
                .onFailure { _status.value = BackupStatus.Error(isWrongPassword(it), it.message) }
        }
    }

    /** Adds a family pack's memories to this vault (nothing existing is replaced). */
    fun addPack(uri: Uri, password: String) {
        viewModelScope.launch {
            _status.value = BackupStatus.Working(0, 0, true)
            runCatching { backup.importPack(uri, password.toCharArray()) }
                .onSuccess { _status.value = BackupStatus.PackAdded(it.memories, it.newPeople) }
                .onFailure { _status.value = BackupStatus.Error(isWrongPassword(it), it.message) }
        }
    }

    fun reset() = _status.update { BackupStatus.Idle }

    private fun isWrongPassword(t: Throwable): Boolean =
        t is BackupManager.WrongPasswordException || t is javax.crypto.AEADBadTagException || t.cause is javax.crypto.AEADBadTagException
}

@Composable
fun BackupRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: BackupViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val autoRunning by viewModel.autoRunning.collectAsStateWithLifecycle()
    var askAutoPassword by remember { mutableStateOf(false) }
    var autoPassword by remember { mutableStateOf<String?>(null) }
    val createAuto = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupManager.MIME)) { uri ->
        val pw = autoPassword
        autoPassword = null
        if (uri != null && pw != null) viewModel.enableAuto(uri, pw)
    }
    var pendingExport by remember { mutableStateOf<Uri?>(null) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupManager.MIME)) { uri -> pendingExport = uri }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImport = uri }
    BackupScreen(
        status = status, onBack = onBack,
        onExport = { create.launch("yaadein-backup-${LocalDate.now()}.${BackupManager.EXTENSION}") },
        onImport = { open.launch(arrayOf("*/*")) },
        onConfirmRestore = { s -> if (s.manifest.pack) viewModel.addPack(s.uri, s.password) else viewModel.restore(s.uri, s.password) },
        onReset = viewModel::reset,
        modifier = modifier,
        auto = AutoBackupUi(
            enabled = settings.autoBackupEnabled,
            lastBackupAtMillis = settings.lastBackupAtMillis,
            failed = settings.autoBackupFailed,
            running = autoRunning,
            onEnable = { askAutoPassword = true },
            onBackUpNow = viewModel::backUpNow,
            onDisable = viewModel::disableAuto,
        ),
    )
    if (askAutoPassword) {
        PasswordDialog(title = stringResource(R.string.backup_set_password), confirmLabel = stringResource(CoreR.string.action_next), requireConfirm = true, onDismiss = { askAutoPassword = false }) { pw ->
            askAutoPassword = false
            autoPassword = pw
            createAuto.launch(AutoBackup.DEFAULT_NAME)
        }
    }
    pendingExport?.let { uri ->
        PasswordDialog(title = stringResource(R.string.backup_set_password), confirmLabel = stringResource(R.string.backup_export), requireConfirm = true, onDismiss = { pendingExport = null }) { pw -> pendingExport = null; viewModel.export(uri, pw) }
    }
    pendingImport?.let { uri ->
        PasswordDialog(title = stringResource(R.string.backup_enter_password), confirmLabel = stringResource(CoreR.string.action_next), requireConfirm = false, onDismiss = { pendingImport = null }) { pw -> pendingImport = null; viewModel.peek(uri, pw) }
    }
}

@Composable
private fun PasswordDialog(title: String, confirmLabel: String, requireConfirm: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val tooShort = pw.length < BackupManager.MIN_PASSWORD_LENGTH
    val mismatch = requireConfirm && pw2.isNotEmpty() && pw2 != pw
    val ok = !tooShort && (!requireConfirm || pw == pw2)
    // A plain text field lets the keyboard auto-capitalise, autocorrect or add a trailing space, so the
    // password saved with the backup was not what the person thought they typed.
    val keyboard = KeyboardOptions(
        keyboardType = KeyboardType.Password,
        capitalization = KeyboardCapitalization.None,
        autoCorrectEnabled = false,
    )
    val transformation = if (visible) VisualTransformation.None else PasswordVisualTransformation()
    val toggle: @Composable () -> Unit = {
        IconButton(onClick = { visible = !visible }) {
            Icon(
                if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = stringResource(if (visible) R.string.backup_password_hide else R.string.backup_password_show),
            )
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Yaadein.spacing.sm)) {
                Text(stringResource(R.string.backup_password_help), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = pw, onValueChange = { pw = it }, label = { Text(stringResource(R.string.backup_password)) },
                    visualTransformation = transformation, keyboardOptions = keyboard, singleLine = true, trailingIcon = toggle,
                    isError = pw.isNotEmpty() && tooShort,
                    supportingText = if (pw.isNotEmpty() && tooShort) { { Text(stringResource(R.string.backup_password_short, BackupManager.MIN_PASSWORD_LENGTH)) } } else null,
                )
                if (requireConfirm) {
                    OutlinedTextField(
                        value = pw2, onValueChange = { pw2 = it }, label = { Text(stringResource(R.string.backup_password_again)) },
                        visualTransformation = transformation, keyboardOptions = keyboard, singleLine = true, isError = mismatch,
                        supportingText = if (mismatch) { { Text(stringResource(R.string.backup_password_mismatch)) } } else null,
                    )
                }
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onConfirm(pw) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(status: BackupStatus, onBack: () -> Unit, onExport: () -> Unit, onImport: () -> Unit, onConfirmRestore: (BackupStatus.Peeked) -> Unit, onReset: () -> Unit, modifier: Modifier = Modifier, auto: AutoBackupUi? = null) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_backup), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(CoreR.string.action_back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.gutter), verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            Text(stringResource(R.string.backup_intro), style = MaterialTheme.typography.bodyLarge)
            auto?.let { AutoBackupCard(it) }
            when (status) {
                is BackupStatus.Working -> Surface(shape = RoundedCornerShape(Yaadein.radius.lg), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text(stringResource(if (status.restoring) R.string.backup_restoring else R.string.backup_exporting), style = MaterialTheme.typography.titleMedium)
                        if (status.total > 0) LinearProgressIndicator(progress = { status.done / status.total.toFloat() }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                is BackupStatus.Exported -> Card(stringResource(R.string.backup_exported_title), stringResource(R.string.backup_exported_text), onReset)
                is BackupStatus.Peeked -> Surface(shape = RoundedCornerShape(Yaadein.radius.lg), color = if (status.manifest.pack) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text(stringResource(if (status.manifest.pack) R.string.pack_confirm_title else R.string.backup_restore_confirm_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (status.manifest.pack) stringResource(R.string.pack_confirm_text, status.manifest.memories, status.manifest.media)
                            else stringResource(R.string.backup_restore_confirm_text, status.manifest.persons, status.manifest.memories, status.manifest.media),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Button(onClick = { onConfirmRestore(status) }) { Text(stringResource(if (status.manifest.pack) R.string.pack_add_now else R.string.backup_restore_now)) }
                        TextButton(onClick = onReset) { Text(stringResource(CoreR.string.action_cancel)) }
                    }
                }
                BackupStatus.Restored -> Card(stringResource(R.string.backup_restored_title), stringResource(R.string.backup_restored_text), onReset)
                is BackupStatus.PackAdded -> Card(stringResource(R.string.pack_added_title), stringResource(R.string.pack_added_text, status.memories, status.newPeople), onReset)
                is BackupStatus.Error -> Card(stringResource(if (status.wrongPassword) R.string.backup_wrong_password else R.string.backup_error_title), status.message ?: stringResource(CoreR.string.error_generic), onReset)
                BackupStatus.Idle -> Unit
            }
            Button(onClick = onExport, enabled = status !is BackupStatus.Working, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_export)) }
            OutlinedButton(onClick = onImport, enabled = status !is BackupStatus.Working, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_import)) }
            Text(stringResource(R.string.backup_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(spacing.xxxl))
        }
    }
}

@Composable
private fun Card(title: String, text: String, onDismiss: () -> Unit) {
    Surface(shape = RoundedCornerShape(Yaadein.radius.lg), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Yaadein.spacing.lg), verticalArrangement = Arrangement.spacedBy(Yaadein.spacing.sm)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.action_done)) }
        }
    }
}

/** State and actions of the "Automatic backup" card. */
data class AutoBackupUi(
    val enabled: Boolean,
    val lastBackupAtMillis: Long,
    val failed: Boolean,
    val running: Boolean,
    val onEnable: () -> Unit,
    val onBackUpNow: () -> Unit,
    val onDisable: () -> Unit,
)

@Composable
private fun AutoBackupCard(auto: AutoBackupUi) {
    val spacing = Yaadein.spacing
    Surface(shape = RoundedCornerShape(Yaadein.radius.lg), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(stringResource(R.string.autobackup_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (!auto.enabled) {
                Text(stringResource(R.string.autobackup_help), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = auto.onEnable) { Text(stringResource(R.string.autobackup_enable)) }
            } else {
                val last = if (auto.lastBackupAtMillis > 0) {
                    java.time.Instant.ofEpochMilli(auto.lastBackupAtMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        .format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
                } else {
                    stringResource(R.string.autobackup_never)
                }
                Text(stringResource(R.string.autobackup_on, last), style = MaterialTheme.typography.bodyMedium)
                if (auto.failed) {
                    Text(stringResource(R.string.autobackup_failed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                if (auto.running) {
                    Text(stringResource(R.string.autobackup_running), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    FilledTonalButton(onClick = auto.onBackUpNow, enabled = !auto.running) { Text(stringResource(R.string.autobackup_now)) }
                    TextButton(onClick = auto.onDisable) { Text(stringResource(R.string.autobackup_disable)) }
                }
            }
        }
    }
}
