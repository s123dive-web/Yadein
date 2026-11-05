package com.yaadein.feature.memory

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaadein.core.ui.components.CoverArt
import com.yaadein.core.ui.components.PersonAvatar
import com.yaadein.core.ui.media.ThumbnailImage
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.core.ui.util.memoryDateText
import com.yaadein.domain.model.Media
import com.yaadein.domain.model.MediaKind
import com.yaadein.domain.model.MemoryDetail
import com.yaadein.domain.model.Mood
import com.yaadein.core.ui.R as CoreR

@Composable
fun MemoryDetailRoute(
    onBack: () -> Unit,
    onOpenMedia: (memoryId: Long, index: Int) -> Unit,
    onOpenPerson: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onAddMedia: (Long) -> Unit,
    onRecap: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MemoryDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MemoryDetailScreen(
        state = state,
        onBack = onBack,
        onOpenMedia = { index -> onOpenMedia(viewModel.memoryId, index) },
        onOpenPerson = onOpenPerson,
        onEdit = { onEdit(viewModel.memoryId) },
        onAddMedia = { onAddMedia(viewModel.memoryId) },
        onRecap = { onRecap(viewModel.memoryId) },
        onDelete = { viewModel.delete(onBack) },
        onSetCover = viewModel::setCover,
        modifier = modifier,
    )
}

@Composable
fun MemoryDetailScreen(
    state: MemoryDetailUiState,
    onBack: () -> Unit,
    onOpenMedia: (Int) -> Unit,
    onOpenPerson: (Long) -> Unit,
    onEdit: () -> Unit,
    onAddMedia: () -> Unit,
    onRecap: () -> Unit,
    onDelete: () -> Unit,
    onSetCover: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = state.detail
    if (state.loading || detail == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { if (state.loading) CircularProgressIndicator() }
        return
    }
    val spacing = Yaadein.spacing
    var confirmDelete by remember { mutableStateOf(false) }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = spacing.xxxl)) {
        item(key = "hero") {
            Hero(detail = detail, onBack = onBack, onEdit = onEdit, onDelete = { confirmDelete = true }, onRecap = onRecap, onOpenCover = { index -> onOpenMedia(index) })
        }
        item(key = "body") {
            Column(modifier = Modifier.padding(horizontal = spacing.gutter)) {
                Spacer(Modifier.height(spacing.lg))
                Text(
                    text = detail.memory.title,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(spacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(text = memoryDateText(detail.memory.date), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (detail.memory.place.isNotBlank()) {
                        Icon(Icons.Rounded.Place, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(Yaadein.sizes.iconSm))
                        Text(text = detail.memory.place, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    detail.memory.mood?.let { MoodPill(it) }
                }
                if (detail.memory.description.isNotBlank()) {
                    Spacer(Modifier.height(spacing.lg))
                    Text(text = detail.memory.description, style = MaterialTheme.typography.bodyLarge)
                }
                if (detail.people.isNotEmpty()) {
                    Spacer(Modifier.height(spacing.xl))
                    Text(text = stringResource(R.string.memory_who_was_there), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.height(spacing.sm))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                        items(detail.people.size, key = { detail.people[it].id }) { i ->
                            val p = detail.people[i]
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onOpenPerson(p.id) }.padding(spacing.xs)) {
                                PersonAvatar(person = p, size = Yaadein.sizes.avatarLg)
                                Spacer(Modifier.height(spacing.xs))
                                Text(text = p.displayName, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(spacing.xl))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (detail.media.isEmpty()) stringResource(R.string.memory_photos) else pluralStringResource(CoreR.plurals.media_count, detail.media.size, detail.media.size),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    TextButton(onClick = onAddMedia) { Text(stringResource(R.string.memory_add_photos)) }
                }
                Spacer(Modifier.height(spacing.sm))
            }
        }
        if (detail.media.isEmpty()) {
            item(key = "no-media") {
                Surface(
                    modifier = Modifier.padding(horizontal = spacing.gutter).fillMaxWidth().clickable(onClick = onAddMedia),
                    shape = RoundedCornerShape(Yaadein.radius.lg),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Text(
                        text = stringResource(R.string.memory_no_photos_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(spacing.lg),
                    )
                }
            }
        } else {
            val rows = detail.media.chunked(3)
            items(rows.size, key = { "row-$it" }) { r ->
                Row(
                    modifier = Modifier.padding(horizontal = spacing.gutter, vertical = spacing.xxs),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    rows[r].forEachIndexed { c, media ->
                        val index = r * 3 + c
                        MediaThumb(media = media, isCover = media.id == detail.memory.coverMediaId, onClick = { onOpenMedia(index) }, onSetCover = { onSetCover(media.id) }, modifier = Modifier.weight(1f))
                    }
                    repeat(3 - rows[r].size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.memory_delete_title)) },
            text = { Text(stringResource(R.string.memory_delete_message)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(CoreR.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(CoreR.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Hero(
    detail: MemoryDetail,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRecap: () -> Unit,
    onOpenCover: (Int) -> Unit,
) {
    val spacing = Yaadein.spacing
    val cover = detail.media.firstOrNull { it.id == detail.memory.coverMediaId } ?: detail.media.firstOrNull()
    var menu by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
        if (cover != null) {
            ThumbnailImage(
                mediaId = cover.id,
                contentDescription = stringResource(R.string.memory_cover_cd, detail.memory.title),
                modifier = Modifier.fillMaxSize().clickable { onOpenCover(detail.media.indexOf(cover).coerceAtLeast(0)) },
            )
        } else {
            CoverArt(style = detail.memory.coverStyle, seed = detail.memory.id, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxWidth().height(Yaadein.sizes.avatarXl).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))))
        Row(
            modifier = Modifier.statusBarsPadding().padding(spacing.sm).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = onBack, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = 0.35f), contentColor = Color.White)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(CoreR.string.action_back))
            }
            Spacer(Modifier.weight(1f))
            FilledTonalIconButton(onClick = onRecap, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = 0.35f), contentColor = Color.White)) {
                Icon(Icons.Rounded.Movie, contentDescription = stringResource(R.string.memory_make_recap))
            }
            FilledTonalIconButton(onClick = onEdit, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = 0.35f), contentColor = Color.White)) {
                Icon(Icons.Rounded.Edit, contentDescription = stringResource(CoreR.string.action_edit))
            }
            Box {
                FilledTonalIconButton(onClick = { menu = true }, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = 0.35f), contentColor = Color.White)) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(CoreR.string.action_more))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(CoreR.string.action_delete)) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
        if (cover?.kind == MediaKind.VIDEO) {
            Icon(
                imageVector = Icons.Rounded.PlayCircle,
                contentDescription = stringResource(R.string.memory_play_video),
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(Yaadein.sizes.avatarLg),
            )
        }
    }
}

