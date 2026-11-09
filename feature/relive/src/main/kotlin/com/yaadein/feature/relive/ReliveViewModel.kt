package com.yaadein.feature.relive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaadein.data.reels.ReelEntry
import com.yaadein.data.reels.ReelKind
import com.yaadein.data.reels.ReelLibrary
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.Person
import com.yaadein.domain.reminders.PromptPick
import com.yaadein.domain.reminders.Reminders
import com.yaadein.domain.relive.CoverPick
import com.yaadein.domain.relive.DailyStory
import com.yaadein.domain.relive.StoryPage
import com.yaadein.domain.repository.TimelineFilter
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
    /** "On this day", widened to this week or this month in earlier years when the exact day has nothing. */
    val rewind: RewindResult? = null,
    val random: MemoryCard? = null,
    /** Bumped on every "Surprise me" so the reveal animation and haptic run again for "Another one". */
    val randomRound: Int = 0,
    val years: List<YearHighlight> = emptyList(),
    val upcomingBirthdays: List<Pair<Person, Int>> = emptyList(),
    val totalMemories: Int = 0,
    /** Newest reel in the library (automatic ones first), played silently at the top. */
    val latestReel: ReelEntry? = null,
    /** This week's story prompt. */
    val prompt: PromptPick? = null,
    /** Where memories happened, most first. */
    val places: List<PlaceHighlight> = emptyList(),
    /** Swipeable daily story at the top: memories from years ago, a person spotlight, the memory of the day. */
    val story: List<StoryPage> = emptyList(),
    /** Show the one-time "shake your phone for a random memory" tip. */
    val showShakeHint: Boolean = false,
)

@HiltViewModel
class ReliveViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val personRepository: PersonRepository,
    private val settingsRepository: SettingsRepository,
    val reelLibrary: ReelLibrary,
) : ViewModel() {
    private val _state = MutableStateFlow(ReliveUiState())
    val state: StateFlow<ReliveUiState> = _state.asStateFlow()
    private var refreshJob: Job? = null

    /** Reloads everything; called each time Relive is shown so new memories, people and reels appear at once. */
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
            val prompt = Reminders.weeklyPrompt(Reminders.build(emptyList(), people, 0L), today)
            val reels = runCatching { reelLibrary.load() }.getOrDefault(emptyList())
            val latest = reels.filter { it.kind == ReelKind.AUTO }.maxByOrNull { it.createdAt } ?: reels.maxByOrNull { it.createdAt }
            val birthdays = people
                .mapNotNull { p -> p.birthdayEpochDay?.let { p to Dates.daysUntilBirthday(it, today) } }
                .filter { it.second <= 30 }.sortedBy { it.second }
            val story = runCatching { buildStory(rewind, people, today) }.getOrDefault(emptyList())
            val settings = settingsRepository.current()
            val total = memoryRepository.count()
            _state.update {
                it.copy(
                    loading = false, rewind = rewind, years = years, upcomingBirthdays = birthdays, totalMemories = total,
                    latestReel = latest, prompt = prompt, places = places, story = story,
                    showShakeHint = settings.shakeForMemory && !settings.shakeHintDismissed && total >= SHAKE_HINT_MIN_MEMORIES,
                )
            }
        }
    }

    private suspend fun buildStory(rewind: com.yaadein.domain.relive.RewindResult?, people: List<Person>, today: LocalDate): List<StoryPage> {
        val counts = personRepository.memoryCounts()
        val spotlight = DailyStory.spotlightPerson(people, counts, today)?.let { person ->
            val ids = memoryRepository.idsFor(TimelineFilter(personIds = setOf(person.id))).take(SPOTLIGHT_CANDIDATES)
            val cards = ids.mapNotNull { memoryRepository.observeCard(it).first() }
            StoryPage.Spotlight(person, counts[person.id] ?: cards.size, cards.firstOrNull { it.cover != null } ?: cards.firstOrNull())
        }
        val ofTheDay = DailyStory.memoryOfTheDayId(memoryRepository.idsFor(TimelineFilter()), today)
            ?.let { memoryRepository.observeCard(it).first() }
        return DailyStory.build(rewind, spotlight, ofTheDay)
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

    fun dismissShakeHint() {
        _state.update { it.copy(showShakeHint = false) }
        viewModelScope.launch { settingsRepository.update { it.copy(shakeHintDismissed = true) } }
    }

    companion object {
        /** A random memory is only a surprise once there are a few to pick from. */
        const val SHAKE_HINT_MIN_MEMORIES = 3
        /** Newest memories of the spotlight person looked at for one with a photo. */
        const val SPOTLIGHT_CANDIDATES = 8
    }
}
