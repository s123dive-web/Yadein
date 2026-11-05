package com.yaadein.feature.recap

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.yaadein.data.recap.MusicSource
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.Group
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryDetail
import com.yaadein.domain.model.Person
import com.yaadein.domain.recap.RecapLength
import com.yaadein.domain.recap.RecapPlan
import com.yaadein.domain.recap.RecapPlanner
import com.yaadein.domain.recap.RecapTheme
import com.yaadein.domain.repository.GroupRepository
import com.yaadein.domain.repository.MemoryRepository
import com.yaadein.domain.repository.PersonRepository
import com.yaadein.domain.repository.TimelineFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

enum class Scope { ALL, PERSON, GROUP, YEAR, MEMORY, DATE_RANGE }

data class BundledTrack(val key: String, val rawResId: Int, val theme: RecapTheme)

val bundledTracks = listOf(
    BundledTrack("warm", R.raw.track_warm, RecapTheme.WARM),
    BundledTrack("minimal", R.raw.track_minimal, RecapTheme.MINIMAL),
    BundledTrack("festive", R.raw.track_festive, RecapTheme.FESTIVE),
    BundledTrack("classic", R.raw.track_classic, RecapTheme.CLASSIC_FILM),
)

sealed interface MusicChoice {
    data class Bundled(val key: String) : MusicChoice
    data class Own(val uri: Uri, val name: String?) : MusicChoice
    data object None : MusicChoice
}

sealed interface RenderStatus {
    data object Idle : RenderStatus
    data class Running(val progress: Int) : RenderStatus
    data class Done(val uri: Uri) : RenderStatus
    data class Failed(val message: String) : RenderStatus
}

data class RecapUiState(
    val loading: Boolean = true,
    val scope: Scope = Scope.ALL,
    val person: Person? = null,
    val group: Group? = null,
    val year: Int? = null,
    val memory: Memory? = null,
    val people: List<Person> = emptyList(),
    val groups: List<Group> = emptyList(),
    val fromEpochDay: Long? = null,
    val toEpochDay: Long? = null,
    val theme: RecapTheme = RecapTheme.WARM,
    val length: RecapLength = RecapLength.MEDIUM,
    val includeVideos: Boolean = true,
    val music: MusicChoice = MusicChoice.Bundled("warm"),
    val memories: List<MemoryDetail> = emptyList(),
    val plan: RecapPlan? = null,
    val title: String = "",
    val render: RenderStatus = RenderStatus.Idle,
)

