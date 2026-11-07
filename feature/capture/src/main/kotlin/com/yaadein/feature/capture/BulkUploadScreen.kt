package com.yaadein.feature.capture

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.yaadein.core.ui.components.EmptyState
import com.yaadein.core.ui.illustrations.IllustrationKind
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.core.ui.util.memoryDateText
import com.yaadein.data.media.ImportCoordinator
import com.yaadein.data.media.ImportFailure
import com.yaadein.data.media.MediaImporter
import com.yaadein.data.media.PickedMedia
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.grouping.PeriodBucket
import com.yaadein.domain.grouping.PeriodFilter
import com.yaadein.domain.grouping.PeriodGrouper
import com.yaadein.domain.grouping.PickedItem
import com.yaadein.domain.grouping.SplitBy
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryDate
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.model.Person
import com.yaadein.domain.polish.PolishEngine
import com.yaadein.domain.repository.MemoryRepository
import com.yaadein.domain.repository.PersonRepository
import com.yaadein.domain.repository.SettingsRepository
import com.yaadein.feature.capture.components.DateChips
import com.yaadein.feature.capture.components.PeoplePicker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import com.yaadein.core.ui.R as CoreR

/** One memory the bulk upload will create: a year or a month of photos. [id] is "2024" or "2024-03". */
data class BulkDraft(
    val id: String,
    val title: String,
    val date: MemoryDate,
    val items: List<PickedMedia>,
)

data class BulkUploadUiState(
    val picking: Boolean = true,
    val inspecting: Boolean = false,
    val inspected: Int = 0,
    val toInspect: Int = 0,
    /** Every accepted photo/video picked so far (several picks add up). */
    val pickedCount: Int = 0,
    val splitBy: SplitBy = SplitBy.MONTH,
    val filter: PeriodFilter = PeriodFilter(),
    /** Years found in the picked photos, newest first: the choices for the year filter. */
    val availableYears: List<Int> = emptyList(),
    /** Memories that will be created, newest first. */
    val drafts: List<BulkDraft> = emptyList(),
    val filteredOut: Int = 0,
    /** The user removed at least one memory from the list (Reset brings them back). */
    val hasRemoved: Boolean = false,
    val undated: List<PickedMedia> = emptyList(),
    val includeUndated: Boolean = false,
    val undatedTitle: String = "",
    val undatedDate: MemoryDate = Dates.exact(LocalDate.now()),
    val people: List<Person> = emptyList(),
    /** People tagged on every memory created by this upload. */
    val selectedPeople: Set<Long> = emptySet(),
    val rejected: List<ImportFailure> = emptyList(),
    val saving: Boolean = false,
) {
    val memoryCount: Int get() = drafts.size + if (includeUndated && undated.isNotEmpty()) 1 else 0
}

/**
 * Bulk upload: pick a lot of photos at once, keep only the years/months asked for, and create one memory per year or
 * per month. The split is recomputed whenever the photos, the filter or the split changes; titles the user edited and
 * memories they removed are remembered by their period id so a change of filter does not undo them.
 */
