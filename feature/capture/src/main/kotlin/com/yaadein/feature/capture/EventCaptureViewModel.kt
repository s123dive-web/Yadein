package com.yaadein.feature.capture

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaadein.data.media.ImportCoordinator
import com.yaadein.data.media.SharedMediaInbox
import com.yaadein.data.media.ImportFailure
import com.yaadein.data.media.MediaImporter
import com.yaadein.data.media.PickedMedia
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.CoverStyle
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryDate
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.model.Mood
import com.yaadein.domain.model.Person
import com.yaadein.domain.polish.PolishEngine
import com.yaadein.domain.repository.MemoryRepository
import com.yaadein.domain.repository.PersonRepository
import com.yaadein.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class EventCaptureUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val memoryId: Long = 0,
    val title: String = "",
    val description: String = "",
    val date: MemoryDate = Dates.exact(LocalDate.now()),
    val place: String = "",
    val mood: Mood? = null,
    val coverStyle: String = CoverStyle.DEFAULT,
    val people: List<Person> = emptyList(),
    val selectedPeople: Set<Long> = emptySet(),
    val existingMediaCount: Int = 0,
    /** Picked but not yet imported (new memory). */
    val pending: List<PickedMedia> = emptyList(),
    val rejected: List<ImportFailure> = emptyList(),
    val titleError: Boolean = false,
    val saving: Boolean = false,
    val optimize: Boolean = true,
)

@HiltViewModel
class EventCaptureViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val memoryRepository: MemoryRepository,
    private val personRepository: PersonRepository,
    private val settingsRepository: SettingsRepository,
    private val importer: MediaImporter,
    private val coordinator: ImportCoordinator,
) : ViewModel() {
    private val memoryId: Long = savedStateHandle["memoryId"] ?: 0L
    private val _state = MutableStateFlow(EventCaptureUiState())
    val state: StateFlow<EventCaptureUiState> = _state.asStateFlow()

    init {
        // Opened from "Share → Yaadein": attach the shared photos/videos straight away (once per screen).
        if (savedStateHandle.get<Boolean>("fromShare") == true && savedStateHandle.get<Boolean>(SHARE_TAKEN) != true) {
            savedStateHandle[SHARE_TAKEN] = true
            SharedMediaInbox.take().takeIf { it.isNotEmpty() }?.let(::onPicked)
        }
        viewModelScope.launch {
            personRepository.observeAll().collect { people -> _state.update { it.copy(people = people) } }
        }
        viewModelScope.launch {
            val settings = settingsRepository.current()
            if (memoryId != 0L) {
                val detail = memoryRepository.observeDetail(memoryId).first()
                if (detail != null) {
                    _state.update {
                        it.copy(
                            loading = false, isNew = false, memoryId = memoryId, title = detail.memory.title, description = detail.memory.description,
                            date = detail.memory.date, place = detail.memory.place, mood = detail.memory.mood, coverStyle = detail.memory.coverStyle,
                            selectedPeople = detail.people.map { p -> p.id }.toSet(), existingMediaCount = detail.media.size, optimize = settings.storeOptimizedCopies,
                        )
                    }
                    return@launch
                }
            }
            _state.update { it.copy(loading = false, optimize = settings.storeOptimizedCopies) }
        }
    }

    fun setTitle(v: String) = _state.update { it.copy(title = v, titleError = false) }
    fun setDescription(v: String) = _state.update { it.copy(description = v) }
    fun setDate(v: MemoryDate) = _state.update { it.copy(date = v) }
    fun setPlace(v: String) = _state.update { it.copy(place = v) }
    fun setMood(v: Mood?) = _state.update { it.copy(mood = v) }
    fun setCoverStyle(v: String) = _state.update { it.copy(coverStyle = v) }
    fun togglePerson(id: Long) = _state.update { it.copy(selectedPeople = if (id in it.selectedPeople) it.selectedPeople - id else it.selectedPeople + id) }
    fun setOptimize(v: Boolean) = _state.update { it.copy(optimize = v) }
    fun removePending(uri: Uri) = _state.update { it.copy(pending = it.pending.filterNot { p -> p.uri == uri }) }
    fun dismissRejected() = _state.update { it.copy(rejected = emptyList()) }

    /** Inspects picked URIs, enforcing size limits up front with a clear message. */
    fun onPicked(uris: List<Uri>) {
        viewModelScope.launch {
            val accepted = ArrayList<PickedMedia>()
            val rejected = ArrayList<ImportFailure>()
            for (uri in uris) {
                val picked = importer.inspect(uri)
                if (picked == null) { rejected += ImportFailure.Unsupported; continue }
                val limit = importer.checkLimit(picked)
                if (limit != null) rejected += limit else accepted += picked
            }
            _state.update { it.copy(pending = it.pending + accepted, rejected = it.rejected + rejected) }
            // Suggest a date from the photos when the user has not changed it from today.
            val earliest = accepted.mapNotNull { it.takenAtMillis }.minOrNull()
            if (earliest != null && _state.value.isNew && _state.value.date == Dates.exact(LocalDate.now())) {
                val d = java.time.Instant.ofEpochMilli(earliest).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                _state.update { it.copy(date = Dates.exact(d)) }
            }
        }
    }

    fun save(onSaved: (Long) -> Unit) {
        val s = _state.value
        var title = s.title.trim()
        if (title.isBlank()) {
            // Fall back to a polished title from the description or place, else require one.
            val source = s.description.takeIf { it.isNotBlank() } ?: s.place.takeIf { it.isNotBlank() }
            if (source == null) { _state.update { it.copy(titleError = true) }; return }
            title = PolishEngine.polish(source).title
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val hasMedia = s.pending.isNotEmpty() || s.existingMediaCount > 0
            val memory = Memory(
                id = s.memoryId, type = if (hasMedia) MemoryType.EVENT else MemoryType.TEXT_ONLY, title = title, description = s.description.trim(),
                date = s.date, place = s.place.trim(), mood = s.mood, coverStyle = s.coverStyle,
            )
            val id = if (s.isNew) memoryRepository.create(memory, s.selectedPeople.toList()) else {
                memoryRepository.update(memory, s.selectedPeople.toList()); s.memoryId
            }
            if (s.pending.isNotEmpty()) coordinator.enqueue(id, s.pending, s.optimize, startPosition = s.existingMediaCount)
            _state.update { it.copy(saving = false) }
            onSaved(id)
        }
    }

    private companion object {
        const val SHARE_TAKEN = "shareTaken"
    }
}
