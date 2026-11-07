package com.yaadein.feature.capture

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.yaadein.core.ui.components.CoverArt
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.core.ui.util.formatBytes
import com.yaadein.data.media.ImportFailure
import com.yaadein.domain.model.CoverStyle
import com.yaadein.feature.capture.components.DateChips
import com.yaadein.feature.capture.components.MoodPicker
import com.yaadein.feature.capture.components.PeoplePicker
import com.yaadein.core.ui.R as CoreR

@Composable
fun EventCaptureRoute(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onAddPerson: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EventCaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)) { uris -> viewModel.onPicked(uris) }
    EventCaptureScreen(
        state = state,
        onBack = onBack,
        onTitle = viewModel::setTitle,
        onDescription = viewModel::setDescription,
        onDate = viewModel::setDate,
        onPlace = viewModel::setPlace,
        onMood = viewModel::setMood,
        onCoverStyle = viewModel::setCoverStyle,
        onTogglePerson = viewModel::togglePerson,
        onAddPerson = onAddPerson,
        onPickMedia = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
        onRemovePending = viewModel::removePending,
        onOptimize = viewModel::setOptimize,
        onDismissRejected = viewModel::dismissRejected,
        onSave = { viewModel.save(onSaved) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventCaptureScreen(
    state: EventCaptureUiState,
    onBack: () -> Unit,
    onTitle: (String) -> Unit,
    onDescription: (String) -> Unit,
    onDate: (com.yaadein.domain.model.MemoryDate) -> Unit,
    onPlace: (String) -> Unit,
    onMood: (com.yaadein.domain.model.Mood?) -> Unit,
    onCoverStyle: (String) -> Unit,
    onTogglePerson: (Long) -> Unit,
    onAddPerson: () -> Unit,
    onPickMedia: () -> Unit,
    onRemovePending: (android.net.Uri) -> Unit,
    onOptimize: (Boolean) -> Unit,
    onDismissRejected: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(if (state.isNew) R.string.event_new_title else R.string.event_edit_title), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, contentDescription = stringResource(CoreR.string.action_close)) } },
            actions = { TextButton(onClick = onSave, enabled = !state.saving && !state.loading) { Text(stringResource(CoreR.string.action_save)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.gutter).widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            DateChips(value = state.date, onChange = onDate)
            OutlinedTextField(
                value = state.title, onValueChange = onTitle, label = { Text(stringResource(R.string.event_title)) },
                isError = state.titleError, supportingText = if (state.titleError) ({ Text(stringResource(R.string.event_title_required)) }) else null,
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(value = state.place, onValueChange = onPlace, label = { Text(stringResource(R.string.event_place)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            PeoplePicker(people = state.people, selected = state.selectedPeople, onToggle = onTogglePerson, onAddPerson = onAddPerson)
            OutlinedTextField(value = state.description, onValueChange = onDescription, label = { Text(stringResource(R.string.event_description)) }, minLines = 3, modifier = Modifier.fillMaxWidth())
            MoodPicker(selected = state.mood, onSelect = onMood)
            MediaSection(state = state, onPickMedia = onPickMedia, onRemovePending = onRemovePending, onOptimize = onOptimize)
            if (state.pending.isEmpty() && state.existingMediaCount == 0) {
                CoverStylePicker(selected = state.coverStyle, onSelect = onCoverStyle)
            }
            Button(onClick = onSave, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(CoreR.string.action_save)) }
            Spacer(Modifier.height(spacing.xxxl))
        }
    }
    if (state.rejected.isNotEmpty()) {
        RejectedDialog(failures = state.rejected, onDismiss = onDismissRejected)
    }
}

@Composable
private fun MediaSection(state: EventCaptureUiState, onPickMedia: () -> Unit, onRemovePending: (android.net.Uri) -> Unit, onOptimize: (Boolean) -> Unit) {
    val spacing = Yaadein.spacing
    Column {
        Text(stringResource(R.string.event_media), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(spacing.sm))
        if (state.existingMediaCount > 0) {
            Text(stringResource(R.string.event_media_existing, state.existingMediaCount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(spacing.sm))
        }
        if (state.pending.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                items(state.pending.size, key = { state.pending[it].uri.toString() }) { i ->
                    val p = state.pending[i]
                    Box(modifier = Modifier.size(96.dp).clip(RoundedCornerShape(Yaadein.radius.md))) {
                        AsyncImage(model = p.uri, contentDescription = p.displayName, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        IconButton(onClick = { onRemovePending(p.uri) }, modifier = Modifier.align(Alignment.TopEnd).size(Yaadein.sizes.touchTarget)) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.event_remove_photo), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        if (p.isVideo) {
                            Text(stringResource(R.string.event_video_badge), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.align(Alignment.BottomStart).padding(spacing.xs))
                        }
                    }
                }
            }
            Spacer(Modifier.height(spacing.sm))
        }
        FilledTonalButton(onClick = onPickMedia) {
            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
            Spacer(Modifier.width(spacing.sm))
            Text(stringResource(R.string.event_add_media))
        }
        Text(stringResource(R.string.event_media_limits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = spacing.xs))
        if (state.pending.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = spacing.sm)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.event_optimize), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.event_optimize_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.optimize, onCheckedChange = onOptimize)
            }
        }
    }
}

@Composable
fun CoverStylePicker(selected: String, onSelect: (String) -> Unit) {
    val spacing = Yaadein.spacing
    Column {
        Text(stringResource(R.string.event_cover), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(spacing.sm))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            items(CoverStyle.ALL.size, key = { CoverStyle.ALL[it] }) { i ->
                val style = CoverStyle.ALL[i]
                val isSelected = style == selected
                Box(
                    modifier = Modifier
                        .size(width = 96.dp, height = 64.dp)
                        .clip(RoundedCornerShape(Yaadein.radius.md))
                        .border(if (isSelected) 3.dp else 0.dp, if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(Yaadein.radius.md))
                        .clickable { onSelect(style) },
                ) {
                    CoverArt(style = style, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
fun RejectedDialog(failures: List<ImportFailure>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reject_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Yaadein.spacing.sm)) {
                failures.take(5).forEach { f ->
                    Text(
                        when (f) {
                            is ImportFailure.TooLarge -> stringResource(
                                if (f.isVideo) R.string.reject_video_too_large else R.string.reject_photo_too_large,
                                formatBytes(f.sizeBytes), formatBytes(f.limitBytes),
                            )
                            ImportFailure.Unsupported -> stringResource(R.string.reject_unsupported)
                            is ImportFailure.Unreadable -> stringResource(R.string.reject_unreadable)
                            ImportFailure.IntegrityCheckFailed -> stringResource(R.string.reject_integrity)
                            is ImportFailure.NotEnoughSpace -> stringResource(R.string.reject_no_space, formatBytes(f.neededBytes))
                        },
                    )
                }
                if (failures.size > 5) Text(stringResource(R.string.reject_more, failures.size - 5))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.action_done)) } },
    )
}