@HiltViewModel
class BulkUploadViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
    personRepository: PersonRepository,
    private val settingsRepository: SettingsRepository,
    private val importer: MediaImporter,
    private val coordinator: ImportCoordinator,
) : ViewModel() {
    private val _state = MutableStateFlow(BulkUploadUiState())
    val state: StateFlow<BulkUploadUiState> = _state.asStateFlow()
    private val picked = LinkedHashMap<String, PickedMedia>()
    private val editedTitles = HashMap<String, String>()
    private val removed = HashSet<String>()

    init {
        viewModelScope.launch { personRepository.observeAll().collect { people -> _state.update { it.copy(people = people) } } }
    }

    fun onPicked(uris: List<Uri>) {
        val fresh = uris.filterNot { picked.containsKey(it.toString()) }
        if (fresh.isEmpty()) { _state.update { it.copy(picking = false) }; return }
        viewModelScope.launch {
            _state.update { it.copy(picking = false, inspecting = true, inspected = 0, toInspect = fresh.size) }
            val rejected = ArrayList<ImportFailure>()
            for (uri in fresh) {
                val p = importer.inspect(uri)
                val failure = if (p == null) ImportFailure.Unsupported else importer.checkLimit(p)
                if (failure != null) rejected += failure else picked[uri.toString()] = p!!
                _state.update { it.copy(inspected = it.inspected + 1) }
            }
            _state.update { it.copy(inspecting = false, rejected = it.rejected + rejected) }
            recompute()
        }
    }

    fun setSplitBy(splitBy: SplitBy) { _state.update { it.copy(splitBy = splitBy) }; recompute() }

    fun toggleYear(year: Int) {
        _state.update { s -> val y = s.filter.years; s.copy(filter = s.filter.copy(years = if (year in y) y - year else y + year)) }
        recompute()
    }

    fun toggleMonth(month: Month) {
        _state.update { s -> val m = s.filter.months; s.copy(filter = s.filter.copy(months = if (month in m) m - month else m + month)) }
        recompute()
    }

    /** Clears the year/month filter and brings back any memories the user removed. */
    fun reset() {
        removed.clear()
        _state.update { it.copy(filter = PeriodFilter()) }
        recompute()
    }

    fun setTitle(id: String, title: String) {
        editedTitles[id] = title
        _state.update { s -> s.copy(drafts = s.drafts.map { if (it.id == id) it.copy(title = title) else it }) }
    }

    fun remove(id: String) { removed += id; recompute() }

    fun setIncludeUndated(include: Boolean) = _state.update { it.copy(includeUndated = include) }
    fun setUndatedTitle(title: String) = _state.update { it.copy(undatedTitle = title) }
    fun setUndatedDate(date: MemoryDate) = _state.update { it.copy(undatedDate = date) }
    fun togglePerson(personId: Long) = _state.update { s ->
        s.copy(selectedPeople = if (personId in s.selectedPeople) s.selectedPeople - personId else s.selectedPeople + personId)
    }
    fun dismissRejected() = _state.update { it.copy(rejected = emptyList()) }

    private fun recompute() = _state.update { s ->
        val all = picked.values.toList()
        val items = all.map { PickedItem(it.uri.toString(), it.takenAtMillis, it.isVideo) }
        val split = PeriodGrouper.split(items, s.splitBy, s.filter)
        val drafts = split.buckets.map { toDraft(it) }.filterNot { it.id in removed }
        // Filter chips stay valid only for years that still exist in the picked photos.
        val years = PeriodGrouper.years(items)
        s.copy(
            pickedCount = all.size,
            availableYears = years,
            filter = s.filter.copy(years = s.filter.years.intersect(years.toSet())),
            drafts = drafts,
            filteredOut = split.filteredOut,
            hasRemoved = removed.isNotEmpty(),
            undated = split.undated.mapNotNull { picked[it.key] },
        )
    }

    private fun toDraft(bucket: PeriodBucket): BulkDraft {
        val ym = bucket.yearMonth
        val id = ym?.toString() ?: bucket.year.toString()
        val date = if (ym != null) Dates.month(ym) else Dates.year(bucket.year)
        val suggested = if (ym != null) "${ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${ym.year}" else bucket.year.toString()
        return BulkDraft(id = id, title = editedTitles[id] ?: suggested, date = date, items = bucket.items.mapNotNull { picked[it.key] })
    }

    fun saveAll(onDone: (firstId: Long?) -> Unit) {
        val s = _state.value
        if (s.memoryCount == 0 || s.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val optimize = settingsRepository.current().storeOptimizedCopies
            val toSave = buildList {
                addAll(s.drafts.map { Triple(it.title, it.date, it.items) })
                if (s.includeUndated && s.undated.isNotEmpty()) add(Triple(s.undatedTitle, s.undatedDate, s.undated))
            }
            var firstId: Long? = null
            for ((title, date, items) in toSave) {
                val polished = PolishEngine.polish(title.ifBlank { "memory" })
                val memory = Memory(
                    type = MemoryType.EVENT, title = title.ifBlank { polished.title }, description = "", date = date,
                    place = polished.place, mood = polished.mood, coverStyle = polished.coverStyle,
                )
                val id = memoryRepository.create(memory, s.selectedPeople.toList())
                if (firstId == null) firstId = id
                coordinator.enqueue(id, items, optimize)
            }
            _state.update { it.copy(saving = false) }
            onDone(firstId)
        }
    }
}

