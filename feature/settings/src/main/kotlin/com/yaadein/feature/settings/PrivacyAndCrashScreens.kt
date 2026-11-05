package com.yaadein.feature.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.FileProvider
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.data.crash.CrashLog
import com.yaadein.core.ui.R as CoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.privacy_title), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(CoreR.string.action_back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.gutter), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            listOf(
                R.string.privacy_s1_title to R.string.privacy_s1_body, R.string.privacy_s2_title to R.string.privacy_s2_body,
                R.string.privacy_s3_title to R.string.privacy_s3_body, R.string.privacy_s4_title to R.string.privacy_s4_body,
                R.string.privacy_s5_title to R.string.privacy_s5_body, R.string.privacy_s6_title to R.string.privacy_s6_body,
            ).forEach { (t, b) ->
                Text(stringResource(t), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(stringResource(b), style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(spacing.xxxl))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashLogScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = Yaadein.spacing
    val context = LocalContext.current
    var text by remember { mutableStateOf(CrashLog.read(context)) }
    val shareTitle = stringResource(CoreR.string.action_share)
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_crash_log), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(CoreR.string.action_back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.gutter), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Text(stringResource(R.string.crash_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (text.isBlank()) {
                Text(stringResource(R.string.crash_empty), style = MaterialTheme.typography.titleMedium)
            } else {
                Button(onClick = {
                    val file = CrashLog.file(context)
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(send, shareTitle))
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.crash_export)) }
                OutlinedButton(onClick = { CrashLog.clear(context); text = "" }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.crash_clear)) }
                Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            }
            Spacer(Modifier.height(spacing.xxxl))
        }
    }
}
