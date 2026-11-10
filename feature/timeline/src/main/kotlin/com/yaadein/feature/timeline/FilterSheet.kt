package com.yaadein.feature.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.yaadein.core.ui.components.PersonAvatar
import com.yaadein.core.ui.components.festivalName
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.model.Group
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.model.Person
import com.yaadein.domain.relive.Festival
import com.yaadein.domain.repository.TimelineFilter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.yaadein.core.ui.R as CoreR

/** Bottom sheet with every timeline filter: people, groups, place, festival, date range, type. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    filter: TimelineFilter,
    people: List<Person>,
    groups: List<Group>,
    places: List<String>,
    onApply: (TimelineFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = Yaadein.spacing
    var draft by remember(filter) { mutableStateOf(filter) }
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }
    val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = spacing.gutter).padding(bottom = spacing.xxl), verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.timeline_filters), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
                TextButton(onClick = { draft = TimelineFilter(query = draft.query) }) { Text(stringResource(R.string.timeline_clear_filters)) }
            }
            Section(stringResource(R.string.filter_people)) {
                people.forEach { p ->
                    FilterChip(
                        selected = p.id in draft.personIds,
                        onClick = { draft = draft.copy(personIds = if (p.id in draft.personIds) draft.personIds - p.id else draft.personIds + p.id) },
                        label = { Text(p.displayName) },
                        leadingIcon = { PersonAvatar(person = p, size = Yaadein.sizes.iconLg) },
                    )
                }
            }
            if (draft.personIds.size >= 2) {
                FilterChip(selected = draft.requireAllPeople, onClick = { draft = draft.copy(requireAllPeople = !draft.requireAllPeople) }, label = { Text(stringResource(R.string.filter_all_together)) })
            }
            if (groups.isNotEmpty()) {
                Section(stringResource(R.string.filter_groups)) {
                    groups.forEach { g ->
                        FilterChip(selected = draft.groupId == g.id, onClick = { draft = draft.copy(groupId = if (draft.groupId == g.id) null else g.id) }, label = { Text("${g.emoji} ${g.name}") })
                    }
                }
            }
            if (places.isNotEmpty()) {
                Section(stringResource(R.string.filter_places)) {
                    places.take(24).forEach { place ->
                        FilterChip(selected = draft.place == place, onClick = { draft = draft.copy(place = if (draft.place == place) null else place) }, label = { Text(place) })
                    }
                }
            }
            Section(stringResource(R.string.filter_festival)) {
                Festival.entries.forEach { f ->
                    FilterChip(selected = draft.festival == f, onClick = { draft = draft.copy(festival = if (draft.festival == f) null else f) }, label = { Text(festivalName(f)) })
                }
            }
            Section(stringResource(R.string.filter_type)) {
                FilterChip(selected = MemoryType.EVENT in draft.types, onClick = { draft = draft.copy(types = toggle(draft.types, MemoryType.EVENT)) }, label = { Text(stringResource(R.string.sidebar_photos)) })
                FilterChip(selected = MemoryType.TEXT_ONLY in draft.types, onClick = { draft = draft.copy(types = toggle(draft.types, MemoryType.TEXT_ONLY)) }, label = { Text(stringResource(R.string.sidebar_stories)) })
                FilterChip(selected = MemoryType.QUICK in draft.types, onClick = { draft = draft.copy(types = toggle(draft.types, MemoryType.QUICK)) }, label = { Text(stringResource(R.string.filter_quick)) })
            }
            Section(stringResource(R.string.filter_dates)) {
                FilterChip(selected = draft.fromEpochDay != null, onClick = { pickFrom = true }, label = { Text(draft.fromEpochDay?.let { LocalDate.ofEpochDay(it).format(fmt) } ?: stringResource(R.string.filter_from)) })
                FilterChip(selected = draft.toEpochDay != null, onClick = { pickTo = true }, label = { Text(draft.toEpochDay?.let { LocalDate.ofEpochDay(it).format(fmt) } ?: stringResource(R.string.filter_to)) })
                if (draft.fromEpochDay != null || draft.toEpochDay != null) {
                    TextButton(onClick = { draft = draft.copy(fromEpochDay = null, toEpochDay = null) }) { Text(stringResource(CoreR.string.action_delete)) }
                }
            }
            Button(onClick = { onApply(draft); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.filter_apply)) }
            Spacer(Modifier.height(spacing.lg))
        }
    }
    if (pickFrom || pickTo) {
        val initial = (if (pickFrom) draft.fromEpochDay else draft.toEpochDay)?.let { it * 86_400_000L }
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { pickFrom = false; pickTo = false },
            confirmButton = {
                TextButton(onClick = {
                    val day = state.selectedDateMillis?.let { it / 86_400_000L }
                    draft = if (pickFrom) draft.copy(fromEpochDay = day) else draft.copy(toEpochDay = day)
                    pickFrom = false; pickTo = false
                }) { Text(stringResource(CoreR.string.action_done)) }
            },
            dismissButton = { TextButton(onClick = { pickFrom = false; pickTo = false }) { Text(stringResource(CoreR.string.action_cancel)) } },
        ) { DatePicker(state = state) }
    }
}

private fun toggle(set: Set<MemoryType>, t: MemoryType) = if (t in set) set - t else set + t

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val spacing = Yaadein.spacing
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(spacing.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xs)) { content() }
    }
}
