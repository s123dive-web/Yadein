package com.yaadein.feature.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yaadein.core.ui.components.PersonAvatar
import com.yaadein.core.ui.components.kinTermNameRes
import com.yaadein.core.ui.components.relationLabel
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.model.Person
import com.yaadein.domain.model.Relation
import com.yaadein.domain.people.KinTerm
import com.yaadein.domain.repository.PersonRepository
import com.yaadein.domain.repository.MediaRepository
import com.yaadein.data.media.ImportOutcome
import com.yaadein.data.media.MediaImporter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject
import com.yaadein.core.ui.R as CoreR

data class PersonEditUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val person: Person = Person(name = ""),
    val saving: Boolean = false,
    val nameError: Boolean = false,
)

@HiltViewModel
class PersonEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val personRepository: PersonRepository,
    private val mediaRepository: MediaRepository,
    private val importer: MediaImporter,
) : ViewModel() {
    private val personId: Long = savedStateHandle["personId"] ?: 0L
    private val _state = MutableStateFlow(PersonEditUiState())
    val state: StateFlow<PersonEditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (personId != 0L) {
                val p = personRepository.get(personId)
                if (p != null) _state.update { it.copy(loading = false, isNew = false, person = p) } else _state.update { it.copy(loading = false) }
            } else {
                val count = personRepository.count()
                _state.update { it.copy(loading = false, person = Person(name = "", avatarColorIndex = count % 8)) }
            }
        }
    }

    fun update(transform: (Person) -> Person) = _state.update { it.copy(person = transform(it.person), nameError = false) }

    /** Imports a profile photo (encrypted like everything else) and points the person at it. */
    fun onPhotoPicked(uri: android.net.Uri?) {
        uri ?: return
        viewModelScope.launch {
            val picked = importer.inspect(uri) ?: return@launch
            if (picked.isVideo || importer.checkLimit(picked) != null) return@launch
            when (val outcome = importer.import(picked, memoryId = null, optimize = true, position = 0)) {
                is ImportOutcome.Success -> {
                    val old = _state.value.person.profileMediaId
                    _state.update { it.copy(person = it.person.copy(profileMediaId = outcome.mediaId)) }
                    if (old != null) mediaRepository.delete(old)
                }
                is ImportOutcome.Failed -> Unit
            }
        }
    }

    fun removePhoto() {
        val old = _state.value.person.profileMediaId ?: return
        _state.update { it.copy(person = it.person.copy(profileMediaId = null)) }
        viewModelScope.launch { mediaRepository.delete(old) }
    }

    fun save(onSaved: (Long) -> Unit) {
        val person = _state.value.person
        if (person.name.isBlank()) {
            _state.update { it.copy(nameError = true) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val id = personRepository.upsert(person)
            _state.update { it.copy(saving = false) }
            onSaved(id)
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            if (personId != 0L) personRepository.delete(personId)
            onDone()
        }
    }
}

