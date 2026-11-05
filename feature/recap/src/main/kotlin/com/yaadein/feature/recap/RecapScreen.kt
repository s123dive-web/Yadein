package com.yaadein.feature.recap

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaadein.core.ui.components.EmptyState
import com.yaadein.core.ui.illustrations.IllustrationKind
import com.yaadein.core.ui.media.EncryptedMediaImage
import com.yaadein.core.ui.media.ThumbnailImage
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.recap.RecapLength
import com.yaadein.domain.recap.RecapPlan
import com.yaadein.domain.recap.RecapSegment
import com.yaadein.domain.recap.RecapTheme
import kotlinx.coroutines.delay
import com.yaadein.core.ui.R as CoreR

@Composable
fun RecapRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: RecapViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareTitle = stringResource(CoreR.string.action_share)
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            viewModel.setMusic(MusicChoice.Own(uri, uri.lastPathSegment))
        }
    }
    RecapScreen(
        state = state, onBack = onBack,
        onScope = viewModel::setScope, onPerson = viewModel::setPerson, onGroup = viewModel::setGroup, onYear = viewModel::setYear,
        onTheme = viewModel::setTheme, onLength = viewModel::setLength, onIncludeVideos = viewModel::setIncludeVideos,
        onMusic = viewModel::setMusic, onPickOwnMusic = { audioPicker.launch(arrayOf("audio/*")) }, onTitle = viewModel::setTitle,
        onRender = viewModel::startRender, onCancel = viewModel::cancelRender, onReset = viewModel::resetRender,
        onShare = { uri ->
            val share = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(share, shareTitle))
        },
        years = viewModel.yearsAvailable(),
        modifier = modifier,
    )
}