@HiltViewModel
class RecapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val memoryRepository: MemoryRepository,
    private val personRepository: PersonRepository,
    private val groupRepository: GroupRepository,
) : ViewModel() {
    private val initialPerson: Long = savedStateHandle["personId"] ?: 0L
    private val initialMemory: Long = savedStateHandle["memoryId"] ?: 0L
    private val initialYear: Int = savedStateHandle["year"] ?: 0
    private val _state = MutableStateFlow(RecapUiState())
    val state: StateFlow<RecapUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val people = personRepository.observeAll().first()
            val groups = groupRepository.observeAll().first()
            val person = people.firstOrNull { it.id == initialPerson }
            val memory = if (initialMemory != 0L) memoryRepository.get(initialMemory) else null
            val scope = when {
                memory != null -> Scope.MEMORY
                initialYear != 0 && person != null -> Scope.YEAR
                initialYear != 0 -> Scope.YEAR
                person != null -> Scope.PERSON
                else -> Scope.ALL
            }
            _state.update { it.copy(loading = false, people = people, groups = groups, person = person, memory = memory, year = initialYear.takeIf { y -> y != 0 }, scope = scope) }
            observeWork()
            rebuild()
        }
    }

    fun setScope(scope: Scope) { _state.update { it.copy(scope = scope) }; rebuild() }
    fun setPerson(p: Person?) { _state.update { it.copy(person = p, scope = if (p != null && it.scope != Scope.YEAR) Scope.PERSON else it.scope) }; rebuild() }
    fun setGroup(g: Group?) { _state.update { it.copy(group = g, scope = Scope.GROUP) }; rebuild() }
    fun setYear(y: Int?) { _state.update { it.copy(year = y, scope = if (y != null) Scope.YEAR else Scope.ALL) }; rebuild() }
    fun setRange(from: Long?, to: Long?) { _state.update { it.copy(fromEpochDay = from, toEpochDay = to, scope = Scope.DATE_RANGE) }; rebuild() }
    fun setTheme(t: RecapTheme) {
        _state.update { s -> s.copy(theme = t, music = if (s.music is MusicChoice.Bundled) MusicChoice.Bundled(bundledTracks.first { it.theme == t }.key) else s.music) }
        rebuild()
    }
    fun setLength(l: RecapLength) { _state.update { it.copy(length = l) }; rebuild() }
    fun setIncludeVideos(v: Boolean) { _state.update { it.copy(includeVideos = v) }; rebuild() }
    fun setMusic(m: MusicChoice) = _state.update { it.copy(music = m) }
    fun setTitle(t: String) = _state.update { it.copy(title = t) }

    private fun rebuild() {
        viewModelScope.launch {
            val s = _state.value
            val ids: List<Long> = when (s.scope) {
                Scope.ALL -> memoryRepository.idsFor(TimelineFilter())
                Scope.PERSON -> s.person?.let { memoryRepository.idsFor(TimelineFilter(personIds = setOf(it.id))) } ?: emptyList()
                Scope.GROUP -> s.group?.let { memoryRepository.idsFor(TimelineFilter(groupId = it.id)) } ?: emptyList()
                Scope.YEAR -> s.year?.let { y -> memoryRepository.cardsForYear(y, s.person?.id).map { it.memory.id } } ?: emptyList()
                Scope.MEMORY -> listOfNotNull(s.memory?.id)
                Scope.DATE_RANGE -> memoryRepository.idsFor(TimelineFilter(fromEpochDay = s.fromEpochDay, toEpochDay = s.toEpochDay))
            }
            val details = memoryRepository.details(ids)
            val title = defaultTitle(s)
            val plan = if (details.isEmpty()) null else RecapPlanner.plan(
                memories = details, theme = s.theme, length = s.length, title = s.title.ifBlank { title },
                subtitle = subtitleFor(details), outroText = context.getString(R.string.recap_outro), dateLabel = { Dates.label(it.memory.date, Locale.getDefault()).text },
                includeVideos = s.includeVideos,
            )
            _state.update { it.copy(memories = details, plan = plan, title = it.title.ifBlank { title }) }
        }
    }

    private fun defaultTitle(s: RecapUiState): String = when (s.scope) {
        Scope.PERSON -> s.person?.let { context.getString(R.string.recap_title_person, it.displayName) } ?: context.getString(R.string.recap_title_all)
        Scope.GROUP -> s.group?.name ?: context.getString(R.string.recap_title_all)
        Scope.YEAR -> if (s.person != null) context.getString(R.string.recap_title_year_person, s.year, s.person.displayName) else context.getString(R.string.recap_title_year, s.year)
        Scope.MEMORY -> s.memory?.title ?: context.getString(R.string.recap_title_all)
        Scope.DATE_RANGE, Scope.ALL -> context.getString(R.string.recap_title_all)
    }

    private fun subtitleFor(details: List<MemoryDetail>): String {
        val years = details.map { Dates.year(it.memory.date) }
        val min = years.minOrNull() ?: return ""
        val max = years.maxOrNull() ?: min
        return if (min == max) min.toString() else "$min – $max"
    }

    fun startRender() {
        val s = _state.value
        val plan = s.plan ?: return
        val music = when (val m = s.music) {
            is MusicChoice.Bundled -> MusicSource.Bundled(bundledTracks.first { it.key == m.key }.rawResId)
            is MusicChoice.Own -> MusicSource.UserFile(m.uri.toString())
            MusicChoice.None -> MusicSource.None
        }
        RecapRenderWorker.enqueue(context, plan, music, s.title.ifBlank { defaultTitle(s) })
        _state.update { it.copy(render = RenderStatus.Running(0)) }
    }

    fun cancelRender() { RecapRenderWorker.cancel(context); _state.update { it.copy(render = RenderStatus.Idle) } }
    fun resetRender() = _state.update { it.copy(render = RenderStatus.Idle) }

    private fun observeWork() {
        viewModelScope.launch {
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(RecapRenderWorker.WORK_NAME).collect { infos ->
                val info = infos.firstOrNull() ?: return@collect
                val status = when (info.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> RenderStatus.Running(0)
                    WorkInfo.State.RUNNING -> RenderStatus.Running(info.progress.getInt(RecapRenderWorker.KEY_PROGRESS, 0))
                    WorkInfo.State.SUCCEEDED -> info.outputData.getString(RecapRenderWorker.KEY_OUTPUT_URI)?.let { RenderStatus.Done(Uri.parse(it)) } ?: RenderStatus.Idle
                    WorkInfo.State.FAILED -> RenderStatus.Failed(info.outputData.getString(RecapRenderWorker.KEY_ERROR) ?: "")
                    WorkInfo.State.CANCELLED -> RenderStatus.Idle
                }
                _state.update { it.copy(render = status) }
            }
        }
    }

    fun yearsAvailable(): List<Int> = _state.value.let { s -> (LocalDate.now().year downTo LocalDate.now().year - 30).toList() }
}