@Composable
fun PersonEditRoute(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PersonEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { viewModel.onPhotoPicked(it) }
    PersonEditScreen(
        state = state,
        onBack = onBack,
        onChange = viewModel::update,
        onPickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onRemovePhoto = viewModel::removePhoto,
        onSave = { viewModel.save(onSaved) },
        onDelete = { viewModel.delete(onBack) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonEditScreen(
    state: PersonEditUiState,
    onBack: () -> Unit,
    onChange: ((Person) -> Person) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onPickPhoto: () -> Unit = {},
    onRemovePhoto: () -> Unit = {},
) {
    val spacing = Yaadein.spacing
    val person = state.person
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(if (state.isNew) R.string.person_new_title else R.string.person_edit_title), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, contentDescription = stringResource(CoreR.string.action_close)) } },
            actions = {
                if (!state.isNew) {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.person_delete)) }
                }
                TextButton(onClick = onSave, enabled = !state.saving && !state.loading) { Text(stringResource(CoreR.string.action_save)) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.gutter)
                .widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                PersonAvatar(person = person.copy(name = person.name.ifBlank { "?" }), size = Yaadein.sizes.avatarXl)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    TextButton(onClick = onPickPhoto) { Text(stringResource(R.string.person_photo_pick)) }
                    if (person.profileMediaId != null) TextButton(onClick = onRemovePhoto) { Text(stringResource(R.string.person_photo_remove)) }
                }
            }
            OutlinedTextField(
                value = person.name,
                onValueChange = { v -> onChange { it.copy(name = v) } },
                label = { Text(stringResource(R.string.person_name)) },
                isError = state.nameError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = person.nickname.orEmpty(),
                onValueChange = { v -> onChange { it.copy(nickname = v) } },
                label = { Text(stringResource(R.string.person_nickname)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            KinSection(person = person, onChange = onChange)
            Column {
                Text(stringResource(R.string.person_birthday), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    OutlinedButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Rounded.Cake, contentDescription = null)
                        Spacer(Modifier.padding(spacing.xs))
                        Text(
                            person.birthdayEpochDay?.let { LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }
                                ?: stringResource(R.string.person_birthday_unset),
                        )
                    }
                    if (person.birthdayEpochDay != null) {
                        TextButton(onClick = { onChange { it.copy(birthdayEpochDay = null) } }) { Text(stringResource(CoreR.string.action_delete)) }
                    }
                }
            }
            OutlinedTextField(
                value = person.notes,
                onValueChange = { v -> onChange { it.copy(notes = v) } },
                label = { Text(stringResource(R.string.person_notes)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onSave, enabled = !state.saving && !state.loading, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(CoreR.string.action_save))
            }
            Spacer(Modifier.height(spacing.xxxl))
        }
    }
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = person.birthdayEpochDay?.let { LocalDate.ofEpochDay(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val day = LocalDate.ofEpochDay(millis / 86_400_000L).toEpochDay()
                        onChange { it.copy(birthdayEpochDay = day) }
                    }
                    showDatePicker = false
                }) { Text(stringResource(CoreR.string.action_done)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(CoreR.string.action_cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.person_delete)) },
            text = { Text(stringResource(R.string.person_delete_confirm, person.displayName)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(CoreR.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(CoreR.string.action_cancel)) } },
        )
    }
}

/**
 * "Who are they to you?": the family words first (one selected at most), then "Other…" for a typed relationship,
 * then the four coarse buckets. Picking a kin term sets both [Person.kinTerm] and the coarse [Person.relation];
 * picking a coarse bucket keeps any typed text but clears the kin term.
 */
@Composable
private fun KinSection(person: Person, onChange: ((Person) -> Person) -> Unit) {
    val spacing = Yaadein.spacing
    val chipHeight = Yaadein.sizes.touchTarget
    val selectedTerm = KinTerm.fromName(person.kinTerm)
    var otherOpen by rememberSaveable(person.id) { mutableStateOf(selectedTerm == null && !person.relationText.isNullOrBlank()) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.person_kin_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(spacing.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            KinTerm.PICKER_ORDER.forEach { term ->
                val selected = selectedTerm == term
                FilterChip(
                    selected = selected,
                    onClick = {
                        otherOpen = false
                        onChange {
                            if (selected) it.copy(kinTerm = null) else it.copy(kinTerm = term.name, relation = term.coarse, relationText = null)
                        }
                    },
                    label = { Text(stringResource(kinTermNameRes(term))) },
                    modifier = Modifier.heightIn(min = chipHeight),
                )
            }
            FilterChip(
                selected = otherOpen,
                onClick = {
                    otherOpen = !otherOpen
                    if (otherOpen) onChange { it.copy(kinTerm = null, relation = Relation.OTHER) }
                },
                label = { Text(stringResource(R.string.person_kin_other)) },
                modifier = Modifier.heightIn(min = chipHeight),
            )
        }
        if (otherOpen) {
            Spacer(Modifier.height(spacing.sm))
            OutlinedTextField(
                value = person.relationText.orEmpty(),
                onValueChange = { v -> onChange { it.copy(relationText = v, kinTerm = null) } },
                label = { Text(stringResource(R.string.person_kin_other_label)) },
                placeholder = { Text(stringResource(R.string.person_kin_other_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(spacing.md))
        Text(stringResource(R.string.person_relation), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(spacing.xs))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Relation.entries.forEach { r ->
                FilterChip(
                    selected = person.relation == r,
                    onClick = { onChange { it.copy(relation = r, kinTerm = null) } },
                    label = { Text(relationLabel(r)) },
                    modifier = Modifier.heightIn(min = chipHeight),
                )
            }
        }
    }
}
