package com.yaadein.feature.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.data.media.VoiceNotes
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.model.Person
import com.yaadein.domain.repository.MemoryRepository
import com.yaadein.domain.repository.PersonRepository
import com.yaadein.feature.capture.components.PeoplePicker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import com.yaadein.core.ui.R as CoreR

data class VoiceCaptureUiState(
    /** Story prompt this recording answers (from the weekly prompt), shown above the button. */
    val prompt: String = "",
    /** Recording is attached to this existing memory instead of creating one. */
    val memoryId: Long = 0L,
    val recording: Boolean = false,
    val elapsedMs: Long = 0L,
    val level: Float = 0f,
    val recordedPath: String? = null,
    val recordedMs: Long = 0L,
    val title: String = "",
    val people: List<Person> = emptyList(),
    val selectedPeople: Set<Long> = emptySet(),
    val saving: Boolean = false,
    val micFailed: Boolean = false,
)

@HiltViewModel
class VoiceCaptureViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val voiceNotes: VoiceNotes,
    private val memoryRepository: MemoryRepository,
    personRepository: PersonRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        VoiceCaptureUiState(
            prompt = savedStateHandle.get<String>("prompt").orEmpty(),
            memoryId = savedStateHandle.get<Long>("memoryId") ?: 0L,
            selectedPeople = setOfNotNull(savedStateHandle.get<Long>("personId")?.takeIf { it > 0 }),
        ),
    )
    val state: StateFlow<VoiceCaptureUiState> = _state.asStateFlow()
    private var ticker: Job? = null
    private var saved = false

    init {
        viewModelScope.launch { personRepository.observeAll().collect { people -> _state.update { it.copy(people = people) } } }
    }

    fun toggleRecording() {
        if (_state.value.recording) stop() else start()
    }

    private fun start() {
        _state.value.recordedPath?.let(voiceNotes::discard)
        if (!voiceNotes.start()) {
            _state.update { it.copy(micFailed = true) }
            return
        }
        _state.update { it.copy(recording = true, recordedPath = null, elapsedMs = 0, micFailed = false) }
        ticker = viewModelScope.launch {
            while (voiceNotes.isRecording) {
                val elapsed = voiceNotes.elapsedMs()
                _state.update { it.copy(elapsedMs = elapsed, level = voiceNotes.level()) }
                if (elapsed >= VoiceNotes.MAX_DURATION_MS) { stop(); break }
                delay(TICK_MS)
            }
        }
    }

    private fun stop() {
        ticker?.cancel()
        val length = voiceNotes.elapsedMs()
        viewModelScope.launch {
            val path = voiceNotes.stopAndSeal()
            _state.update { it.copy(recording = false, level = 0f, recordedPath = path, recordedMs = length, micFailed = path == null) }
        }
    }

    fun setTitle(v: String) = _state.update { it.copy(title = v) }
    fun togglePerson(id: Long) = _state.update { it.copy(selectedPeople = if (id in it.selectedPeople) it.selectedPeople - id else it.selectedPeople + id) }

    /** [defaultTitle] is used when the title is left empty (localised by the screen). */
    fun save(defaultTitle: String, onSaved: (Long) -> Unit) {
        val s = _state.value
        val path = s.recordedPath ?: return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val id = if (s.memoryId > 0) {
                voiceNotes.attach(s.memoryId, path)
                s.memoryId
            } else {
                val memory = Memory(
                    type = MemoryType.QUICK,
                    title = s.title.trim().ifBlank { defaultTitle },
                    description = s.prompt,
                    date = Dates.exact(LocalDate.now()),
                    coverStyle = "plain_2",
                )
                val newId = memoryRepository.create(memory, s.selectedPeople.toList())
                voiceNotes.attach(newId, path)
                newId
            }
            saved = true
            _state.update { it.copy(saving = false) }
            onSaved(id)
        }
    }

    override fun onCleared() {
        voiceNotes.cancel()
        // Recorded but never saved: do not leave the sealed file behind.
        if (!saved) _state.value.recordedPath?.let(voiceNotes::discard)
    }

    private companion object { const val TICK_MS = 100L }
}

@Composable
fun VoiceCaptureRoute(onBack: () -> Unit, onSaved: (Long) -> Unit, onAddPerson: () -> Unit, modifier: Modifier = Modifier, viewModel: VoiceCaptureViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) viewModel.toggleRecording() }
    val selectedName = state.people.firstOrNull { it.id in state.selectedPeople }?.displayName
    val defaultTitle = if (selectedName != null) stringResource(R.string.voice_title_person, selectedName) else stringResource(R.string.voice_title_default)
    VoiceCaptureScreen(
        state = state,
        onBack = onBack,
        onToggle = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) viewModel.toggleRecording()
            else permission.launch(Manifest.permission.RECORD_AUDIO)
        },
        onTitle = viewModel::setTitle,
        onTogglePerson = viewModel::togglePerson,
        onAddPerson = onAddPerson,
        onSave = { viewModel.save(defaultTitle, onSaved) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCaptureScreen(
    state: VoiceCaptureUiState,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onTitle: (String) -> Unit,
    onTogglePerson: (Long) -> Unit,
    onAddPerson: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.voice_title), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, contentDescription = stringResource(CoreR.string.action_close)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.gutter).widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.prompt.isNotBlank()) {
                Surface(shape = RoundedCornerShape(Yaadein.radius.lg), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(state.prompt, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(spacing.lg))
                }
            } else {
                Text(stringResource(R.string.voice_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(spacing.md))
            RecordButton(recording = state.recording, level = state.level, onClick = onToggle)
            Text(
                text = when {
                    state.recording -> formatClock(state.elapsedMs)
                    state.recordedPath != null -> stringResource(R.string.voice_recorded, formatClock(state.recordedMs))
                    else -> stringResource(R.string.voice_tap_to_record)
                },
                style = MaterialTheme.typography.titleLarge,
            )
            if (state.micFailed) {
                Text(stringResource(R.string.voice_mic_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            if (state.recordedPath != null && !state.recording) {
                TextButton(onClick = onToggle) { Text(stringResource(R.string.voice_record_again)) }
                if (state.memoryId == 0L) {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = onTitle,
                        label = { Text(stringResource(R.string.voice_title_field)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PeoplePicker(people = state.people, selected = state.selectedPeople, onToggle = onTogglePerson, onAddPerson = onAddPerson, title = stringResource(R.string.voice_who))
                }
                Button(onClick = onSave, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.voice_save)) }
            }
            Text(stringResource(R.string.voice_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(spacing.xxl))
        }
    }
}

/** Big round button; while recording a ring grows with the voice level. */
@Composable
private fun RecordButton(recording: Boolean, level: Float, onClick: () -> Unit) {
    val description = stringResource(if (recording) R.string.voice_stop else R.string.voice_start)
    val ring = 96.dp + (48.dp * level.coerceIn(0f, 1f))
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(152.dp)) {
        if (recording) {
            Box(Modifier.size(ring).clip(CircleShape).background(MaterialTheme.colorScheme.errorContainer))
        }
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp).semantics { contentDescription = description },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = null,
                    tint = if (recording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(Yaadein.sizes.iconLg),
                )
            }
        }
    }
}

private fun formatClock(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