@Composable
fun themeName(theme: RecapTheme): String = when (theme) {
    RecapTheme.WARM -> stringResource(R.string.theme_warm)
    RecapTheme.MINIMAL -> stringResource(R.string.theme_minimal)
    RecapTheme.FESTIVE -> stringResource(R.string.theme_festive)
    RecapTheme.CLASSIC_FILM -> stringResource(R.string.theme_classic)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapScreen(
    state: RecapUiState,
    onBack: () -> Unit,
    onScope: (Scope) -> Unit,
    onPerson: (com.yaadein.domain.model.Person?) -> Unit,
    onGroup: (com.yaadein.domain.model.Group?) -> Unit,
    onYear: (Int?) -> Unit,
    onTheme: (RecapTheme) -> Unit,
    onLength: (RecapLength) -> Unit,
    onIncludeVideos: (Boolean) -> Unit,
    onMusic: (MusicChoice) -> Unit,
    onPickOwnMusic: () -> Unit,
    onTitle: (String) -> Unit,
    onRender: () -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onShare: (android.net.Uri) -> Unit,
    years: List<Int>,
    modifier: Modifier = Modifier,
) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.recap_title), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(CoreR.string.action_back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        if (state.loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.gutter).widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(spacing.lg)) {
            // ---- preview ----
            val plan = state.plan
            if (plan != null) {
                InlinePreview(plan = plan, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(Yaadein.radius.lg)))
                Storyboard(plan = plan)
                Text(
                    text = stringResource(R.string.recap_summary, plan.mediaCount, formatDuration(plan.totalDurationMs)),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                EmptyState(illustration = IllustrationKind.FILM_REEL, title = stringResource(R.string.recap_empty_title), message = stringResource(R.string.recap_empty_message))
            }
            // ---- render status ----
            when (val r = state.render) {
                is RenderStatus.Running -> Surface(shape = RoundedCornerShape(Yaadein.radius.lg), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text(stringResource(R.string.recap_rendering, r.progress), style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(progress = { r.progress / 100f }, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.recap_rendering_help), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onCancel) { Text(stringResource(CoreR.string.action_cancel)) }
                    }
                }
                is RenderStatus.Done -> Surface(shape = RoundedCornerShape(Yaadein.radius.lg), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text(stringResource(R.string.recap_done_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.recap_done_text), style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            Button(onClick = { onShare(r.uri) }) { Icon(Icons.Rounded.Share, contentDescription = null); Spacer(Modifier.width(spacing.sm)); Text(stringResource(CoreR.string.action_share)) }
                            TextButton(onClick = onReset) { Text(stringResource(R.string.recap_make_another)) }
                        }
                    }
                }
                is RenderStatus.Failed -> Surface(shape = RoundedCornerShape(Yaadein.radius.lg), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text(stringResource(R.string.recap_failed_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.recap_failed_text, r.message), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = onReset) { Text(stringResource(CoreR.string.action_retry)) }
                    }
                }
                RenderStatus.Idle -> Button(onClick = onRender, enabled = plan != null, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Movie, contentDescription = null); Spacer(Modifier.width(spacing.sm)); Text(stringResource(R.string.recap_render))
                }
            }
            // ---- scope ----
            Section(stringResource(R.string.recap_scope)) {
                FilterChip(selected = state.scope == Scope.ALL, onClick = { onScope(Scope.ALL) }, label = { Text(stringResource(R.string.scope_all)) })
                state.people.forEach { p -> FilterChip(selected = state.scope == Scope.PERSON && state.person?.id == p.id, onClick = { onPerson(p) }, label = { Text(p.displayName) }) }
                state.groups.forEach { g -> FilterChip(selected = state.scope == Scope.GROUP && state.group?.id == g.id, onClick = { onGroup(g) }, label = { Text("${g.emoji} ${g.name}") }) }
                if (state.memory != null) FilterChip(selected = state.scope == Scope.MEMORY, onClick = { onScope(Scope.MEMORY) }, label = { Text(state.memory.title, maxLines = 1, overflow = TextOverflow.Ellipsis) })
            }
            Section(stringResource(R.string.recap_year)) {
                FilterChip(selected = state.year == null, onClick = { onYear(null) }, label = { Text(stringResource(R.string.scope_any_year)) })
                years.take(12).forEach { y -> FilterChip(selected = state.year == y, onClick = { onYear(y) }, label = { Text(y.toString()) }) }
            }
            OutlinedTextField(value = state.title, onValueChange = onTitle, label = { Text(stringResource(R.string.recap_video_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            // ---- theme ----
            Section(stringResource(R.string.recap_theme)) {
                RecapTheme.entries.forEach { t -> ThemeSwatch(theme = t, selected = state.theme == t, onClick = { onTheme(t) }) }
            }
            // ---- music ----
            Section(stringResource(R.string.recap_music)) {
                bundledTracks.forEach { track ->
                    FilterChip(
                        selected = (state.music as? MusicChoice.Bundled)?.key == track.key,
                        onClick = { onMusic(MusicChoice.Bundled(track.key)) },
                        label = { Text(themeName(track.theme)) },
                        leadingIcon = { Icon(Icons.Rounded.MusicNote, contentDescription = null) },
                    )
                }
                FilterChip(selected = state.music is MusicChoice.Own, onClick = onPickOwnMusic, label = { Text((state.music as? MusicChoice.Own)?.name?.take(24) ?: stringResource(R.string.music_own)) })
                FilterChip(selected = state.music is MusicChoice.None, onClick = { onMusic(MusicChoice.None) }, label = { Text(stringResource(R.string.music_none)) })
            }
            Text(stringResource(R.string.music_licence), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // ---- length ----
            Section(stringResource(R.string.recap_length)) {
                RecapLength.entries.forEach { l ->
                    FilterChip(selected = state.length == l, onClick = { onLength(l) }, label = { Text(stringResource(R.string.recap_length_seconds, l.targetSeconds)) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.recap_include_videos), modifier = Modifier.weight(1f))
                Switch(checked = state.includeVideos, onCheckedChange = onIncludeVideos)
            }
            Spacer(Modifier.height(spacing.xxxl))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val spacing = Yaadein.spacing
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(spacing.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xs)) { content() }
    }
}

@Composable
fun themeColors(theme: RecapTheme): Pair<Color, Color> = when (theme) {
    RecapTheme.WARM -> Color(0xFF3B1A5C) to Color(0xFF7A3E9D)
    RecapTheme.MINIMAL -> Color(0xFF111111) to Color(0xFF2A2A2A)
    RecapTheme.FESTIVE -> Color(0xFF8A0F23) to Color(0xFFE07B00)
    RecapTheme.CLASSIC_FILM -> Color(0xFF1C1A17) to Color(0xFF3A342C)
}

@Composable
private fun ThemeSwatch(theme: RecapTheme, selected: Boolean, onClick: () -> Unit) {
    val (a, b) = themeColors(theme)
    val name = themeName(theme)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(Yaadein.radius.md))
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Yaadein.radius.md))
            .clickable(onClick = onClick)
            .semantics { contentDescription = name }
            .padding(Yaadein.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(width = 96.dp, height = 54.dp).clip(RoundedCornerShape(Yaadein.radius.sm)).background(Brush.linearGradient(listOf(a, b))))
        Spacer(Modifier.height(Yaadein.spacing.xs))
        Text(name, style = MaterialTheme.typography.labelLarge)
    }
}

