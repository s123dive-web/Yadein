package com.yaadein.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.yaadein.core.ui.illustrations.Illustration
import com.yaadein.core.ui.illustrations.IllustrationKind
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.core.ui.R as CoreR

/** "What is Yaadein?" — a plain explanation of the app, opened from the Timeline and from About. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInfoScreen(versionName: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.app_info_title), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(CoreR.string.action_back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Illustration(kind = IllustrationKind.PHOTO_STACK, modifier = Modifier.size(Yaadein.sizes.illustration * 0.7f).align(Alignment.CenterHorizontally))
            Text(stringResource(R.string.app_info_headline), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.app_info_intro), style = MaterialTheme.typography.bodyLarge)
            Feature(Icons.Rounded.AddAPhoto, R.string.app_info_capture_title, R.string.app_info_capture_body)
            Feature(Icons.Rounded.People, R.string.app_info_relive_title, R.string.app_info_relive_body)
            Feature(Icons.Rounded.Movie, R.string.app_info_reels_title, R.string.app_info_reels_body)
            Feature(Icons.Rounded.Lock, R.string.app_info_private_title, R.string.app_info_private_body)
            Feature(Icons.Rounded.Backup, R.string.app_info_backup_title, R.string.app_info_backup_body)
            Feature(Icons.Rounded.AutoAwesome, R.string.app_info_share_title, R.string.app_info_share_body)
            Text(
                stringResource(CoreR.string.app_credit) + " · " + stringResource(R.string.about_version, versionName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(spacing.xxxl))
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, title: Int, body: Int) {
    val spacing = Yaadein.spacing
    Surface(shape = RoundedCornerShape(Yaadein.radius.lg), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(spacing.lg), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(Yaadein.sizes.icon))
            Spacer(Modifier.width(spacing.md))
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
