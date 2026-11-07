package com.yaadein.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import androidx.room.withTransaction
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.db.entity.ImportJobEntity
import com.yaadein.data.db.entity.MediaEntity
import com.yaadein.data.repository.MemoryRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/** What we know about a picked item before importing it. */
data class PickedMedia(
    val uri: Uri,
    val isVideo: Boolean,
    val mimeType: String?,
    val sizeBytes: Long,
    val takenAtMillis: Long?,
    val displayName: String?,
)

sealed interface ImportFailure {
    data class TooLarge(val isVideo: Boolean, val sizeBytes: Long, val limitBytes: Long) : ImportFailure
    data object Unsupported : ImportFailure
    data class Unreadable(val reason: String?) : ImportFailure
    data object IntegrityCheckFailed : ImportFailure
    /** The phone lacks the temporary space to copy and compress this file. */
    data class NotEnoughSpace(val neededBytes: Long) : ImportFailure
}

sealed interface ImportOutcome {
    data class Success(val mediaId: Long) : ImportOutcome
    data class Failed(val failure: ImportFailure) : ImportOutcome
}

/** Transcodes a video file to at most 1080p. Injected so JVM tests can stub it. */
fun interface VideoOptimizer {
    /** Returns the output file, or null to keep the original. */
    suspend fun optimize(input: File, output: File): File?
}

/**
 * Import pipeline: inspect → enforce limits → copy to temp → (optimise) → thumbnail → encrypt →
 * verify checksum → DB row in one transaction → delete temp. Every stage is recorded in
 * `import_jobs` so an interrupted import can be finished or cleaned up on the next launch.
 */
