package com.yaadein.feature.onboarding

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaadein.data.media.ImportCoordinator
import com.yaadein.data.media.MediaImporter
import com.yaadein.data.media.PickedMedia
import com.yaadein.data.seed.DemoSeeder
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.grouping.PhotoGrouper
import com.yaadein.domain.grouping.PickedItem
import com.yaadein.domain.grouping.SuggestedGroup
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryDate
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.polish.PolishEngine
import com.yaadein.domain.repository.MemoryRepository
import com.yaadein.domain.repository.SettingsRepository
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

/** What the welcome screen is waiting for. */
enum class OnboardingBusy { IMPORTING, SAMPLE }

/** Where the welcome screen hands over to: the first story (photos were picked) or Home (sample family, or skipped). */
enum class OnboardingExit { STORY, HOME }

data class OnboardingUiState(
    val busy: OnboardingBusy? = null,
    val exit: OnboardingExit? = null,
    /** Photos were picked but none could be read or all were over the size limit. */
    val nothingAdded: Boolean = false,
)

/**
 * First run, photos first. Three ways out of the one welcome screen:
 * - pick photos: they are grouped into memories by day ([PhotoGrouper]), each memory row is written at once and the
 *   files are copied and encrypted in the background ([ImportCoordinator], copy-first), then the first story plays;
 * - see a sample family: the demo data, marked with `demoMode` so Home can label and clear it;
 * - skip: Home with its empty state.
 * No person form and no PIN before any of them: the vault is Keystore-protected until the user sets a lock.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    // Lazy on purpose: this ViewModel is created for the very first frame of a cold start, and the database, the vault
    // and the importer must not be built on the main thread before it (docs/PERFORMANCE.md, cold-start budget).
    private val lazyMemoryRepository: Lazy<MemoryRepository>,
    private val lazyImporter: Lazy<MediaImporter>,
    private val lazyCoordinator: Lazy<ImportCoordinator>,
    private val lazySeeder: Lazy<DemoSeeder>,
) : ViewModel() {
    private val memoryRepository get() = lazyMemoryRepository.get()
    private val importer get() = lazyImporter.get()
    private val coordinator get() = lazyCoordinator.get()
    private val seeder get() = lazySeeder.get()

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    /** The Photo Picker's result. An empty pick (cancelled) changes nothing. */
    fun onPicked(uris: List<Uri>) {
        if (uris.isEmpty() || _state.value.busy != null) return
        _state.update { it.copy(busy = OnboardingBusy.IMPORTING, nothingAdded = false) }
        viewModelScope.launch {
            val optimize = settings.current().storeOptimizedCopies
            withContext(Dispatchers.IO) { lazyImporter.get(); lazyCoordinator.get(); lazyMemoryRepository.get() }
            val accepted = ArrayList<PickedMedia>()
            for (uri in uris.distinct().take(MAX_ITEMS)) {
                val picked = importer.inspect(uri) ?: continue
                if (importer.checkLimit(picked, optimize) == null) accepted += picked
            }
            if (accepted.isEmpty()) {
                _state.update { it.copy(busy = null, nothingAdded = true) }
                return@launch
            }
            val byKey = accepted.associateBy { it.uri.toString() }
            val groups = PhotoGrouper.group(accepted.map { PickedItem(it.uri.toString(), it.takenAtMillis, it.isVideo) })
            val today = LocalDate.now()
            // Oldest first, so the newest memory is also the most recently created one.
            for (group in groups.asReversed()) {
                val date = dateOf(group, today)
                val id = memoryRepository.create(Memory(type = MemoryType.EVENT, title = PolishEngine.titleFor(date), date = date), emptyList())
                coordinator.enqueue(id, group.items.mapNotNull { byKey[it.key] }, optimize)
            }
            settings.update { it.copy(onboardingDone = true) }
            _state.update { it.copy(busy = null, exit = OnboardingExit.STORY) }
        }
    }

    fun seeSample() {
        if (_state.value.busy != null) return
        _state.update { it.copy(busy = OnboardingBusy.SAMPLE, nothingAdded = false) }
        viewModelScope.launch {
            // The sample photos are drawn on the phone: keep that off the main thread.
            withContext(Dispatchers.Default) { lazySeeder.get().seed() }
            settings.update { it.copy(onboardingDone = true, demoMode = true) }
            _state.update { it.copy(busy = null, exit = OnboardingExit.HOME) }
        }
    }

    fun skip() {
        if (_state.value.busy != null) return
        viewModelScope.launch {
            settings.update { it.copy(onboardingDone = true) }
            _state.update { it.copy(exit = OnboardingExit.HOME) }
        }
    }

    companion object {
        /** The welcome screen suggests 10 to 20 photos; more are accepted up to this many. */
        const val MAX_ITEMS = 60

        /** One day, a run of days, or today for photos that carry no date. */
        internal fun dateOf(group: SuggestedGroup, today: LocalDate): MemoryDate {
            val start = group.startDate ?: return Dates.exact(today)
            return if (group.isMultiDay) Dates.exact(start, group.endDate) else Dates.exact(start)
        }
    }
}
