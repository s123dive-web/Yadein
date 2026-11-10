package com.yaadein.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.work.WorkInfo
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.faces.FaceSuggester
import com.yaadein.domain.faces.FaceModel
import com.yaadein.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.yaadein.core.ui.R as CoreR

data class FacesUiState(
    val enabled: Boolean = false,
    /** Faces nobody has named yet (the "Who is this?" queue). */
    val unknownFaces: Int = 0,
    /** Photos the current model has not looked at. */
    val unscannedPhotos: Int = 0,
    val indexing: Boolean = false,
    val indexDone: Int = 0,
    val indexTotal: Int = 0,
)

/**
 * Settings → Face suggestions: the opt-in switch with a plain explanation, the state of the background index, the way
 * into "Who is this?", and "Delete all face data". Recognition never runs until the switch is on.
 */
@HiltViewModel
class FacesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val suggester: FaceSuggester,
    db: AppDatabase,
) : ViewModel() {
    val state: StateFlow<FacesUiState> = combine(
        settingsRepository.settings,
        db.faceDao().observeUnknownCount(),
        db.faceDao().observeUnscannedCount(FaceModel.VERSION),
        FaceReindexWorker.observe(context),
    ) { s, unknown, unscanned, work ->
        val running = work.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        FacesUiState(
            enabled = s.faceSuggestions,
            unknownFaces = unknown,
            unscannedPhotos = unscanned,
            indexing = running != null,
            indexDone = running?.progress?.getInt(FaceReindexWorker.KEY_DONE, 0) ?: 0,
            indexTotal = running?.progress?.getInt(FaceReindexWorker.KEY_TOTAL, 0) ?: 0,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FacesUiState())

    /** Turning it on starts the index straight away; turning it off stops it (face data stays until deleted). */
    fun setEnabled(v: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(faceSuggestions = v) }
            if (v) FaceReindexWorker.enqueue(context, now = true) else FaceReindexWorker.cancel(context)
        }
    }

    fun scanNow() = FaceReindexWorker.enqueue(context, now = true)

    /** Every embedding, rejection and centroid goes; tags already on photos stay. */
    fun deleteAll() {
        viewModelScope.launch {
            FaceReindexWorker.cancel(context)
            suggester.deleteAll()
        }
    }
}

@Composable
fun FacesRoute(onBack: () -> Unit, onWhoIsThis: () -> Unit, modifier: Modifier = Modifier, viewModel: FacesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FacesScreen(state = state, onBack = onBack, onEnabled = viewModel::setEnabled, onScan = viewModel::scanNow, onWhoIsThis = onWhoIsThis, onDeleteAll = viewModel::deleteAll, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacesScreen(
    state: FacesUiState,
    onBack: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onScan: () -> Unit,
    onWhoIsThis: () -> Unit,
    onDeleteAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = Yaadein.spacing
    var confirmDelete by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_faces), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(CoreR.string.action_back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = spacing.xxxl), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            item(key = "toggle") {
                Column {
                    SettingsSwitchRow(icon = Icons.Rounded.Face, title = stringResource(R.string.faces_enable), subtitle = stringResource(R.string.faces_enable_help), checked = state.enabled, onCheckedChange = onEnabled)
                    Text(stringResource(R.string.faces_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = spacing.gutter))
                }
            }
            if (!state.enabled) {
                item(key = "explain") {
                    Surface(shape = RoundedCornerShape(Yaadein.radius.lg), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.padding(horizontal = spacing.gutter).fillMaxWidth()) {
                        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            Text(stringResource(R.string.faces_optin_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.faces_optin_body), style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = { onEnabled(true) }) { Text(stringResource(R.string.faces_optin_action)) }
                        }
                    }
                }
            } else {
                item(key = "index") {
                    Column(Modifier.padding(horizontal = spacing.gutter), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        when {
                            state.indexing -> {
                                Text(
                                    if (state.indexTotal > 0) stringResource(R.string.faces_scanning, state.indexDone, state.indexTotal) else stringResource(R.string.faces_reindex_waiting),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                LinearProgressIndicator(progress = { if (state.indexTotal == 0) 0f else state.indexDone / state.indexTotal.toFloat() }, modifier = Modifier.fillMaxWidth())
                            }
                            state.unscannedPhotos > 0 -> {
                                Text(pluralStringResource(R.plurals.faces_unscanned, state.unscannedPhotos, state.unscannedPhotos), style = MaterialTheme.typography.bodyMedium)
                                OutlinedButton(onClick = onScan) { Text(stringResource(R.string.faces_scan)) }
                            }
                            else -> Text(stringResource(R.string.faces_all_scanned), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item(key = "who") {
                    Surface(shape = RoundedCornerShape(Yaadein.radius.lg), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.padding(horizontal = spacing.gutter).fillMaxWidth()) {
                        Row(Modifier.padding(spacing.lg), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (state.unknownFaces == 0) stringResource(R.string.faces_none) else pluralStringResource(R.plurals.faces_pending, state.unknownFaces, state.unknownFaces),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(stringResource(R.string.faces_who_help), style = MaterialTheme.typography.bodySmall)
                            }
                            if (state.unknownFaces > 0) Button(onClick = onWhoIsThis) { Text(stringResource(R.string.faces_who)) }
                        }
                    }
                }
            }
            item(key = "delete") {
                Column(Modifier.padding(horizontal = spacing.gutter), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Spacer(Modifier.height(spacing.md))
                    Text(stringResource(R.string.faces_delete_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text(stringResource(R.string.faces_delete))
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.faces_delete)) },
            text = { Text(stringResource(R.string.faces_delete_confirm)) },
            confirmButton = { TextButton(onClick = { onDeleteAll(); confirmDelete = false }) { Text(stringResource(CoreR.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(CoreR.string.action_cancel)) } },
        )
    }
}
