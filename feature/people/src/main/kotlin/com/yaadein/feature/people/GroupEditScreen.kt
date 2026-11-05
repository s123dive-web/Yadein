package com.yaadein.feature.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yaadein.core.ui.components.PersonAvatar
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.model.Group
import com.yaadein.domain.model.Person
import com.yaadein.domain.repository.GroupRepository
import com.yaadein.domain.repository.PersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.yaadein.core.ui.R as CoreR

data class GroupEditUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val group: Group = Group(name = ""),
    val people: List<Person> = emptyList(),
    val nameError: Boolean = false,
    val saving: Boolean = false,
)

@HiltViewModel
class GroupEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groupRepository: GroupRepository,
    personRepository: PersonRepository,
) : ViewModel() {
    private val groupId: Long = savedStateHandle["groupId"] ?: 0L
    private val _state = MutableStateFlow(GroupEditUiState())
    val state: StateFlow<GroupEditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { personRepository.observeAll().collect { p -> _state.update { it.copy(people = p) } } }
        viewModelScope.launch {
            if (groupId != 0L) {
                val g = groupRepository.observe(groupId).first()
                if (g != null) { _state.update { it.copy(loading = false, isNew = false, group = g) }; return@launch }
            }
            _state.update { it.copy(loading = false) }
        }
    }

    fun setName(v: String) = _state.update { it.copy(group = it.group.copy(name = v), nameError = false) }
    fun setEmoji(v: String) = _state.update { it.copy(group = it.group.copy(emoji = v)) }
    fun toggleMember(id: Long) = _state.update { s -> s.copy(group = s.group.copy(memberIds = if (id in s.group.memberIds) s.group.memberIds - id else s.group.memberIds + id)) }

    fun save(onSaved: () -> Unit) {
        val g = _state.value.group
        if (g.name.isBlank()) { _state.update { it.copy(nameError = true) }; return }
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            groupRepository.upsert(g)
            onSaved()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch { if (groupId != 0L) groupRepository.delete(groupId); onDone() }
    }
}

private val emojiChoices = listOf("👨‍👩‍👧‍👦", "🏡", "🎓", "💼", "🏏", "🎉", "🌊", "🧳", "❤️", "🧑‍🤝‍🧑", "🐾", "🎶", "🍛", "🪔", "🌸")

@Composable
fun GroupEditRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: GroupEditViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GroupEditScreen(state = state, onBack = onBack, onName = viewModel::setName, onEmoji = viewModel::setEmoji, onToggleMember = viewModel::toggleMember, onSave = { viewModel.save(onBack) }, onDelete = { viewModel.delete(onBack) }, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupEditScreen(
    state: GroupEditUiState,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onEmoji: (String) -> Unit,
    onToggleMember: (Long) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = Yaadein.spacing
    var confirmDelete by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(if (state.isNew) R.string.group_new_title else R.string.group_edit_title), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, contentDescription = stringResource(CoreR.string.action_close)) } },
            actions = {
                if (!state.isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.group_delete)) }
                TextButton(onClick = onSave, enabled = !state.saving && !state.loading) { Text(stringResource(CoreR.string.action_save)) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.gutter).widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                OutlinedTextField(value = state.group.emoji, onValueChange = { if (it.length <= 8) onEmoji(it) }, label = { Text(stringResource(R.string.group_emoji)) }, singleLine = true, modifier = Modifier.widthIn(max = 96.dp))
                OutlinedTextField(value = state.group.name, onValueChange = onName, label = { Text(stringResource(R.string.group_name)) }, isError = state.nameError, singleLine = true, modifier = Modifier.weight(1f))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                emojiChoices.forEach { e -> FilterChip(selected = state.group.emoji == e, onClick = { onEmoji(e) }, label = { Text(e) }) }
            }
            Text(stringResource(R.string.group_members), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                state.people.forEach { p ->
                    FilterChip(selected = p.id in state.group.memberIds, onClick = { onToggleMember(p.id) }, label = { Text(p.displayName) }, leadingIcon = { PersonAvatar(person = p, size = Yaadein.sizes.iconLg) })
                }
            }
            Button(onClick = onSave, enabled = !state.saving && !state.loading, modifier = Modifier.fillMaxWidth()) { Text(stringResource(CoreR.string.action_save)) }
            Spacer(Modifier.height(spacing.xxxl))
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.group_delete)) },
            text = { Text(state.group.name) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(CoreR.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(CoreR.string.action_cancel)) } },
        )
    }
}
