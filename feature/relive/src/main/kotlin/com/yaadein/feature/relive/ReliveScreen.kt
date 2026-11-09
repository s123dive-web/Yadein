package com.yaadein.feature.relive

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaadein.core.ui.components.CoverArt
import com.yaadein.core.ui.components.EmptyState
import com.yaadein.core.ui.components.MemoryCardView
import com.yaadein.core.ui.components.PersonAvatar
import com.yaadein.core.ui.illustrations.IllustrationKind
import com.yaadein.core.ui.media.ThumbnailImage
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.model.Person
import com.yaadein.domain.relive.CoverPick
import com.yaadein.domain.relive.RewindScope
import com.yaadein.core.ui.R as CoreR

/** Birthdays this close get a "Make a reel" button on their card. */
private const val BIRTHDAY_REEL_DAYS = 7

@Composable
fun ReliveRoute(
    onOpenMemory: (Long) -> Unit,
    onOpenPerson: (Long) -> Unit,
    onRecap: () -> Unit,
    onYear: (Int) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onReels: () -> Unit = {},
    onPlayReel: (String) -> Unit = {},
    onRecordPrompt: (text: String, personId: Long) -> Unit = { _, _ -> },
    onPlace: (String) -> Unit = {},
    onBirthdayReel: (personId: Long) -> Unit = {},
    viewModel: ReliveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Reload whenever Relive comes back into view (tab switch, back from a new memory, app resumed).
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val promptText = state.prompt?.let { com.yaadein.feature.relive.notify.OnThisDayWorker.promptText(context, it.index, it.person?.name, it.person?.relation) }
    ReliveScreen(
        state = state,
        onOpenMemory = onOpenMemory,
        onOpenPerson = onOpenPerson,
        onSurprise = viewModel::surpriseMe,
        onDismissRandom = viewModel::dismissRandom,
        onRecap = onRecap,
        onYear = onYear,
        contentPadding = contentPadding,
        modifier = modifier,
        onReels = onReels,
        latestReel = state.latestReel?.let { reel -> { LatestReelCard(reel = reel, library = viewModel.reelLibrary, onPlay = { onPlayReel(reel.id) }) } },
        onPlace = onPlace,
        promptCard = promptText?.let { text -> { PromptCard(text = text, onRecord = { onRecordPrompt(text, state.prompt?.person?.id ?: 0L) }) } },
        onBirthdayReel = onBirthdayReel,
        onDismissShakeHint = viewModel::dismissShakeHint,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReliveScreen(
    state: ReliveUiState,
    onOpenMemory: (Long) -> Unit,
    onOpenPerson: (Long) -> Unit,
    onSurprise: () -> Unit,
    onDismissRandom: () -> Unit,
    onRecap: () -> Unit,
    onYear: (Int) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    onReels: () -> Unit = {},
    /** Slots filled by the route (they need the vault's players); null hides them. */
    latestReel: (@Composable () -> Unit)? = null,
    promptCard: (@Composable () -> Unit)? = null,
    onPlace: (String) -> Unit = {},
    onBirthdayReel: (personId: Long) -> Unit = {},
    onDismissShakeHint: () -> Unit = {},
) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.relive_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.totalMemories == 0 -> EmptyState(
                illustration = IllustrationKind.KITE,
                title = stringResource(R.string.relive_empty_title),
                message = stringResource(R.string.relive_empty_message),
                modifier = Modifier.fillMaxSize().padding(contentPadding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = spacing.gutter, end = spacing.gutter, bottom = spacing.xxxl + contentPadding.calculateBottomPadding() + Yaadein.sizes.fabSize),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                // A birthday today or tomorrow is the most timely thing on the screen.
                val birthdaysFirst = state.upcomingBirthdays.firstOrNull()?.second?.let { it <= 1 } == true
                if (state.story.isNotEmpty()) {
                    item(key = "story") { DailyStoryCard(pages = state.story, onOpenMemory = onOpenMemory, onOpenPerson = onOpenPerson) }
                }
                if (birthdaysFirst) birthdaysItem(state.upcomingBirthdays, onOpenPerson, onBirthdayReel)
                latestReel?.let { card -> item(key = "latest-reel") { card() } }
                promptCard?.let { card -> item(key = "prompt") { card() } }
                item(key = "actions") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        FilledTonalButton(onClick = onSurprise) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null)
                            Spacer(Modifier.width(spacing.sm))
                            Text(stringResource(R.string.relive_surprise))
                        }
                        Button(onClick = onRecap) {
                            Icon(Icons.Rounded.Movie, contentDescription = null)
                            Spacer(Modifier.width(spacing.sm))
                            Text(stringResource(R.string.relive_make_recap))
                        }
                        FilledTonalButton(onClick = onReels) {
                            Icon(Icons.Rounded.VideoLibrary, contentDescription = null)
                            Spacer(Modifier.width(spacing.sm))
                            Text(stringResource(R.string.relive_your_reels))
                        }
                    }
                }
                if (state.showShakeHint) {
                    item(key = "shake-hint") { ShakeHintCard(onTry = onSurprise, onDismiss = onDismissShakeHint) }
                }
                if (!birthdaysFirst) birthdaysItem(state.upcomingBirthdays, onOpenPerson, onBirthdayReel)
                val rewind = state.rewind
                item(key = "otd-title") {
                    SectionTitle(
                        stringResource(
                            when (rewind?.scope) {
                                RewindScope.WEEK -> R.string.relive_this_week
                                RewindScope.MONTH -> R.string.relive_this_month
                                RewindScope.DAY, null -> R.string.relive_on_this_day
                            },
                        ),
                    )
                }
                if (rewind == null) {
                    item(key = "otd-empty") {
                        Text(
                            text = stringResource(R.string.relive_on_this_day_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(rewind.items.size, key = { "otd-" + rewind.items[it].card.memory.id }) { i ->
                        val item = rewind.items[i]
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            Text(
                                pluralStringResource(R.plurals.relive_years_ago, item.yearsAgo, item.yearsAgo),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            MemoryCardView(card = item.card, onClick = { onOpenMemory(item.card.memory.id) }, compact = true)
                        }
                    }
                }
                if (state.places.isNotEmpty()) {
                    item(key = "places") {
                        Column {
                            SectionTitle(stringResource(R.string.relive_places))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                items(state.places.size, key = { "place-" + state.places[it].place }) { i ->
                                    val p = state.places[i]
                                    PhotoTile(
                                        title = p.place,
                                        subtitle = pluralStringResource(CoreR.plurals.memory_count, p.count, p.count),
                                        cover = p.cover,
                                        onClick = { onPlace(p.place) },
                                        showPin = true,
                                    )
                                }
                            }
                        }
                    }
                }
                if (state.years.isNotEmpty()) {
                    item(key = "years") {
                        Column {
                            SectionTitle(stringResource(R.string.relive_years))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                items(state.years.size, key = { state.years[it].year }) { i ->
                                    val y = state.years[i]
                                    PhotoTile(
                                        title = y.year.toString(),
                                        subtitle = pluralStringResource(CoreR.plurals.memory_count, y.count, y.count),
                                        cover = y.cover,
                                        onClick = { onYear(y.year) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    state.random?.let { card ->
        val haptics = LocalHapticFeedback.current
        LaunchedEffect(state.randomRound) { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
        ModalBottomSheet(onDismissRequest = onDismissRandom) {
            Column(modifier = Modifier.padding(horizontal = spacing.gutter).padding(bottom = spacing.xxl)) {
                Text(stringResource(R.string.relive_random_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(spacing.md))
                // Each new pick pops in, so "Another one" feels like drawing a card rather than a refresh.
                AnimatedContent(
                    targetState = state.randomRound to card,
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), initialScale = 0.85f))
                            .togetherWith(fadeOut(tween(120)))
                    },
                    contentKey = { it.first },
                    label = "surprise",
                ) { (_, shown) ->
                    MemoryCardView(card = shown, onClick = { onDismissRandom(); onOpenMemory(shown.memory.id) })
                }
                Spacer(Modifier.height(spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    FilledTonalButton(onClick = onSurprise) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null)
                        Spacer(Modifier.width(spacing.sm))
                        Text(stringResource(R.string.relive_another))
                    }
                }
            }
        }
    }
}

/** Upcoming birthdays as cards; within a week, one tap makes a reel of that person. */
private fun androidx.compose.foundation.lazy.LazyListScope.birthdaysItem(
    birthdays: List<Pair<Person, Int>>,
    onOpenPerson: (Long) -> Unit,
    onBirthdayReel: (Long) -> Unit,
) {
    if (birthdays.isEmpty()) return
    item(key = "birthdays") {
        val spacing = Yaadein.spacing
        Column {
            SectionTitle(stringResource(R.string.relive_birthdays))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                items(birthdays.size, key = { birthdays[it].first.id }) { i ->
                    val (person, days) = birthdays[i]
                    Surface(
                        onClick = { onOpenPerson(person.id) },
                        shape = RoundedCornerShape(Yaadein.radius.lg),
                        color = if (days == 0) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Column(Modifier.padding(spacing.md).width(200.dp), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                PersonAvatar(person = person, size = Yaadein.sizes.avatarMd)
                                Text(
                                    if (days == 0) stringResource(R.string.relive_birthday_today, person.displayName)
                                    else stringResource(R.string.relive_birthday_in, person.displayName, days),
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (days <= BIRTHDAY_REEL_DAYS) {
                                Button(onClick = { onBirthdayReel(person.id) }) {
                                    Icon(Icons.Rounded.Movie, contentDescription = null)
                                    Spacer(Modifier.width(spacing.sm))
                                    Text(stringResource(R.string.relive_birthday_reel))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A year or a place: its photo (or cover art) with the name and count on a scrim. */
@Composable
private fun PhotoTile(title: String, subtitle: String, cover: CoverPick?, onClick: () -> Unit, showPin: Boolean = false) {
    val spacing = Yaadein.spacing
    Box(
        modifier = Modifier
            .width(148.dp)
            .height(112.dp)
            .clip(RoundedCornerShape(Yaadein.radius.md))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        val mediaId = cover?.mediaId
        if (mediaId != null) {
            ThumbnailImage(mediaId = mediaId, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            CoverArt(style = cover?.coverStyle ?: com.yaadein.domain.model.CoverStyle.DEFAULT, seed = cover?.memoryId ?: title.hashCode().toLong(), modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showPin) {
                    Icon(Icons.Rounded.Place, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f), maxLines = 1)
        }
    }
}

/** One-time tip that shaking the phone opens a random memory. */
@Composable
private fun ShakeHintCard(onTry: () -> Unit, onDismiss: () -> Unit) {
    val spacing = Yaadein.spacing
    Surface(shape = RoundedCornerShape(Yaadein.radius.lg), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = spacing.lg, top = spacing.md, bottom = spacing.sm, end = spacing.xs), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.ScreenRotation, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(top = spacing.xs))
            Spacer(Modifier.width(spacing.md))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.relive_shake_hint_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(stringResource(R.string.shake_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                TextButton(onClick = onTry) { Text(stringResource(R.string.relive_surprise)) }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.relive_got_it), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Yaadein.spacing.sm, bottom = Yaadein.spacing.sm).semantics { heading() },
    )
}
