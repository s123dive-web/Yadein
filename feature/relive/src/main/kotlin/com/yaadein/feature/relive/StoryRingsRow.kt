package com.yaadein.feature.relive

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yaadein.core.ui.components.CoverArt
import com.yaadein.core.ui.components.PersonAvatar
import com.yaadein.core.ui.components.festivalName
import com.yaadein.core.ui.media.ThumbnailImage
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.model.CoverStyle
import com.yaadein.domain.relive.RingKind
import com.yaadein.domain.relive.StoryRing

/** The words under a ring: "Today", a person's name, "This year", the festival, or the place. */
@Composable
fun ringLabel(ring: StoryRing): String = when (ring.kind) {
    RingKind.TODAY -> stringResource(R.string.story_ring_today)
    RingKind.PERSON -> ring.person?.displayName.orEmpty()
    RingKind.YEAR -> stringResource(R.string.story_ring_this_year)
    RingKind.FESTIVAL -> ring.festival?.let { festivalName(it) }.orEmpty()
    RingKind.PLACE -> ring.place.orEmpty()
}

/**
 * The story rings at the top of Relive. Each ring is one target (the picture and its label together) that says what it
 * is and whether it is new today; a ring that was watched today loses its colour.
 */
@Composable
fun StoryRingsRow(rings: List<RingUi>, onOpen: (ringKey: String) -> Unit, modifier: Modifier = Modifier) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.story_rings_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = spacing.sm).semantics { heading() },
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
            items(rings.size, key = { rings[it].ring.key }) { i ->
                StoryRingItem(ring = rings[i], onOpen = { onOpen(rings[i].ring.key) })
            }
        }
    }
}

@Composable
private fun StoryRingItem(ring: RingUi, onOpen: () -> Unit) {
    val spacing = Yaadein.spacing
    val colors = MaterialTheme.colorScheme
    val label = ringLabel(ring.ring)
    val description = stringResource(if (ring.seen) R.string.story_ring_seen_cd else R.string.story_ring_new_cd, label)
    val border = if (ring.seen) {
        BorderStroke(RING_WIDTH_SEEN, colors.outlineVariant)
    } else {
        BorderStroke(RING_WIDTH, Brush.sweepGradient(listOf(colors.primary, colors.secondary, colors.tertiary, colors.primary)))
    }
    Column(
        modifier = Modifier
            .width(RING_SIZE + spacing.sm)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onOpen)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick { onOpen(); true }
            }
            .padding(vertical = spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Box(modifier = Modifier.size(RING_SIZE).border(border, CircleShape).padding(RING_WIDTH + RING_GAP).clip(CircleShape), contentAlignment = Alignment.Center) {
            val cover = ring.cover
            val person = ring.ring.person
            when {
                cover?.mediaId != null -> ThumbnailImage(mediaId = cover.mediaId!!, contentDescription = null, modifier = Modifier.fillMaxSize())
                person != null -> PersonAvatar(person = person, size = RING_SIZE - (RING_WIDTH + RING_GAP) * 2)
                else -> CoverArt(style = cover?.coverStyle ?: CoverStyle.DEFAULT, seed = cover?.memoryId ?: ring.ring.key.hashCode().toLong(), modifier = Modifier.fillMaxSize())
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (ring.seen) colors.onSurfaceVariant else colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private val RING_SIZE = 76.dp
private val RING_WIDTH = 3.dp
private val RING_WIDTH_SEEN = 1.dp
private val RING_GAP = 3.dp
