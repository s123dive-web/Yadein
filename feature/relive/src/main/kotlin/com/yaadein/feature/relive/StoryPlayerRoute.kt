package com.yaadein.feature.relive

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.yaadein.data.media.EncryptedDataSource
import com.yaadein.domain.model.Media

/**
 * The full-screen story player with its music and muted video clips.
 *
 * [bundledTracks] are raw resource ids of the app's own music (they live with the reel maker, so the app passes them
 * in); the story of each ring gets one of them unless the user chose a song from "Your songs". No sound starts by
 * itself while the phone is on silent or vibrate, and nothing moves by itself when system animations are off.
 */
@Composable
fun StoryPlayerRoute(
    onClose: () -> Unit,
    onOpenMemory: (Long) -> Unit,
    onRecordVoice: (memoryId: Long) -> Unit,
    onMakeReel: (ReelScope) -> Unit,
    modifier: Modifier = Modifier,
    bundledTracks: List<Int> = emptyList(),
    viewModel: StoryPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(state.finished) { if (state.finished) onClose() }

    val motion = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    // Silent or vibrate: the story plays without sound until the user asks for it with the speaker button.
    val phoneSilent = remember { phoneIsSilent(context) }
    var askedForSound by remember { mutableStateOf(false) }
    val soundOn = !state.muted && (!phoneSilent || askedForSound)

    val songPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        // Keep read access after a restart: the song is only ever read from where it is.
        uris.forEach { uri -> runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        if (uris.isNotEmpty()) askedForSound = true
        viewModel.addSongs(uris)
    }

    val musicUri: Uri? = remember(state.musicUri, state.ringIndex, bundledTracks) {
        state.musicUri?.let(Uri::parse)
            ?: bundledTracks.takeIf { it.isNotEmpty() }?.let { tracks -> Uri.parse("android.resource://${context.packageName}/${tracks[state.ringIndex % tracks.size]}") }
    }
    if (!state.loading && !state.finished) StoryMusic(uri = musicUri, soundOn = soundOn)

    val dataSourceFactory = remember { EncryptedDataSource.Factory(viewModel.db, viewModel.mediaStore) }
    StoryPlayerScreen(
        state = state,
        actions = StoryPlayerActions(
            onClose = onClose,
            onNextRing = viewModel::nextRing,
            onPreviousRing = viewModel::previousRing,
            onOpenMemory = onOpenMemory,
            onToggleFavorite = viewModel::toggleFavorite,
            onAddNote = viewModel::addNote,
            onRecordVoice = onRecordVoice,
            onMakeReel = { page -> onMakeReel(viewModel.reelScope(page)) },
            onToggleSound = {
                if (soundOn) viewModel.setMuted(true) else { askedForSound = true; viewModel.setMuted(false) }
            },
            onChooseMusic = { uri -> askedForSound = true; viewModel.chooseMusic(uri) },
            onAddSongs = { runCatching { songPicker.launch(arrayOf("audio/*")) } },
        ),
        modifier = modifier,
        soundOn = soundOn,
        motion = motion,
        video = { media, playing -> StoryVideo(media = media, dataSourceFactory = dataSourceFactory, playing = playing) },
    )
}

private fun phoneIsSilent(context: Context): Boolean =
    runCatching { (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.ringerMode != AudioManager.RINGER_MODE_NORMAL }.getOrDefault(false)

/** Quiet enough to sit under a story. */
private const val MUSIC_VOLUME = 0.6f

/** The music under a story: loops, takes audio focus only while it is audible, and stops when the screen is left. */
@Composable
private fun StoryMusic(uri: Uri?, soundOn: Boolean) {
    if (uri == null) return
    val context = LocalContext.current
    val player = remember(uri) {
        runCatching {
            ExoPlayer.Builder(context).build().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
                setMediaItem(MediaItem.fromUri(uri))
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
            }
        }.getOrNull()
    }
    DisposableEffect(player) { onDispose { player?.release() } }
    LaunchedEffect(player, soundOn) {
        player?.volume = if (soundOn) MUSIC_VOLUME else 0f
        player?.playWhenReady = soundOn
    }
    LifecycleStartEffect(player, soundOn) {
        if (soundOn) player?.play()
        onStopOrDispose { player?.pause() }
    }
}

/** A video page: the memory's clip from the vault, muted, filling the screen, playing while the page is not paused. */
@OptIn(UnstableApi::class)
@Composable
private fun StoryVideo(media: Media, dataSourceFactory: androidx.media3.datasource.DataSource.Factory, playing: Boolean) {
    val context = LocalContext.current
    val player = remember(media.id) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(EncryptedDataSource.uriFor(media.id)))
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
            }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player, playing) { player.playWhenReady = playing }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
