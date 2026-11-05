package com.yaadein.data.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** Re-encodes a video to H.264 at most 1080p using Media3 Transformer. Returns null on failure (caller keeps original). */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TransformerVideoOptimizer(private val context: Context) : VideoOptimizer {
    override suspend fun optimize(input: File, output: File): File? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(output)
                    }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        output.delete()
                        if (cont.isActive) cont.resume(null)
                    }
                })
                .build()
            val item = EditedMediaItem.Builder(MediaItem.fromUri(android.net.Uri.fromFile(input)))
                .setEffects(Effects(emptyList(), listOf(Presentation.createForHeight(MediaImporter.MAX_VIDEO_EDGE_SHORT))))
                .build()
            transformer.start(item, output.path)
            cont.invokeOnCancellation { transformer.cancel(); output.delete() }
        }
    }
}
