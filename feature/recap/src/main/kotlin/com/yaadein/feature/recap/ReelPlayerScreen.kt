package com.yaadein.feature.recap

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.yaadein.core.ui.components.EmptyState
import com.yaadein.core.ui.illustrations.IllustrationKind
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.data.reels.ReelEntry
import com.yaadein.data.reels.ReelLibrary
import com.yaadein.data.reels.dataSourceFactory
import com.yaadein.data.reels.reelUri
import com.yaadein.core.ui.R as CoreR

/** Plays a reel kept in the library, full screen, with Share and Delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelPlayerRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: ReelPlayerViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareTitle = stringResource(CoreR.string.action_share)
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    val reel = state.reel
    if (confirmDelete && reel != null) {
        DeleteReelDialog(reel = reel, onConfirm = { confirmDelete = false; viewModel.delete() }, onDismiss = { confirmDelete = false })
    }
    Column(modifier = modifier.fillMaxSize().background(Color.Black)) {
        TopAppBar(
            title = { Text(reel?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(CoreR.string.action_back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White, navigationIconContentColor = Color.White),
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                state.loading -> CircularProgressIndicator()
                reel == null -> EmptyState(
                    illustration = IllustrationKind.FILM_REEL, title = stringResource(R.string.reel_missing_title),
                    message = stringResource(R.string.reel_missing_text), modifier = Modifier.background(MaterialTheme.colorScheme.surface).fillMaxSize(),
                )
                else -> ReelVideo(reel = reel, library = viewModel.library)
            }
        }
        if (reel != null) {
            Row(
                Modifier.fillMaxWidth().padding(Yaadein.spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Yaadein.spacing.md, Alignment.CenterHorizontally),
            ) {
                Button(onClick = { viewModel.share(shareTitle) { intent: Intent -> context.startActivity(intent) } }, enabled = !state.sharing) {
                    if (state.sharing) CircularProgressIndicator(Modifier.size(Yaadein.sizes.iconSm), color = MaterialTheme.colorScheme.onPrimary)
                    else Icon(Icons.Rounded.Share, contentDescription = null)
                    Spacer(Modifier.width(Yaadein.spacing.sm))
                    Text(stringResource(CoreR.string.action_share))
                }
                OutlinedButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                    Icon(Icons.Rounded.Delete, contentDescription = null)
                    Spacer(Modifier.width(Yaadein.spacing.sm))
                    Text(stringResource(R.string.reel_delete))
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun ReelVideo(reel: ReelEntry, library: ReelLibrary) {
    val context = LocalContext.current
    val player = remember(reel.id) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(library.dataSourceFactory()))
            .build()
            .apply { setMediaItem(MediaItem.fromUri(reelUri(reel))); prepare(); playWhenReady = true }
    }
    DisposableEffect(reel.id) { onDispose { player.release() } }
    // Pause when the app goes to the background (or locks); the user resumes with the controls.
    LifecycleStartEffect(player) { onStopOrDispose { player.pause() } }
    AndroidView(
        factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true; setShowNextButton(false); setShowPreviousButton(false) } },
        modifier = Modifier.fillMaxSize(),
    )
}
