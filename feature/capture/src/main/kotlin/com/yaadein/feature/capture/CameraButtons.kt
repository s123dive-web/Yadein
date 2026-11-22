package com.yaadein.feature.capture

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.data.media.CameraCapture

/** "Take photo" / "Record video" callbacks for a capture screen; the defaults do nothing (previews, tests). */
data class CameraActions(val onPhoto: () -> Unit = {}, val onVideo: () -> Unit = {})

/**
 * Opens the phone's camera app for one photo or one video that is written straight into Yaadein's private cache
 * ([CameraCapture]), never the gallery, and hands the result to [onCaptured] like a picked file.
 *
 * Yaadein declares the CAMERA permission, and Android then refuses the camera intent until it is granted, so the first
 * use asks for it. [beforeLeave] runs right before another app comes to the front (PIN grace).
 */
@Composable
fun rememberCameraActions(beforeLeave: () -> Unit, onCaptured: (Uri) -> Unit): CameraActions {
    val context = LocalContext.current
    // Survives the process being closed while the camera app is in front.
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    var wantsVideo by rememberSaveable { mutableStateOf(false) }

    fun done() {
        val uri = target?.let(Uri::parse)
        target = null
        if (uri != null && CameraCapture.finished(context, uri)) onCaptured(uri)
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { done() }
    val video = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { done() }

    fun open() {
        val uri = CameraCapture.newTarget(context, wantsVideo)
        target = uri.toString()
        beforeLeave()
        runCatching { if (wantsVideo) video.launch(uri) else photo.launch(uri) }.onFailure {
            target = null
            CameraCapture.discard(context, uri)
            Toast.makeText(context, R.string.camera_missing, Toast.LENGTH_LONG).show()
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) open() else Toast.makeText(context, R.string.camera_permission_needed, Toast.LENGTH_LONG).show()
    }
    fun start(asVideo: Boolean) {
        wantsVideo = asVideo
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) open()
        else { beforeLeave(); permission.launch(Manifest.permission.CAMERA) }
    }
    return CameraActions(onPhoto = { start(false) }, onVideo = { start(true) })
}

/** The two camera buttons plus the line that says where the shot goes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CameraButtons(actions: CameraActions, modifier: Modifier = Modifier) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            OutlinedButton(onClick = actions.onPhoto) {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(spacing.sm))
                Text(stringResource(R.string.camera_take_photo))
            }
            OutlinedButton(onClick = actions.onVideo) {
                Icon(Icons.Rounded.Videocam, contentDescription = null)
                Spacer(Modifier.width(spacing.sm))
                Text(stringResource(R.string.camera_record_video))
            }
        }
        Text(stringResource(R.string.camera_private_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