/** Strip of thumbnails with durations. */
@Composable
private fun Storyboard(plan: RecapPlan) {
    val spacing = Yaadein.spacing
    LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
        items(plan.segments.size, key = { it }) { i ->
            val seg = plan.segments[i]
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(width = 72.dp, height = 44.dp).clip(RoundedCornerShape(Yaadein.radius.sm))) {
                    when (seg) {
                        is RecapSegment.TitleCard -> {
                            val (a, b) = themeColors(plan.theme)
                            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(a, b))), contentAlignment = Alignment.Center) {
                                Text(seg.title.take(10), style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1)
                            }
                        }
                        is RecapSegment.Photo -> ThumbnailImage(mediaId = seg.mediaId, contentDescription = seg.caption, modifier = Modifier.fillMaxSize())
                        is RecapSegment.Clip -> Box { ThumbnailImage(mediaId = seg.mediaId, contentDescription = seg.caption, modifier = Modifier.fillMaxSize()); Icon(Icons.Rounded.Movie, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(Yaadein.sizes.iconSm)) }
                    }
                }
                Text("${seg.durationMs / 1000}s", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Plays the plan inline: title cards in Compose, photos with the same Ken Burns framing the renderer uses. */
@Composable
fun InlinePreview(plan: RecapPlan, modifier: Modifier = Modifier) {
    var index by remember(plan) { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    val seg = plan.segments[index.coerceIn(0, plan.segments.lastIndex)]
    LaunchedEffect(plan, index, playing) {
        if (!playing) return@LaunchedEffect
        delay(seg.durationMs.coerceAtMost(6_000))
        index = (index + 1) % plan.segments.size
    }
    val (a, b) = themeColors(plan.theme)
    Box(modifier = modifier.background(Color.Black).clickable { playing = !playing }) {
        when (seg) {
            is RecapSegment.TitleCard -> Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(a, b))), contentAlignment = Alignment.CenterStart) {
                Column(Modifier.padding(horizontal = 32.dp)) {
                    Text(seg.title, style = MaterialTheme.typography.headlineMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (seg.subtitle.isNotBlank()) Text(seg.subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                }
            }
            is RecapSegment.Photo -> {
                var started by remember(seg) { mutableStateOf(false) }
                LaunchedEffect(seg) { started = true }
                val t by animateFloatAsState(targetValue = if (started) 1f else 0f, animationSpec = tween(seg.durationMs.toInt(), easing = LinearEasing), label = "kenburns")
                val zoom = seg.start.zoom + (seg.end.zoom - seg.start.zoom) * t
                val cx = seg.start.cx + (seg.end.cx - seg.start.cx) * t
                val cy = seg.start.cy + (seg.end.cy - seg.start.cy) * t
                EncryptedMediaImage(
                    mediaId = seg.mediaId, thumbnail = true, contentDescription = seg.caption,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = zoom; scaleY = zoom
                        translationX = -(cx - 0.5f) * size.width * zoom
                        translationY = -(cy - 0.5f) * size.height * zoom
                    },
                )
                seg.caption?.let { CaptionBar(it, seg.dateLabel) }
            }
            is RecapSegment.Clip -> {
                ThumbnailImage(mediaId = seg.mediaId, contentDescription = seg.caption, modifier = Modifier.fillMaxSize())
                Icon(Icons.Rounded.Movie, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(Yaadein.sizes.avatarLg))
                seg.caption?.let { CaptionBar(it, seg.dateLabel) }
            }
        }
        Text(
            text = "${index + 1}/${plan.segments.size}",
            style = MaterialTheme.typography.labelSmall, color = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(Yaadein.spacing.sm).background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(Yaadein.radius.pill)).padding(horizontal = Yaadein.spacing.sm, vertical = Yaadein.spacing.xxs),
        )
        if (!playing) Text(stringResource(R.string.recap_paused), color = Color.White, modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(Yaadein.radius.pill)).padding(Yaadein.spacing.sm))
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.CaptionBar(title: String, date: String?) {
    Column(
        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
            .padding(Yaadein.spacing.md),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!date.isNullOrBlank()) Text(date, style = MaterialTheme.typography.labelMedium, color = Color(0xFFE8A33D))
    }
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
