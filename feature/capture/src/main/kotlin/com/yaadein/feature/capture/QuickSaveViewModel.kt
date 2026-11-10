package com.yaadein.feature.capture

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaadein.data.faces.FaceSuggester
import com.yaadein.data.lock.AppLockState
import com.yaadein.data.media.ImportCoordinator
import com.yaadein.data.media.ImportFailure
import com.yaadein.data.media.MediaImporter
import com.yaadein.data.media.PickedMedia
import com.yaadein.data.media.SharedMediaInbox
import com.yaadein.core.ui.components.festivalNameRes
import com.yaadein.domain.capture.CaptureEntry
import com.yaadein.domain.capture.CaptureTimer
import com.yaadein.domain.capture.DateSource
import com.yaadein.domain.capture.KnownPlace
import com.yaadein.domain.capture.QuickSavePrefill
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.grouping.PickedItem
import com.yaadein.domain.model.DateKind
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryDate
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.model.Person
import com.yaadein.domain.polish.PolishEngine
import com.yaadein.domain.polish.PolishResult
import com.yaadein.domain.relive.Festivals
import com.yaadein.domain.repository.CaptureStatsRepository
import com.yaadein.domain.repository.MemoryRepository
import com.yaadein.domain.repository.PersonRepository
import com.yaadein.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class QuickSaveUiState(
    /** True until the picked files have been inspected (dates, sizes). */
    val inspecting: Boolean = false,
    val items: List<PickedMedia> = emptyList(),
    /** Index into [items] of the photo that becomes the cover (always imported first). */
    val coverIndex: Int = 0,
    val date: MemoryDate = Dates.exact(LocalDate.now()),
    val dateSource: DateSource = DateSource.TODAY,
    /** The user changed the date: pre-fill must not overwrite it. */
    val dateEdited: Boolean = false,
    val suggestedTitle: String = "",
    val title: String = "",
    /** Everyone, ordered: face suggestions, then recently tagged, then the rest. */
    val people: List<Person> = emptyList(),
    /** The people shown as chips by default (recent + suggested); the rest are behind "all people". */
    val shortlist: List<Person> = emptyList(),
    val selectedPeople: Set<Long> = emptySet(),
    /** Face suggestions for the picked photos (shown with a badge, never auto-selected in this phase). */
    val suggestedPeople: Set<Long> = emptySet(),
    val place: String = "",
    val placeOpen: Boolean = false,
    val splitInto: Int? = null,
    val splitDismissed: Boolean = false,
    /** Words-only mode: no photos, a Polish field instead. */
    val words: String = "",
    val polished: PolishResult? = null,
    val rejected: List<ImportFailure> = emptyList(),
    val cappedTo: Int? = null,
    val saving: Boolean = false,
    val entry: CaptureEntry = CaptureEntry.FAB,
) {
    val wordsOnly: Boolean get() = items.isEmpty() && !inspecting
    val canSave: Boolean get() = !saving && !inspecting && (items.isNotEmpty() || words.isNotBlank())
}

/**
 * Quick Save: tap + → pick photos → Save. Everything is pre-filled by [QuickSavePrefill] and editable; Save writes the
 * memory at once and leaves encryption to [ImportCoordinator], so the screen closes immediately.
 */
