package com.yaadein.feature.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.yaadein.core.ui.components.PersonAvatar
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.model.Person
import com.yaadein.domain.repository.TimelineFilter

/** Left pane on tablets and foldables: people and quick filters that narrow the timeline. */
@Composable
fun TimelineSidebar(
    people: List<Person>,
    filter: TimelineFilter,
    onFilterChange: (TimelineFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = Yaadein.spacing
    Surface(modifier = modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = spacing.lg)) {
            item(key = "types") {
                Column(modifier = Modifier.padding(horizontal = spacing.lg)) {
                    Text(stringResource(R.string.sidebar_show), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(spacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        FilterChip(selected = filter.types.isEmpty(), onClick = { onFilterChange(filter.copy(types = emptySet())) }, label = { Text(stringResource(R.string.sidebar_all)) })
                        FilterChip(selected = MemoryType.EVENT in filter.types, onClick = { onFilterChange(filter.copy(types = toggle(filter.types, MemoryType.EVENT))) }, label = { Text(stringResource(R.string.sidebar_photos)) })
                        FilterChip(selected = MemoryType.TEXT_ONLY in filter.types, onClick = { onFilterChange(filter.copy(types = toggle(filter.types, MemoryType.TEXT_ONLY))) }, label = { Text(stringResource(R.string.sidebar_stories)) })
                    }
                    Spacer(Modifier.height(spacing.lg))
                    Text(stringResource(R.string.sidebar_people), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(spacing.sm))
                }
            }
            items(people, key = { it.id }) { person ->
                val selected = person.id in filter.personIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.sm, vertical = spacing.xxs)
                        .then(
                            if (selected) Modifier else Modifier,
                        )
                        .clickable(role = Role.Checkbox) {
                            onFilterChange(filter.copy(personIds = if (selected) filter.personIds - person.id else filter.personIds + person.id))
                        }
                        .semantics { this.selected = selected },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(Yaadein.radius.md),
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                            PersonAvatar(person = person, size = Yaadein.sizes.avatarMd)
                            Spacer(Modifier.width(spacing.md))
                            Text(person.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private fun toggle(set: Set<MemoryType>, type: MemoryType): Set<MemoryType> = if (type in set) set - type else set + type
