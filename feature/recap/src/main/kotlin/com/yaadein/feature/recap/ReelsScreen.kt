package com.yaadein.feature.recap

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaadein.core.ui.media.ThumbnailImage
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.data.reels.ReelEntry
import com.yaadein.domain.recap.RecapTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import com.yaadein.core.ui.R as CoreR

@Composable
fun ReelsRoute(
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReelsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareTitle = stringResource(CoreR.string.action_share)
    ReelsScreen(
        state = state, onBack = onBack, onPlay = onPlay, onCreate = onCreate,
        onShare = { entry -> viewModel.share(entry, shareTitle) { intent: Intent -> context.startActivity(intent) } },
        onDelete = viewModel::delete,
        modifier = modifier,
        whatsAppAvailable = rememberWhatsAppAvailable(),
        onSendWhatsApp = { entry ->
            viewModel.export(entry) { uri ->
                if (!WhatsAppShare.sendToWhatsApp(context, uri, shareTitle)) Toast.makeText(context, R.string.recap_whatsapp_missing, Toast.LENGTH_SHORT).show()
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelsScreen(
    state: ReelsUiState,
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
    onCreate: () -> Unit,
    onShare: (ReelEntry) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** WhatsApp (or WhatsApp Business) is installed: every card gets a "Send to WhatsApp" action. */
    whatsAppAvailable: Boolean = false,
    onSendWhatsApp: (ReelEntry) -> Unit = {},
) {
    val spacing = Yaadein.spacing
    var confirmDelete by remember { mutableStateOf<ReelEntry?>(null) }
    confirmDelete?.let { reel ->
        DeleteReelDialog(reel = reel, onConfirm = { onDelete(reel.id); confirmDelete = null }, onDismiss = { confirmDelete = null })
    }
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.reels_library_title), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(CoreR.string.action_back)) } },
            actions = {
                TextButton(onClick = onCreate) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(spacing.xs))
                    Text(stringResource(R.string.recap_render))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = spacing.gutter, end = spacing.gutter, bottom = spacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item(key = "auto-title") {
                SectionHeader(stringResource(R.string.reels_made_for_you), stringResource(R.string.reels_made_for_you_hint))
            }
            item(key = "auto") {
                when {
                    state.auto.isEmpty() && state.preparing -> PreparingCard()
                    state.auto.isEmpty() -> Text(stringResource(R.string.reels_auto_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            items(state.auto.size, key = { state.auto[it].id }) { i ->
                                val reel = state.auto[i]
                                ReelCard(
                                    reel = reel, sharing = state.sharing == reel.id, onPlay = { onPlay(reel.id) }, onShare = { onShare(reel) },
                                    onSendWhatsApp = if (whatsAppAvailable) ({ onSendWhatsApp(reel) }) else null,
                                    onDelete = null, modifier = Modifier.width(AUTO_CARD_WIDTH),
                                )
                            }
                        }
                        if (state.preparing) PreparingCard()
                    }
                }
            }
            item(key = "mine-title") {
                SectionHeader(stringResource(R.string.reels_made_by_you), stringResource(R.string.reels_made_by_you_hint, com.yaadein.data.reels.ReelLibrary.MAX_USER_REELS))
            }
            if (state.mine.isEmpty()) {
                item(key = "mine-empty") {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text(stringResource(R.string.reels_mine_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onCreate) { Icon(Icons.Rounded.Add, contentDescription = null); Spacer(Modifier.width(spacing.sm)); Text(stringResource(R.string.recap_render)) }
                    }
                }
            } else {
                val rows = state.mine.chunked(2)
                items(rows.size, key = { "row-" + rows[it].first().id }) { r ->
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        rows[r].forEach { reel ->
                            ReelCard(
                                reel = reel, sharing = state.sharing == reel.id, onPlay = { onPlay(reel.id) }, onShare = { onShare(reel) },
                                onSendWhatsApp = if (whatsAppAvailable) ({ onSendWhatsApp(reel) }) else null,
                                onDelete = { confirmDelete = reel }, modifier = Modifier.weight(1f),
                            )
                        }
                        if (rows[r].size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, hint: String) {
    Column(Modifier.padding(top = Yaadein.spacing.sm)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PreparingCard() {
    val spacing = Yaadein.spacing
    Surface(shape = RoundedCornerShape(Yaadein.radius.lg), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                Spacer(Modifier.width(spacing.sm))
                Text(stringResource(R.string.auto_reel_notification), style = MaterialTheme.typography.titleSmall)
            }
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

/** 9:16 cover with play badge, title, date and actions. [onSendWhatsApp] is null when WhatsApp is not installed. */
@Composable
private fun ReelCard(
    reel: ReelEntry,
    sharing: Boolean,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onSendWhatsApp: (() -> Unit)? = null,
) {
    val spacing = Yaadein.spacing
    val playLabel = stringResource(R.string.reel_play_named, reel.title)
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(Yaadein.radius.lg)).clickable(onClickLabel = playLabel, onClick = onPlay),
        ) {
            ReelCover(reel, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
            Icon(
                Icons.Rounded.PlayArrow, contentDescription = playLabel, tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(Yaadein.sizes.avatarMd).background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(Yaadein.radius.pill)).padding(spacing.sm),
            )
            Text(
                formatReelDuration(reel.durationMs), style = MaterialTheme.typography.labelMedium, color = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(spacing.sm),
            )
        }
        Spacer(Modifier.height(spacing.xs))
        Text(reel.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(formatReelDate(reel.createdAt, LocalLocale.current.platformLocale), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row {
            if (sharing) {
                Box(Modifier.size(Yaadein.sizes.touchTarget), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(Yaadein.sizes.iconSm)) }
            } else {
                IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.reel_share_named, reel.title)) }
                if (onSendWhatsApp != null) {
                    IconButton(onClick = onSendWhatsApp) { Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.reel_whatsapp_named, reel.title)) }
                }
            }
            if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.reel_delete_named, reel.title)) }
        }
    }
}

@Composable
internal fun ReelCover(reel: ReelEntry, modifier: Modifier = Modifier) {
    val theme = runCatching { RecapTheme.valueOf(reel.theme) }.getOrDefault(RecapTheme.WARM)
    val (a, b) = themeColors(theme)
    Box(modifier.background(Brush.linearGradient(listOf(a, b)))) {
        reel.coverMediaId?.let { ThumbnailImage(mediaId = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
internal fun DeleteReelDialog(reel: ReelEntry, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reel_delete_title, reel.title)) },
        text = { Text(stringResource(R.string.reel_delete_text)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.reel_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.action_cancel)) } },
    )
}

internal fun formatReelDuration(ms: Long): String {
    val s = (ms + 500) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

internal fun formatReelDate(epochMs: Long, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate())

private val AUTO_CARD_WIDTH = 150.dp
