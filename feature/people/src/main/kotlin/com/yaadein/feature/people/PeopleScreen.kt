package com.yaadein.feature.people

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.PersonAdd
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaadein.core.ui.components.EmptyState
import com.yaadein.core.ui.components.PersonAvatar
import com.yaadein.core.ui.illustrations.IllustrationKind
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.model.Group
import com.yaadein.core.ui.R as CoreR

@Composable
fun PeopleRoute(
    onOpenPerson: (Long) -> Unit,
    onAddPerson: () -> Unit,
    onOpenGroup: (Long) -> Unit,
    onAddGroup: () -> Unit,
    onOpenRelationships: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: PeopleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PeopleScreen(
        state = state,
        onOpenPerson = onOpenPerson,
        onAddPerson = onAddPerson,
        onOpenGroup = onOpenGroup,
        onAddGroup = onAddGroup,
        onOpenRelationships = onOpenRelationships,
        contentPadding = contentPadding,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
    state: PeopleUiState,
    onOpenPerson: (Long) -> Unit,
    onAddPerson: () -> Unit,
    onOpenGroup: (Long) -> Unit,
    onAddGroup: () -> Unit,
    onOpenRelationships: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.people_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
            },
            actions = {
                if (state.people.size >= 2) {
                    IconButton(onClick = onOpenRelationships) {
                        Icon(Icons.Rounded.Hub, contentDescription = stringResource(R.string.people_relationships))
                    }
                }
                IconButton(onClick = onAddPerson) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = stringResource(R.string.people_add))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.people.isEmpty() -> EmptyState(
                illustration = IllustrationKind.PEOPLE_CIRCLE,
                title = stringResource(R.string.people_empty_title),
                message = stringResource(R.string.people_empty_message),
                actionLabel = stringResource(R.string.people_add),
                onAction = onAddPerson,
                modifier = Modifier.fillMaxSize().padding(contentPadding),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 112.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = spacing.gutter, end = spacing.gutter,
                    top = spacing.sm, bottom = spacing.xxxl + contentPadding.calculateBottomPadding() + Yaadein.sizes.fabSize,
                ),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "groups") {
                    GroupsRow(groups = state.groups, onOpenGroup = onOpenGroup, onAddGroup = onAddGroup)
                }
                items(state.people, key = { it.person.id }) { tile ->
                    PersonTileView(tile = tile, onClick = { onOpenPerson(tile.person.id) })
                }
            }
        }
    }
}

@Composable
private fun GroupsRow(groups: List<Group>, onOpenGroup: (Long) -> Unit, onAddGroup: () -> Unit) {
    val spacing = Yaadein.spacing
    Column(modifier = Modifier.padding(bottom = spacing.sm)) {
        Text(
            text = stringResource(R.string.people_groups),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(spacing.sm))
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            items(groups.size, key = { groups[it].id }) { index ->
                val g = groups[index]
                AssistChip(
                    onClick = { onOpenGroup(g.id) },
                    label = { Text("${g.emoji} ${g.name}") },
                )
            }
            item(key = "add-group") {
                AssistChip(
                    onClick = onAddGroup,
                    label = { Text(stringResource(R.string.people_new_group)) },
                    leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                )
            }
        }
    }
}

@Composable
fun PersonTileView(tile: PersonTile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = Yaadein.spacing
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Yaadein.radius.lg),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(vertical = spacing.lg, horizontal = spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PersonAvatar(person = tile.person, size = Yaadein.sizes.avatarLg)
            Spacer(Modifier.height(spacing.sm))
            Text(
                text = tile.person.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = pluralStringResource(CoreR.plurals.memory_count, tile.memoryCount, tile.memoryCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
