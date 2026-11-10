package com.yaadein.feature.relive

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.yaadein.core.ui.components.CoverArt
import com.yaadein.core.ui.components.PersonAvatar
import com.yaadein.core.ui.media.EncryptedMediaImage
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.core.ui.util.memoryDateText
import com.yaadein.domain.model.CoverStyle
import com.yaadein.domain.model.Media
import com.yaadein.domain.model.MediaKind
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.relive.Milestone
import com.yaadein.domain.relive.StoryPage
import com.yaadein.domain.relive.memoryId
import java.time.LocalDate
import kotlin.math.abs
import com.yaadein.core.ui.R as CoreR

/** How long a page stays before the story moves on by itself. */
internal const val STORY_PAGE_MILLIS = 6_000

/** Everything the player can ask for; grouped so the screen stays previewable and testable without a ViewModel. */
class StoryPlayerActions(
    val onClose: () -> Unit = {},
    val onNextRing: () -> Unit = {},
    val onPreviousRing: () -> Unit = {},
    val onOpenMemory: (Long) -> Unit = {},
    val onToggleFavorite: (Long) -> Unit = {},
    val onAddNote: (memoryId: Long, text: String) -> Unit = { _, _ -> },
    val onRecordVoice: (memoryId: Long) -> Unit = {},
    val onMakeReel: (page: StoryPage?) -> Unit = {},
    val onToggleSound: () -> Unit = {},
    /** Null is the bundled music. */
    val onChooseMusic: (uri: String?) -> Unit = {},
    val onAddSongs: () -> Unit = {},
)

private enum class StorySheet { NOTE, MUSIC }