@Composable
fun BulkUploadRoute(onBack: () -> Unit, onDone: (Long?) -> Unit, onAddPerson: () -> Unit, modifier: Modifier = Modifier, viewModel: BulkUploadViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // No maxItems: the Photo Picker allows as many as the system permits; "Add more photos" adds further picks.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { viewModel.onPicked(it) }
    val launch = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
    LaunchedEffect(Unit) { if (state.picking) launch() }
    BulkUploadScreen(
        state = state,
        onBack = onBack,
        onPickMore = launch,
        onSplitBy = viewModel::setSplitBy,
        onToggleYear = viewModel::toggleYear,
        onToggleMonth = viewModel::toggleMonth,
        onReset = viewModel::reset,
        onTitle = viewModel::setTitle,
        onRemove = viewModel::remove,
        onIncludeUndated = viewModel::setIncludeUndated,
        onUndatedTitle = viewModel::setUndatedTitle,
        onUndatedDate = viewModel::setUndatedDate,
        onTogglePerson = viewModel::togglePerson,
        onAddPerson = onAddPerson,
        onDismissRejected = viewModel::dismissRejected,
        onSaveAll = { viewModel.saveAll(onDone) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkUploadScreen(
    state: BulkUploadUiState,
    onBack: () -> Unit,
    onPickMore: () -> Unit,
    onSplitBy: (SplitBy) -> Unit,
    onToggleYear: (Int) -> Unit,
    onToggleMonth: (Month) -> Unit,
    onReset: () -> Unit,
    onTitle: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onIncludeUndated: (Boolean) -> Unit,
    onUndatedTitle: (String) -> Unit,
    onUndatedDate: (MemoryDate) -> Unit,
    onTogglePerson: (Long) -> Unit,
    onAddPerson: () -> Unit,
    onDismissRejected: () -> Unit,
    onSaveAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = Yaadein.spacing
    val canSave = state.memoryCount > 0 && !state.saving && !state.inspecting
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.bulk_title), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, contentDescription = stringResource(CoreR.string.action_close)) } },
            actions = { TextButton(onClick = onSaveAll, enabled = canSave) { Text(stringResource(R.string.photos_save_all, state.memoryCount)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        when {
            state.inspecting -> Column(Modifier.fillMaxSize().padding(spacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
                Spacer(Modifier.height(spacing.md))
                Text(stringResource(R.string.photos_inspecting), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(spacing.sm))
                Text(stringResource(R.string.bulk_inspect_progress, state.inspected, state.toInspect), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.picking -> Box(Modifier.fillMaxSize())
            state.pickedCount == 0 -> EmptyState(
                illustration = IllustrationKind.PHOTO_STACK,
                title = stringResource(R.string.photos_empty_title),
                message = stringResource(R.string.bulk_empty_message),
                actionLabel = stringResource(R.string.photos_pick),
                onAction = onPickMore,
                modifier = Modifier.fillMaxSize(),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = spacing.gutter, end = spacing.gutter, bottom = spacing.xxxl),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                item(key = "options") {
                    SplitOptions(state = state, onSplitBy = onSplitBy, onToggleYear = onToggleYear, onToggleMonth = onToggleMonth, onReset = onReset, onPickMore = onPickMore)
                }
                item(key = "summary") { Summary(state) }
                items(state.drafts.size, key = { state.drafts[it].id }) { index ->
                    val d = state.drafts[index]
                    BulkDraftCard(draft = d, onTitle = { onTitle(d.id, it) }, onRemove = { onRemove(d.id) })
                }
                if (state.undated.isNotEmpty()) item(key = "undated") {
                    UndatedCard(state = state, onInclude = onIncludeUndated, onTitle = onUndatedTitle, onDate = onUndatedDate)
                }
                item(key = "people") {
                    PeoplePicker(
                        people = state.people, selected = state.selectedPeople, onToggle = onTogglePerson, onAddPerson = onAddPerson,
                        title = stringResource(R.string.bulk_people),
                    )
                }
                item(key = "save") {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Button(onClick = onSaveAll, enabled = canSave, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.photos_save_all, state.memoryCount)) }
                        if (state.saving) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
    if (state.rejected.isNotEmpty()) RejectedDialog(failures = state.rejected, onDismiss = onDismissRejected)
}

@Composable
private fun SplitOptions(
    state: BulkUploadUiState,
    onSplitBy: (SplitBy) -> Unit,
    onToggleYear: (Int) -> Unit,
    onToggleMonth: (Month) -> Unit,
    onReset: () -> Unit,
    onPickMore: () -> Unit,
) {
    val spacing = Yaadein.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                pluralStringResource(CoreR.plurals.media_count, state.pickedCount, state.pickedCount),
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onPickMore) { Text(stringResource(R.string.bulk_add_more)) }
        }

        Text(stringResource(R.string.bulk_split_label), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        val options = listOf(SplitBy.MONTH to R.string.bulk_split_month, SplitBy.YEAR to R.string.bulk_split_year)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = state.splitBy == value,
                    onClick = { onSplitBy(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) { Text(stringResource(label)) }
            }
        }

        if (state.availableYears.isNotEmpty()) {
            Spacer(Modifier.height(spacing.xs))
            Text(stringResource(R.string.bulk_filter_years), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                state.availableYears.forEach { year ->
                    FilterChip(selected = year in state.filter.years, onClick = { onToggleYear(year) }, label = { Text(year.toString()) })
                }
            }
            Text(stringResource(R.string.bulk_filter_months), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Month.entries.forEach { month ->
                    FilterChip(
                        selected = month in state.filter.months,
                        onClick = { onToggleMonth(month) },
                        label = { Text(month.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                    )
                }
            }
            Text(stringResource(R.string.bulk_filter_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!state.filter.isEmpty || state.hasRemoved) TextButton(onClick = onReset) { Text(stringResource(R.string.bulk_reset)) }
    }
}

@Composable
private fun Summary(state: BulkUploadUiState) {
    val spacing = Yaadein.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(
            pluralStringResource(R.plurals.bulk_will_create, state.memoryCount, state.memoryCount),
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
        )
        if (state.filteredOut > 0) {
            Text(pluralStringResource(R.plurals.bulk_filtered_out, state.filteredOut, state.filteredOut), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.drafts.isEmpty() && state.pickedCount > state.undated.size) {
            Text(stringResource(R.string.bulk_nothing_matches), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val MAX_THUMBS = 12

@Composable
private fun Thumbs(items: List<PickedMedia>) {
    val spacing = Yaadein.spacing
    val shown = items.take(MAX_THUMBS)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
        items(shown.size, key = { shown[it].uri.toString() }) { i ->
            AsyncImage(model = shown[i].uri, contentDescription = null, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(Yaadein.radius.sm)), contentScale = ContentScale.Crop)
        }
        if (items.size > shown.size) item(key = "more") {
            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.bulk_more_count, items.size - shown.size), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun BulkDraftCard(draft: BulkDraft, onTitle: (String) -> Unit, onRemove: () -> Unit) {
    val spacing = Yaadein.spacing
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest), shape = RoundedCornerShape(Yaadein.radius.lg)) {
        Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = memoryDateText(draft.date) + " · " + pluralStringResource(CoreR.plurals.media_count, draft.items.size, draft.items.size),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove) { Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.photos_remove_group)) }
            }
            Thumbs(draft.items)
            OutlinedTextField(value = draft.title, onValueChange = onTitle, label = { Text(stringResource(R.string.event_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun UndatedCard(state: BulkUploadUiState, onInclude: (Boolean) -> Unit, onTitle: (String) -> Unit, onDate: (MemoryDate) -> Unit) {
    val spacing = Yaadein.spacing
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(Yaadein.radius.lg)) {
        Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(pluralStringResource(R.plurals.bulk_undated, state.undated.size, state.undated.size), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.bulk_undated_sub), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.includeUndated, onCheckedChange = onInclude)
            }
            if (state.includeUndated) {
                Thumbs(state.undated)
                OutlinedTextField(value = state.undatedTitle, onValueChange = onTitle, label = { Text(stringResource(R.string.event_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                DateChips(value = state.undatedDate, onChange = onDate)
            }
        }
    }
}
