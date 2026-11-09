package com.yaadein.feature.relive

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.yaadein.core.ui.R as CoreR

data class YearlySummaryUiState(
    val loading: Boolean = true,
    val year: Int = 0,
    val person: Person? = null,
    val cards: List<MemoryCard> = emptyList(),
    val photoCount: Int = 0,
    val places: List<String> = emptyList(),
    val topPeople: List<Pair<Person, Int>> = emptyList(),
)

/** "Your 2026 with Rahul" (or "Your 2026" when personId is 0). */
@HiltViewModel
class YearlySummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val memoryRepository: MemoryRepository,
    private val personRepository: PersonRepository,
) : ViewModel() {
    val year: Int = checkNotNull(savedStateHandle["year"])
    val personId: Long = savedStateHandle["personId"] ?: 0L
    private val _state = MutableStateFlow(YearlySummaryUiState(year = year))
    val state: StateFlow<YearlySummaryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val person = if (personId != 0L) personRepository.get(personId) else null
            val cards = memoryRepository.cardsForYear(year, personId.takeIf { it != 0L })
            val people = cards.flatMap { it.people }.filter { it.id != personId }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5).map { it.key to it.value }
            _state.value = YearlySummaryUiState(
                loading = false, year = year, person = person, cards = cards,
                photoCount = cards.sumOf { it.mediaCount },
                places = cards.map { it.memory.place }.filter { it.isNotBlank() }.distinct(),
                topPeople = people,
            )
        }
    }
}

@Composable
fun YearlySummaryRoute(onBack: () -> Unit, onOpenMemory: (Long) -> Unit, onOpenPerson: (Long) -> Unit, onRecap: (personId: Long, year: Int) -> Unit, modifier: Modifier = Modifier, viewModel: YearlySummaryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    YearlySummaryScreen(state = state, onBack = onBack, onOpenMemory = onOpenMemory, onOpenPerson = onOpenPerson, onRecap = { onRecap(viewModel.personId, viewModel.year) }, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearlySummaryScreen(state: YearlySummaryUiState, onBack: () -> Unit, onOpenMemory: (Long) -> Unit, onOpenPerson: (Long) -> Unit, onRecap: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = Yaadein.spacing
    val title = state.person?.let { stringResource(R.string.yearly_title, state.year, it.displayName) } ?: stringResource(R.string.yearly_all_title, state.year)
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title, modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(CoreR.string.action_back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        if (!state.loading && state.cards.isEmpty()) {
            EmptyState(illustration = IllustrationKind.CALENDAR_STAR, title = stringResource(R.string.yearly_empty, state.year), message = "", modifier = Modifier.fillMaxSize())
            return
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = spacing.gutter, end = spacing.gutter, bottom = spacing.xxxl), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            item(key = "hero") {
                Surface(shape = RoundedCornerShape(Yaadein.radius.xl), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(spacing.xl), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text(state.year.toString(), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                        Text(title, style = MaterialTheme.typography.headlineSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
                            Stat(stringResource(R.string.yearly_memories, state.cards.size))
                            Stat(stringResource(R.string.yearly_photos, state.photoCount))
                            Stat(stringResource(R.string.yearly_places, state.places.size))
                        }
                        Spacer(Modifier.height(spacing.xs))
                        Button(onClick = onRecap) {
                            Icon(Icons.Rounded.Movie, contentDescription = null)
                            Spacer(Modifier.padding(spacing.xs))
                            Text(stringResource(R.string.relive_make_recap))
                        }
                    }
                }
            }
            if (state.topPeople.isNotEmpty()) {
                item(key = "people") {
                    Column {
                        Text(stringResource(R.string.yearly_top_people), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Spacer(Modifier.height(spacing.sm))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                            items(state.topPeople.size, key = { state.topPeople[it].first.id }) { i ->
                                val (p, n) = state.topPeople[i]
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onOpenPerson(p.id) }) {
                                    PersonAvatar(person = p, size = Yaadein.sizes.avatarLg)
                                    Text(p.displayName, style = MaterialTheme.typography.labelMedium)
                                    Text(androidx.compose.ui.res.pluralStringResource(CoreR.plurals.memory_count, n, n), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            if (state.cards.isNotEmpty()) {
                item(key = "first-label") { Text(stringResource(R.string.yearly_first), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
                item(key = "first") { state.cards.first().let { c -> MemoryCardView(card = c, onClick = { onOpenMemory(c.memory.id) }, compact = true) } }
                if (state.cards.size > 1) {
                    item(key = "last-label") { Text(stringResource(R.string.yearly_last), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
                    item(key = "last") { state.cards.last().let { c -> MemoryCardView(card = c, onClick = { onOpenMemory(c.memory.id) }, compact = true) } }
                }
                if (state.cards.size > 2) {
                    item(key = "all-label") { Text(stringResource(R.string.relive_years), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
                    items(state.cards.size - 2, key = { state.cards[it + 1].memory.id }) { i ->
                        val c = state.cards[i + 1]
                        MemoryCardView(card = c, onClick = { onOpenMemory(c.memory.id) }, compact = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