@HiltViewModel
class QuickSaveViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val memoryRepository: MemoryRepository,
    private val personRepository: PersonRepository,
    private val settingsRepository: SettingsRepository,
    private val importer: MediaImporter,
    private val coordinator: ImportCoordinator,
    private val captureStats: CaptureStatsRepository,
    private val faceSuggester: FaceSuggester,
    private val lockState: AppLockState,
    private val festivals: Festivals,
) : ViewModel() {
    private val _state = MutableStateFlow(QuickSaveUiState())
    val state: StateFlow<QuickSaveUiState> = _state.asStateFlow()
    private var timer = CaptureTimer(CaptureEntry.FAB, SystemClock.elapsedRealtime())
    private var knownPlaces: List<KnownPlace> = emptyList()
    private var recent: List<Person> = emptyList()
    private var everyone: List<Person> = emptyList()

    init {
        val fromShare = savedStateHandle.get<Boolean>("fromShare") == true
        if (savedStateHandle.get<Boolean>(TAKEN) != true) {
            savedStateHandle[TAKEN] = true
            if (fromShare) {
                val uris = SharedMediaInbox.take()
                timer = CaptureTimer(CaptureEntry.SHARE, SharedMediaInbox.receivedAtElapsed.takeIf { it > 0 } ?: SystemClock.elapsedRealtime())
                _state.update { it.copy(entry = CaptureEntry.SHARE) }
                if (uris.isNotEmpty()) onPicked(uris)
            } else {
                QuickSaveInbox.take()?.let { req ->
                    timer = CaptureTimer(req.entry, req.startedAtElapsed)
                    _state.update { it.copy(entry = req.entry) }
                    if (req.uris.isNotEmpty()) onPicked(req.uris)
                }
            }
        }
        viewModelScope.launch {
            recent = personRepository.recentlyTagged(QuickSavePrefill.Rules.RECENT_PEOPLE)
            knownPlaces = memoryRepository.knownPlaces()
            personRepository.observeAll().collect { people ->
                everyone = people
                _state.update { it.withPeople() }
                refill()
            }
        }
    }

    private fun QuickSaveUiState.withPeople(): QuickSaveUiState {
        val suggested = everyone.filter { it.id in suggestedPeople }
        val recentOnly = recent.filter { r -> r.id !in suggestedPeople && everyone.any { it.id == r.id } }
        val rest = everyone.filter { p -> suggested.none { it.id == p.id } && recentOnly.none { it.id == p.id } }
        return copy(people = suggested + recentOnly + rest, shortlist = suggested + recentOnly)
    }

    /** Inspects the picked files (size limits, EXIF) and pre-fills the card. */
    fun onPicked(uris: List<Uri>) {
        val distinct = uris.distinct()
        val kept = distinct.take(QuickSavePrefill.Rules.MAX_ITEMS)
        viewModelScope.launch {
            _state.update { it.copy(inspecting = true, cappedTo = if (distinct.size > kept.size) kept.size else it.cappedTo) }
            val accepted = ArrayList<PickedMedia>()
            val rejected = ArrayList<ImportFailure>()
            val already = _state.value.items.map { it.uri }.toSet()
            val optimize = settingsRepository.current().storeOptimizedCopies
            for (uri in kept) {
                if (uri in already) continue
                val picked = importer.inspect(uri)
                if (picked == null) { rejected += ImportFailure.Unsupported; continue }
                val limit = importer.checkLimit(picked, optimize)
                if (limit != null) rejected += limit else accepted += picked
            }
            _state.update { it.copy(inspecting = false, items = it.items + accepted, rejected = it.rejected + rejected) }
            refill()
            suggestFaces(accepted)
        }
    }

    /** Re-runs the pre-fill rules; never overwrites a date or title the user typed. */
    private fun refill() {
        val s = _state.value
        if (s.items.isEmpty()) return
        val picked = s.items.map { PickedItem(it.uri.toString(), it.takenAtMillis, it.isVideo, latitude = it.latitude, longitude = it.longitude) }
        val names = s.people.filter { it.id in s.selectedPeople }.map { it.displayName }
        val today = LocalDate.now()
        // A pick on a festival day is titled after it ("Diwali 2024 with Aai"); only an exact date can name a festival.
        val festival = festivalName(if (s.dateEdited) s.date else QuickSavePrefill.date(picked, today).first)
        val suggestion = QuickSavePrefill.suggest(picked, today, knownPlaces = knownPlaces, peopleNames = names, festival = festival)
        _state.update {
            it.copy(
                date = if (it.dateEdited) it.date else suggestion.date,
                dateSource = if (it.dateEdited) it.dateSource else suggestion.dateSource,
                splitInto = suggestion.splitInto,
                place = it.place.ifBlank { suggestion.place },
                suggestedTitle = if (it.dateEdited || it.selectedPeople.isNotEmpty() || it.place != suggestion.place) {
                    PolishEngine.titleFor(it.date, it.place.ifBlank { suggestion.place }, names, festival)
                } else {
                    suggestion.title
                },
            )
        }
    }

    /** The festival whose window holds [date] (exact dates only), named in the user's language; null otherwise. */
    private fun festivalName(date: MemoryDate): String? {
        if (date.kind != DateKind.EXACT) return null
        val festival = festivals.primary(LocalDate.ofEpochDay(date.startEpochDay)) ?: return null
        return context.getString(festivalNameRes(festival.festival))
    }

    private fun suggestFaces(items: List<PickedMedia>) {
        viewModelScope.launch {
            if (!settingsRepository.current().faceSuggestions) return@launch
            val found = HashSet<Long>()
            for (p in items.filter { !it.isVideo }.take(FACE_SCAN_LIMIT)) {
                found += runCatching { faceSuggester.suggestPeople { context.contentResolver.openInputStream(p.uri) } }.getOrDefault(emptySet())
            }
            if (found.isNotEmpty()) _state.update { it.copy(suggestedPeople = it.suggestedPeople + found).withPeople() }
        }
    }

    fun setTitle(v: String) = _state.update { it.copy(title = v) }
    fun setDate(v: MemoryDate) { _state.update { it.copy(date = v, dateEdited = true, dateSource = DateSource.PHOTOS) }; refill() }
    fun togglePerson(id: Long) {
        _state.update { it.copy(selectedPeople = if (id in it.selectedPeople) it.selectedPeople - id else it.selectedPeople + id) }
        refill()
    }
    fun setPlace(v: String) { _state.update { it.copy(place = v) }; refill() }
    fun openPlace() = _state.update { it.copy(placeOpen = true) }
    fun setCover(index: Int) = _state.update { if (index in it.items.indices) it.copy(coverIndex = index) else it }
    fun remove(uri: Uri) {
        _state.update { s ->
            val items = s.items.filterNot { it.uri == uri }
            s.copy(items = items, coverIndex = s.coverIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)))
        }
        refill()
    }
    fun dismissSplit() = _state.update { it.copy(splitDismissed = true) }
    fun dismissRejected() = _state.update { it.copy(rejected = emptyList()) }

    fun setWords(v: String) = _state.update {
        it.copy(words = v, polished = if (v.isBlank()) null else PolishEngine.polish(v))
    }

    /** Hands the photos to Photo-first so each day becomes its own memory. */
    fun prepareSplit(): Boolean {
        val items = _state.value.items
        if (items.isEmpty()) return false
        QuickSaveInbox.offerForSplit(items.map { it.uri })
        return true
    }

    /** Call right before opening the Photo Picker from this screen (no PIN on return). */
    fun beforePicker() = lockState.allowExternalScreen(System.currentTimeMillis())

    fun save(onSaved: (Long) -> Unit) {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val now = SystemClock.elapsedRealtime()
            timer.saved(now)
            val optimize = settingsRepository.current().storeOptimizedCopies
            val memory = if (s.items.isEmpty()) {
                val polished = s.polished ?: PolishEngine.polish(s.words)
                Memory(
                    type = MemoryType.TEXT_ONLY, title = s.title.ifBlank { polished.title }, description = polished.description,
                    date = if (s.dateEdited) s.date else polished.date, place = s.place.ifBlank { polished.place }, mood = polished.mood, coverStyle = polished.coverStyle,
                )
            } else {
                val fallback = s.suggestedTitle.ifBlank { PolishEngine.titleFor(s.date, s.place, festival = festivalName(s.date)) }
                Memory(
                    type = MemoryType.EVENT, title = s.title.ifBlank { fallback }, description = s.words.trim(),
                    date = s.date, place = s.place.trim(),
                )
            }
            val id = memoryRepository.create(memory, s.selectedPeople.toList())
            val statId = captureStats.record(timer.stat(itemCount = s.items.size, createdAt = System.currentTimeMillis()))
            if (s.items.isNotEmpty()) {
                // The chosen cover goes first: the first imported photo becomes the memory's cover.
                val ordered = listOf(s.items[s.coverIndex]) + s.items.filterIndexed { i, _ -> i != s.coverIndex }
                val saveTimer = timer
                coordinator.enqueue(id, ordered, optimize) {
                    saveTimer.encrypted(SystemClock.elapsedRealtime())?.let { ms -> runCatching { captureStats.markEncrypted(statId, ms) } }
                }
            }
            _state.update { it.copy(saving = false) }
            onSaved(id)
        }
    }

    private companion object {
        const val TAKEN = "quickSaveTaken"
        /** Face suggestions look at the first few photos only; the rest are scanned after import as before. */
        const val FACE_SCAN_LIMIT = 10
    }
}
