package com.yaadein.feature.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yaadein.core.ui.components.CoverArt
import com.yaadein.core.ui.components.FaceStack
import com.yaadein.core.ui.media.ThumbnailImage
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.core.ui.util.memoryDateText
import com.yaadein.core.ui.util.currentLocale
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.MemoryCard
import com.yaadein.core.ui.util.PagedItems
import com.yaadein.core.ui.util.keyAt

/** One memory per line: small cover, title, date and place, and who was there. */
@Composable
fun CompactMemoryRow(card: MemoryCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = Yaadein.spacing
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(Yaadein.radius.md),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(spacing.sm)) {
            Cover(card, Modifier.size(64.dp).clip(RoundedCornerShape(Yaadein.radius.sm)))
            Spacer(Modifier.width(spacing.md))
            Column(Modifier.weight(1f)) {
                Text(card.memory.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val where = card.memory.place.takeIf { it.isNotBlank() }
                Text(
                    text = listOfNotNull(memoryDateText(card.memory.date), where).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (card.people.isNotEmpty()) {
                Spacer(Modifier.width(spacing.sm))
                FaceStack(people = card.people, max = 3, size = Yaadein.sizes.avatarSm)
            }
        }
    }
}

/** Photo grid: three covers per row under the same year and month headers. */
@Composable
fun TimelineGrid(items: PagedItems<TimelineItem>, onOpenMemory: (Long) -> Unit, contentPadding: PaddingValues) {
    val spacing = Yaadein.spacing
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = spacing.gutter,
            end = spacing.gutter,
            top = spacing.sm + contentPadding.calculateTopPadding(),
            bottom = spacing.xxxl + contentPadding.calculateBottomPadding() + Yaadein.sizes.fabSize,
        ),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        items(
            count = items.itemCount,
            key = { index -> items.keyAt(index) { it.key } },
            span = { index -> if (items.peek(index) is TimelineItem.Card) GridItemSpan(1) else GridItemSpan(maxLineSpan) },
        ) { index ->
            when (val item = items[index]) {
                is TimelineItem.YearHeader -> Text(
                    "${item.year}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Yaadein.colors.timelineYear,
                    modifier = Modifier.padding(top = spacing.lg, bottom = spacing.xs).semantics { heading() },
                )
                is TimelineItem.MonthHeader -> Text(
                    Dates.monthName(item.month, currentLocale()),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = spacing.sm),
                )
                is TimelineItem.Card -> GridTile(item.card) { onOpenMemory(item.card.memory.id) }
                null -> Box(Modifier.aspectRatio(1f))
            }
        }
    }
}

@Composable
private fun GridTile(card: MemoryCard, onClick: () -> Unit) {
    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(Yaadein.radius.sm)).clickable(onClick = onClick)) {
        Cover(card, Modifier.fillMaxSize())
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))) {
            Text(
                card.memory.title,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(Yaadein.spacing.xs),
            )
        }
    }
}

@Composable
private fun Cover(card: MemoryCard, modifier: Modifier) {
    val cover = card.cover
    if (cover != null) ThumbnailImage(mediaId = cover.id, contentDescription = null, modifier = modifier)
    else CoverArt(style = card.memory.coverStyle, seed = card.memory.id, modifier = modifier)
}
