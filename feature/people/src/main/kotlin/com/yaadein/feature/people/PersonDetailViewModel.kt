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
import kotlinx.coroutines.flow.Flow
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

    val memories: Flow<PagingData<MemoryCard>> =
        memoryRepository.pagedCards(TimelineFilter(personIds = setOf(personId))).cachedIn(viewModelScope)

    private fun yearsFor(story: PersonStory?): List<Int> {
        val first = story?.firstMemory?.let { Dates.year(it.date) } ?: return emptyList()
        val last = story.lastMemory?.let { Dates.year(it.date) } ?: first
        return (last downTo first).toList()
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            personRepository.delete(personId)
            onDone()
        }
    }
}
