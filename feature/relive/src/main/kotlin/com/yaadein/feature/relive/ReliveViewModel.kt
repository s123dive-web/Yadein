package com.yaadein.feature.relive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaadein.data.reels.ReelEntry
import com.yaadein.data.reels.ReelKind
import com.yaadein.data.reels.ReelLibrary
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.reminders.PromptPick
import com.yaadein.domain.reminders.Reminders
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.Person
import com.yaadein.domain.repository.MemoryRepository
import com.yaadein.domain.repository.PersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class YearHighlight(val year: Int, val count: Int)

data class ReliveUiState(
    val loading: Boolean = true,
    val onThisDay: List<MemoryCard> = emptyList(),
    val random: MemoryCard? = null,
    val years: List<YearHighlight> = emptyList(),
    val upcomingBirthdays: List<Pair<Person, Int>> = emptyList(),
    val totalMemories: Int = 0,
    /** Newest reel in the library (automatic ones first), played silently at the top. */
    val latestReel: ReelEntry? = null,
    /** This week's story prompt. */
    val prompt: PromptPick? = null,
    /** Where memories happened, most first. */
    val places: List<Pair<String, Int>> = emptyList(),
)

@HiltViewModel
class ReliveViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val personRepository: PersonRepository,
    val reelLibrary: ReelLibrary,
) : ViewModel() {
    private val _state = MutableStateFlow(ReliveUiState())
    val state: StateFlow<ReliveUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val today = LocalDate.now()
            val onThisDay = memoryRepository.onThisDay(Dates.monthDayKey(today), excludeYear = today.year)
            val years = memoryRepository.countsByYear().entries.sortedByDescending { it.key }.map { YearHighlight(it.key, it.value) }
            val people = personRepository.observeAll().first()
            val prompt = Reminders.weeklyPrompt(Reminders.build(emptyList(), people, 0L), today)
            val reels = runCatching { reelLibrary.load() }.getOrDefault(emptyList())
            val latest = reels.filter { it.kind == ReelKind.AUTO }.maxByOrNull { it.createdAt } ?: reels.maxByOrNull { it.createdAt }
            val birthdays = people
                .mapNotNull { p -> p.birthdayEpochDay?.let { p to Dates.daysUntilBirthday(it, today) } }
                .filter { it.second <= 30 }.sortedBy { it.second }
            _state.update {
                it.copy(loading = false, onThisDay = onThisDay, years = years, upcomingBirthdays = birthdays, totalMemories = memoryRepository.count(), latestReel = latest, prompt = prompt, places = memoryRepository.topPlaces())
            }
        }
    }

    fun surpriseMe() {
        viewModelScope.launch { _state.update { it.copy(random = memoryRepository.random()) } }
    }

    fun dismissRandom() = _state.update { it.copy(random = null) }
}
