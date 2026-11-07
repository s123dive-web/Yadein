package com.yaadein.feature.capture

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.core.ui.util.formatBytes
import com.yaadein.data.media.ExportImporter
import com.yaadein.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import com.yaadein.core.ui.R as CoreR

sealed interface ImportExportState {
    data object Idle : ImportExportState
    data class Reading(val files: Int) : ImportExportState
    data class Importing(val done: Int, val total: Int) : ImportExportState
    data class Done(val result: ExportImporter.Result) : ImportExportState
    data class Failed(val reason: Reason, val neededBytes: Long = 0) : ImportExportState
    enum class Reason { NOT_AN_EXPORT, NO_SPACE, OTHER }
}

@HiltViewModel
class ImportExportViewModel @Inject constructor(
    private val exportImporter: ExportImporter,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<ImportExportState>(ImportExportState.Idle)
    val state: StateFlow<ImportExportState> = _state.asStateFlow()

    fun start(uri: Uri, untitled: (LocalDate) -> String, chatTitle: (String, LocalDate) -> String) {
        if (_state.value is ImportExportState.Reading || _state.value is ImportExportState.Importing) return
        viewModelScope.launch {
            _state.value = ImportExportState.Reading(0)
            runCatching {
                exportImporter.import(uri, settingsRepository.current().storeOptimizedCopies, untitled, chatTitle) { p ->
                    _state.value = when (p) {
                        is ExportImporter.Progress.Reading -> ImportExportState.Reading(p.files)
                        is ExportImporter.Progress.Importing -> ImportExportState.Importing(p.done, p.total)
                    }
                }
            }.fold(
                onSuccess = { _state.value = ImportExportState.Done(it) },
                onFailure = { e ->
                    _state.value = when (e) {
                        is ExportImporter.NotAnExportException -> ImportExportState.Failed(ImportExportState.Reason.NOT_AN_EXPORT)
                        is ExportImporter.NotEnoughSpaceException -> ImportExportState.Failed(ImportExportState.Reason.NO_SPACE, e.neededBytes)
                        else -> ImportExportState.Failed(ImportExportState.Reason.OTHER)
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportExportRoute(onBack: () -> Unit, onOpenMemory: (Long) -> Unit, modifier: Modifier = Modifier, viewModel: ImportExportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.start(
            uri,
            untitled = { d -> resources.getString(R.string.import_untitled, d.format(dateFormat)) },
            chatTitle = { name, d -> resources.getString(R.string.import_chat_title, name, d.format(dateFormat)) },
        )
    }
    val spacing = Yaadein.spacing
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.import_title), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, contentDescription = stringResource(CoreR.string.action_close)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.gutter).widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Text(stringResource(R.string.import_intro), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.import_takeout_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.import_whatsapp_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (val s = state) {
                ImportExportState.Idle -> Button(onClick = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.import_choose))
                }
                is ImportExportState.Reading -> {
                    Text(stringResource(R.string.import_reading, s.files), style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is ImportExportState.Importing -> {
                    Text(stringResource(R.string.import_importing, s.done, s.total), style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(progress = { s.done.toFloat() / s.total.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                }
                is ImportExportState.Done -> {
                    Text(stringResource(R.string.import_done, s.result.files, s.result.memories), style = MaterialTheme.typography.titleMedium)
                    if (s.result.skipped > 0) Text(stringResource(R.string.import_skipped, s.result.skipped), style = MaterialTheme.typography.bodyMedium)
                    s.result.firstMemoryId?.let { id -> Button(onClick = { onOpenMemory(id) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_open)) } }
                }
                is ImportExportState.Failed -> {
                    Text(
                        when (s.reason) {
                            ImportExportState.Reason.NOT_AN_EXPORT -> stringResource(R.string.import_not_export)
                            ImportExportState.Reason.NO_SPACE -> stringResource(R.string.import_no_space, formatBytes(s.neededBytes))
                            ImportExportState.Reason.OTHER -> stringResource(R.string.import_failed)
                        },
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }) { Text(stringResource(R.string.import_choose)) }
                }
            }
            Text(stringResource(R.string.import_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
