package com.yaadein.feature.capture

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.data.media.ImportCoordinator
import com.yaadein.data.media.ImportFailure
import com.yaadein.data.media.MediaImporter
import com.yaadein.data.media.PickedMedia
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.model.Person
import com.yaadein.domain.polish.PolishEngine
import com.yaadein.domain.repository.MemoryRepository
import com.yaadein.domain.repository.PersonRepository
import com.yaadein.domain.repository.SettingsRepository
import com.yaadein.feature.capture.components.PeoplePicker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import com.yaadein.core.ui.R as CoreR

data class QuickCaptureUiState(
    val text: String = "",
    val photo: PickedMedia? = null,
    val people: List<Person> = emptyList(),
    val selectedPeople: Set<Long> = emptySet(),
    val rejected: List<ImportFailure> = emptyList(),
    val saving: Boolean = false,
)

/** "Today" flow: one line, optional photo, optional people. Designed to be done in under five seconds. */
@HiltViewModel
class QuickCaptureViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
    personRepository: PersonRepository,
    private val settingsRepository: SettingsRepository,
    private val importer: MediaImporter,
    private val coordinator: ImportCoordinator,
) : ViewModel() {
    private val _state = MutableStateFlow(QuickCaptureUiState())
    val state: StateFlow<QuickCaptureUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { personRepository.observeAll().collect { people -> _state.update { it.copy(people = people) } } }
    }

    fun setText(v: String) = _state.update { it.copy(text = v) }
    fun togglePerson(id: Long) = _state.update { it.copy(selectedPeople = if (id in it.selectedPeople) it.selectedPeople - id else it.selectedPeople + id) }
    fun clearPhoto() = _state.update { it.copy(photo = null) }
    fun dismissRejected() = _state.update { it.copy(rejected = emptyList()) }

    fun onPicked(uri: Uri?) {
        uri ?: return
        viewModelScope.launch {
            val picked = importer.inspect(uri)
            if (picked == null) { _state.update { it.copy(rejected = listOf(ImportFailure.Unsupported)) }; return@launch }
            val limit = importer.checkLimit(picked)
            if (limit != null) _state.update { it.copy(rejected = listOf(limit)) } else _state.update { it.copy(photo = picked) }
        }
    }

    fun save(onSaved: (Long) -> Unit) {
        val s = _state.value
        if (s.text.isBlank() && s.photo == null) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val polished = PolishEngine.polish(s.text.ifBlank { "today" })
            val today = Dates.exact(LocalDate.now())
            val memory = Memory(
                type = MemoryType.QUICK,
                title = if (s.text.isBlank()) polished.title else polished.title.substringBefore(", ").ifBlank { polished.title },
                description = s.text.trim(),
                date = today,
                place = polished.place,
                mood = polished.mood,
                coverStyle = polished.coverStyle,
            )
            val id = memoryRepository.create(memory, s.selectedPeople.toList())
            s.photo?.let { coordinator.enqueue(id, listOf(it), settingsRepository.current().storeOptimizedCopies) }
            _state.update { it.copy(saving = false) }
            onSaved(id)
        }
    }
}

@Composable
fun QuickCaptureRoute(onBack: () -> Unit, onSaved: (Long) -> Unit, onAddPerson: () -> Unit, modifier: Modifier = Modifier, viewModel: QuickCaptureViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { viewModel.onPicked(it) }
    QuickCaptureScreen(
        state = state, onBack = onBack, onText = viewModel::setText,
        onPickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
        onClearPhoto = viewModel::clearPhoto, onTogglePerson = viewModel::togglePerson, onAddPerson = onAddPerson,
        onDismissRejected = viewModel::dismissRejected, onSave = { viewModel.save(onSaved) }, modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCaptureScreen(
    state: QuickCaptureUiState,
    onBack: () -> Unit,
    onText: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onClearPhoto: () -> Unit,
    onTogglePerson: (Long) -> Unit,
    onAddPerson: () -> Unit,
    onDismissRejected: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = Yaadein.spacing
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.quick_title), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, contentDescription = stringResource(CoreR.string.action_close)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = spacing.gutter).widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            Text(stringResource(R.string.quick_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = state.text,
                onValueChange = onText,
                placeholder = { Text(stringResource(R.string.quick_placeholder)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                minLines = 2,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                val photo = state.photo
                if (photo != null) {
                    Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(Yaadein.radius.md))) {
                        AsyncImage(model = photo.uri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        IconButton(onClick = onClearPhoto, modifier = Modifier.align(Alignment.TopEnd).size(Yaadein.sizes.touchTarget)) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.event_remove_photo), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
                FilledTonalButton(onClick = onPickPhoto) {
                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(spacing.sm))
                    Text(stringResource(if (photo == null) R.string.quick_add_photo else R.string.quick_change_photo))
                }
            }
            PeoplePicker(people = state.people, selected = state.selectedPeople, onToggle = onTogglePerson, onAddPerson = onAddPerson, title = stringResource(R.string.quick_who))
            Spacer(Modifier.height(spacing.sm))
            Button(onClick = onSave, enabled = !state.saving && (state.text.isNotBlank() || state.photo != null), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.quick_save))
            }
        }
    }
    if (state.rejected.isNotEmpty()) RejectedDialog(failures = state.rejected, onDismiss = onDismissRejected)
}
