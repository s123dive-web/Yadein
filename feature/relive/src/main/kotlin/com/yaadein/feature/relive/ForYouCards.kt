package com.yaadein.feature.relive

import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.data.reels.ReelEntry
import com.yaadein.data.reels.ReelLibrary
import com.yaadein.data.reels.dataSourceFactory
import com.yaadein.data.reels.reelUri

/**
 * The newest reel playing silently on a loop at the top of Relive; tap to open it full screen with sound.
 * Keeps the reels the app makes automatically in front of people instead of buried in a library.
 */
@OptIn(UnstableApi::class)
@Composable
fun LatestReelCard(reel: ReelEntry, library: ReelLibrary, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(reel.id) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(library.dataSourceFactory()))
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(reelUri(reel)))
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ONE
                prepare()
                playWhenReady = true
            }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Box(
        modifier = modifier.fillMaxWidth().height(Yaadein.sizes.cardCoverHeight * 1.4f).clip(RoundedCornerShape(Yaadein.radius.lg)).clickable(onClick = onPlay),
    ) {
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
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))) {
            Row(Modifier.padding(Yaadein.spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.relive_made_for_you), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
                    Text(reel.title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2)
                }
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.relive_play_reel), tint = Color.White, modifier = Modifier.width(Yaadein.sizes.iconLg))
            }
        }
    }
}

/** This week's story prompt, with a button that opens voice recording. */
@Composable
fun PromptCard(text: String, onRecord: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = Yaadein.spacing
    Surface(shape = RoundedCornerShape(Yaadein.radius.lg), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(stringResource(R.string.notification_prompt_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Button(onClick = onRecord) {
                Icon(Icons.Rounded.Mic, contentDescription = null)
                Spacer(Modifier.width(spacing.sm))
                Text(stringResource(R.string.relive_record_story))
            }
        }
    }
}