@Composable
private fun MediaThumb(media: Media, isCover: Boolean, onClick: () -> Unit, onSetCover: () -> Unit, modifier: Modifier = Modifier) {
    val radius = Yaadein.radius
    var menu by remember { mutableStateOf(false) }
    Box(modifier = modifier.aspectRatio(1f).clip(RoundedCornerShape(radius.sm)).clickable(onClick = onClick)) {
        ThumbnailImage(mediaId = media.id, contentDescription = media.caption.ifBlank { null }, modifier = Modifier.fillMaxSize())
        if (media.kind == MediaKind.VIDEO) {
            Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(Yaadein.sizes.iconLg))
        }
        if (isCover) {
            Box(
                modifier = Modifier.align(Alignment.TopStart).padding(Yaadein.spacing.xs)
                    .clip(RoundedCornerShape(radius.pill)).background(MaterialTheme.colorScheme.primary).padding(horizontal = Yaadein.spacing.sm, vertical = Yaadein.spacing.xxs),
            ) {
                Text(stringResource(R.string.memory_cover_badge), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(Yaadein.sizes.touchTarget)) {
                Box(Modifier.size(Yaadein.sizes.icon).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(CoreR.string.action_more), tint = Color.White, modifier = Modifier.size(Yaadein.sizes.iconSm))
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.memory_set_cover)) }, onClick = { menu = false; onSetCover() })
            }
        }
    }
}

@Composable
private fun MoodPill(mood: Mood) {
    val colors = Yaadein.colors
    val (label, color) = when (mood) {
        Mood.JOYFUL -> stringResource(CoreR.string.mood_joyful) to colors.moodJoyful
        Mood.CALM -> stringResource(CoreR.string.mood_calm) to colors.moodCalm
        Mood.NOSTALGIC -> stringResource(CoreR.string.mood_nostalgic) to colors.moodNostalgic
        Mood.PROUD -> stringResource(CoreR.string.mood_proud) to colors.moodProud
        Mood.FUNNY -> stringResource(CoreR.string.mood_funny) to colors.moodFunny
        Mood.SAD -> stringResource(CoreR.string.mood_sad) to colors.moodSad
        Mood.CELEBRATION -> stringResource(CoreR.string.mood_celebration) to colors.moodCelebration
    }
    Box(
        modifier = Modifier.clip(RoundedCornerShape(Yaadein.radius.pill)).background(color.copy(alpha = 0.18f)).padding(horizontal = Yaadein.spacing.sm, vertical = Yaadein.spacing.xxs),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
