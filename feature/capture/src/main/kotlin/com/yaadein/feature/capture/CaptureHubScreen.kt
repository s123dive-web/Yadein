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
)

/** The single + button opens this sheet: three ways in, plus quick capture and the past wizard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureHubSheet(actions: CaptureActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        CaptureHubContent(actions = actions)
    }
}

@Composable
fun CaptureHubContent(actions: CaptureActions, modifier: Modifier = Modifier) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier.padding(horizontal = spacing.gutter).padding(bottom = spacing.xxl), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(stringResource(R.string.hub_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.hub_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(spacing.sm))
        HubOption(Icons.Rounded.Bolt, stringResource(R.string.hub_quick), stringResource(R.string.hub_quick_sub), highlight = true, onClick = actions.onQuick)
        HubOption(Icons.Rounded.PhotoLibrary, stringResource(R.string.hub_photos), stringResource(R.string.hub_photos_sub), onClick = actions.onPhotos)
        HubOption(Icons.Rounded.Event, stringResource(R.string.hub_event), stringResource(R.string.hub_event_sub), onClick = actions.onEvent)
        HubOption(Icons.Rounded.Notes, stringResource(R.string.hub_text), stringResource(R.string.hub_text_sub), onClick = actions.onTextOnly)
        HubOption(Icons.Rounded.History, stringResource(R.string.hub_past), stringResource(R.string.hub_past_sub), onClick = actions.onPastWizard)
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
