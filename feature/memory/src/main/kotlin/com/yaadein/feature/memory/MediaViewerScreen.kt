package com.yaadein.feature.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.yaadein.core.ui.components.PersonAvatar
import com.yaadein.core.ui.media.EncryptedMediaImage
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.media.EncryptedDataSource
import com.yaadein.data.media.MediaStore
import com.yaadein.domain.model.Media
import com.yaadein.domain.model.MediaKind
import com.yaadein.domain.model.Person
import com.yaadein.domain.repository.MediaRepository
import com.yaadein.domain.repository.MemoryRepository
import com.yaadein.domain.repository.PersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.yaadein.core.ui.R as CoreR

data class MediaViewerUiState(
    val media: List<Media> = emptyList(),
    val people: List<Person> = emptyList(),
    val taggedByMedia: Map<Long, Set<Long>> = emptyMap(),
)

@HiltViewModel
class MediaViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    memoryRepository: MemoryRepository,
    private val mediaRepository: MediaRepository,
    personRepository: PersonRepository,
    val db: AppDatabase,
    val mediaStore: MediaStore,
) : ViewModel() {
    val memoryId: Long = checkNotNull(savedStateHandle["memoryId"])
    val initialIndex: Int = savedStateHandle["index"] ?: 0
    private val tagged = MutableStateFlow<Map<Long, Set<Long>>>(emptyMap())

    val state: StateFlow<MediaViewerUiState> = combine(
        memoryRepository.observeDetail(memoryId), personRepository.observeAll(), tagged,
    ) { detail, people, tags -> MediaViewerUiState(media = detail?.media.orEmpty(), people = people, taggedByMedia = tags) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaViewerUiState())

    fun loadTags(mediaId: Long) {
        viewModelScope.launch {
            val ids = db.mediaDao().personIds(mediaId).toSet()
            tagged.value = tagged.value + (mediaId to ids)
        }
    }

    fun toggleTag(mediaId: Long, personId: Long) {
        viewModelScope.launch {
            val current = tagged.value[mediaId] ?: db.mediaDao().personIds(mediaId).toSet()
            val next = if (personId in current) current - personId else current + personId
            mediaRepository.setPeople(mediaId, next.toList())
            tagged.value = tagged.value + (mediaId to next)
        }
    }

    fun setCaption(mediaId: Long, caption: String) { viewModelScope.launch { mediaRepository.updateCaption(mediaId, caption) } }
    fun remove(mediaId: Long, onDone: () -> Unit) { viewModelScope.launch { mediaRepository.delete(mediaId); onDone() } }
}

@Composable
fun MediaViewerRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: MediaViewerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MediaViewerScreen(
        state = state, initialIndex = viewModel.initialIndex, onBack = onBack,
        dataSourceFactory = remember { EncryptedDataSource.Factory(viewModel.db, viewModel.mediaStore) },
        onLoadTags = viewModel::loadTags, onToggleTag = viewModel::toggleTag, onCaption = viewModel::setCaption,
        onRemove = { id -> viewModel.remove(id) { if (state.media.size <= 1) onBack() } },
        modifier = modifier,
    )
}

