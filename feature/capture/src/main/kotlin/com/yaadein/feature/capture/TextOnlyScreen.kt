package com.yaadein.feature.capture

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yaadein.core.ui.components.MemoryCardView
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.data.polish.SmartPolishProvider
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.model.Person
import com.yaadein.domain.polish.PolishEngine
import com.yaadein.domain.polish.PolishResult
import com.yaadein.domain.repository.MemoryRepository
import com.yaadein.domain.repository.PersonRepository
import com.yaadein.domain.repository.SettingsRepository
import com.yaadein.feature.capture.components.DateChips
import com.yaadein.feature.capture.components.PeoplePicker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.yaadein.core.ui.R as CoreR

data class TextOnlyUiState(
    val input: String = "",
    val polished: PolishResult? = null,
    val people: List<Person> = emptyList(),
    val selectedPeople: Set<Long> = emptySet(),
    val smartAvailable: Boolean = false,
    val smartEnabled: Boolean = false,
    val smartBusy: Boolean = false,
    val dateOverride: com.yaadein.domain.model.MemoryDate? = null,
    val saving: Boolean = false,
)

@HiltViewModel
class TextOnlyViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
    personRepository: PersonRepository,
    private val settingsRepository: SettingsRepository,
    private val smartPolish: SmartPolishProvider,
) : ViewModel() {
    private val _state = MutableStateFlow(TextOnlyUiState())
    val state: StateFlow<TextOnlyUiState> = _state.asStateFlow()
    private var smartJob: Job? = null

    init {
        viewModelScope.launch { personRepository.observeAll().collect { people -> _state.update { it.copy(people = people) } } }
        viewModelScope.launch {
            val settings = settingsRepository.current()
            val available = smartPolish.isAvailable()
            _state.update { it.copy(smartAvailable = available, smartEnabled = available && settings.smartPolish) }
        }
    }

    fun setInput(v: String) {
        _state.update { it.copy(input = v, polished = if (v.isBlank()) null else PolishEngine.polish(v)) }
        if (_state.value.smartEnabled) scheduleSmart(v)
    }

    fun appendDictation(text: String) = setInput(listOf(_state.value.input.trim(), text.trim()).filter { it.isNotEmpty() }.joinToString(" "))
    fun togglePerson(id: Long) = _state.update { it.copy(selectedPeople = if (id in it.selectedPeople) it.selectedPeople - id else it.selectedPeople + id) }
    fun setDate(d: com.yaadein.domain.model.MemoryDate) = _state.update { it.copy(dateOverride = d) }

    fun setSmart(enabled: Boolean) {
        _state.update { it.copy(smartEnabled = enabled) }
        viewModelScope.launch { settingsRepository.update { it.copy(smartPolish = enabled) } }
        if (enabled) scheduleSmart(_state.value.input) else setInput(_state.value.input)
    }

    private fun scheduleSmart(input: String) {
        smartJob?.cancel()
        if (input.isBlank()) return
        smartJob = viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            _state.update { it.copy(smartBusy = true) }
            val base = PolishEngine.polish(input)
            val improved = smartPolish.polish(input, base)
            _state.update { if (it.input == input) it.copy(polished = improved ?: base, smartBusy = false) else it.copy(smartBusy = false) }
        }
    }

    fun save(onSaved: (Long) -> Unit) {
        val s = _state.value
        val p = s.polished ?: return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val memory = Memory(
                type = MemoryType.TEXT_ONLY, title = p.title, description = p.description, date = s.dateOverride ?: p.date,
                place = p.place, mood = p.mood, coverStyle = p.coverStyle,
            )
            val id = memoryRepository.create(memory, s.selectedPeople.toList())
            _state.update { it.copy(saving = false) }
            onSaved(id)
        }
    }
}

@Composable
fun TextOnlyRoute(onBack: () -> Unit, onSaved: (Long) -> Unit, onAddPerson: () -> Unit, modifier: Modifier = Modifier, viewModel: TextOnlyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(viewModel::appendDictation)
    }
    val canDictate = remember(context) { Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(context.packageManager) != null }
    TextOnlyScreen(
        state = state,
        onBack = onBack,
        onInput = viewModel::setInput,
        onDictate = if (canDictate) ({
            speech.launch(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true),
            )
        }) else null,
        onTogglePerson = viewModel::togglePerson,
        onAddPerson = onAddPerson,
        onDate = viewModel::setDate,
        onSmart = viewModel::setSmart,
        onSave = { viewModel.save(onSaved) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextOnlyScreen(
    state: TextOnlyUiState,
    onBack: () -> Unit,
    onInput: (String) -> Unit,
    onDictate: (() -> Unit)?,
    onTogglePerson: (Long) -> Unit,
    onAddPerson: () -> Unit,
    onDate: (com.yaadein.domain.model.MemoryDate) -> Unit,
    onSmart: (Boolean) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.text_title), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, contentDescription = stringResource(CoreR.string.action_close)) } },
            actions = { TextButton(onClick = onSave, enabled = state.polished != null && !state.saving) { Text(stringResource(CoreR.string.action_save)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.gutter).widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = onInput,
                label = { Text(stringResource(R.string.text_hint)) },
                placeholder = { Text(stringResource(R.string.text_placeholder)) },
                trailingIcon = if (onDictate != null) ({ IconButton(onClick = onDictate) { Icon(Icons.Rounded.Mic, contentDescription = stringResource(R.string.text_dictate)) } }) else null,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.smartAvailable) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(spacing.sm))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.text_smart), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.text_smart_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.smartEnabled, onCheckedChange = onSmart)
                }
            }
            val polished = state.polished
            if (polished != null) {
                Text(stringResource(R.string.text_preview), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                val card = MemoryCard(
                    memory = Memory(
                        id = state.input.hashCode().toLong(), type = MemoryType.TEXT_ONLY, title = polished.title, description = polished.description,
                        date = state.dateOverride ?: polished.date, place = polished.place, mood = polished.mood, coverStyle = polished.coverStyle,
                    ),
                    people = state.people.filter { it.id in state.selectedPeople }, cover = null, mediaCount = 0,
                )
                MemoryCardView(card = card, onClick = {})
                if (!polished.dateDetected && state.dateOverride == null) {
                    Text(stringResource(R.string.text_no_date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DateChips(value = state.dateOverride ?: polished.date, onChange = onDate)
                PeoplePicker(people = state.people, selected = state.selectedPeople, onToggle = onTogglePerson, onAddPerson = onAddPerson)
                Button(onClick = onSave, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.text_save)) }
            } else {
                Text(stringResource(R.string.text_examples), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(spacing.xxxl))
        }
    }
}
