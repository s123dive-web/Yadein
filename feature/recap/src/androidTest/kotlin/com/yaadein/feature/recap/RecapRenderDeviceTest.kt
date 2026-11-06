package com.yaadein.feature.recap

import android.content.Context
import android.graphics.Typeface
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yaadein.data.crypto.MediaCrypto
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.db.DatabaseFactory
import com.yaadein.data.db.entity.MediaEntity
import com.yaadein.data.media.MediaStore
import com.yaadein.data.recap.MusicSource
import com.yaadein.data.recap.RecapRenderer
import com.yaadein.data.recap.RenderStyle
import com.yaadein.domain.recap.Framing
import com.yaadein.domain.recap.RecapPlan
import com.yaadein.domain.recap.RecapSegment
import com.yaadein.domain.recap.RecapTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * Runs the real Media3 Transformer pipeline on a device or emulator: encrypted photos and phone-style
 * clips (with and without sound, H.264 and HEVC, rotated) through [RecapRenderer] into an MP4.
 * The JVM tests cannot cover this because it needs MediaCodec and OpenGL.
 */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RecapRenderDeviceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
    private lateinit var db: AppDatabase
    private lateinit var store: MediaStore
    private val style = RenderStyle(Typeface.SERIF, Typeface.SANS_SERIF)

    @Before
    fun setUp() {
        db = DatabaseFactory.plainForTests(context)
        store = MediaStore(context, MediaCrypto(SecretKeySpec(ByteArray(32) { 5 }, "AES")))
    }

    @After
    fun tearDown() { db.close(); store.wipeAll() }

    private fun vault(asset: String, kind: String, durationMs: Long? = null): Long = runBlocking {
        val target = store.newMediaFile(if (kind == "VIDEO") "mp4" else "jpg")
        val sha = testAssets.open(asset).use { store.crypto.encryptFile(it, target) }
        val thumb = store.newThumbFile().also { store.crypto.encryptBytes(ByteArray(0), it) }
        db.mediaDao().insert(
            MediaEntity(
                memoryId = null, kind = kind, encryptedPath = store.relativize(target), thumbPath = store.relativize(thumb),
                takenAt = null, durationMs = durationMs, sizeBytes = target.length(), caption = "", width = 0, height = 0,
                sha256 = sha, position = 0, createdAt = 1,
            ),
        )
    }

    private fun photo(id: Long, caption: String? = null) =
        RecapSegment.Photo(id, 2_000, Framing(0.45f, 0.5f, 1.0f), Framing(0.55f, 0.5f, 1.18f), caption, caption?.let { "5 Nov 2026" })

    private fun plan(theme: RecapTheme = RecapTheme.WARM): RecapPlan {
        val big = vault("photo_large.jpg", "PHOTO")
        val portrait = vault("photo_portrait.jpg", "PHOTO")
        val rotated = vault("clip_h264_rot90.mp4", "VIDEO", 5_000)
        val hevc = vault("clip_hevc.mp4", "VIDEO", 5_000)
        val silent = vault("clip_silent.mp4", "VIDEO", 4_000)
        return RecapPlan(
            theme,
            listOf(
                RecapSegment.TitleCard("Our memories", "2026", 2_000),
                photo(big, "School Traditional Day"),
                RecapSegment.Clip(rotated, 500, 3_500, null, null),
                photo(portrait),
                RecapSegment.Clip(hevc, 0, 2_500, "Picnic", "6 Nov 2026"),
                RecapSegment.Clip(silent, 0, 2_000, null, null),
                RecapSegment.TitleCard("Made with Yaadein", "", 2_000, isOutro = true),
            ),
        )
    }

    private fun render(plan: RecapPlan, music: MusicSource): File {
        val out = File(context.cacheDir, "recap-test-${System.nanoTime()}.mp4")
        // Software emulators can be far slower than any phone, so the stall watchdog is off here; the
        // test still fails on a real stall through the instrumentation timeout.
        runBlocking { RecapRenderer(context, db, store, maxDelayBetweenMuxerSamplesMs = androidx.media3.common.C.TIME_UNSET).render(plan, music, style, out) {} }
        return out
    }

    private fun assertPlayableVideo(out: File, plan: RecapPlan, expectAudio: Boolean) {
        assertTrue("output written", out.exists() && out.length() > 10_000)
        val mmr = MediaMetadataRetriever().apply { setDataSource(out.path) }
        val durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
        assertEquals("yes", mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
        assertTrue("duration $durationMs vs plan ${plan.totalDurationMs}", abs(durationMs - plan.totalDurationMs) < 1_500)
        // Vertical reel: 1080 wide, 1920 tall (allowing for a rotation tag instead of swapped dimensions).
        val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)!!.toInt()
        val h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)!!.toInt()
        val rotation = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        val (shownW, shownH) = if (rotation % 180 == 0) w to h else h to w
        assertEquals("reel width", plan.width, shownW)
        assertEquals("reel height", plan.height, shownH)
        mmr.release()
        val ex = MediaExtractor().apply { setDataSource(out.path) }
        val mimes = (0 until ex.trackCount).map { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME) }
        ex.release()
        assertTrue("video track in $mimes", mimes.any { it == MediaFormat.MIMETYPE_VIDEO_AVC })
        if (expectAudio) assertTrue("audio track in $mimes", mimes.any { it == MediaFormat.MIMETYPE_AUDIO_AAC })
        out.delete()
    }

    @Test
    fun rendersPhotosAndClipsWithBundledMusic() {
        val p = plan()
        assertPlayableVideo(render(p, MusicSource.Bundled(R.raw.track_warm)), p, expectAudio = true)
    }

    @Test
    fun rendersPhotosAndClipsWithoutMusicKeepingClipSound() {
        val p = plan(RecapTheme.CLASSIC_FILM)
        assertPlayableVideo(render(p, MusicSource.None), p, expectAudio = true)
    }

    @Test
    fun rendersPhotosOnlyWithMusic() {
        val p = plan(RecapTheme.FESTIVE).let { full -> full.copy(segments = full.segments.filter { it !is RecapSegment.Clip }) }
        assertPlayableVideo(render(p, MusicSource.Bundled(R.raw.track_minimal)), p, expectAudio = true)
    }

    @Test
    fun rendersPhotosOnlyWithoutMusic() {
        val p = plan(RecapTheme.MINIMAL).let { full -> full.copy(segments = full.segments.filter { it !is RecapSegment.Clip }) }
        assertPlayableVideo(render(p, MusicSource.None), p, expectAudio = false)
    }
}