/**
 * A story, full screen: progress segments, tap the right side for the next page and the left for the previous one,
 * hold to pause, swipe sideways for the next ring, swipe down to close. Photos drift slowly (Ken Burns) unless
 * [motion] is off (system animations disabled), in which case nothing moves or advances by itself. Every gesture also
 * has a button or a TalkBack action.
 *
 * [soundOn] is what the speaker button shows (the route owns the player and the phone's silent mode); [video] plays a
 * muted clip for video pages (supplied by the route, which has the vault's data source), null shows the still frame.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryPlayerScreen(
    state: StoryPlayerUiState,
    actions: StoryPlayerActions,
    modifier: Modifier = Modifier,
    soundOn: Boolean = !state.muted,
    motion: Boolean = true,
    video: (@Composable (media: Media, playing: Boolean) -> Unit)? = null,
) {
    val spacing = Yaadein.spacing
    val pages = state.pages
    var page by rememberSaveable(state.ringIndex) { mutableIntStateOf(0) }
    if (page > pages.lastIndex && pages.isNotEmpty()) page = pages.lastIndex
    var held by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<StorySheet?>(null) }
    val paused = held || sheet != null
    val progress = remember { Animatable(0f) }
    var progressFor by remember { mutableStateOf(-1 to -1) }
    val current = pages.getOrNull(page)
    val currentPages by rememberUpdatedState(pages)
    val currentActions by rememberUpdatedState(actions)

    fun next() {
        if (page < currentPages.lastIndex) page++ else currentActions.onNextRing()
    }
    fun previous() {
        if (page > 0) page-- else currentActions.onPreviousRing()
    }

    // Fill the current segment; when it is full, go on (to the next ring after the last page).
    LaunchedEffect(state.ringIndex, page, paused, motion, pages.size) {
        if (pages.isEmpty()) return@LaunchedEffect
        if (progressFor != (state.ringIndex to page)) { progress.snapTo(0f); progressFor = state.ringIndex to page }
        if (!motion) { progress.snapTo(1f); return@LaunchedEffect }
        if (paused) { progress.stop(); return@LaunchedEffect }
        val remaining = ((1f - progress.value) * STORY_PAGE_MILLIS).toInt().coerceAtLeast(1)
        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
        next()
    }

    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val title = state.ring?.let { ringLabel(it.ring) }.orEmpty()
    val pageLabel = if (pages.isEmpty()) title else title + ". " + stringResource(R.string.relive_story_page, page + 1, pages.size)
    val nextPageLabel = stringResource(R.string.story_next_page)
    val previousPageLabel = stringResource(R.string.story_previous_page)
    val nextStoryLabel = stringResource(R.string.story_next_story)
    val previousStoryLabel = stringResource(R.string.story_previous_story)

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        // The page itself, with the gestures. TalkBack gets the same four moves as actions.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .semantics {
                    contentDescription = pageLabel
                    customActions = listOf(
                        CustomAccessibilityAction(nextPageLabel) { next(); true },
                        CustomAccessibilityAction(previousPageLabel) { previous(); true },
                        CustomAccessibilityAction(nextStoryLabel) { currentActions.onNextRing(); true },
                        CustomAccessibilityAction(previousStoryLabel) { currentActions.onPreviousRing(); true },
                    )
                }
                .pointerInput(state.ringIndex) {
                    detectTapGestures(
                        onPress = { held = true; tryAwaitRelease(); held = false },
                        onTap = { offset ->
                            val back = if (rtl) offset.x > size.width * 2f / 3f else offset.x < size.width / 3f
                            if (back) previous() else next()
                        },
                    )
                }
                .pointerInput(state.ringIndex) {
                    var dx = 0f
                    var dy = 0f
                    val threshold = 64.dp.toPx()
                    detectDragGestures(
                        onDragStart = { dx = 0f; dy = 0f },
                        onDragEnd = {
                            when {
                                dy > threshold && abs(dy) > abs(dx) -> currentActions.onClose()
                                abs(dx) > threshold && abs(dx) > abs(dy) -> if ((dx < 0) != rtl) currentActions.onNextRing() else currentActions.onPreviousRing()
                            }
                        },
                        onDrag = { change, amount -> change.consume(); dx += amount.x; dy += amount.y },
                    )
                },
        ) {
            when {
                current != null -> StoryPageView(page = current, progress = if (motion) progress.value else 0f, playing = !paused, video = video)
                state.loading || state.pagesLoading -> CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
                else -> Text(
                    stringResource(R.string.story_empty),
                    style = MaterialTheme.typography.titleMedium, color = Color.White, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(spacing.xl),
                )
            }
        }
        // Scrims so the white text and buttons stay readable over any photo.
        Box(Modifier.fillMaxWidth().height(160.dp).align(Alignment.TopCenter).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))))
        Box(Modifier.fillMaxWidth().height(320.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))))

        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(horizontal = spacing.md, vertical = spacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                (if (pages.isEmpty()) listOf(0) else pages.indices.toList()).forEach { i ->
                    val fill = when {
                        pages.isEmpty() -> 0f
                        i < page -> 1f
                        i == page -> progress.value
                        else -> 0f
                    }
                    Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.35f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(Color.White))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = spacing.xs)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                StoryIconButton(Icons.Rounded.MusicNote, stringResource(R.string.story_music)) { sheet = StorySheet.MUSIC }
                StoryIconButton(
                    if (soundOn) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                    stringResource(if (soundOn) R.string.story_mute else R.string.story_unmute),
                    onClick = actions.onToggleSound,
                )
                StoryIconButton(Icons.Rounded.Close, stringResource(R.string.story_close), onClick = actions.onClose)
            }
        }

        if (current != null) {
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().navigationBarsPadding().padding(horizontal = spacing.lg, vertical = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                StoryCaption(current)
                val memoryId = current.memoryId
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (memoryId != null) {
                        val favorite = memoryId in state.favorites
                        StoryIconButton(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.relive_story_open)) { actions.onOpenMemory(memoryId) }
                        StoryIconButton(
                            if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            stringResource(if (favorite) CoreR.string.favorite_remove else CoreR.string.favorite_add),
                        ) { actions.onToggleFavorite(memoryId) }
                        StoryIconButton(Icons.Rounded.EditNote, stringResource(R.string.story_add_note)) { sheet = StorySheet.NOTE }
                    }
                    Spacer(Modifier.weight(1f))
                    StoryIconButton(Icons.Rounded.Movie, stringResource(R.string.story_make_reel)) { actions.onMakeReel(current) }
                }
            }
        }
    }

    val noteFor = current?.memoryId
    when (sheet) {
        StorySheet.NOTE -> if (noteFor != null) {
            ModalBottomSheet(onDismissRequest = { sheet = null }) {
                AddNoteContent(
                    onSave = { text -> sheet = null; actions.onAddNote(noteFor, text) },
                    onRecordVoice = { sheet = null; actions.onRecordVoice(noteFor) },
                )
            }
        }
        StorySheet.MUSIC -> ModalBottomSheet(onDismissRequest = { sheet = null }) {
            StoryMusicContent(
                state = state,
                onChoose = { uri -> sheet = null; actions.onChooseMusic(uri) },
                onAdd = { sheet = null; actions.onAddSongs() },
            )
        }
        null -> Unit
    }
}

@Composable
private fun StoryIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = label, tint = Color.White) }
}

/** One page: a memory's photo (drifting), a person, a Then & Now split, or a milestone. */
@Composable
private fun StoryPageView(page: StoryPage, progress: Float, playing: Boolean, video: (@Composable (Media, Boolean) -> Unit)?) {
    when (page) {
        is StoryPage.Moment -> StoryMedia(page.card, progress, playing, video, photoId = page.mediaId)
        is StoryPage.Spotlight -> Box(Modifier.fillMaxSize()) {
            StoryMedia(page.card, progress, playing, video)
            PersonAvatar(person = page.person, size = Yaadein.sizes.avatarXl, modifier = Modifier.align(Alignment.Center))
        }
        is StoryPage.ThenNow -> Column(Modifier.fillMaxSize()) {
            ThenNowHalf(mediaId = page.pair.then.mediaId, label = stringResource(R.string.story_then), epochDay = page.pair.then.epochDay, modifier = Modifier.weight(1f))
            Spacer(Modifier.height(2.dp))
            ThenNowHalf(mediaId = page.pair.now.mediaId, label = stringResource(R.string.story_now), epochDay = page.pair.now.epochDay, modifier = Modifier.weight(1f))
        }
        is StoryPage.Milestone -> Box(Modifier.fillMaxSize()) {
            StoryMedia(page.card, progress, playing, video = null)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        }
    }
}

