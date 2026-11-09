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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yaadein.core.ui.illustrations.Illustration
import com.yaadein.core.ui.illustrations.IllustrationKind
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.DateKind
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.model.Person
import com.yaadein.domain.polish.PolishEngine
import com.yaadein.domain.repository.MemoryRepository
import com.yaadein.domain.repository.PersonRepository
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

data class PastWizardUiState(
    val years: List<Int> = emptyList(),
    val index: Int = 0,
    val input: String = "",
    val countsByYear: Map<Int, Int> = emptyMap(),
    val addedThisSession: Int = 0,
    val people: List<Person> = emptyList(),
    val selectedPeople: Set<Long> = emptySet(),
    val finished: Boolean = false,
) {
    val year: Int? get() = years.getOrNull(index)
}

/** "Fill in the past": walks year by year ("Anything memorable in 2019?") and creates text-only memories fast. */
@HiltViewModel
class PastWizardViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
    personRepository: PersonRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PastWizardUiState())
    val state: StateFlow<PastWizardUiState> = _state.asStateFlow()

    init {
        val thisYear = LocalDate.now().year
        _state.update { it.copy(years = (thisYear - 1 downTo thisYear - 25).toList()) }
        viewModelScope.launch { _state.update { it.copy(countsByYear = memoryRepository.countsByYear()) } }
        viewModelScope.launch { personRepository.observeAll().collect { people -> _state.update { it.copy(people = people) } } }
    }

    fun setInput(v: String) = _state.update { it.copy(input = v) }
    fun appendDictation(t: String) = setInput(listOf(_state.value.input.trim(), t.trim()).filter { it.isNotEmpty() }.joinToString(" "))
    fun togglePerson(id: Long) = _state.update { it.copy(selectedPeople = if (id in it.selectedPeople) it.selectedPeople - id else it.selectedPeople + id) }

    fun skip() = _state.update { it.copy(index = it.index + 1, input = "", selectedPeople = emptySet(), finished = it.index + 1 >= it.years.size) }
    fun back() = _state.update { if (it.index > 0) it.copy(index = it.index - 1, input = "") else it }

    /** Saves the current line as a memory in the current year; stays on the year so more can be added. */
    fun saveAndStay(onSaved: () -> Unit = {}) {
        val s = _state.value
        val year = s.year ?: return
        if (s.input.isBlank()) return
        viewModelScope.launch {
            val polished = PolishEngine.polish(s.input)
            // Keep the parsed date only if it falls inside the year being asked about.
            val date = if (polished.dateDetected && Dates.year(polished.date) == year) polished.date else Dates.year(year)
            val title = if (polished.dateDetected) polished.title else "${polished.title}, $year"
            val memory = Memory(
                type = MemoryType.TEXT_ONLY, title = title, description = polished.description, date = date,
                place = polished.place, mood = polished.mood, coverStyle = polished.coverStyle,
            )
            memoryRepository.create(memory, s.selectedPeople.toList())
            _state.update {
                it.copy(input = "", selectedPeople = emptySet(), addedThisSession = it.addedThisSession + 1, countsByYear = it.countsByYear + (year to (it.countsByYear[year] ?: 0) + 1))
            }
            onSaved()
        }
    }

    fun saveAndNext() = saveAndStay { skip() }
}

@Composable
fun PastWizardRoute(onBack: () -> Unit, onAddPerson: () -> Unit, modifier: Modifier = Modifier, viewModel: PastWizardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(viewModel::appendDictation)
    }
    val canDictate = remember(context) { Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(context.packageManager) != null }
    PastWizardScreen(
        state = state, onBack = onBack, onInput = viewModel::setInput,
        onDictate = if (canDictate) ({ speech.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)) }) else null,
        onTogglePerson = viewModel::togglePerson, onAddPerson = onAddPerson,
        onSaveMore = { viewModel.saveAndStay() }, onSaveNext = viewModel::saveAndNext, onSkip = viewModel::skip, onPrevious = viewModel::back,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastWizardScreen(
    state: PastWizardUiState,
    onBack: () -> Unit,
    onInput: (String) -> Unit,
    onDictate: (() -> Unit)?,
    onTogglePerson: (Long) -> Unit,
    onAddPerson: () -> Unit,
    onSaveMore: () -> Unit,
    onSaveNext: () -> Unit,
    onSkip: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.past_title), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, contentDescription = stringResource(CoreR.string.action_close)) } },
            actions = { TextButton(onClick = onBack) { Text(stringResource(CoreR.string.action_done)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        val year = state.year
        if (state.finished || year == null) {
            Column(Modifier.fillMaxSize().padding(spacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Illustration(kind = IllustrationKind.CALENDAR_STAR, modifier = Modifier.size(Yaadein.sizes.illustration))
                Spacer(Modifier.height(spacing.lg))
                Text(stringResource(R.string.past_done_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Text(pluralStringResource(R.plurals.past_added, state.addedThisSession, state.addedThisSession), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(spacing.lg))
                Button(onClick = onBack) { Text(stringResource(CoreR.string.action_done)) }
            }
            return
        }
        LinearProgressIndicator(progress = { (state.index + 1f) / state.years.size }, modifier = Modifier.fillMaxWidth())
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.gutter).widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Spacer(Modifier.height(spacing.md))
            Text(stringResource(R.string.past_question, year), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            val existing = state.countsByYear[year] ?: 0
            Text(
                text = if (existing == 0) stringResource(R.string.past_nothing_yet) else pluralStringResource(R.plurals.past_existing, existing, existing),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.input, onValueChange = onInput,
                placeholder = { Text(stringResource(R.string.past_placeholder)) },
                trailingIcon = if (onDictate != null) ({ IconButton(onClick = onDictate) { Icon(Icons.Rounded.Mic, contentDescription = stringResource(R.string.text_dictate)) } }) else null,
                minLines = 2, modifier = Modifier.fillMaxWidth(),
            )
            PeoplePicker(people = state.people, selected = state.selectedPeople, onToggle = onTogglePerson, onAddPerson = onAddPerson)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                OutlinedButton(onClick = onSaveMore, enabled = state.input.isNotBlank(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.past_save_more)) }
                Button(onClick = onSaveNext, enabled = state.input.isNotBlank(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.past_save_next)) }
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onPrevious, enabled = state.index > 0) { Text(stringResource(R.string.past_previous)) }
                TextButton(onClick = onSkip) { Text(stringResource(R.string.past_skip_year)) }
            }
            Spacer(Modifier.height(spacing.xxxl))
        }
    }
}
