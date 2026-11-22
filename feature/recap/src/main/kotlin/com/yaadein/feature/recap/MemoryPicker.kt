package com.yaadein.feature.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yaadein.core.ui.components.CoverArt
import com.yaadein.core.ui.components.CoverDateChip
import com.yaadein.core.ui.components.EmptyState
import com.yaadein.core.ui.illustrations.IllustrationKind
import com.yaadein.core.ui.media.ThumbnailImage
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.recap.RecapPlanner
import com.yaadein.core.ui.R as CoreR

/**
 * Full-screen "Pick memories" chooser for a journey reel: the memories as a grid of covers, newest first like the
 * Timeline, with a search box. Tap to tick; nothing changes on the reel screen until Done.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryPicker(
    state: MemoryPickerState,
    /** Memories already in the reel: ticked when the chooser opens. */
    initial: List<Long>,
    onQuery: (String) -> Unit,
    onDone: (List<Long>) -> Unit,
    onCancel: () -> Unit,
) {
    val spacing = Yaadein.spacing
    var picked by remember { mutableStateOf(initial.toSet()) }
    val limit = RecapPlanner.MAX_SELECTED_ITEMS
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.recap_pick_memories), modifier = Modifier.semantics { heading() })
                            Text(
                                pluralStringResource(R.plurals.recap_picked_memories, picked.size, picked.size),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Rounded.Close, contentDescription = stringResource(CoreR.string.action_close)) } },
                    actions = { Button(onClick = { onDone(picked.toList()) }, modifier = Modifier.padding(end = spacing.sm)) { Text(stringResource(R.string.recap_choose_done)) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                OutlinedTextField(
                    value = state.query, onValueChange = onQuery,
                    placeholder = { Text(stringResource(R.string.recap_pick_search)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.gutter),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = spacing.gutter), horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    // "Select all" takes what the search shows, so "fitness" + Select all picks the whole series.
                    TextButton(onClick = { picked = (picked + state.cards.map { it.memory.id }).take(limit).toSet() }, enabled = state.cards.isNotEmpty()) {
                        Text(stringResource(R.string.recap_select_all))
                    }
                    TextButton(onClick = { picked = emptySet() }, enabled = picked.isNotEmpty()) { Text(stringResource(R.string.recap_clear_all)) }
                }
                if (picked.size >= limit) {
                    Text(
                        stringResource(R.string.recap_pick_limit, limit), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = spacing.gutter),
                    )
                }
                if (state.cards.isEmpty() && state.query.isNotBlank()) {
                    EmptyState(
                        illustration = IllustrationKind.SEARCH_GLASS,
                        title = stringResource(R.string.recap_pick_none_title),
                        message = stringResource(R.string.recap_pick_none_message),
                        modifier = Modifier.fillMaxSize(),
                    )
                    return@Column
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = spacing.gutter, end = spacing.gutter, top = spacing.sm, bottom = spacing.xxxl),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    items(state.cards.size, key = { state.cards[it].memory.id }) { i ->
                        val card = state.cards[i]
                        val id = card.memory.id
                        val isPicked = id in picked
                        val cover = card.cover
                        Box(
                            Modifier.aspectRatio(1f).clip(RoundedCornerShape(Yaadein.radius.sm))
                                .toggleable(value = isPicked, role = Role.Checkbox, enabled = isPicked || picked.size < limit, onValueChange = { picked = if (isPicked) picked - id else picked + id })
                                .then(if (isPicked) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(Yaadein.radius.sm)) else Modifier)
                                .semantics { contentDescription = card.memory.title },
                        ) {
                            if (cover != null) ThumbnailImage(mediaId = cover.id, contentDescription = null, modifier = Modifier.fillMaxSize())
                            else CoverArt(style = card.memory.coverStyle, seed = id, modifier = Modifier.fillMaxSize())
                            CoverDateChip(date = card.memory.date, small = true, modifier = Modifier.align(Alignment.TopEnd).padding(spacing.xs))
                            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))) {
                                Text(card.memory.title, style = MaterialTheme.typography.labelMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(spacing.xs))
                            }
                            Icon(
                                if (isPicked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isPicked) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.align(Alignment.TopStart).padding(spacing.xs)
                                    .background(if (isPicked) Color.White else Color.Black.copy(alpha = 0.3f), RoundedCornerShape(Yaadein.radius.pill)),
                            )
                        }
                    }
                }
            }
        }
    }
}