@Composable
private fun ThenNowHalf(mediaId: Long, label: String, epochDay: Long, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().clipToBounds()) {
        EncryptedMediaImage(mediaId = mediaId, thumbnail = false, contentDescription = null, modifier = Modifier.fillMaxSize())
        Text(
            text = "$label · ${LocalDate.ofEpochDay(epochDay).year}",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(Yaadein.spacing.md)
                .clip(RoundedCornerShape(Yaadein.radius.pill))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = Yaadein.spacing.md, vertical = Yaadein.spacing.xs),
        )
    }
}

/**
 * The picture behind a page. Photos are decrypted at full size and drift slowly: a little zoom and a little pan whose
 * direction depends on the memory, driven by the page's own progress so pausing the story pauses the drift.
 */
@Composable
private fun StoryMedia(card: MemoryCard?, progress: Float, playing: Boolean, video: (@Composable (Media, Boolean) -> Unit)?, photoId: Long? = null) {
    val cover = card?.cover
    when {
        // A chosen photo (in a person's story: one that shows them) replaces the memory's cover.
        photoId != null && photoId != cover?.id -> EncryptedMediaImage(
            mediaId = photoId,
            thumbnail = false,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val zoom = 1f + KEN_BURNS_ZOOM * progress
                scaleX = zoom
                scaleY = zoom
                translationX = (if (photoId % 2L == 0L) 1f else -1f) * size.width * KEN_BURNS_PAN * progress
            },
        )
        cover == null -> CoverArt(style = card?.memory?.coverStyle ?: CoverStyle.DEFAULT, seed = card?.memory?.id ?: 0L, modifier = Modifier.fillMaxSize())
        cover.kind == MediaKind.VIDEO && video != null -> Box(Modifier.fillMaxSize()) { video(cover, playing) }
        else -> {
            val direction = if (cover.id % 2L == 0L) 1f else -1f
            EncryptedMediaImage(
                mediaId = cover.id,
                thumbnail = cover.kind == MediaKind.VIDEO,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    val zoom = 1f + KEN_BURNS_ZOOM * progress
                    scaleX = zoom
                    scaleY = zoom
                    translationX = direction * size.width * KEN_BURNS_PAN * progress
                },
            )
        }
    }
}

