package com.yaadein.data.recap

import android.content.Context
import android.graphics.Matrix
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.Composition
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.google.common.util.concurrent.MoreExecutors
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.media.EncryptedDataSource
import com.yaadein.data.media.MediaStore
import com.yaadein.domain.recap.Framing
import com.yaadein.domain.recap.RecapPlan
import com.yaadein.domain.recap.RecapSegment
import com.yaadein.domain.recap.RecapTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.coroutineContext

/** Where the music comes from. */
sealed interface MusicSource {
    data class Bundled(val rawResId: Int) : MusicSource
    data class UserFile(val uri: String) : MusicSource
    data object None : MusicSource
}

/**
 * Turns a [RecapPlan] into a vertical 1080×1920 (9:16 reel) H.264/AAC MP4 with Media3 Transformer.
 * Encrypted photos and videos are read through [VaultDataSourceFactory]; nothing is decrypted to disk.
 */
@UnstableApi
class RecapRenderer(
    private val context: Context,
    private val db: AppDatabase,
    private val mediaStore: MediaStore,
    /**
     * Media3 aborts an export when nothing reaches the muxer for this long. Its default (10 s on devices)
     * is tight for a slow phone decoding a large HEVC clip; tests on emulators pass C.TIME_UNSET to disable it.
     */
    private val maxDelayBetweenMuxerSamplesMs: Long = MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS,
) {
    class RenderException(message: String, cause: Throwable? = null) : Exception(message, cause)

    suspend fun render(plan: RecapPlan, music: MusicSource, style: RenderStyle, output: File, onProgress: (Int) -> Unit) {
        val painter = CardPainter(style)
        val cardDir = File(mediaStore.renderDir, "cards-${System.nanoTime()}").apply { mkdirs() }
        try {
            val musicSequence = musicSequence(music)
            // With a soundtrack, clips are muted so the music is the only audio. Without one, clips keep
            // their sound and images get a silent track (see buildVideoSequence).
            val video = buildVideoSequence(plan, painter, cardDir, keepClipAudio = musicSequence == null)
            val sequences = ArrayList<EditedMediaItemSequence>()
            sequences += video
            musicSequence?.let { sequences += it }
            val composition = Composition.Builder(sequences)
                .setEffects(Effects(emptyList(), listOf(Presentation.createForWidthAndHeight(plan.width, plan.height, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)) + themeEffects(plan.theme)))
                // Phone videos are often HDR (HLG/HDR10) while photos and cards are SDR; the output is 8-bit
                // H.264, so tone-map everything to SDR instead of failing on mixed colour spaces.
                .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                .build()
            // A codec can be taken away mid-export when the phone is short of resources (Android reclaims it and
            // configure() fails on a released codec). That is transient, so try once more before giving up.
            var attempt = 0
            while (true) {
                try {
                    withContext(Dispatchers.Main) { export(composition, output, onProgress) }
                    break
                } catch (e: RenderException) {
                    if (++attempt >= MAX_ATTEMPTS || !isTransientCodecFailure(e)) throw e
                    output.delete()
                    delay(RETRY_DELAY_MS)
                }
            }
        } finally {
            cardDir.deleteRecursively()
        }
    }

    private suspend fun export(composition: Composition, output: File, onProgress: (Int) -> Unit) {
        val dataSourceFactory = VaultDataSourceFactory(context, db, mediaStore)
        val bitmapExecutor = Executors.newSingleThreadExecutor()
        val bitmapLoader = DataSourceBitmapLoader.Builder(context)
            .setExecutorService(MoreExecutors.listeningDecorator(bitmapExecutor))
            .setDataSourceFactory(dataSourceFactory)
            .setMaximumOutputDimension(MAX_BITMAP_EDGE)
            .build()
        val assetLoaderFactory = DefaultAssetLoaderFactory(
            context,
            DefaultDecoderFactory.Builder(context).build(),
            Clock.DEFAULT,
            DefaultMediaSourceFactory(dataSourceFactory),
            bitmapLoader,
        )
        try {
            awaitExport(assetLoaderFactory, composition, output, onProgress)
        } finally {
            bitmapExecutor.shutdown()
        }
    }

    private suspend fun awaitExport(assetLoaderFactory: DefaultAssetLoaderFactory, composition: Composition, output: File, onProgress: (Int) -> Unit) {
        suspendCancellableCoroutine { cont ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoderFactory())
                .setAssetLoaderFactory(assetLoaderFactory)
                .setMaxDelayBetweenMuxerSamplesMs(maxDelayBetweenMuxerSamplesMs)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) { if (cont.isActive) cont.resume(Unit) }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        if (cont.isActive) cont.resumeWithException(RenderException(describe(exportException), exportException))
                    }
                })
                .build()
            output.delete()
            transformer.start(composition, output.path)
            val holder = ProgressHolder()
            val poller = kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                while (isActive && cont.isActive) {
                    val state = transformer.getProgress(holder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
                    delay(500)
                }
            }
            cont.invokeOnCancellation { poller.cancel(); transformer.cancel(); output.delete() }
            kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                // Stop polling once the continuation completes.
                while (cont.isActive) delay(250)
                poller.cancel()
            }
        }
    }

    /**
     * Builds the picture track. A Transformer sequence must have the same track types throughout: if
     * it starts with an image (no audio) and later reaches a clip with audio, export fails with
     * "Asset loader error". So clips are either muted ([keepClipAudio] false, music plays instead) or
     * the sequence is told to add silence under the images.
     */
    private suspend fun buildVideoSequence(plan: RecapPlan, painter: CardPainter, cardDir: File, keepClipAudio: Boolean): EditedMediaItemSequence {
        val framer = PhotoFramer(plan.width, plan.height)
        val items = ArrayList<EditedMediaItem>()
        var hasClip = false
        plan.segments.forEachIndexed { index, seg ->
            when (seg) {
                is RecapSegment.TitleCard -> {
                    val bmp = painter.titleCard(plan.theme, seg.title, seg.subtitle, plan.width, plan.height, seg.isOutro)
                    val file = File(cardDir, "card$index.png")
                    FileOutputStream(file).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    bmp.recycle()
                    items += imageItem(Uri.fromFile(file), seg.durationMs, MimeTypes.IMAGE_PNG, emptyList())
                }
                is RecapSegment.Photo -> {
                    // Pre-framed at 1.25x the reel size (blurred backdrop for wide photos), then: pan/zoom on the
                    // big frame, scale down to the reel size (always downsampling, so it stays sharp), caption last
                    // so it is drawn in output pixels and does not zoom.
                    val framed = File(cardDir, "photo$index.jpg")
                    val source = if (framePhoto(framer, seg.mediaId, framed)) Uri.fromFile(framed) else EncryptedDataSource.uriFor(seg.mediaId)
                    val effects = ArrayList<Effect>()
                    effects += kenBurns(seg)
                    effects += fitToFrame(plan)
                    captionOverlay(painter, plan, seg.caption, seg.dateLabel)?.let { effects += it }
                    items += imageItem(source, seg.durationMs, MimeTypes.IMAGE_JPEG, effects)
                }
                is RecapSegment.Clip -> {
                    val effects = ArrayList<Effect>()
                    // A wide (or square) clip cropped to 9:16 was magnified ~2.7x and looked soft. Show it whole instead,
                    // over a blurred backdrop made from its thumbnail, like wide photos; near-9:16 clips fill the frame.
                    val backdrop = clipBackdrop(framer, seg.mediaId)
                    if (backdrop == null) {
                        effects += fitToFrame(plan)
                    } else {
                        effects += Presentation.createForWidthAndHeight(plan.width, plan.height, Presentation.LAYOUT_SCALE_TO_FIT)
                        effects += OverlayEffect(listOf(BitmapOverlay.createStaticBitmapOverlay(backdrop)))
                    }
                    captionOverlay(painter, plan, seg.caption, seg.dateLabel)?.let { effects += it }
                    val mediaItem = MediaItem.Builder()
                        .setUri(EncryptedDataSource.uriFor(seg.mediaId))
                        .setMimeType(MimeTypes.VIDEO_MP4)
                        .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(seg.startMs).setEndPositionMs(seg.endMs).build())
                        .build()
                    items += EditedMediaItem.Builder(mediaItem)
                        .setRemoveAudio(!keepClipAudio)
                        .setEffects(Effects(emptyList(), effects))
                        .build()
                    hasClip = true
                }
            }
        }
        return EditedMediaItemSequence.Builder(items)
            .experimentalSetForceAudioTrack(keepClipAudio && hasClip)
            .build()
    }

    /**
     * A still image for [durationMs]. The duration must be set on the MediaItem with
     * `setImageDurationMs`: Media3's DefaultAssetLoaderFactory only routes an item to the image loader
     * when `imageDurationMs` is set. Without it the item went to the ExoPlayer loader, which reports no
     * duration for a still picture, and every export failed with "Asset loader error (Could not
     * retrieve required duration for EditedMediaItem 0)".
     */
    /** Decrypts one vault photo in memory and writes the framed JPEG; false if it cannot be read (renderer falls back). */
    private suspend fun framePhoto(framer: PhotoFramer, mediaId: Long, out: File): Boolean = runCatching {
        val media = db.mediaDao().get(mediaId) ?: return false
        framer.frame(mediaStore.crypto.decryptToBytes(mediaStore.file(media.encryptedPath)), out)
        true
    }.getOrDefault(false)

    private suspend fun clipBackdrop(framer: PhotoFramer, mediaId: Long): android.graphics.Bitmap? = runCatching {
        val media = db.mediaDao().get(mediaId) ?: return null
        framer.videoBackdrop(mediaStore.crypto.decryptToBytes(mediaStore.file(media.thumbPath)))
    }.getOrNull()

    private fun imageItem(uri: Uri, durationMs: Long, mime: String, effects: List<Effect>): EditedMediaItem =
        EditedMediaItem.Builder(MediaItem.Builder().setUri(uri).setMimeType(mime).setImageDurationMs(durationMs).build())
            .setDurationUs(durationMs * 1_000)
            .setFrameRate(FRAME_RATE)
            .setEffects(Effects(emptyList(), effects))
            .build()

    private fun musicSequence(music: MusicSource): EditedMediaItemSequence? {
        val uri = when (music) {
            is MusicSource.Bundled -> androidx.media3.datasource.RawResourceDataSource.buildRawResourceUri(music.rawResId)
            is MusicSource.UserFile -> Uri.parse(music.uri)
            MusicSource.None -> return null
        }
        val item = EditedMediaItem.Builder(MediaItem.fromUri(uri)).setRemoveVideo(true).build()
        return EditedMediaItemSequence.Builder(item).setIsLooping(true).build()
    }

    private fun isTransientCodecFailure(e: RenderException): Boolean {
        val code = (e.cause as? ExportException)?.errorCode ?: return false
        return code == ExportException.ERROR_CODE_DECODER_INIT_FAILED || code == ExportException.ERROR_CODE_DECODING_FAILED ||
            code == ExportException.ERROR_CODE_ENCODER_INIT_FAILED || code == ExportException.ERROR_CODE_ENCODING_FAILED
    }

    /**
     * Fixed high bitrate instead of the encoder's own pick, which is often low for a 1080x1920 frame on phone
     * hardware encoders; reel apps re-encode on upload, so a clean source matters. Fallback stays on so a phone
     * that cannot do this still gets the closest supported setting instead of a failure.
     */
    private fun encoderFactory(): DefaultEncoderFactory =
        DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(VIDEO_BITRATE).build())
            .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(AUDIO_BITRATE).build())
            .setEnableFallback(true)
            .build()

    private fun fitToFrame(plan: RecapPlan): Presentation =
        Presentation.createForWidthAndHeight(plan.width, plan.height, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)

    /**
     * Smooth zoom/pan between two framings over the photo's duration, applied after [fitToFrame].
     * The pan is limited to the margin the zoom creates, so the frame edge never shows as a black bar.
     */
    private fun kenBurns(seg: RecapSegment.Photo): MatrixTransformation {
        val durationUs = seg.durationMs * 1_000f
        return MatrixTransformation { presentationTimeUs ->
            val t = (presentationTimeUs / durationUs).coerceIn(0f, 1f)
            val e = t * t * (3 - 2 * t) // smoothstep
            val f = lerp(seg.start, seg.end, e)
            val zoom = f.zoom.coerceAtLeast(1f)
            val maxShift = (zoom - 1f) / zoom
            Matrix().apply {
                // NDC space: translate so (cx, cy) moves towards the centre, then zoom.
                postTranslate((-(f.cx - 0.5f) * 2f).coerceIn(-maxShift, maxShift), ((f.cy - 0.5f) * 2f).coerceIn(-maxShift, maxShift))
                postScale(zoom, zoom)
            }
        }
    }

    private fun lerp(a: Framing, b: Framing, t: Float) = Framing(a.cx + (b.cx - a.cx) * t, a.cy + (b.cy - a.cy) * t, a.zoom + (b.zoom - a.zoom) * t)

    private fun captionOverlay(painter: CardPainter, plan: RecapPlan, title: String?, date: String?): Effect? {
        if (title.isNullOrBlank()) return null
        val bmp = painter.caption(plan.theme, title, date, plan.width, plan.height)
        val settings = StaticOverlaySettings.Builder()
            .setBackgroundFrameAnchor(0f, -1f)
            .setOverlayFrameAnchor(0f, -1f)
            .build()
        return OverlayEffect(listOf(BitmapOverlay.createStaticBitmapOverlay(bmp, settings)))
    }

    private fun themeEffects(theme: RecapTheme): List<Effect> = when (theme) {
        RecapTheme.WARM -> listOf(RgbAdjustment.Builder().setRedScale(1.06f).setGreenScale(1.0f).setBlueScale(0.92f).build(), HslAdjustment.Builder().adjustSaturation(8f).build())
        RecapTheme.MINIMAL -> listOf(HslAdjustment.Builder().adjustSaturation(-25f).build(), Contrast(0.05f))
        RecapTheme.FESTIVE -> listOf(HslAdjustment.Builder().adjustSaturation(28f).build(), Contrast(0.12f))
        RecapTheme.CLASSIC_FILM -> listOf(RgbFilter.createGrayscaleFilter(), RgbAdjustment.Builder().setRedScale(1.12f).setGreenScale(1.0f).setBlueScale(0.82f).build(), Contrast(0.1f))
    }

    companion object {
        const val FRAME_RATE = 30
        const val MAX_DELAY_BETWEEN_MUXER_SAMPLES_MS = 60_000L
        /** 16 Mbit/s H.264 for 1080x1920 at 30 fps: visually clean, about 2 MB per second of reel. */
        const val VIDEO_BITRATE = 16_000_000
        const val AUDIO_BITRATE = 192_000
        private const val MAX_ATTEMPTS = 2
        private const val RETRY_DELAY_MS = 1_500L

        /** Photos are decoded no larger than this on their long edge; keeps 50 MP originals within GPU texture limits and memory. */
        const val MAX_BITMAP_EDGE = 2560

        /** "Asset loader error" on its own says nothing; append the innermost cause so the message is actionable. */
        fun describe(e: Throwable): String {
            var root: Throwable = e
            while (root.cause != null && root.cause !== root) root = root.cause!!
            val head = e.message ?: e.javaClass.simpleName
            if (root === e) return head
            val detail = root.message?.takeIf { it.isNotBlank() } ?: root.javaClass.simpleName
            return "$head (${detail.take(200)})"
        }
    }
}
