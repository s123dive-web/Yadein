package com.yaadein.feature.relive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.Person
import com.yaadein.domain.relive.CoverPick
import com.yaadein.domain.relive.Rewind
import com.yaadein.domain.relive.RewindResult
import com.yaadein.domain.repository.MemoryRepository
import com.yaadein.domain.repository.PersonRepository
import com.yaadein.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** A year on Relive, with the photo that stands for it. */
data class YearHighlight(val year: Int, val count: Int, val cover: CoverPick? = null)

/** A place on Relive, with the photo that stands for it. */
data class PlaceHighlight(val place: String, val count: Int, val cover: CoverPick? = null)

data class ReliveUiState(
    val loading: Boolean = true,
    /** Story rings at the top: Today, people, this year, a festival, a place. One tap opens a story full screen. */
    val rings: List<RingUi> = emptyList(),
    /** "On this day", widened to this week or this month in earlier years when the exact day has nothing. */
    val rewind: RewindResult? = null,
    val random: MemoryCard? = null,
    /** Bumped on every "Surprise me" so the reveal animation and haptic run again for "Another one". */
    val randomRound: Int = 0,
    val years: List<YearHighlight> = emptyList(),
    val upcomingBirthdays: List<Pair<Person, Int>> = emptyList(),
    val totalMemories: Int = 0,
    /** Where memories happened, most first. */
    val places: List<PlaceHighlight> = emptyList(),
)

@HiltViewModel
class ReliveViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val personRepository: PersonRepository,
    private val settingsRepository: SettingsRepository,
    private val stories: StoryLoader,
) : ViewModel() {
    private val _state = MutableStateFlow(ReliveUiState())
    val state: StateFlow<ReliveUiState> = _state.asStateFlow()
    private var refreshJob: Job? = null

    /** Reloads everything; called each time Relive is shown so new memories, people and watched rings appear at once. */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val today = LocalDate.now()
            val counts = memoryRepository.countsByYear()
            val rewind = counts.keys.minOrNull()?.let { earliest ->
                Rewind.pick(memoryRepository.inDayRanges(Rewind.windows(today, earliest)), today)
            }
            val yearCovers = runCatching { memoryRepository.yearCovers() }.getOrDefault(emptyMap())
            val years = counts.entries.sortedByDescending { it.key }.map { YearHighlight(it.key, it.value, yearCovers[it.key]) }
            val placeCovers = runCatching { memoryRepository.placeCovers() }.getOrDefault(emptyMap())
            val places = memoryRepository.topPlaces().map { (place, n) -> PlaceHighlight(place, n, placeCovers[place.trim().lowercase()]) }
            val people = personRepository.observeAll().first()
            val birthdays = people
                .mapNotNull { p -> p.birthdayEpochDay?.let { p to Dates.daysUntilBirthday(it, today) } }
                .filter { it.second <= 30 }.sortedBy { it.second }
            val rings = runCatching { stories.rings(today, settingsRepository.current().storySeen) }.getOrDefault(emptyList())
            val total = memoryRepository.count()
            _state.update {
                it.copy(
                    loading = false, rings = rings, rewind = rewind, years = years, upcomingBirthdays = birthdays,
                    totalMemories = total, places = places,
                )
            }
        }
    }

    fun surpriseMe() {
        viewModelScope.launch {
            val previous = _state.value.random?.memory?.id
            // Avoid showing the same memory twice in a row when there is more than one.
            var next = memoryRepository.random()
            if (next != null && next.memory.id == previous && _state.value.totalMemories > 1) next = memoryRepository.random() ?: next
            _state.update { it.copy(random = next, randomRound = it.randomRound + 1) }
        }
    }

    fun dismissRandom() = _state.update { it.copy(random = null) }
}
