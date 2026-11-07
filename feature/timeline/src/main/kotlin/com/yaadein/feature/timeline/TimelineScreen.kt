package com.yaadein.feature.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.yaadein.core.ui.util.PagedItems
import com.yaadein.core.ui.util.asPagedItems
import com.yaadein.core.ui.util.keyAt
import com.yaadein.core.ui.util.currentLocale
import com.yaadein.core.ui.components.EmptyState
import com.yaadein.core.ui.components.MemoryCardView
import com.yaadein.domain.settings.TimelineLayout
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.automirrored.rounded.ViewList
import com.yaadein.core.ui.illustrations.IllustrationKind
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.repository.TimelineFilter
import kotlinx.coroutines.launch
import com.yaadein.core.ui.R as CoreR

@Composable
fun TimelineRoute(
    onOpenMemory: (Long) -> Unit,
    onCreate: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    twoPane: Boolean = false,
    onAbout: () -> Unit = {},
    /** Opened from Relive → Places: show only memories from this place. */
    placeFilter: String? = null,
    onPlaceFilterConsumed: () -> Unit = {},
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(placeFilter) {
        if (placeFilter != null) {
            viewModel.setFilter(com.yaadein.domain.repository.TimelineFilter(place = placeFilter))
            onPlaceFilterConsumed()
        }
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsLazyPagingItems().asPagedItems()
    if (twoPane) {
        Row(modifier = modifier.fillMaxSize()) {
            TimelineSidebar(
                people = state.people,
                filter = state.filter,
                onFilterChange = viewModel::setFilter,
                modifier = Modifier.width(Yaadein.sizes.twoPaneSidebarWidth),
            )
            TimelineScreen(
                state = state,
                items = items,
                onOpenMemory = onOpenMemory,
                onCreate = onCreate,
                onQueryChange = viewModel::setQuery,
                onSearchActive = viewModel::setSearchActive,
                onFilterChange = viewModel::setFilter,
                onClearFilter = viewModel::clearFilter,
                onOpenFilters = viewModel::openFilters,
                onCloseFilters = viewModel::closeFilters,
                onAbout = onAbout,
                contentPadding = contentPadding,
                modifier = Modifier.weight(1f),
                layout = layout,
                onCycleLayout = viewModel::cycleLayout,
            )
        }
        return
    }
    TimelineScreen(
        state = state,
        items = items,
        onOpenMemory = onOpenMemory,
        onCreate = onCreate,
        onQueryChange = viewModel::setQuery,
        onSearchActive = viewModel::setSearchActive,
        onFilterChange = viewModel::setFilter,
        onClearFilter = viewModel::clearFilter,
        onOpenFilters = viewModel::openFilters,
        onCloseFilters = viewModel::closeFilters,
        onAbout = onAbout,
        contentPadding = contentPadding,
        modifier = modifier,
        layout = layout,
        onCycleLayout = viewModel::cycleLayout,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    state: TimelineUiState,
    items: PagedItems<TimelineItem>,
    onOpenMemory: (Long) -> Unit,
    onCreate: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchActive: (Boolean) -> Unit,
    onFilterChange: (TimelineFilter) -> Unit,
    onClearFilter: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onOpenFilters: () -> Unit = {},
    onCloseFilters: () -> Unit = {},
    onAbout: () -> Unit = {},
    layout: TimelineLayout = TimelineLayout.CARDS,
    onCycleLayout: () -> Unit = {},
) {
    val spacing = Yaadein.spacing
    val scope = rememberCoroutineScope()
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.timeline_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
            },
            actions = {
                IconButton(onClick = onCycleLayout) {
                    Icon(
                        imageVector = when (layout) {
                            TimelineLayout.CARDS -> Icons.Rounded.ViewAgenda
                            TimelineLayout.COMPACT -> Icons.AutoMirrored.Rounded.ViewList
                            TimelineLayout.GRID -> Icons.Rounded.GridView
                        },
                        contentDescription = stringResource(R.string.timeline_layout, stringResource(
                            when (layout) {
                                TimelineLayout.CARDS -> R.string.timeline_layout_cards
                                TimelineLayout.COMPACT -> R.string.timeline_layout_compact
                                TimelineLayout.GRID -> R.string.timeline_layout_grid
                            },
                        )),
                    )
                }
                IconButton(onClick = { onSearchActive(!state.searchActive) }) {
                    Icon(
                        imageVector = if (state.searchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = stringResource(if (state.searchActive) CoreR.string.action_close else R.string.timeline_search),
                    )
                }
                FilterButton(active = !state.filter.isEmpty, onClick = onOpenFilters)
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        AnimatedVisibility(visible = state.searchActive, enter = fadeIn(), exit = fadeOut()) {
            OutlinedTextField(
                value = state.filter.query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.gutter, vertical = spacing.sm),
                placeholder = { Text(stringResource(R.string.timeline_search_hint)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            )
        }
        ActiveFilterRow(state = state, onClearFilter = onClearFilter)
        val refreshing = items.isRefreshing
        when {
            refreshing && items.itemCount == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            items.itemCount == 0 && state.totalMemories == 0 -> EmptyState(
                illustration = IllustrationKind.PHOTO_STACK,
                title = stringResource(R.string.timeline_empty_title),
                message = stringResource(R.string.timeline_empty_message),
                actionLabel = stringResource(R.string.timeline_empty_action),
                onAction = onCreate,
                modifier = Modifier.fillMaxSize().padding(contentPadding),
            )
            items.itemCount == 0 -> EmptyState(
                illustration = IllustrationKind.SEARCH_GLASS,
                title = stringResource(R.string.timeline_no_results_title),
                message = stringResource(R.string.timeline_no_results_message),
                actionLabel = stringResource(R.string.timeline_clear_filters),
                onAction = {
                    onClearFilter(); onSearchActive(false)
                    scope.launch { listState.animateScrollToItem(0) }
                },
                modifier = Modifier.fillMaxSize().padding(contentPadding),
            )
            else -> when (layout) {
                TimelineLayout.GRID -> TimelineGrid(items = items, onOpenMemory = onOpenMemory, contentPadding = contentPadding)
                else -> TimelineList(items = items, onOpenMemory = onOpenMemory, contentPadding = contentPadding, listState = listState, dense = layout == TimelineLayout.COMPACT)
            }
        }
    }
    if (state.filterSheetOpen) {
        FilterSheet(filter = state.filter, people = state.people, groups = state.groups, places = state.places, onApply = onFilterChange, onDismiss = onCloseFilters)
    }
}

@Composable
private fun FilterButton(active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Box {
            Icon(imageVector = Icons.Rounded.FilterList, contentDescription = stringResource(R.string.timeline_filters))
            if (active) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary),
                )
            }
        }
    }
}

