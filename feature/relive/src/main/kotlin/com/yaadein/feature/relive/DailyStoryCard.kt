package com.yaadein.feature.relive

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.yaadein.core.ui.components.CoverArt
import com.yaadein.core.ui.components.PersonAvatar
import com.yaadein.core.ui.media.EncryptedMediaImage
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.core.ui.util.memoryDateText
import com.yaadein.domain.model.MediaKind
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.relive.StoryPage
import kotlinx.coroutines.launch
import com.yaadein.core.ui.R as CoreR

/** How long each page stays before the story moves on by itself. */
private const val PAGE_MILLIS = 6_000

/**
 * The daily story at the top of Relive: full-width pages with progress segments, moving on by itself (unless the
 * system has animations turned off or a finger is held down). Tap the right side for the next page, the left side
 * for the previous one, or swipe.
 */
@Composable
fun DailyStoryCard(
    pages: List<StoryPage>,
    onOpenMemory: (Long) -> Unit,
    onOpenPerson: (Long) -> Unit,
    modifier: Modifier = Modifier,
    /** Label for pages that are not "N years ago"; null means "Memory of the day". The first story says "Just added". */
    momentLabel: String? = null,
) {
    if (pages.isEmpty()) return
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val autoplay = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    var held by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val current = pagerState.currentPage

    // Fill the current segment; when it is full, go to the next page (the story stops on the last one).
    var progressPage by remember { mutableStateOf(0) }
    LaunchedEffect(current, held, autoplay) {
        if (progressPage != current) { progress.snapTo(0f); progressPage = current }
        if (!autoplay) { progress.snapTo(1f); return@LaunchedEffect }
        if (held) { progress.stop(); return@LaunchedEffect }
        val remaining = ((1f - progress.value) * PAGE_MILLIS).toInt().coerceAtLeast(1)
        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
        if (current < pages.lastIndex) pagerState.animateScrollToPage(current + 1)
    }

    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pageLabel = stringResource(R.string.relive_story_page, current + 1, pages.size)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(420.dp)
            .clip(RoundedCornerShape(Yaadein.radius.xl))
            .semantics { contentDescription = pageLabel },
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { pageKey(pages[it]) }) { index ->
            Box(
                Modifier.fillMaxSize().pointerInput(pages.size) {
                    detectTapGestures(
                        onPress = { held = true; tryAwaitRelease(); held = false },
                        onTap = { offset ->
                            val back = if (rtl) offset.x > size.width * 2f / 3f else offset.x < size.width / 3f
                            val target = (pagerState.currentPage + if (back) -1 else 1).coerceIn(0, pages.lastIndex)
                            scope.launch { pagerState.animateScrollToPage(target) }
                        },
                    )
                },
            ) {
                StoryPageContent(page = pages[index], onOpenMemory = onOpenMemory, onOpenPerson = onOpenPerson, momentLabel = momentLabel)
            }
        }
        // Progress segments, one per page.
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = Yaadein.spacing.md, vertical = Yaadein.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            pages.indices.forEach { i ->
                val fill = when {
                    i < current -> 1f
                    i == current -> progress.value
                    else -> 0f
                }
                Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.35f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(Color.White))
                }
            }
        }
    }
}

private fun pageKey(page: StoryPage): String = when (page) {
    is StoryPage.Moment -> "m-${page.card.memory.id}-${page.yearsAgo}"
    is StoryPage.Spotlight -> "p-${page.person.id}"
    is StoryPage.ThenNow -> "t-${page.pair.then.mediaId}-${page.pair.now.mediaId}"
    is StoryPage.Milestone -> "s-${page.milestone}"
}

@Composable
private fun StoryPageContent(page: StoryPage, onOpenMemory: (Long) -> Unit, onOpenPerson: (Long) -> Unit, momentLabel: String?) {
    val spacing = Yaadein.spacing
    val card = when (page) {
        is StoryPage.Moment -> page.card
        is StoryPage.Spotlight -> page.card
        // Then & Now and milestones belong to the full-screen player; this card only ever gets the two kinds above.
        is StoryPage.ThenNow -> null
        is StoryPage.Milestone -> page.card
    }
    Box(Modifier.fillMaxSize()) {
        StoryBackground(card)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.35f), 0.35f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f)),
            ),
        )
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            when (page) {
                is StoryPage.Moment -> {
                    Text(
                        page.yearsAgo?.let { pluralStringResource(R.plurals.relive_years_ago, it, it) } ?: momentLabel ?: stringResource(R.string.relive_story_memory_of_day),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                    Text(page.card.memory.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val details = listOf(memoryDateText(page.card.memory.date), page.card.memory.place, page.card.people.take(3).joinToString(", ") { it.displayName })
                        .filter { it.isNotBlank() }.joinToString(" · ")
                    if (details.isNotBlank()) {
                        Text(details, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(spacing.xs))
                    StoryButton(stringResource(R.string.relive_story_open)) { onOpenMemory(page.card.memory.id) }
                }
                is StoryPage.Spotlight -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                        PersonAvatar(person = page.person, size = Yaadein.sizes.avatarLg)
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.relive_story_spotlight, page.person.displayName), style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(pluralStringResource(CoreR.plurals.memory_count, page.memoryCount, page.memoryCount), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                    Spacer(Modifier.height(spacing.xs))
                    StoryButton(stringResource(R.string.relive_story_see_person)) { onOpenPerson(page.person.id) }
                }
                is StoryPage.ThenNow, is StoryPage.Milestone -> Unit
            }
        }
    }
}

@Composable
private fun StoryBackground(card: MemoryCard?) {
    val cover = card?.cover
    if (cover != null) {
        // Full-size photo so it stays sharp at this size; videos show their thumbnail frame.
        EncryptedMediaImage(mediaId = cover.id, thumbnail = cover.kind == MediaKind.VIDEO, contentDescription = null, modifier = Modifier.fillMaxSize())
    } else {
        CoverArt(style = card?.memory?.coverStyle ?: com.yaadein.domain.model.CoverStyle.DEFAULT, seed = card?.memory?.id ?: 0L, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun StoryButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
        Text(text)
        Spacer(Modifier.width(Yaadein.spacing.sm))
        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
    }
}
