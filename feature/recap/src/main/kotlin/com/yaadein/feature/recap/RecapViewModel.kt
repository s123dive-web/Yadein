package com.yaadein.feature.recap

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.yaadein.data.recap.MusicLibrary
import com.yaadein.data.recap.MusicSource
import com.yaadein.data.recap.OwnSong
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
import kotlinx.coroutines.Job
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

/** A bundled soundtrack. [theme]: the reel theme it is the default for (null for extra tracks); [bpm] drives beat-synced cuts. */
data class BundledTrack(val key: String, val rawResId: Int, val theme: RecapTheme?, val bpm: Int, val labelRes: Int) {
    val beatMs: Long get() = 60_000L / bpm
}

val bundledTracks = listOf(
    BundledTrack("warm", R.raw.track_warm, RecapTheme.WARM, 90, R.string.track_warm),
    BundledTrack("minimal", R.raw.track_minimal, RecapTheme.MINIMAL, 80, R.string.track_minimal),
    BundledTrack("festive", R.raw.track_festive, RecapTheme.FESTIVE, 110, R.string.track_festive),
    BundledTrack("classic", R.raw.track_classic, RecapTheme.CLASSIC_FILM, 84, R.string.track_classic),
    BundledTrack("lullaby", R.raw.track_lullaby, null, 72, R.string.track_lullaby),
    BundledTrack("celebration", R.raw.track_celebration, null, 112, R.string.track_celebration),
    BundledTrack("acoustic", R.raw.track_acoustic, null, 96, R.string.track_acoustic),
    BundledTrack("upbeat", R.raw.track_upbeat, null, 120, R.string.track_upbeat),
)

sealed interface MusicChoice {
    data class Bundled(val key: String) : MusicChoice
    /** A song from the phone ([MusicLibrary]); [name] is shown on the chip. */
    data class Own(val uri: Uri, val name: String?) : MusicChoice
    data object None : MusicChoice
}

/** The "Your songs" sheet: every song added from the phone, filtered by [query]. */
data class OwnMusicState(
    val songs: List<OwnSong> = emptyList(),
    val query: String = "",
    /** True while picked files are being read (title, artist, length). */
    val adding: Boolean = false,
    /** Title of a song that could no longer be opened and was dropped from the list; shown once. */
    val missingTitle: String? = null,
) {
    val filtered: List<OwnSong> get() = MusicLibrary.search(songs, query)
}

sealed interface RenderStatus {
    data object Idle : RenderStatus
    /** [etaSeconds]: estimated time left, once enough progress has been seen to guess (null before that). */
    data class Running(val progress: Int, val etaSeconds: Int? = null) : RenderStatus
    /**
     * [reelId]: the copy kept in the app's reel library, for in-app playback (null if it could not be stored).
     * [sizeBytes] / [durationMs]: of the MP4 (0 when unknown, e.g. work queued by an older build).
     */
    data class Done(val uri: Uri, val reelId: String? = null, val sizeBytes: Long = 0L, val durationMs: Long = 0L) : RenderStatus {
        /** Short enough and small enough to post as a WhatsApp status untouched. */
        val fitsWhatsAppStatus: Boolean get() = WhatsAppShare.fitsStatus(durationMs, sizeBytes)
    }
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
    val ownMusic: OwnMusicState = OwnMusicState(),
    val memories: List<MemoryDetail> = emptyList(),
    /** Photos and clips going into the reel (picked automatically until the user changes them). */
    val selectedMediaIds: Set<Long> = emptySet(),
    /** True once the user added or removed a photo; changing who/what or the length then starts over. */
    val customSelection: Boolean = false,
    /** Set when the user tried to go past [RecapPlanner.MAX_SELECTED_ITEMS]. */
    val selectionLimitHit: Boolean = false,
    val plan: RecapPlan? = null,
    val title: String = "",
    val render: RenderStatus = RenderStatus.Idle,
) {
    /** Every photo/clip the current scope could use, memory by memory (for the photo chooser). */
    val candidates: List<Pair<MemoryDetail, List<com.yaadein.domain.model.Media>>>
        get() = memories.sortedWith(compareBy({ it.memory.date.startEpochDay }, { it.memory.id }))
            .map { it to RecapPlanner.usableMedia(it, includeVideos) }.filter { it.second.isNotEmpty() }
    val candidateCount: Int get() = memories.sumOf { RecapPlanner.usableMedia(it, includeVideos).size }
}

