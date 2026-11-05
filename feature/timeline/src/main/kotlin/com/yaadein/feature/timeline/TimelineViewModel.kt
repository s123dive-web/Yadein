package com.yaadein.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.Group
import com.yaadein.domain.model.Person
import com.yaadein.domain.repository.GroupRepository
import com.yaadein.domain.repository.MemoryRepository
import com.yaadein.domain.repository.PersonRepository
import com.yaadein.domain.repository.TimelineFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Month
import javax.inject.Inject

/** One row of the timeline: year header, month sub-header or a memory card. */
sealed interface TimelineItem {
    val key: String

    /** A new year starts; also names the first month shown under it. */
    data class YearHeader(val year: Int, val firstMonth: Month) : TimelineItem {
        override val key get() = "year-$year"
    }

    data class MonthHeader(val year: Int, val month: Month) : TimelineItem {
        override val key get() = "month-$year-${month.value}"
    }

    data class Card(val card: MemoryCard) : TimelineItem {
        override val key get() = "memory-${card.memory.id}"
    }
}

data class TimelineUiState(
    val filter: TimelineFilter = TimelineFilter(),
    val people: List<Person> = emptyList(),
    val groups: List<Group> = emptyList(),
    val places: List<String> = emptyList(),
    val totalMemories: Int = -1,
    val searchActive: Boolean = false,
    val filterSheetOpen: Boolean = false,
)

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
    personRepository: PersonRepository,
    groupRepository: GroupRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(TimelineFilter())
    private val searchActive = MutableStateFlow(false)
    private val totalMemories = MutableStateFlow(-1)
    private val filterSheetOpen = MutableStateFlow(false)
    private val places = MutableStateFlow<List<String>>(emptyList())

    val uiState: StateFlow<TimelineUiState> = combine(
        filter, personRepository.observeAll(), groupRepository.observeAll(), totalMemories, searchActive, filterSheetOpen, places,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        TimelineUiState(
            filter = values[0] as TimelineFilter,
            people = values[1] as List<Person>,
            groups = values[2] as List<Group>,
            totalMemories = values[3] as Int,
            searchActive = values[4] as Boolean,
            filterSheetOpen = values[5] as Boolean,
            places = values[6] as List<String>,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineUiState())

    @OptIn(ExperimentalCoroutinesApi::class)
    val items: Flow<PagingData<TimelineItem>> = filter
        .flatMapLatest { f -> memoryRepository.pagedCards(f) }
        .map { paging ->
            paging.map<MemoryCard, TimelineItem> { TimelineItem.Card(it) }
                .insertSeparators { before, after -> separatorBetween(before, after) }
        }
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            memoryRepository.observeCards(TimelineFilter()).map { it.size }.collect {
                totalMemories.value = it
                places.value = memoryRepository.distinctPlaces()
            }
        }
    }

    fun openFilters() { filterSheetOpen.value = true }
    fun closeFilters() { filterSheetOpen.value = false }

    fun setFilter(newFilter: TimelineFilter) = filter.update { newFilter }
    fun clearFilter() = filter.update { TimelineFilter() }
    fun setQuery(query: String) = filter.update { it.copy(query = query) }
    fun setSearchActive(active: Boolean) {
        searchActive.value = active
        if (!active) filter.update { it.copy(query = "") }
    }

    companion object {
        /** Inserts year and month headers where the year / month changes between neighbouring cards. */
        fun separatorBetween(before: TimelineItem?, after: TimelineItem?): TimelineItem? {
            val next = (after as? TimelineItem.Card)?.card ?: return null
            val nextDate = Dates.start(next.memory.date)
            val prev = (before as? TimelineItem.Card)?.card
            if (prev == null) return TimelineItem.YearHeader(nextDate.year, nextDate.month)
            val prevDate = Dates.start(prev.memory.date)
            if (prevDate.year != nextDate.year) return TimelineItem.YearHeader(nextDate.year, nextDate.month)
            if (prevDate.month != nextDate.month) return TimelineItem.MonthHeader(nextDate.year, nextDate.month)
            return null
        }
    }
}
