package com.yaadein.data.media

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.reels.EncryptedFileDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Voice memories: recorded to a temporary AAC file, then encrypted into `vault/voice/` (same seekable AES-CTR format
 * as photos) and linked to a memory via memories.voice_note_path. Playback streams through [dataSourceFactory], so the
 * recording never sits on disk in the clear after it is sealed.
 */
class VoiceNotes @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: MediaStore,
    private val db: AppDatabase,
) {
    private var recorder: MediaRecorder? = null
    private var temp: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    /** Starts recording (RECORD_AUDIO must be granted). Returns false when the microphone is unavailable. */
    fun start(): Boolean {
        if (recorder != null) return true
        val file = store.newTempFile()
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(SAMPLE_RATE)
            r.setAudioEncodingBitRate(BIT_RATE)
            r.setMaxDuration(MAX_DURATION_MS)
            r.setOutputFile(file.path)
            r.prepare()
            r.start()
            recorder = r; temp = file; startedAt = System.currentTimeMillis()
            true
        }.getOrElse { runCatching { r.release() }; file.delete(); false }
    }

    /** 0..1 loudness since the last call, for the level meter. */
    fun level(): Float = (recorder?.let { runCatching { it.maxAmplitude }.getOrDefault(0) } ?: 0) / 32_767f

    fun elapsedMs(): Long = if (recorder != null) System.currentTimeMillis() - startedAt else 0L

    /** Stops and seals the recording into the vault; returns its vault-relative path, or null if nothing was recorded. */
    suspend fun stopAndSeal(): String? = withContext(Dispatchers.IO) {
        val r = recorder ?: return@withContext null
        val file = temp
        recorder = null; temp = null
        val ok = runCatching { r.stop() }.isSuccess
        runCatching { r.release() }
        if (!ok || file == null || !file.exists() || file.length() == 0L) { file?.delete(); return@withContext null }
        val target = store.newVoiceFile()
        try {
            file.inputStream().use { store.crypto.encryptFile(it, target) }
            store.relativize(target)
        } finally {
            file.delete()
        }
    }

    fun cancel() {
        recorder?.let { runCatching { it.stop() }; runCatching { it.release() } }
        recorder = null
        temp?.delete(); temp = null
    }

    /** Links a sealed recording to [memoryId]; the previous recording, if any, is deleted. */
    suspend fun attach(memoryId: Long, path: String?) = withContext(Dispatchers.IO) {
        val old = db.memoryDao().get(memoryId)?.voiceNotePath
        db.memoryDao().setVoiceNote(memoryId, path, System.currentTimeMillis())
        if (old != null && old != path) store.deleteFiles(old)
    }

    fun discard(path: String) = store.deleteFiles(path)

    @OptIn(UnstableApi::class)
    fun dataSourceFactory(): DataSource.Factory = DataSource.Factory {
        EncryptedFileDataSource(store.crypto) { uri -> uri.path?.removePrefix("/")?.let(store::file) }
    }

    companion object {
        const val SCHEME = "yaadein-voice"
        /** Playback URI for a vault-relative path. */
        fun uriFor(path: String): Uri = Uri.Builder().scheme(SCHEME).authority("voice").path(path).build()
        const val MAX_DURATION_MS = 30 * 60 * 1000
        private const val SAMPLE_RATE = 44_100
        private const val BIT_RATE = 64_000
    }
}
