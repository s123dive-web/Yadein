package com.yaadein.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick as semanticsOnLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yaadein.core.ui.R
import com.yaadein.core.ui.media.ThumbnailImage
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.core.ui.util.currentLocale
import com.yaadein.core.ui.util.memoryDateText
import com.yaadein.core.ui.util.rememberLongPress
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.MediaKind
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.MemoryDate
import com.yaadein.domain.model.MemoryType

/**
 * The timeline card: cover (photo thumbnail or CoverArt), title, date, place and who was there.
 * Text-only memories render with the same visual weight as photo memories.
 */
@Composable
fun MemoryCardView(
    card: MemoryCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    /** Photos of a just-saved memory still being encrypted: 0..1, or null when nothing is pending. */
    importProgress: Float? = null,
    /** Photos that could not be added; tapping the badge calls [onImportFailuresClick]. */
    importFailed: Int = 0,
    onImportFailuresClick: (() -> Unit)? = null,
    /** Press and hold (the Timeline uses it to start selecting memories); [onLongClickLabel] names it for TalkBack. */
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
) {
    val spacing = Yaadein.spacing
    val sizes = Yaadein.sizes
    val radius = Yaadein.radius
    val press = rememberLongPress(onClick = onClick, onLongClick = onLongClick)
    val dateText = memoryDateText(card.memory.date)
    val coverHeight = if (compact) sizes.cardCoverHeightCompact else sizes.cardCoverHeight
    val description = stringResource(R.string.cd_memory_card, card.memory.title, dateText)
    Card(
        onClick = press.onClick,
        interactionSource = press.interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = description
                if (onLongClick != null) semanticsOnLongClick(label = onLongClickLabel) { onLongClick.invoke(); true }
            },
        shape = RoundedCornerShape(radius.lg),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = Yaadein.elevation.card),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(coverHeight)
                .clip(RoundedCornerShape(topStart = radius.lg, topEnd = radius.lg)),
        ) {
            val cover = card.cover
            if (cover != null) {
                ThumbnailImage(
                    mediaId = cover.id,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(coverHeight),
                    contentScale = ContentScale.Crop,
                )
            } else {
                CoverArt(style = card.memory.coverStyle, seed = card.memory.id, modifier = Modifier.fillMaxWidth().height(coverHeight))
            }
            // Bottom scrim so chips stay legible on any cover.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)), startY = coverHeight.value * 1.2f)),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(spacing.md),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (card.mediaCount > 0) {
                    CoverBadge(
                        icon = if (cover?.kind == MediaKind.VIDEO) Icons.Rounded.PlayCircle else Icons.Rounded.PhotoLibrary,
                        text = pluralStringResource(R.plurals.media_count, card.mediaCount, card.mediaCount),
                    )
                } else if (card.memory.type == MemoryType.TEXT_ONLY) {
                    CoverBadge(icon = Icons.Rounded.Notes, text = stringResource(R.string.badge_story))
                }
            }
            if (card.people.isNotEmpty()) {
                FaceStack(
                    people = card.people,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(spacing.md),
                )
            }
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                ImportStatusBadge(progress = importProgress, failed = importFailed, onFailuresClick = onImportFailuresClick)
                CoverDateChip(date = card.memory.date)
            }
        }
        Column(modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md)) {
            Text(
                text = card.memory.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (card.memory.place.isNotBlank()) {
                    Icon(
                        imageVector = Icons.Rounded.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Yaadein.sizes.iconSm),
                    )
                    Text(
                        text = card.memory.place,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!compact && card.memory.description.isNotBlank()) {
                Spacer(Modifier.height(spacing.sm))
                Text(
                    text = card.memory.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The memory's date in the top corner of its cover: "18th Nov 26". Decorative for TalkBack (the card says the date). */
@Composable
fun CoverDateChip(date: MemoryDate, modifier: Modifier = Modifier, small: Boolean = false) {
    val spacing = Yaadein.spacing
    Text(
        text = Dates.shortLabel(date, currentLocale()),
        style = if (small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(Yaadein.radius.pill))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = if (small) spacing.xs else spacing.sm, vertical = if (small) spacing.xxs else spacing.xs)
            .clearAndSetSemantics { },
    )
}

@Composable
private fun CoverBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val spacing = Yaadein.spacing
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Yaadein.radius.pill))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(Yaadein.sizes.iconSm))
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = Color.White)
        Spacer(Modifier.width(spacing.xxs))
    }
}

/**
 * Top-right corner of a card: a ring while a memory's photos are still being encrypted in the background, then a
 * small badge when some could not be added. Nothing is shown once everything landed.
 */
@Composable
fun ImportStatusBadge(progress: Float?, failed: Int, onFailuresClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val sizes = Yaadein.sizes
    val spacing = Yaadein.spacing
    when {
        progress != null && progress < 1f -> {
            val percent = (progress * 100).toInt().coerceIn(0, 100)
            val label = stringResource(R.string.cd_import_progress, percent)
            Box(
                modifier = modifier
                    .size(sizes.touchTarget)
                    .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(sizes.iconLg)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), androidx.compose.foundation.shape.CircleShape),
                )
                androidx.compose.material3.CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.size(sizes.icon),
                    strokeWidth = Yaadein.spacing.xxs + 1.dp,
                )
            }
        }
        failed > 0 -> {
            val label = pluralStringResource(R.plurals.cd_import_failed, failed, failed)
            androidx.compose.material3.Surface(
                onClick = onFailuresClick ?: {},
                enabled = onFailuresClick != null,
                shape = RoundedCornerShape(Yaadein.radius.pill),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = modifier.semantics { contentDescription = label },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs).height(sizes.icon),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, modifier = Modifier.size(sizes.iconSm))
                    Text(failed.toString(), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
