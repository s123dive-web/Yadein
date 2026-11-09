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
    /** EXIF GPS position, when the photo has one. */
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/** A picked item copied into the app's temp dir, with its `import_jobs` row; the source grant is no longer needed. */
class StagedMedia(val picked: PickedMedia, val jobId: Long, val temp: File, val memoryId: Long?, val optimize: Boolean, val position: Int)

sealed interface StageOutcome {
    data class Staged(val media: StagedMedia) : StageOutcome
    data class Failed(val failure: ImportFailure) : StageOutcome
}

/** What `import_jobs.picked_json` holds, so a copied file can finish importing after a restart. */
@kotlinx.serialization.Serializable
internal data class PickedJson(
    val uri: String, val isVideo: Boolean, val mimeType: String?, val sizeBytes: Long, val takenAtMillis: Long?,
    val displayName: String?, val latitude: Double? = null, val longitude: Double? = null, val position: Int = 0,
) {
    fun toPicked() = PickedMedia(Uri.parse(uri), isVideo, mimeType, sizeBytes, takenAtMillis, displayName, latitude, longitude)

    companion object {
        private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
        fun of(p: PickedMedia, position: Int) = PickedJson(p.uri.toString(), p.isVideo, p.mimeType, p.sizeBytes, p.takenAtMillis, p.displayName, p.latitude, p.longitude, position)
        fun encode(p: PickedMedia, position: Int): String = json.encodeToString(serializer(), of(p, position))
        fun decode(text: String): PickedJson? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()
    }
}

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
        // Some providers answer getType with null; fall back to the file name, then to the first bytes.
        val mime = resolver.getType(uri) ?: mimeFromName(uri.lastPathSegment) ?: sniffMime(uri)
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
        val exif = if (isVideo) null else exifOf(uri)
        val takenAt = if (isVideo) videoTakenAt(uri) else exif?.let(::takenAtOf)
        val latLong = exif?.latLong
        PickedMedia(
            uri = uri, isVideo = isVideo, mimeType = mime, sizeBytes = size, takenAtMillis = takenAt, displayName = name,
            latitude = latLong?.getOrNull(0), longitude = latLong?.getOrNull(1),
        )
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

    /** Stage 1 of an import: copies the source into the temp dir while its grant is valid; nothing heavy happens here. */
    suspend fun stage(picked: PickedMedia, memoryId: Long?, optimize: Boolean, position: Int): StageOutcome = withContext(Dispatchers.IO) {
        checkLimit(picked, optimize)?.let { return@withContext StageOutcome.Failed(it) }
        var job = ImportJobEntity(
            sourceUri = picked.uri.toString(), memoryId = memoryId, stage = STAGE_COPYING,
            tempPath = null, targetPath = null, thumbPath = null, sha256 = null, optimize = optimize, createdAt = clock(),
            pickedJson = PickedJson.encode(picked, position),
        )
        job = job.copy(id = db.importJobDao().insert(job))
        val temp = mediaStore.newTempFile()
        try {
            resolver.openInputStream(picked.uri)?.use { input -> FileOutputStream(temp).use { out -> input.copyTo(out) } }
                ?: return@withContext StageOutcome.Failed(fail(job, ImportFailure.Unreadable("openInputStream returned null"), temp).failure)
            db.importJobDao().update(job.copy(tempPath = temp.path, stage = STAGE_COPIED))
            StageOutcome.Staged(StagedMedia(picked, job.id, temp, memoryId, optimize, position))
        } catch (e: Exception) {
            StageOutcome.Failed(fail(job, ImportFailure.Unreadable(e.message), temp).failure)
        }
    }

    /** Stage 1 and 2 together: the simple call used by profile photos, exports and tests. */
    suspend fun import(picked: PickedMedia, memoryId: Long?, optimize: Boolean, position: Int, caption: String = ""): ImportOutcome =
        when (val s = stage(picked, memoryId, optimize, position)) {
            is StageOutcome.Failed -> ImportOutcome.Failed(s.failure)
            is StageOutcome.Staged -> finish(s.media, caption)
        }

    /** Stage 2: optimise, thumbnail, encrypt, verify and write the row for a copied file. */
    suspend fun finish(staged: StagedMedia, caption: String = ""): ImportOutcome =
        withContext(Dispatchers.IO) {
            val picked = staged.picked
            val memoryId = staged.memoryId
            val optimize = staged.optimize
            val position = staged.position
            var job = db.importJobDao().get(staged.jobId) ?: ImportJobEntity(
                id = staged.jobId, sourceUri = picked.uri.toString(), memoryId = memoryId, stage = STAGE_COPIED,
                tempPath = staged.temp.path, targetPath = null, thumbPath = null, sha256 = null, optimize = optimize, createdAt = clock(),
            )
            val temp = staged.temp
            val targetFile = mediaStore.newMediaFile(if (picked.isVideo) "mp4" else "jpg")
            val thumbFile = mediaStore.newThumbFile()
            try {
                if (!temp.exists()) return@withContext fail(job, ImportFailure.Unreadable("copy missing"), temp, targetFile, thumbFile)
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
                            position = position, createdAt = clock(), latitude = picked.latitude, longitude = picked.longitude,
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

    private suspend fun fail(job: ImportJobEntity, failure: ImportFailure, vararg files: File): ImportOutcome.Failed {
        files.forEach { runCatching { it.delete() } }
        db.importJobDao().update(job.copy(stage = STAGE_FAILED))
        db.importJobDao().delete(job.id)
        return ImportOutcome.Failed(failure)
    }

    /** Cleans up anything an interrupted import left behind (called at startup). */
    suspend fun recoverPending() = withContext(Dispatchers.IO) {
        val pending = db.importJobDao().pending()
        for (job in pending) {
            // A copied file whose heavy work never ran (the app was closed) is finished now: the copy is ours, no grant needed.
            val copied = job.tempPath?.let(::File)
            val pickedJson = job.pickedJson?.let { PickedJson.decode(it) }
            if (job.stage == STAGE_COPIED && copied?.exists() == true && pickedJson != null && job.memoryId != null) {
                finish(StagedMedia(pickedJson.toPicked(), job.id, copied, job.memoryId, job.optimize, pickedJson.position))
                continue
            }
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

    private fun mimeFromName(name: String?): String? {
        val ext = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "webp" -> "image/webp"; "heic", "heif" -> "image/heic"; "gif" -> "image/gif"
            "mp4", "m4v" -> "video/mp4"; "mov" -> "video/quicktime"; "3gp" -> "video/3gpp"; "webm" -> "video/webm"; "mkv" -> "video/x-matroska"
            else -> null
        }
    }

    private fun sniffMime(uri: Uri): String? = runCatching {
        resolver.openInputStream(uri)?.use { input ->
            val head = ByteArray(16)
            val n = input.read(head)
            if (n < 4) return@use null
            when {
                head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() && head[2] == 0xFF.toByte() -> "image/jpeg"
                head[0] == 0x89.toByte() && head[1] == 'P'.code.toByte() && head[2] == 'N'.code.toByte() -> "image/png"
                head[0] == 'G'.code.toByte() && head[1] == 'I'.code.toByte() && head[2] == 'F'.code.toByte() -> "image/gif"
                n >= 12 && String(head, 0, 4, Charsets.US_ASCII) == "RIFF" && String(head, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
                n >= 12 && String(head, 4, 4, Charsets.US_ASCII) == "ftyp" -> {
                    val brand = String(head, 8, 4, Charsets.US_ASCII)
                    if (brand.startsWith("hei") || brand == "mif1" || brand == "msf1") "image/heic" else "video/mp4"
                }
                else -> null
            }
        }
    }.getOrNull()

    private fun exifOf(uri: Uri): ExifInterface? = runCatching { resolver.openInputStream(uri)?.use { ExifInterface(it) } }.getOrNull()

    private fun takenAtOf(exif: ExifInterface): Long? = runCatching {
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME) ?: return null
        LocalDateTime.parse(raw, EXIF_FORMAT).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
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
        /** Copied into the temp dir; the heavy work has not started (schema v2 jobs). */
        const val STAGE_COPIED = "COPIED"
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
