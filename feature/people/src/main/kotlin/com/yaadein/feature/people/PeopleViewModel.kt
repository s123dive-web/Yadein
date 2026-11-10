package com.yaadein.feature.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaadein.data.db.AppDatabase
import com.yaadein.domain.model.Group
import com.yaadein.domain.model.Person
import com.yaadein.domain.repository.GroupRepository
import com.yaadein.domain.repository.PersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class PersonTile(val person: Person, val memoryCount: Int)

data class PeopleUiState(
    val loading: Boolean = true,
    val people: List<PersonTile> = emptyList(),
    val groups: List<Group> = emptyList(),
    /** Faces found in photos that nobody has named yet ("Who is this?" banner); 0 hides the banner. */
    val unknownFaces: Int = 0,
)

@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val personRepository: PersonRepository,
    groupRepository: GroupRepository,
    db: AppDatabase,
) : ViewModel() {
    val uiState: StateFlow<PeopleUiState> = combine(
        personRepository.observeAll(),
        groupRepository.observeAll(),
        flow { emit(personRepository.memoryCounts()) },
        db.faceDao().observeUnknownCount(),
    ) { people, groups, counts, unknownFaces ->
        PeopleUiState(
            loading = false,
            people = people.map { PersonTile(it, counts[it.id] ?: 0) }.sortedByDescending { it.memoryCount },
            groups = groups,
            unknownFaces = unknownFaces,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeopleUiState())
}
