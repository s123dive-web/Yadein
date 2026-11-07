package com.yaadein.feature.capture

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.yaadein.core.ui.theme.Yaadein

/** Actions the hub can start. The app wires each to a navigation route. */
data class CaptureActions(
    val onEvent: () -> Unit,
    val onPhotos: () -> Unit,
    val onTextOnly: () -> Unit,
    val onQuick: () -> Unit,
    val onPastWizard: () -> Unit,
    /** Record a voice memory (null hides it). */
    val onVoice: (() -> Unit)? = null,
    /** Bring in a Google Photos or WhatsApp export (null hides it). */
    val onImport: (() -> Unit)? = null,
)

/** The single + button opens this sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureHubSheet(actions: CaptureActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        CaptureHubContent(actions = actions)
    }
}

/**
 * Two big, obvious ways in (photos, or a line of text), a voice memory, and the rest folded under "More ways" so the
 * sheet never asks someone to choose between five similar options.
 */
@Composable
fun CaptureHubContent(actions: CaptureActions, modifier: Modifier = Modifier, initiallyExpanded: Boolean = false) {
    val spacing = Yaadein.spacing
    var more by rememberSaveable { mutableStateOf(initiallyExpanded) }
    Column(modifier = modifier.padding(horizontal = spacing.gutter).padding(bottom = spacing.xxl), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(stringResource(R.string.hub_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.hub_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            BigOption(Icons.Rounded.PhotoLibrary, stringResource(R.string.hub_photos), stringResource(R.string.hub_photos_short), actions.onPhotos, primary = true, modifier = Modifier.weight(1f).fillMaxHeight())
            BigOption(Icons.Rounded.Bolt, stringResource(R.string.hub_quick), stringResource(R.string.hub_quick_short), actions.onQuick, primary = false, modifier = Modifier.weight(1f).fillMaxHeight())
        }
        actions.onVoice?.let { HubOption(Icons.Rounded.Mic, stringResource(R.string.hub_voice), stringResource(R.string.hub_voice_sub), onClick = it) }
        TextButton(onClick = { more = !more }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (more) R.string.hub_fewer else R.string.hub_more))
            Icon(if (more) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
        }
        AnimatedVisibility(visible = more) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                HubOption(Icons.Rounded.Event, stringResource(R.string.hub_event), stringResource(R.string.hub_event_sub), onClick = actions.onEvent)
                HubOption(Icons.Rounded.Notes, stringResource(R.string.hub_text), stringResource(R.string.hub_text_sub), onClick = actions.onTextOnly)
                HubOption(Icons.Rounded.History, stringResource(R.string.hub_past), stringResource(R.string.hub_past_sub), onClick = actions.onPastWizard)
                actions.onImport?.let { HubOption(Icons.Rounded.Download, stringResource(R.string.hub_import), stringResource(R.string.hub_import_sub), onClick = it) }
            }
        }
    }
}

@Composable
private fun BigOption(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, primary: Boolean, modifier: Modifier = Modifier) {
    val spacing = Yaadein.spacing
    Surface(
        shape = RoundedCornerShape(Yaadein.radius.lg),
        color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.clickable(onClick = onClick, role = Role.Button),
    ) {
        Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(Yaadein.sizes.iconLg))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun HubOption(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, highlight: Boolean = false) {
    val spacing = Yaadein.spacing
    Surface(
        shape = RoundedCornerShape(Yaadein.radius.lg),
        color = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick, role = Role.Button),
    ) {
        Row(modifier = Modifier.padding(spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Yaadein.sizes.iconLg),
            )
            Spacer(Modifier.width(spacing.lg))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