@HiltViewModel
class RecapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val memoryRepository: MemoryRepository,
    private val personRepository: PersonRepository,
    private val groupRepository: GroupRepository,
    private val musicLibrary: MusicLibrary,
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
            reload()
        }
        viewModelScope.launch {
            musicLibrary.load()
            musicLibrary.songs.collect { songs -> _state.update { it.copy(ownMusic = it.ownMusic.copy(songs = songs)) } }
        }
    }

    fun setScope(scope: Scope) { _state.update { it.copy(scope = scope) }; reload() }
    fun setPerson(p: Person?) { _state.update { it.copy(person = p, scope = if (p != null && it.scope != Scope.YEAR) Scope.PERSON else it.scope) }; reload() }
    fun setGroup(g: Group?) { _state.update { it.copy(group = g, scope = Scope.GROUP) }; reload() }
    fun setYear(y: Int?) { _state.update { it.copy(year = y, scope = if (y != null) Scope.YEAR else Scope.ALL) }; reload() }
    fun setRange(from: Long?, to: Long?) { _state.update { it.copy(fromEpochDay = from, toEpochDay = to, scope = Scope.DATE_RANGE) }; reload() }
    fun setTheme(t: RecapTheme) {
        // A theme's own track follows the theme; an extra track the user picked (lullaby, upbeat...) stays.
        _state.update { s ->
            val current = (s.music as? MusicChoice.Bundled)?.let { m -> bundledTracks.firstOrNull { it.key == m.key } }
            s.copy(theme = t, music = if (current?.theme != null) MusicChoice.Bundled(bundledTracks.first { it.theme == t }.key) else s.music)
        }
        replan()
    }
    fun setLength(l: RecapLength) {
        _state.update { s -> s.copy(length = l, selectedMediaIds = if (s.customSelection) s.selectedMediaIds else defaultSelection(s.memories, s.includeVideos, l)) }
        replan()
    }
    fun setIncludeVideos(v: Boolean) {
        _state.update { s ->
            val kept = if (v) s.selectedMediaIds else s.selectedMediaIds - s.memories.flatMap { it.media }.filter { it.kind == com.yaadein.domain.model.MediaKind.VIDEO }.map { it.id }.toSet()
            s.copy(includeVideos = v, selectedMediaIds = if (s.customSelection) kept else defaultSelection(s.memories, v, s.length))
        }
        replan()
    }
    fun setMusic(m: MusicChoice) { _state.update { it.copy(music = m) }; replan() }

    fun setSongQuery(q: String) = _state.update { it.copy(ownMusic = it.ownMusic.copy(query = q)) }

    /** Songs picked from the phone: remembered in [MusicLibrary], and the first one becomes the reel's music. */
    fun addOwnSongs(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _state.update { it.copy(ownMusic = it.ownMusic.copy(adding = true)) }
        viewModelScope.launch {
            val added = runCatching { musicLibrary.add(uris) }.getOrDefault(emptyList())
            _state.update { it.copy(ownMusic = it.ownMusic.copy(adding = false, query = "")) }
            added.firstOrNull()?.let { selectOwnSong(it) }
        }
    }

    /** Uses [song] for the reel; a song that can no longer be opened is dropped and reported instead. */
    fun selectOwnSong(song: OwnSong) {
        viewModelScope.launch {
            if (musicLibrary.isReadable(song.uri)) {
                _state.update { it.copy(ownMusic = it.ownMusic.copy(missingTitle = null)) }
                setMusic(MusicChoice.Own(Uri.parse(song.uri), song.title))
            } else {
                musicLibrary.remove(song.uri)
                _state.update { s ->
                    val music = if ((s.music as? MusicChoice.Own)?.uri?.toString() == song.uri) MusicChoice.Bundled(bundledTracks.first { it.theme == s.theme }.key) else s.music
                    s.copy(music = music, ownMusic = s.ownMusic.copy(missingTitle = song.title))
                }
                replan()
            }
        }
    }

    fun removeOwnSong(song: OwnSong) {
        viewModelScope.launch {
            musicLibrary.remove(song.uri)
            _state.update { s ->
                if ((s.music as? MusicChoice.Own)?.uri?.toString() == song.uri) s.copy(music = MusicChoice.Bundled(bundledTracks.first { it.theme == s.theme }.key)) else s
            }
            replan()
        }
    }

    fun dismissMissingSong() = _state.update { it.copy(ownMusic = it.ownMusic.copy(missingTitle = null)) }
    fun setTitle(t: String) { _state.update { it.copy(title = t) }; replan() }

    /** Adds or removes one photo/clip (tap in the chooser or on the storyboard). */
    fun toggleMedia(mediaId: Long) {
        _state.update { s ->
            when {
                mediaId in s.selectedMediaIds -> s.copy(selectedMediaIds = s.selectedMediaIds - mediaId, customSelection = true, selectionLimitHit = false)
                s.selectedMediaIds.size >= RecapPlanner.MAX_SELECTED_ITEMS -> s.copy(selectionLimitHit = true)
                else -> s.copy(selectedMediaIds = s.selectedMediaIds + mediaId, customSelection = true, selectionLimitHit = false)
            }
        }
        replan()
    }

    /** Selects or clears every photo/clip of one memory. */
    fun setMemorySelected(memoryId: Long, selected: Boolean) {
        _state.update { s ->
            val ids = s.memories.firstOrNull { it.memory.id == memoryId }?.let { RecapPlanner.usableMedia(it, s.includeVideos) }?.map { it.id } ?: emptyList()
            if (!selected) return@update s.copy(selectedMediaIds = s.selectedMediaIds - ids.toSet(), customSelection = true, selectionLimitHit = false)
            val room = RecapPlanner.MAX_SELECTED_ITEMS - s.selectedMediaIds.size
            val toAdd = ids.filter { it !in s.selectedMediaIds }
            s.copy(selectedMediaIds = s.selectedMediaIds + toAdd.take(room.coerceAtLeast(0)), customSelection = true, selectionLimitHit = toAdd.size > room)
        }
        replan()
    }

    /** Selects everything the scope has (up to the limit), or nothing. */
    fun selectAll(selected: Boolean) {
        _state.update { s ->
            if (!selected) return@update s.copy(selectedMediaIds = emptySet(), customSelection = true, selectionLimitHit = false)
            val all = s.candidates.flatMap { it.second }.map { it.id }
            s.copy(selectedMediaIds = all.take(RecapPlanner.MAX_SELECTED_ITEMS).toSet(), customSelection = true, selectionLimitHit = all.size > RecapPlanner.MAX_SELECTED_ITEMS)
        }
        replan()
    }

    /** Back to the automatic pick. */
    fun resetSelection() {
        _state.update { s -> s.copy(selectedMediaIds = defaultSelection(s.memories, s.includeVideos, s.length), customSelection = false, selectionLimitHit = false) }
        replan()
    }

    private var loadJob: Job? = null

    /** Loads the memories for the chosen scope and picks photos automatically. */
    private fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
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
            _state.update {
                it.copy(
                    memories = details, selectedMediaIds = defaultSelection(details, it.includeVideos, it.length),
                    customSelection = false, selectionLimitHit = false, title = defaultTitle(it),
                )
            }
            replan()
        }
    }

    /** Rebuilds the storyboard from the current memories and selection (cheap: no database work). */
    private fun replan() {
        val s = _state.value
        val title = defaultTitle(s)
        val plan = if (s.memories.isEmpty()) null else RecapPlanner.plan(
            memories = s.memories, theme = s.theme, length = s.length, title = s.title.ifBlank { title },
            subtitle = subtitleFor(s.memories), outroText = context.getString(R.string.recap_outro),
            outroSubtitle = context.getString(R.string.recap_outro_credit),
            dateLabel = { Dates.label(it.memory.date, Locale.getDefault()).text },
            includeVideos = s.includeVideos, selectedMediaIds = s.selectedMediaIds,
            beatMs = (s.music as? MusicChoice.Bundled)?.let { m -> bundledTracks.firstOrNull { it.key == m.key }?.beatMs },
        ).takeIf { p -> p.segments.size > 2 }
        _state.update { it.copy(plan = plan) }
    }

    private fun defaultSelection(memories: List<MemoryDetail>, includeVideos: Boolean, length: RecapLength) =
        RecapPlanner.defaultSelection(memories, includeVideos, RecapPlanner.defaultItemCount(length))

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

    private val eta = EtaEstimator()

    private fun observeWork() {
        viewModelScope.launch {
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(RecapRenderWorker.WORK_NAME).collect { infos ->
                val info = infos.firstOrNull() ?: return@collect
                val status = when (info.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> { eta.reset(); RenderStatus.Running(0) }
                    WorkInfo.State.RUNNING -> info.progress.getInt(RecapRenderWorker.KEY_PROGRESS, 0).let { p ->
                        RenderStatus.Running(p, eta.estimate(p, android.os.SystemClock.elapsedRealtime()))
                    }
                    WorkInfo.State.SUCCEEDED -> info.outputData.getString(RecapRenderWorker.KEY_OUTPUT_URI)?.let {
                        RenderStatus.Done(
                            uri = Uri.parse(it), reelId = info.outputData.getString(RecapRenderWorker.KEY_REEL_ID),
                            sizeBytes = info.outputData.getLong(RecapRenderWorker.KEY_SIZE_BYTES, 0L),
                            durationMs = info.outputData.getLong(RecapRenderWorker.KEY_DURATION_MS, 0L),
                        )
                    } ?: RenderStatus.Idle
                    WorkInfo.State.FAILED -> RenderStatus.Failed(info.outputData.getString(RecapRenderWorker.KEY_ERROR) ?: "")
                    WorkInfo.State.CANCELLED -> RenderStatus.Idle
                }
                _state.update { it.copy(render = status) }
            }
        }
    }

    fun yearsAvailable(): List<Int> = _state.value.let { s -> (LocalDate.now().year downTo LocalDate.now().year - 30).toList() }
}

/** Time left from the rate of progress since the first progress report (linear; rendering is close to linear). */
class EtaEstimator {
    private var startProgress = -1
    private var startAt = 0L

    fun reset() { startProgress = -1 }

    fun estimate(progress: Int, nowMs: Long): Int? {
        if (progress <= 0) return null
        if (startProgress < 0 || progress < startProgress) { startProgress = progress; startAt = nowMs; return null }
        val done = progress - startProgress
        if (done < MIN_STEP) return null
        val msPerPercent = (nowMs - startAt).toDouble() / done
        return ((100 - progress) * msPerPercent / 1000).toInt().coerceAtLeast(1)
    }

    private companion object { const val MIN_STEP = 3 }
}
