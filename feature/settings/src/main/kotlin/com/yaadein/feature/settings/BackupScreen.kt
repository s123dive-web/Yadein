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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.data.backup.BackupManager
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
    data class Error(val wrongPassword: Boolean, val message: String?) : BackupStatus
}

@HiltViewModel
class BackupViewModel @Inject constructor(private val backup: BackupManager) : ViewModel() {
    private val _status = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val status: StateFlow<BackupStatus> = _status.asStateFlow()

    fun export(uri: Uri, password: String) {
        viewModelScope.launch {
            _status.value = BackupStatus.Working(0, 0, false)
            runCatching { backup.export(uri, password.toCharArray()) { p -> if (p is BackupManager.Progress.Working) _status.value = BackupStatus.Working(p.done, p.total, false) } }
                .onSuccess { _status.value = BackupStatus.Exported(uri) }
                .onFailure { _status.value = BackupStatus.Error(false, it.message) }
        }
    }

    fun peek(uri: Uri, password: String) {
        viewModelScope.launch {
            _status.value = BackupStatus.Working(0, 0, true)
            runCatching { backup.peek(uri, password.toCharArray()) }
                .onSuccess { _status.value = BackupStatus.Peeked(uri, it, password) }
                .onFailure { _status.value = BackupStatus.Error(it is BackupManager.WrongPasswordException || it is javax.crypto.AEADBadTagException || it.cause is javax.crypto.AEADBadTagException, it.message) }
        }
    }

    fun restore(uri: Uri, password: String) {
        viewModelScope.launch {
            _status.value = BackupStatus.Working(0, 0, true)
            runCatching { backup.restore(uri, password.toCharArray()) { p -> if (p is BackupManager.Progress.Working) _status.value = BackupStatus.Working(p.done, p.total, true) } }
                .onSuccess { _status.value = BackupStatus.Restored }
                .onFailure { _status.value = BackupStatus.Error(it is BackupManager.WrongPasswordException, it.message) }
        }
    }

    fun reset() = _status.update { BackupStatus.Idle }
}

@Composable
fun BackupRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: BackupViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    var pendingExport by remember { mutableStateOf<Uri?>(null) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupManager.MIME)) { uri -> pendingExport = uri }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImport = uri }
    BackupScreen(
        status = status, onBack = onBack,
        onExport = { create.launch("yaadein-backup-${LocalDate.now()}.${BackupManager.EXTENSION}") },
        onImport = { open.launch(arrayOf("*/*")) },
        onConfirmRestore = { s -> viewModel.restore(s.uri, s.password) },
        onReset = viewModel::reset,
        modifier = modifier,
    )
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
    val ok = pw.length >= 6 && (!requireConfirm || pw == pw2)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Yaadein.spacing.sm)) {
                Text(stringResource(R.string.backup_password_help), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = pw, onValueChange = { pw = it }, label = { Text(stringResource(R.string.backup_password)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                if (requireConfirm) OutlinedTextField(value = pw2, onValueChange = { pw2 = it }, label = { Text(stringResource(R.string.backup_password_again)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, isError = pw2.isNotEmpty() && pw2 != pw)
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onConfirm(pw) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(status: BackupStatus, onBack: () -> Unit, onExport: () -> Unit, onImport: () -> Unit, onConfirmRestore: (BackupStatus.Peeked) -> Unit, onReset: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_backup), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(CoreR.string.action_back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.gutter), verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            Text(stringResource(R.string.backup_intro), style = MaterialTheme.typography.bodyLarge)
            when (status) {
                is BackupStatus.Working -> Surface(shape = RoundedCornerShape(Yaadein.radius.lg), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text(stringResource(if (status.restoring) R.string.backup_restoring else R.string.backup_exporting), style = MaterialTheme.typography.titleMedium)
                        if (status.total > 0) LinearProgressIndicator(progress = { status.done / status.total.toFloat() }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                is BackupStatus.Exported -> Card(stringResource(R.string.backup_exported_title), stringResource(R.string.backup_exported_text), onReset)
                is BackupStatus.Peeked -> Surface(shape = RoundedCornerShape(Yaadein.radius.lg), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text(stringResource(R.string.backup_restore_confirm_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.backup_restore_confirm_text, status.manifest.persons, status.manifest.memories, status.manifest.media), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { onConfirmRestore(status) }) { Text(stringResource(R.string.backup_restore_now)) }
                        TextButton(onClick = onReset) { Text(stringResource(CoreR.string.action_cancel)) }
                    }
                }
                BackupStatus.Restored -> Card(stringResource(R.string.backup_restored_title), stringResource(R.string.backup_restored_text), onReset)
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