private const val KEN_BURNS_ZOOM = 0.12f
private const val KEN_BURNS_PAN = 0.03f

/** The words at the bottom of a page. */
@Composable
private fun StoryCaption(page: StoryPage) {
    val small = MaterialTheme.typography.labelLarge
    val big = MaterialTheme.typography.headlineSmall
    val body = MaterialTheme.typography.bodyMedium
    val soft = Color.White.copy(alpha = 0.88f)
    when (page) {
        is StoryPage.Moment -> {
            page.yearsAgo?.let { Text(pluralStringResource(R.plurals.relive_years_ago, it, it), style = small, color = soft) }
            Text(page.card.memory.title, style = big, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val details = listOf(memoryDateText(page.card.memory.date), page.card.memory.place, page.card.people.take(3).joinToString(", ") { it.displayName })
                .filter { it.isNotBlank() }.joinToString(" · ")
            if (details.isNotBlank()) Text(details, style = body, color = soft, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        is StoryPage.Spotlight -> {
            Text(stringResource(R.string.relive_story_spotlight, page.person.displayName), style = big, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(pluralStringResource(CoreR.plurals.memory_count, page.memoryCount, page.memoryCount), style = body, color = soft)
        }
        is StoryPage.ThenNow -> {
            Text(stringResource(R.string.story_then_now, page.person?.displayName ?: page.place.orEmpty()), style = big, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(pluralStringResource(R.plurals.story_years_apart, page.pair.yearsApart, page.pair.yearsApart), style = body, color = soft)
        }
        is StoryPage.Milestone -> when (val m = page.milestone) {
            is Milestone.MemoryCount -> {
                Text(stringResource(R.string.story_milestone_count, m.count), style = big, color = Color.White)
                Text(stringResource(R.string.story_milestone_count_sub), style = body, color = soft)
            }
            is Milestone.YearsWith -> {
                Text(pluralStringResource(R.plurals.story_years_with, m.years, m.years, page.person?.displayName.orEmpty()), style = big, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.story_years_with_sub), style = body, color = soft)
            }
        }
    }
}

/** "Add what you remember": a few words appended to the memory, or a voice note instead. */
@Composable
fun AddNoteContent(onSave: (String) -> Unit, onRecordVoice: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = Yaadein.spacing
    var text by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = spacing.xl).padding(bottom = spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(stringResource(R.string.story_add_note), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.story_add_note_hint)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onSave(text) }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = Yaadein.sizes.touchTarget)) {
            Text(stringResource(R.string.story_add_note_save))
        }
        TextButton(onClick = onRecordVoice, modifier = Modifier.fillMaxWidth().heightIn(min = Yaadein.sizes.touchTarget)) {
            Icon(Icons.Rounded.Mic, contentDescription = null)
            Spacer(Modifier.width(spacing.sm))
            Text(stringResource(R.string.story_add_note_voice))
        }
    }
}

/** The music under stories: the bundled music or a song from "Your songs"; more can be added from the phone. */
@Composable
fun StoryMusicContent(state: StoryPlayerUiState, onChoose: (String?) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = Yaadein.spacing
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = spacing.xl).padding(bottom = spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(stringResource(R.string.story_music), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.story_music_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MusicRow(label = stringResource(R.string.story_music_bundled), selected = state.musicUri == null) { onChoose(null) }
        state.songs.forEach { song -> MusicRow(label = song.label, selected = state.musicUri == song.uri) { onChoose(song.uri) } }
        Spacer(Modifier.height(spacing.sm))
        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().heightIn(min = Yaadein.sizes.touchTarget)) {
            Text(stringResource(R.string.story_music_add))
        }
    }
}

@Composable
private fun MusicRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Yaadein.radius.md))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = Yaadein.sizes.touchTarget)
            .padding(horizontal = Yaadein.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}
