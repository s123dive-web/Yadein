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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yaadein.core.ui.media.ThumbnailImage
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.MediaKind
import com.yaadein.domain.recap.RecapPlanner
import com.yaadein.core.ui.R as CoreR

/**
 * Full-screen chooser for the photos and clips in a reel: every photo of the memories in scope, grouped by memory.
 * Tap a photo to add or remove it; each memory has "Select all" / "Clear". Changes apply as they are made.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaChooser(
    state: RecapUiState,
    onToggle: (Long) -> Unit,
    onMemorySelected: (Long, Boolean) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
) {
    val spacing = Yaadein.spacing
    val groups = state.candidates
    val total = groups.sumOf { it.second.size }
    val selected = state.selectedMediaIds
    val locale = LocalLocale.current.platformLocale
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.recap_choose_photos), modifier = Modifier.semantics { heading() })
                            Text(
                                stringResource(R.string.recap_photos_selected, selected.size, total),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Rounded.Close, contentDescription = stringResource(CoreR.string.action_back)) } },
                    actions = { Button(onClick = onDone, modifier = Modifier.padding(end = spacing.sm)) { Text(stringResource(R.string.recap_choose_done)) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = spacing.gutter), horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onSelectAll(true) }) { Text(stringResource(R.string.recap_select_all)) }
                    TextButton(onClick = { onSelectAll(false) }) { Text(stringResource(R.string.recap_clear_all)) }
                    TextButton(onClick = onReset) { Text(stringResource(R.string.recap_auto_pick)) }
                }
                if (state.selectionLimitHit) {
                    Text(
                        stringResource(R.string.recap_limit, RecapPlanner.MAX_SELECTED_ITEMS),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = spacing.gutter),
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 96.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = spacing.gutter, end = spacing.gutter, bottom = spacing.xxxl),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    groups.forEach { (detail, media) ->
                        val inMemory = media.count { it.id in selected }
                        val all = inMemory == media.size
                        item(key = "h${detail.memory.id}", span = { GridItemSpan(maxLineSpan) }) {
                            Row(Modifier.fillMaxWidth().padding(top = spacing.md), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(detail.memory.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        Dates.label(detail.memory.date, locale).text + " · " + stringResource(R.string.recap_photos_selected, inMemory, media.size),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onClick = { onMemorySelected(detail.memory.id, !all) }) {
                                    Text(stringResource(if (all) R.string.recap_clear_all else R.string.recap_select_all))
                                }
                            }
                        }
                        items(media.size, key = { media[it].id }) { i ->
                            val m = media[i]
                            val isSelected = m.id in selected
                            Box(
                                Modifier.aspectRatio(1f).clip(RoundedCornerShape(Yaadein.radius.sm))
                                    .toggleable(value = isSelected, role = Role.Checkbox, onValueChange = { onToggle(m.id) })
                                    .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(Yaadein.radius.sm)) else Modifier),
                            ) {
                                ThumbnailImage(
                                    mediaId = m.id, contentDescription = m.caption.ifBlank { detail.memory.title },
                                    modifier = Modifier.fillMaxSize().alpha(if (isSelected) 1f else 0.45f),
                                )
                                if (m.kind == MediaKind.VIDEO) {
                                    Icon(Icons.Rounded.Movie, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(spacing.xs).size(Yaadein.sizes.iconSm))
                                }
                                Icon(
                                    if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(spacing.xs)
                                        .background(if (isSelected) Color.White else Color.Black.copy(alpha = 0.3f), RoundedCornerShape(Yaadein.radius.pill)),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
