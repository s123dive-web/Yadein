package com.yaadein.feature.recap

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
    val whatsAppAvailable = rememberWhatsAppAvailable()
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
            // A reel that fits a WhatsApp status (≤ 30 s, ≤ 16 MB) leads with WhatsApp; otherwise Share leads.
            val whatsAppFirst = whatsAppAvailable && WhatsAppShare.fitsStatus(reel.durationMs, reel.sizeBytes)
            val sendToWhatsApp = {
                viewModel.export { uri ->
                    if (!WhatsAppShare.sendToWhatsApp(context, uri, shareTitle)) Toast.makeText(context, R.string.recap_whatsapp_missing, Toast.LENGTH_SHORT).show()
                }
            }
            val share = { viewModel.share(shareTitle) { intent: Intent -> context.startActivity(intent) } }
            Column(Modifier.fillMaxWidth().padding(Yaadein.spacing.lg), verticalArrangement = Arrangement.spacedBy(Yaadein.spacing.sm)) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Yaadein.spacing.md, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(Yaadein.spacing.xs),
                ) {
                    if (whatsAppFirst) {
                        Button(onClick = sendToWhatsApp, enabled = !state.sharing) {
                            if (state.sharing) CircularProgressIndicator(Modifier.size(Yaadein.sizes.iconSm), color = MaterialTheme.colorScheme.onPrimary)
                            else Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null)
                            Spacer(Modifier.width(Yaadein.spacing.sm))
                            Text(stringResource(R.string.recap_send_whatsapp))
                        }
                        FilledTonalButton(onClick = share, enabled = !state.sharing) {
                            Icon(Icons.Rounded.Share, contentDescription = null)
                            Spacer(Modifier.width(Yaadein.spacing.sm))
                            Text(stringResource(CoreR.string.action_share))
                        }
                    } else {
                        Button(onClick = share, enabled = !state.sharing) {
                            if (state.sharing) CircularProgressIndicator(Modifier.size(Yaadein.sizes.iconSm), color = MaterialTheme.colorScheme.onPrimary)
                            else Icon(Icons.Rounded.Share, contentDescription = null)
                            Spacer(Modifier.width(Yaadein.spacing.sm))
                            Text(stringResource(CoreR.string.action_share))
                        }
                        if (whatsAppAvailable) {
                            FilledTonalButton(onClick = sendToWhatsApp, enabled = !state.sharing) {
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null)
                                Spacer(Modifier.width(Yaadein.spacing.sm))
                                Text(stringResource(R.string.recap_send_whatsapp))
                            }
                        }
                    }
                    OutlinedButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                        Spacer(Modifier.width(Yaadein.spacing.sm))
                        Text(stringResource(R.string.reel_delete))
                    }
                }
                if (whatsAppAvailable && WhatsAppShare.tooBigForStatus(reel.sizeBytes)) {
                    Text(stringResource(R.string.recap_whatsapp_too_big), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
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