@Composable
fun MediaViewerScreen(
    state: MediaViewerUiState,
    initialIndex: Int,
    onBack: () -> Unit,
    dataSourceFactory: androidx.media3.datasource.DataSource.Factory,
    onLoadTags: (Long) -> Unit,
    onToggleTag: (Long, Long) -> Unit,
    onCaption: (Long, String) -> Unit,
    onRemove: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = Yaadein.spacing
    if (state.media.isEmpty()) { Box(modifier.fillMaxSize().background(Color.Black)); return }
    val pager = rememberPagerState(initialPage = initialIndex.coerceIn(0, state.media.lastIndex)) { state.media.size }
    var chrome by remember { mutableStateOf(true) }
    var editingCaption by remember { mutableStateOf(false) }
    var tagging by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val current = state.media[pager.currentPage.coerceIn(0, state.media.lastIndex)]
    LaunchedEffect(current.id) { onLoadTags(current.id) }
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { state.media[it].id }) { page ->
            val m = state.media[page]
            if (m.kind == MediaKind.VIDEO) {
                VideoPage(media = m, dataSourceFactory = dataSourceFactory, active = page == pager.currentPage)
            } else {
                ZoomableImage(media = m, onTap = { chrome = !chrome })
            }
        }
        if (chrome) {
            Row(modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(spacing.sm).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(CoreR.string.action_back), tint = Color.White) }
                Text(stringResource(R.string.viewer_of, pager.currentPage + 1, state.media.size), color = Color.White, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { tagging = !tagging }) { Icon(Icons.Rounded.People, contentDescription = stringResource(R.string.viewer_tag_people), tint = Color.White) }
                IconButton(onClick = { editingCaption = true }) { Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.viewer_caption_hint), tint = Color.White) }
                IconButton(onClick = { confirmRemove = true }) { Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.viewer_remove), tint = Color.White) }
            }
            Column(modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(spacing.lg).fillMaxWidth()) {
                if (current.caption.isNotBlank()) Text(current.caption, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                if (tagging) {
                    Spacer(Modifier.width(spacing.sm))
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        items(state.people.size, key = { state.people[it].id }) { i ->
                            val p = state.people[i]
                            val selected = p.id in (state.taggedByMedia[current.id] ?: emptySet())
                            FilterChip(selected = selected, onClick = { onToggleTag(current.id, p.id) }, label = { Text(p.displayName) }, leadingIcon = { PersonAvatar(person = p, size = Yaadein.sizes.iconLg) })
                        }
                    }
                }
            }
        }
    }
    if (editingCaption) {
        var text by remember(current.id) { mutableStateOf(current.caption) }
        AlertDialog(
            onDismissRequest = { editingCaption = false },
            title = { Text(stringResource(R.string.viewer_caption_hint)) },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { onCaption(current.id, text); editingCaption = false }) { Text(stringResource(CoreR.string.action_save)) } },
            dismissButton = { TextButton(onClick = { editingCaption = false }) { Text(stringResource(CoreR.string.action_cancel)) } },
        )
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.viewer_remove)) },
            confirmButton = { TextButton(onClick = { confirmRemove = false; onRemove(current.id) }) { Text(stringResource(CoreR.string.action_delete)) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(CoreR.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ZoomableImage(media: Media, onTap: () -> Unit) {
    var scale by remember(media.id) { mutableFloatStateOf(1f) }
    var offsetX by remember(media.id) { mutableFloatStateOf(0f) }
    var offsetY by remember(media.id) { mutableFloatStateOf(0f) }
    val description = media.caption.ifBlank { stringResource(R.string.memory_photos) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(media.id) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) { offsetX += pan.x; offsetY += pan.y } else { offsetX = 0f; offsetY = 0f }
                }
            }
            .pointerInput(media.id) { detectTapGestures(onTap = { onTap() }, onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offsetX = 0f; offsetY = 0f }) }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        EncryptedMediaImage(
            mediaId = media.id, thumbnail = false, contentDescription = null,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY },
            contentScale = ContentScale.Fit,
        )
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoPage(media: Media, dataSourceFactory: androidx.media3.datasource.DataSource.Factory, active: Boolean) {
    val context = LocalContext.current
    val player = remember(media.id) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply { setMediaItem(MediaItem.fromUri(EncryptedDataSource.uriFor(media.id))); prepare() }
    }
    DisposableEffect(media.id) { onDispose { player.release() } }
    LaunchedEffect(active) { player.playWhenReady = active; if (!active) player.pause() }
    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true; setShowNextButton(false); setShowPreviousButton(false) } },
        modifier = Modifier.fillMaxSize(),
    )
}
