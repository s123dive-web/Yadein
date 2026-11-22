package com.yaadein.feature.people

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.PersonStory
import com.yaadein.domain.repository.MemoryRepository
import com.yaadein.domain.repository.PersonRepository
import com.yaadein.domain.repository.TimelineFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonDetailUiState(
    val loading: Boolean = true,
    val story: PersonStory? = null,
    val years: List<Int> = emptyList(),
)

@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val personRepository: PersonRepository,
    private val memoryRepository: MemoryRepository,
) : ViewModel() {
    val personId: Long = checkNotNull(savedStateHandle["personId"])

    val uiState: StateFlow<PersonDetailUiState> = personRepository
        .observeStory(personId, Dates.todayEpochDay())
        .map { story -> PersonDetailUiState(loading = false, story = story, years = yearsFor(story)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonDetailUiState())

    private val _query = MutableStateFlow("")
    /** Words typed into "Search these memories"; the list below shows only this person's memories that match. */
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val memories: Flow<PagingData<MemoryCard>> = _query
        .debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .map { it.trim() }
        .distinctUntilChanged()
        .flatMapLatest { q -> memoryRepository.pagedCards(TimelineFilter(personIds = setOf(personId), query = q)) }
        .cachedIn(viewModelScope)

    fun setQuery(value: String) { _query.value = value }

    private fun yearsFor(story: PersonStory?): List<Int> {
        val first = story?.firstMemory?.let { Dates.year(it.date) } ?: return emptyList()
        val last = story.lastMemory?.let { Dates.year(it.date) } ?: first
        return (last downTo first).toList()
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            personRepository.delete(personId)
            onDone()
        }
    }
}