@Singleton
class MediaImporter @Inject constructor(
    private val context: Context,
    private val db: AppDatabase,
    private val mediaStore: MediaStore,
    private val memoryRepository: MemoryRepositoryImpl,
    private val videoOptimizer: VideoOptimizer,
    private val clock: () -> Long,
) {
    private val resolver get() = context.contentResolver

    suspend fun inspect(uri: Uri): PickedMedia? = withContext(Dispatchers.IO) {
        val mime = resolver.getType(uri)
        var size = -1L
        var name: String? = null
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE, OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val sizeIndex = c.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !c.isNull(sizeIndex)) size = c.getLong(sizeIndex)
                    val nameIndex = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) name = c.getString(nameIndex)
                }
            }
        }
        if (size < 0) {
            size = runCatching { resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L }.getOrDefault(-1L)
        }
        if (size < 0) {
            size = runCatching { resolver.openInputStream(uri)?.use { s -> var n = 0L; val buf = ByteArray(64 * 1024); while (true) { val r = s.read(buf); if (r < 0) break; n += r }; n } ?: -1L }.getOrDefault(-1L)
        }
        if (size < 0) return@withContext null
        val isVideo = mime?.startsWith("video/") == true
        val isImage = mime?.startsWith("image/") == true
        if (!isVideo && !isImage) return@withContext null
        val takenAt = if (isVideo) videoTakenAt(uri) else exifTakenAt(uri)
        PickedMedia(uri = uri, isVideo = isVideo, mimeType = mime, sizeBytes = size, takenAtMillis = takenAt, displayName = name)
    }

    /**
     * Largest file accepted. With [optimize] on, a video may be up to [MAX_SOURCE_VIDEO_BYTES]: it is compressed to 1080p
     * first and [MAX_VIDEO_BYTES] applies to the compressed copy (a two-minute 4K clip used to be refused outright).
     */
    fun limitFor(isVideo: Boolean, optimize: Boolean = true): Long = when {
        !isVideo -> MAX_PHOTO_BYTES
        optimize -> MAX_SOURCE_VIDEO_BYTES
        else -> MAX_VIDEO_BYTES
    }

    fun checkLimit(picked: PickedMedia, optimize: Boolean = true): ImportFailure? {
        val limit = limitFor(picked.isVideo, optimize)
        if (picked.sizeBytes > limit) return ImportFailure.TooLarge(picked.isVideo, picked.sizeBytes, limit)
        // Copy + compressed copy + encrypted file can briefly need about three times the size.
        val needed = picked.sizeBytes * 3 + SPACE_MARGIN_BYTES
        val free = runCatching { android.os.StatFs(context.cacheDir.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
        if (picked.sizeBytes > MAX_VIDEO_BYTES && free < needed) return ImportFailure.NotEnoughSpace(needed)
        return null
    }

    suspend fun import(picked: PickedMedia, memoryId: Long?, optimize: Boolean, position: Int, caption: String = ""): ImportOutcome =
        withContext(Dispatchers.IO) {
            checkLimit(picked, optimize)?.let { return@withContext ImportOutcome.Failed(it) }
            val now = clock()
            var job = ImportJobEntity(
                sourceUri = picked.uri.toString(), memoryId = memoryId, stage = STAGE_COPYING,
                tempPath = null, targetPath = null, thumbPath = null, sha256 = null, optimize = optimize, createdAt = now,
            )
            job = job.copy(id = db.importJobDao().insert(job))
            val temp = mediaStore.newTempFile()
            val targetFile = mediaStore.newMediaFile(if (picked.isVideo) "mp4" else "jpg")
            val thumbFile = mediaStore.newThumbFile()
            try {
                // 1. copy source to temp
                resolver.openInputStream(picked.uri)?.use { input -> FileOutputStream(temp).use { out -> input.copyTo(out) } }
                    ?: return@withContext fail(job, ImportFailure.Unreadable("openInputStream returned null"), temp, targetFile, thumbFile)
                job = job.copy(tempPath = temp.path, targetPath = mediaStore.relativize(targetFile), thumbPath = mediaStore.relativize(thumbFile), stage = STAGE_ENCRYPTING)
                db.importJobDao().update(job)

                // 2. prepare plaintext (optimised or original), thumbnail, dimensions
                val prepared = (if (picked.isVideo) prepareVideo(temp, optimize) else preparePhoto(temp, optimize))
                    ?: return@withContext fail(job, ImportFailure.Unsupported, temp, targetFile, thumbFile)
                // The limit for what is kept applies after compression.
                val keptSize = prepared.plainFile.length()
                if (picked.isVideo && keptSize > MAX_VIDEO_BYTES) {
                    if (prepared.plainFile != temp) prepared.plainFile.delete()
                    return@withContext fail(job, ImportFailure.TooLarge(true, keptSize, MAX_VIDEO_BYTES), temp, targetFile, thumbFile)
                }

                // 3. encrypt + hash
                val sha = FileInputStream(prepared.plainFile).use { mediaStore.crypto.encryptFile(it, targetFile) }
                mediaStore.crypto.encryptBytes(prepared.thumbJpeg, thumbFile)
                job = job.copy(sha256 = sha, stage = STAGE_VERIFYING)
                db.importJobDao().update(job)

                // 4. verify what we wrote decrypts back to the same bytes
                val verified = mediaStore.crypto.sha256OfDecrypted(targetFile)
                if (verified != sha) return@withContext fail(job, ImportFailure.IntegrityCheckFailed, temp, targetFile, thumbFile)

                // 5. DB row, cover and search index in one transaction
                val sizeBytes = prepared.plainFile.length()
                val mediaId = db.withTransaction {
                    val id = db.mediaDao().insert(
                        MediaEntity(
                            memoryId = memoryId, kind = if (picked.isVideo) "VIDEO" else "PHOTO",
                            encryptedPath = mediaStore.relativize(targetFile), thumbPath = mediaStore.relativize(thumbFile),
                            takenAt = picked.takenAtMillis, durationMs = prepared.durationMs, sizeBytes = sizeBytes,
                            caption = caption, width = prepared.width, height = prepared.height, sha256 = sha,
                            position = position, createdAt = clock(),
                        ),
                    )
                    if (memoryId != null) {
                        val memory = db.memoryDao().get(memoryId)
                        if (memory != null) {
                            // One update: first photo becomes the cover and a text-only card upgrades itself to an event.
                            db.memoryDao().update(
                                memory.copy(
                                    coverMediaId = memory.coverMediaId ?: id,
                                    type = if (memory.type == "TEXT_ONLY") "EVENT" else memory.type,
                                    updatedAt = clock(),
                                ),
                            )
                        }
                        memoryRepository.reindex(memoryId)
                    }
                    db.importJobDao().delete(job.id)
                    id
                }
                if (prepared.plainFile != temp) prepared.plainFile.delete()
                temp.delete()
                ImportOutcome.Success(mediaId)
            } catch (e: Exception) {
                fail(job, ImportFailure.Unreadable(e.message), temp, targetFile, thumbFile)
            }
        }

    private suspend fun fail(job: ImportJobEntity, failure: ImportFailure, vararg files: File): ImportOutcome {
        files.forEach { runCatching { it.delete() } }
        db.importJobDao().update(job.copy(stage = STAGE_FAILED))
        db.importJobDao().delete(job.id)
        return ImportOutcome.Failed(failure)
    }

    /** Cleans up anything an interrupted import left behind (called at startup). */
    suspend fun recoverPending() = withContext(Dispatchers.IO) {
        val pending = db.importJobDao().pending()
        for (job in pending) {
            // A job that reached VERIFYING has a complete encrypted file; finish it if it checks out.
            val target = job.targetPath?.let { mediaStore.file(it) }
            val thumb = job.thumbPath?.let { mediaStore.file(it) }
            val memoryId = job.memoryId
            if (job.stage == STAGE_VERIFYING && target?.exists() == true && thumb?.exists() == true && job.sha256 != null && memoryId != null) {
                val ok = runCatching { mediaStore.crypto.sha256OfDecrypted(target) == job.sha256 }.getOrDefault(false)
                if (ok) {
                    val bytes = runCatching { mediaStore.crypto.decryptToBytes(thumb) }.getOrNull()
                    val bounds = bytes?.let { b -> BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeByteArray(b, 0, b.size, it) } }
                    db.withTransaction {
                        val id = db.mediaDao().insert(
                            MediaEntity(
                                memoryId = memoryId, kind = if (target.name.contains(".mp4")) "VIDEO" else "PHOTO",
                                encryptedPath = job.targetPath, thumbPath = job.thumbPath, takenAt = null, durationMs = null,
                                sizeBytes = mediaStore.crypto.plainLength(target), caption = "", width = bounds?.outWidth ?: 0, height = bounds?.outHeight ?: 0,
                                sha256 = job.sha256, position = 0, createdAt = clock(),
                            ),
                        )
                        val memory = db.memoryDao().get(memoryId)
                        if (memory != null && memory.coverMediaId == null) db.memoryDao().setCover(memoryId, id, clock())
                        db.importJobDao().delete(job.id)
                    }
                    continue
                }
            }
            // Otherwise discard partial files; the source URI grant will not have survived process death.
            listOfNotNull(job.tempPath?.let(::File), target, thumb).forEach { runCatching { it.delete() } }
            db.importJobDao().delete(job.id)
        }
        mediaStore.clearTemp()
        val referenced = db.mediaDao().allPaths().toSet()
        mediaStore.pruneOrphans(referenced)
        mediaStore.pruneVoiceOrphans(db.memoryDao().voicePaths().toSet())
    }

    private class Prepared(val plainFile: File, val thumbJpeg: ByteArray, val width: Int, val height: Int, val durationMs: Long?)

    private fun preparePhoto(temp: File, optimize: Boolean): Prepared? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(temp.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val orientation = runCatching { ExifInterface(temp).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val longest = max(bounds.outWidth, bounds.outHeight)
        val needsResize = optimize && longest > MAX_PHOTO_EDGE
        val rotated = orientation != ExifInterface.ORIENTATION_NORMAL && orientation != ExifInterface.ORIENTATION_UNDEFINED
        var width = bounds.outWidth
        var height = bounds.outHeight
        var plainFile = temp
        val fullBitmap = decodeScaled(temp, if (needsResize) MAX_PHOTO_EDGE else longest).let { applyOrientation(it, orientation) }
        try {
            if (needsResize || rotated) {
                // Re-encode: smaller (max 2560 px) and/or upright so every consumer can ignore EXIF.
                val out = mediaStore.newTempFile()
                FileOutputStream(out).use { fullBitmap.compress(Bitmap.CompressFormat.JPEG, PHOTO_QUALITY, it) }
                plainFile = out
                width = fullBitmap.width
                height = fullBitmap.height
            } else if (rotated) {
                width = fullBitmap.width; height = fullBitmap.height
            }
            val thumb = thumbnailOf(fullBitmap)
            return Prepared(plainFile, thumb, width, height, null)
        } finally {
            fullBitmap.recycle()
        }
    }

    private suspend fun prepareVideo(temp: File, optimize: Boolean): Prepared? {
        val retriever = MediaMetadataRetriever()
        var width = 0; var height = 0; var duration: Long? = null
        var frame: Bitmap? = null
        try {
            retriever.setDataSource(temp.path)
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) { val t = width; width = height; height = t }
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            frame = retriever.getFrameAtTime(1_000_000L.coerceAtMost((duration ?: 0L) * 500), MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { retriever.release() }
        }
        if (width <= 0 || height <= 0) return null
        var plainFile = temp
        // Compress when larger than 1080p, or when the file alone is over the keep limit (long 1080p clips).
        if (optimize && (max(width, height) > MAX_VIDEO_EDGE || temp.length() > MAX_VIDEO_BYTES)) {
            val out = mediaStore.newTempFile()
            val result = videoOptimizer.optimize(temp, out)
            if (result != null && result.exists() && result.length() > 0) {
                plainFile = result
                if (max(width, height) > MAX_VIDEO_EDGE) {
                    val scale = MAX_VIDEO_EDGE.toFloat() / max(width, height)
                    width = (width * scale).toInt(); height = (height * scale).toInt()
                }
            }
        }
        val thumb = frame?.let { thumbnailOf(it).also { _ -> it.recycle() } } ?: placeholderThumb(width, height)
        return Prepared(plainFile, thumb, width, height, duration)
    }

    private fun decodeScaled(file: File, targetLongest: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        var longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / 2 >= targetLongest) { sample *= 2; longest /= 2 }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = BitmapFactory.decodeFile(file.path, opts) ?: error("decode failed")
        val l = max(bmp.width, bmp.height)
        if (l <= targetLongest) return bmp
        val scale = targetLongest.toFloat() / l
        val scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)
        if (scaled != bmp) bmp.recycle()
        return scaled
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.preScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.preScale(-1f, 1f) }
            else -> return bitmap
        }
        val out = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        if (out != bitmap) bitmap.recycle()
        return out
    }

    private fun thumbnailOf(bitmap: Bitmap): ByteArray {
        val l = max(bitmap.width, bitmap.height)
        val scale = THUMB_EDGE.toFloat() / l
        val thumb = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
        val out = ByteArrayOutputStream()
        thumb.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, out)
        if (thumb != bitmap) thumb.recycle()
        return out.toByteArray()
    }

    private fun placeholderThumb(width: Int, height: Int): ByteArray {
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF334155.toInt()) }
        return thumbnailOf(bmp).also { bmp.recycle() }
    }

    private fun exifTakenAt(uri: Uri): Long? = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            val exif = ExifInterface(input)
            val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME) ?: return@use null
            val dt = LocalDateTime.parse(raw, EXIF_FORMAT)
            dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }.getOrNull()

    private fun videoTakenAt(uri: Uri): Long? = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val raw = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE) ?: return null
            parseVideoDate(raw)
        } finally { runCatching { r.release() } }
    }.getOrNull()

    companion object {
        const val MAX_PHOTO_BYTES = 20L * 1024 * 1024
        const val MAX_VIDEO_BYTES = 200L * 1024 * 1024
        /** Source videos up to 2 GB are accepted when they will be compressed. */
        const val MAX_SOURCE_VIDEO_BYTES = 2048L * 1024 * 1024
        private const val SPACE_MARGIN_BYTES = 100L * 1024 * 1024
        const val MAX_PHOTO_EDGE = 2560
        const val MAX_VIDEO_EDGE = 1920
        /** Short edge for 1080p output (Presentation.createForHeight on landscape sources). */
        const val MAX_VIDEO_EDGE_SHORT = 1080
        const val THUMB_EDGE = 512
        private const val PHOTO_QUALITY = 90
        private const val THUMB_QUALITY = 82
        const val STAGE_COPYING = "COPYING"
        const val STAGE_ENCRYPTING = "ENCRYPTING"
        const val STAGE_VERIFYING = "VERIFYING"
        const val STAGE_FAILED = "FAILED"
        private val EXIF_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

        /** Parses MediaMetadataRetriever dates such as "20240101T120000.000Z" or "2024-01-01T12:00:00.000Z". */
        fun parseVideoDate(raw: String): Long? {
            val candidates = listOf(
                DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'"),
                DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"),
                DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            )
            for (f in candidates) {
                try {
                    return if (f == DateTimeFormatter.ISO_OFFSET_DATE_TIME) java.time.OffsetDateTime.parse(raw, f).toInstant().toEpochMilli()
                    else LocalDateTime.parse(raw, f).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
                } catch (_: DateTimeParseException) { }
            }
            return null
        }
    }
}
