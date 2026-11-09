package com.yaadein.feature.people

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.yaadein.core.ui.util.PagedItems
import com.yaadein.core.ui.util.asPagedItems
import com.yaadein.core.ui.util.keyAt
import com.yaadein.domain.dates.Dates
import com.yaadein.core.ui.components.EmptyState
import com.yaadein.core.ui.components.MemoryCardView
import com.yaadein.core.ui.components.PersonAvatar
import com.yaadein.core.ui.illustrations.IllustrationKind
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.PersonStory
import com.yaadein.domain.model.Relation
import com.yaadein.core.ui.R as CoreR

@Composable
fun PersonDetailRoute(
    onBack: () -> Unit,
    onOpenMemory: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onTogether: (Long) -> Unit,
    onYearSummary: (Long, Int) -> Unit,
    onRecap: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PersonDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val memories = viewModel.memories.collectAsLazyPagingItems().asPagedItems()
    PersonDetailScreen(
        state = state,
        memories = memories,
        onBack = onBack,
        onOpenMemory = onOpenMemory,
        onEdit = { onEdit(viewModel.personId) },
        onTogether = { onTogether(viewModel.personId) },
        onYearSummary = { year -> onYearSummary(viewModel.personId, year) },
        onRecap = { onRecap(viewModel.personId) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonDetailScreen(
    state: PersonDetailUiState,
    memories: PagedItems<MemoryCard>,
    onBack: () -> Unit,
    onOpenMemory: (Long) -> Unit,
    onEdit: () -> Unit,
    onTogether: () -> Unit,
    onYearSummary: (Int) -> Unit,
    onRecap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = Yaadein.spacing
    val story = state.story
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(CoreR.string.action_back))
                }
            },
            actions = {
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, contentDescription = stringResource(CoreR.string.action_edit)) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        if (state.loading || story == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { if (state.loading) CircularProgressIndicator() }
            return
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = spacing.gutter, end = spacing.gutter, bottom = spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item(key = "header") { StoryHeader(story = story, onTogether = onTogether, onRecap = onRecap) }
            if (state.years.size > 1) {
                item(key = "years") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        items(state.years.size, key = { state.years[it] }) { i ->
                            val year = state.years[i]
                            AssistChip(onClick = { onYearSummary(year) }, label = { Text(stringResource(R.string.person_year_with, year, story.person.displayName)) })
                        }
                    }
                }
            }
            if (memories.itemCount == 0) {
                item(key = "empty") {
                    EmptyState(
                        illustration = IllustrationKind.BOX_CLOSED,
                        title = stringResource(R.string.person_empty_title, story.person.displayName),
                        message = stringResource(R.string.person_empty_message),
                    )
                }
            }
            items(count = memories.itemCount, key = { index -> memories.keyAt(index) { it.memory.id } }) { index ->
                memories[index]?.let { card -> MemoryCardView(card = card, onClick = { onOpenMemory(card.memory.id) }, compact = true) }
            }
        }
    }
}

@Composable
private fun StoryHeader(story: PersonStory, onTogether: () -> Unit, onRecap: () -> Unit) {
    val spacing = Yaadein.spacing
    val person = story.person
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        PersonAvatar(person = person, size = Yaadein.sizes.avatarXl)
        Spacer(Modifier.height(spacing.md))
        Text(text = person.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        val relationText = when (person.relation) {
            Relation.FAMILY -> stringResource(CoreR.string.relation_family)
            Relation.FRIEND -> stringResource(CoreR.string.relation_friend)
            Relation.COLLEAGUE -> stringResource(CoreR.string.relation_colleague)
            Relation.OTHER -> stringResource(CoreR.string.relation_other)
        }
        Text(
            text = listOfNotNull(person.nickname?.takeIf { it.isNotBlank() }?.let { "“$it”" }, relationText).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), modifier = Modifier.fillMaxWidth()) {
            StatCard(
                value = story.memoryCount.toString(),
                label = stringResource(R.string.person_stat_memories),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = story.firstMemory?.let { Dates.label(it.date).text } ?: "—",
                label = stringResource(R.string.person_first_memory),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = story.lastMemory?.let { Dates.label(it.date).text } ?: "—",
                label = stringResource(R.string.person_last_memory),
                modifier = Modifier.weight(1f),
            )
        }
        story.daysToBirthday?.let { days ->
            Spacer(Modifier.height(spacing.sm))
            Surface(
                shape = RoundedCornerShape(Yaadein.radius.md),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Icon(Icons.Rounded.Cake, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        text = when (days) {
                            0 -> stringResource(R.string.person_birthday_today)
                            1 -> stringResource(R.string.person_birthday_tomorrow)
                            else -> stringResource(R.string.person_birthday_in_days, days)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        if (story.groups.isNotEmpty()) {
            Spacer(Modifier.height(spacing.sm))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                items(story.groups.size, key = { story.groups[it].id }) { i ->
                    val g = story.groups[i]
                    AssistChip(onClick = {}, label = { Text("${g.emoji} ${g.name}") })
                }
            }
        }
        Spacer(Modifier.height(spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            AssistChip(onClick = onTogether, label = { Text(stringResource(R.string.person_together)) }, leadingIcon = { Icon(Icons.Rounded.People, contentDescription = null) })
            AssistChip(onClick = onRecap, label = { Text(stringResource(R.string.person_recap)) }, leadingIcon = { Icon(Icons.Rounded.Movie, contentDescription = null) })
        }
        Spacer(Modifier.height(spacing.sm))
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    val spacing = Yaadein.spacing
    Surface(modifier = modifier, shape = RoundedCornerShape(Yaadein.radius.md), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(modifier = Modifier.padding(spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
