package com.yaadein.feature.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yaadein.core.ui.components.EmptyState
import com.yaadein.core.ui.components.MemoryCardView
import com.yaadein.core.ui.components.PersonAvatar
import com.yaadein.core.ui.illustrations.IllustrationKind
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.Person
import com.yaadein.domain.repository.MemoryRepository
import com.yaadein.domain.repository.PersonRepository
import com.yaadein.domain.repository.TimelineFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import com.yaadein.core.ui.R as CoreR

data class TogetherUiState(
    val people: List<Person> = emptyList(),
    val selected: Set<Long> = emptySet(),
    val cards: List<MemoryCard> = emptyList(),
)

/** Pick two or more people; show only memories where all of them appear. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TogetherViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    personRepository: PersonRepository,
    memoryRepository: MemoryRepository,
) : ViewModel() {
    private val selected = MutableStateFlow(setOfNotNull(savedStateHandle.get<Long>("personId")?.takeIf { it != 0L }))

    val state: StateFlow<TogetherUiState> = combine(
        personRepository.observeAll(),
        selected,
        selected.flatMapLatest { ids -> if (ids.size >= 2) memoryRepository.observeCards(TimelineFilter(personIds = ids, requireAllPeople = true)) else flowOf(emptyList()) },
    ) { people, sel, cards -> TogetherUiState(people, sel, cards) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TogetherUiState())

    fun toggle(id: Long) = selected.update { if (id in it) it - id else it + id }
}

@Composable
fun TogetherRoute(onBack: () -> Unit, onOpenMemory: (Long) -> Unit, modifier: Modifier = Modifier, viewModel: TogetherViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TogetherScreen(state = state, onBack = onBack, onToggle = viewModel::toggle, onOpenMemory = onOpenMemory, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TogetherScreen(state: TogetherUiState, onBack: () -> Unit, onToggle: (Long) -> Unit, onOpenMemory: (Long) -> Unit, modifier: Modifier = Modifier) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.together_title), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(CoreR.string.action_back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = spacing.gutter, end = spacing.gutter, bottom = spacing.xxxl), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            item(key = "picker") {
                Column {
                    Text(stringResource(R.string.together_pick), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(spacing.sm))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        state.people.forEach { p ->
                            FilterChip(selected = p.id in state.selected, onClick = { onToggle(p.id) }, label = { Text(p.displayName) }, leadingIcon = { PersonAvatar(person = p, size = Yaadein.sizes.iconLg) })
                        }
                    }
                    Spacer(Modifier.height(spacing.sm))
                }
            }
            if (state.selected.size >= 2 && state.cards.isEmpty()) {
                item(key = "empty") {
                    EmptyState(illustration = IllustrationKind.PEOPLE_CIRCLE, title = stringResource(R.string.together_empty_title), message = stringResource(R.string.together_empty_message))
                }
            }
            items(state.cards.size, key = { state.cards[it].memory.id }) { i ->
                val c = state.cards[i]
                MemoryCardView(card = c, onClick = { onOpenMemory(c.memory.id) }, compact = true)
            }
        }
    }
}
