package com.yaadein.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.yaadein.app.R
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.data.crash.CrashLog

/**
 * Shown at the top of Home when the previous run ended with an uncaught exception, so the local
 * problem log is one tap away even if the screen that crashed was Settings itself.
 */
@Composable
fun CrashNoticeBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf(CrashLog.hasPendingReport(context)) }
    if (!pending) return
    val shareTitle = stringResource(R.string.crash_notice_share)
    val spacing = Yaadein.spacing
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = spacing.gutter, vertical = spacing.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
    ) {
        Column(modifier = Modifier.padding(spacing.lg)) {
            Text(stringResource(R.string.crash_notice_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(spacing.xs))
            Text(stringResource(R.string.crash_notice_body), style = MaterialTheme.typography.bodyMedium)
            Row(modifier = Modifier.fillMaxWidth().padding(top = spacing.md)) {
                TextButton(onClick = { CrashLog.markSeen(context); pending = false }) { Text(stringResource(R.string.crash_notice_dismiss)) }
                Spacer(Modifier.width(spacing.sm))
                Button(onClick = {
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", CrashLog.file(context))
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(send, shareTitle))
                    CrashLog.markSeen(context); pending = false
                }) { Text(shareTitle) }
            }
        }
    }
}
