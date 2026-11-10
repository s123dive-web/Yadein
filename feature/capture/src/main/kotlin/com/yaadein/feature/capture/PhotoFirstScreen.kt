package com.yaadein.feature.capture

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.yaadein.domain.grouping.PhotoGrouper
import com.yaadein.domain.grouping.PickedItem
import com.yaadein.domain.grouping.SuggestedGroup
import com.yaadein.domain.model.DateKind
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
import java.util.Locale
import javax.inject.Inject
import com.yaadein.core.ui.R as CoreR

/** One suggested memory built from a run of photos. */
data class DraftMemory(
    val key: Long,
    val title: String,
    val date: MemoryDate,
    val items: List<PickedMedia>,
    val selectedPeople: Set<Long> = emptySet(),
)

data class PhotoFirstUiState(
    val picking: Boolean = true,
    val inspecting: Boolean = false,
    val drafts: List<DraftMemory> = emptyList(),
    val rejected: List<ImportFailure> = emptyList(),
    val people: List<Person> = emptyList(),
    val saving: Boolean = false,
    val savedCount: Int = 0,
)

@HiltViewModel
class PhotoFirstViewModel @Inject constructor(
    private val memoryRepository: MemoryRepository,
    personRepository: PersonRepository,
    private val settingsRepository: SettingsRepository,
    private val importer: MediaImporter,
    private val coordinator: ImportCoordinator,
) : ViewModel() {
    private val _state = MutableStateFlow(PhotoFirstUiState())
    val state: StateFlow<PhotoFirstUiState> = _state.asStateFlow()
    private var nextKey = 1L
    private val byKey = HashMap<String, PickedMedia>()

    init {
        viewModelScope.launch { personRepository.observeAll().collect { people -> _state.update { it.copy(people = people) } } }
        // Quick Save's "Looks like N events, split?" hands its photos over instead of opening the picker again.
        QuickSaveInbox.takeForSplit()?.let { uris -> _state.update { it.copy(picking = false) }; onPicked(uris) }
    }

    /**
     * First pick: the photos are grouped into drafts. "Pick more": photos already on the screen are skipped (the strip is
     * keyed by URI), the new ones are grouped, and each new group folds into the existing draft whose days it touches
     * (title, date range, people and any merge/split the user did are kept) or becomes a new draft in date order.
     */
    fun onPicked(uris: List<Uri>) {
        val fresh = PickMerging.fresh(uris, byKey.keys) { it.toString() }
        if (fresh.isEmpty()) { _state.update { it.copy(picking = false) }; return }
        viewModelScope.launch {
            _state.update { it.copy(picking = false, inspecting = true) }
            val optimize = settingsRepository.current().storeOptimizedCopies
            val accepted = ArrayList<PickedMedia>()
            val rejected = ArrayList<ImportFailure>()
            for (uri in fresh) {
                val p = importer.inspect(uri)
                if (p == null) { rejected += ImportFailure.Unsupported; continue }
                val limit = importer.checkLimit(p, optimize)
                if (limit != null) rejected += limit else accepted += p
            }
            accepted.forEach { byKey[it.uri.toString()] = it }
            val groups = PhotoGrouper.group(accepted.map { PickedItem(it.uri.toString(), it.takenAtMillis, it.isVideo) })
            _state.update { s ->
                var drafts = s.drafts
                for (group in groups) drafts = addGroup(drafts, group)
                s.copy(inspecting = false, drafts = drafts, rejected = s.rejected + rejected)
            }
        }
    }

    /** Folds [group] into the draft whose days it touches, else inserts it as a new draft (newest first, undated last). */
    private fun addGroup(drafts: List<DraftMemory>, group: SuggestedGroup): List<DraftMemory> {
        val start = group.startDate?.toEpochDay()
        val end = group.endDate?.toEpochDay() ?: start
        if (start != null && end != null) {
            val ranges = drafts.map { d -> if (d.date.kind == DateKind.EXACT) d.date.startEpochDay..(d.date.endEpochDay ?: d.date.startEpochDay) else null }
            val at = PickMerging.foldTarget(ranges, start, end)
            if (at >= 0) {
                val target = drafts[at]
                val items = (target.items + group.items.mapNotNull { byKey[it.key] }).sortedBy { it.takenAtMillis ?: Long.MAX_VALUE }
                val first = minOf(target.date.startEpochDay, start)
                val last = maxOf(target.date.endEpochDay ?: target.date.startEpochDay, end)
                val date = Dates.exact(LocalDate.ofEpochDay(first), LocalDate.ofEpochDay(last))
                return drafts.toMutableList().apply { set(at, target.copy(items = items, date = date)) }
            }
        }
        val draft = toDraft(group)
        val index = if (start == null) drafts.size else drafts.indexOfFirst { it.date.kind == DateKind.EXACT && it.date.startEpochDay < start }.let { if (it < 0) drafts.size else it }
        return drafts.toMutableList().apply { add(index, draft) }
    }

    private fun toDraft(group: SuggestedGroup): DraftMemory {
        val date = when {
            group.startDate == null -> Dates.exact(LocalDate.now())
            group.isMultiDay -> Dates.exact(group.startDate!!, group.endDate)
            else -> Dates.exact(group.startDate!!)
        }
        val title = suggestedTitle(group)
        return DraftMemory(key = nextKey++, title = title, date = date, items = group.items.mapNotNull { byKey[it.key] })
    }

    private fun suggestedTitle(group: SuggestedGroup): String {
        val start = group.startDate ?: return ""
        val month = start.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
        return if (group.isMultiDay) "$month ${start.year}" else "${start.dayOfMonth} $month ${start.year}"
    }

    fun setTitle(key: Long, title: String) = updateDraft(key) { it.copy(title = title) }
    fun setDate(key: Long, date: MemoryDate) = updateDraft(key) { it.copy(date = date) }
    fun togglePerson(key: Long, personId: Long) = updateDraft(key) { it.copy(selectedPeople = if (personId in it.selectedPeople) it.selectedPeople - personId else it.selectedPeople + personId) }
    fun remove(key: Long) {
        // Forget the removed photos so that picking one of them again brings it back (outside update {}, which may retry).
        _state.value.drafts.firstOrNull { it.key == key }?.items?.forEach { byKey.remove(it.uri.toString()) }
        _state.update { s -> s.copy(drafts = s.drafts.filterNot { d -> d.key == key }) }
    }
    fun dismissRejected() = _state.update { it.copy(rejected = emptyList()) }

    /** Merges a draft into the previous (newer) one. */
    fun mergeWithPrevious(key: Long) = _state.update { s ->
        val index = s.drafts.indexOfFirst { it.key == key }
        if (index <= 0) return@update s
        val prev = s.drafts[index - 1]; val cur = s.drafts[index]
        val items = (prev.items + cur.items).sortedBy { it.takenAtMillis ?: Long.MAX_VALUE }
        val days = items.mapNotNull { it.takenAtMillis }.map { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
        val date = if (days.isEmpty()) prev.date else Dates.exact(days.min(), days.max())
        val merged = prev.copy(items = items, date = date, selectedPeople = prev.selectedPeople + cur.selectedPeople)
        s.copy(drafts = s.drafts.toMutableList().apply { set(index - 1, merged); removeAt(index) })
    }

    /** Splits a draft in half (by time). */
    fun split(key: Long) = _state.update { s ->
        val index = s.drafts.indexOfFirst { it.key == key }
        if (index < 0) return@update s
        val cur = s.drafts[index]
        if (cur.items.size < 2) return@update s
        val half = cur.items.size / 2
        val a = cur.items.take(half); val b = cur.items.drop(half)
        fun dateOf(items: List<PickedMedia>, fallback: MemoryDate): MemoryDate {
            val days = items.mapNotNull { it.takenAtMillis }.map { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
            return if (days.isEmpty()) fallback else Dates.exact(days.min(), days.max())
        }
        val first = cur.copy(items = a, date = dateOf(a, cur.date))
        val second = cur.copy(key = nextKey++, items = b, date = dateOf(b, cur.date), title = cur.title)
        s.copy(drafts = s.drafts.toMutableList().apply { set(index, first); add(index + 1, second) })
    }

    private fun updateDraft(key: Long, f: (DraftMemory) -> DraftMemory) = _state.update { s -> s.copy(drafts = s.drafts.map { if (it.key == key) f(it) else it }) }

    fun saveAll(onDone: (firstId: Long?) -> Unit) {
        val s = _state.value
        if (s.drafts.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val optimize = settingsRepository.current().storeOptimizedCopies
            var firstId: Long? = null
            for (d in s.drafts) {
                val polished = PolishEngine.polish(d.title.ifBlank { "memory" })
                val memory = Memory(
                    type = MemoryType.EVENT, title = d.title.ifBlank { polished.title }, description = "", date = d.date,
                    place = polished.place, mood = polished.mood, coverStyle = polished.coverStyle,
                )
                val id = memoryRepository.create(memory, d.selectedPeople.toList())
                if (firstId == null) firstId = id
                coordinator.enqueue(id, d.items, optimize)
            }
            _state.update { it.copy(saving = false, savedCount = s.drafts.size) }
            onDone(firstId)
        }
    }
}

@Composable
fun PhotoFirstRoute(onBack: () -> Unit, onDone: (Long?) -> Unit, onAddPerson: () -> Unit, modifier: Modifier = Modifier, viewModel: PhotoFirstViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = 100)) { viewModel.onPicked(it) }
    LaunchedEffect(Unit) {
        if (state.picking) picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }
    PhotoFirstScreen(
        state = state,
        onBack = onBack,
        onPickMore = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
        onTitle = viewModel::setTitle,
        onDate = viewModel::setDate,
        onTogglePerson = viewModel::togglePerson,
        onAddPerson = onAddPerson,
        onMerge = viewModel::mergeWithPrevious,
        onSplit = viewModel::split,
        onRemove = viewModel::remove,
        onDismissRejected = viewModel::dismissRejected,
        onSaveAll = { viewModel.saveAll(onDone) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoFirstScreen(
    state: PhotoFirstUiState,
    onBack: () -> Unit,
    onPickMore: () -> Unit,
    onTitle: (Long, String) -> Unit,
    onDate: (Long, MemoryDate) -> Unit,
    onTogglePerson: (Long, Long) -> Unit,
    onAddPerson: () -> Unit,
    onMerge: (Long) -> Unit,
    onSplit: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onDismissRejected: () -> Unit,
    onSaveAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.photos_title), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, contentDescription = stringResource(CoreR.string.action_close)) } },
            actions = { TextButton(onClick = onSaveAll, enabled = state.drafts.isNotEmpty() && !state.saving) { Text(stringResource(R.string.photos_save_all, state.drafts.size)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        when {
            state.inspecting -> Column(Modifier.fillMaxSize().padding(spacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
                Spacer(Modifier.height(spacing.md))
                Text(stringResource(R.string.photos_inspecting), style = MaterialTheme.typography.bodyLarge)
            }
            state.drafts.isEmpty() && !state.picking -> EmptyState(
                illustration = IllustrationKind.PHOTO_STACK,
                title = stringResource(R.string.photos_empty_title),
                message = stringResource(R.string.photos_empty_message),
                actionLabel = stringResource(R.string.photos_pick),
                onAction = onPickMore,
                modifier = Modifier.fillMaxSize(),
            )
            state.picking -> Box(Modifier.fillMaxSize())
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = spacing.gutter, end = spacing.gutter, bottom = spacing.xxxl),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                item(key = "intro") {
                    Text(pluralStringResource(R.plurals.photos_grouped, state.drafts.size, state.drafts.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(state.drafts.size, key = { state.drafts[it].key }) { index ->
                    val d = state.drafts[index]
                    DraftCard(
                        draft = d, people = state.people, canMerge = index > 0,
                        onTitle = { onTitle(d.key, it) }, onDate = { onDate(d.key, it) }, onTogglePerson = { onTogglePerson(d.key, it) },
                        onAddPerson = onAddPerson, onMerge = { onMerge(d.key) }, onSplit = { onSplit(d.key) }, onRemove = { onRemove(d.key) },
                    )
                }
                item(key = "actions") {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        TextButton(onClick = onPickMore) { Text(stringResource(R.string.photos_pick_more)) }
                        Button(onClick = onSaveAll, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.photos_save_all, state.drafts.size)) }
                        if (state.saving) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
    if (state.rejected.isNotEmpty()) RejectedDialog(failures = state.rejected, onDismiss = onDismissRejected)
}

@Composable
private fun DraftCard(
    draft: DraftMemory, people: List<Person>, canMerge: Boolean,
    onTitle: (String) -> Unit, onDate: (MemoryDate) -> Unit, onTogglePerson: (Long) -> Unit, onAddPerson: () -> Unit,
    onMerge: () -> Unit, onSplit: () -> Unit, onRemove: () -> Unit,
) {
    val spacing = Yaadein.spacing
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest), shape = RoundedCornerShape(Yaadein.radius.lg)) {
        Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                items(draft.items.size, key = { draft.items[it].uri.toString() }) { i ->
                    AsyncImage(model = draft.items[i].uri, contentDescription = null, modifier = Modifier.size(84.dp).clip(RoundedCornerShape(Yaadein.radius.sm)), contentScale = ContentScale.Crop)
                }
            }
            Text(
                text = pluralStringResource(CoreR.plurals.media_count, draft.items.size, draft.items.size) + " · " + memoryDateText(draft.date),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            )
            OutlinedTextField(value = draft.title, onValueChange = onTitle, label = { Text(stringResource(R.string.event_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            DateChips(value = draft.date, onChange = onDate)
            PeoplePicker(people = people, selected = draft.selectedPeople, onToggle = onTogglePerson, onAddPerson = onAddPerson)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                if (canMerge) TextButton(onClick = onMerge) { Icon(Icons.Rounded.CallMerge, contentDescription = null); Spacer(Modifier.size(spacing.xs)); Text(stringResource(R.string.photos_merge_up)) }
                if (draft.items.size > 1) TextButton(onClick = onSplit) { Icon(Icons.Rounded.CallSplit, contentDescription = null); Spacer(Modifier.size(spacing.xs)); Text(stringResource(R.string.photos_split)) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onRemove) { Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.photos_remove_group)) }
            }
        }
    }
}
