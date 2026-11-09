package com.yaadein.feature.memory

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.data.media.MediaDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.yaadein.core.ui.R as CoreR

/** Progress and outcome of saving photos/videos out of the vault to the phone. */
sealed interface DownloadStatus {
    data object Idle : DownloadStatus
    data object Working : DownloadStatus
    data class Done(val saved: Int, val total: Int, val toFolder: Boolean) : DownloadStatus
}

/** Owned by a ViewModel (runs in its scope so a rotation does not cancel a long video download). */
class DownloadController(private val scope: CoroutineScope, private val downloader: MediaDownloader) {
    private val _status = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val status: StateFlow<DownloadStatus> = _status.asStateFlow()

    fun toGallery(ids: List<Long>) = launch(ids, toFolder = false) { downloader.saveToGallery(ids) }
    fun toFolder(ids: List<Long>, tree: Uri) = launch(ids, toFolder = true) { downloader.saveToFolder(ids, tree) }
    fun reset() { _status.value = DownloadStatus.Idle }

    private fun launch(ids: List<Long>, toFolder: Boolean, block: suspend () -> MediaDownloader.Result) {
        if (ids.isEmpty() || _status.value == DownloadStatus.Working) return
        scope.launch {
            _status.value = DownloadStatus.Working
            val result = runCatching { block() }.getOrElse { MediaDownloader.Result(0, ids.distinct().size) }
            _status.value = DownloadStatus.Done(result.saved, result.saved + result.failed, toFolder)
        }
    }
}

/**
 * Returns the action to call with the media to save. Android 10+ saves straight to the gallery; older phones first
 * show the system folder picker (Yaadein never asks for storage permission).
 */
@Composable
fun rememberDownloadAction(controller: DownloadController): (List<Long>) -> Unit {
    var pending by remember { mutableStateOf<List<Long>>(emptyList()) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null && pending.isNotEmpty()) controller.toFolder(pending, tree)
        pending = emptyList()
    }
    return remember(controller, folderPicker) {
        { ids ->
            if (MediaDownloader.supportsGallery) {
                controller.toGallery(ids)
            } else if (ids.isNotEmpty()) {
                pending = ids
                folderPicker.launch(null)
            }
        }
    }
}

@Composable
fun DownloadStatusDialog(status: DownloadStatus, onDismiss: () -> Unit) {
    when (status) {
        DownloadStatus.Idle -> Unit
        DownloadStatus.Working -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(Yaadein.sizes.icon))
                    Spacer(Modifier.width(Yaadein.spacing.md))
                    Text(stringResource(R.string.share_working))
                }
            },
        )
        is DownloadStatus.Done -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreR.string.action_done)) } },
            text = {
                Text(
                    when {
                        status.saved == 0 -> stringResource(R.string.share_failed)
                        status.saved < status.total -> stringResource(R.string.download_partial, status.saved, status.total)
                        status.toFolder -> stringResource(R.string.download_saved_folder)
                        else -> stringResource(R.string.download_saved_gallery)
                    },
                )
            },
        )
    }
}