@Composable
private fun ActiveFilterRow(state: TimelineUiState, onClearFilter: () -> Unit) {
    val f = state.filter
    if (f.isEmpty || (f.query.isNotBlank() && f.personIds.isEmpty() && f.groupId == null && f.place == null && f.types.isEmpty() && f.fromEpochDay == null)) return
    val spacing = Yaadein.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.gutter, vertical = spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val people = state.people.filter { it.id in f.personIds }
        people.take(3).forEach { person ->
            FilterChip(selected = true, onClick = onClearFilter, label = { Text(person.displayName) })
        }
        val place = f.place
        if (place != null) FilterChip(selected = true, onClick = onClearFilter, label = { Text(place) })
        if (f.types.isNotEmpty() || f.fromEpochDay != null || f.groupId != null) {
            FilterChip(selected = true, onClick = onClearFilter, label = { Text(stringResource(R.string.timeline_filters)) })
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onClearFilter) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.timeline_clear_filters))
        }
    }
}

@Composable
fun TimelineList(
    items: PagedItems<TimelineItem>,
    onOpenMemory: (Long) -> Unit,
    contentPadding: PaddingValues,
    listState: LazyListState = rememberLazyListState(),
    compact: Boolean = false,
    /** One line per memory with a small thumbnail: about four times as many memories per screen as cards. */
    dense: Boolean = false,
) {
    val spacing = Yaadein.spacing
    val sizes = Yaadein.sizes
    val railColor = Yaadein.colors.timelineLine
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = spacing.gutter,
            end = spacing.gutter,
            top = spacing.sm + contentPadding.calculateTopPadding(),
            bottom = spacing.xxxl + contentPadding.calculateBottomPadding() + sizes.fabSize,
        ),
        verticalArrangement = Arrangement.spacedBy(if (dense) spacing.xs else spacing.md),
    ) {
        items(
            count = items.itemCount,
            key = { index -> items.keyAt(index) { it.key } },
            contentType = { index -> items.peek(index)?.let { it::class.simpleName } ?: "card" },
        ) { index ->
            when (val item = items[index]) {
                is TimelineItem.YearHeader -> YearHeader(item)
                is TimelineItem.MonthHeader -> MonthHeader(item)
                is TimelineItem.Card -> if (dense) {
                    CompactMemoryRow(card = item.card, onClick = { onOpenMemory(item.card.memory.id) })
                } else {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TimelineRail(color = railColor)
                        MemoryCardView(card = item.card, onClick = { onOpenMemory(item.card.memory.id) }, compact = compact)
                    }
                }
                null -> Unit
            }
        }
        if (items.isAppending) {
            item(key = "append-loading") {
                Box(Modifier.fillMaxWidth().padding(spacing.lg), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(Yaadein.sizes.icon))
                }
            }
        }
    }
}

@Composable
private fun TimelineRail(color: androidx.compose.ui.graphics.Color) {
    val sizes = Yaadein.sizes
    val spacing = Yaadein.spacing
    Column(
        modifier = Modifier.width(sizes.timelineDot + spacing.md),
        horizontalAlignment = Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .padding(top = spacing.lg)
                .size(sizes.timelineDot)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
private fun YearHeader(item: TimelineItem.YearHeader) {
    val spacing = Yaadein.spacing
    val locale = currentLocale()
    Column(modifier = Modifier.padding(top = spacing.lg, bottom = spacing.xs)) {
        Text(
            text = item.year.toString(),
            style = MaterialTheme.typography.displayMedium,
            color = Yaadein.colors.timelineYear,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = Dates.monthName(item.firstMonth, locale),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MonthHeader(item: TimelineItem.MonthHeader) {
    val spacing = Yaadein.spacing
    Text(
        text = Dates.monthName(item.month, currentLocale()),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = spacing.sm)
            .semantics { heading() },
    )
}
