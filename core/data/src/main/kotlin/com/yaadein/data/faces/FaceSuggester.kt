package com.yaadein.data.faces

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.db.entity.FaceClusterEntity
import com.yaadein.data.media.MediaStore
import com.yaadein.domain.faces.Cluster
import com.yaadein.domain.faces.FaceClustering
import com.yaadein.domain.faces.FaceFeatures
import com.google.mlkit.vision.face.FaceLandmark
import com.yaadein.domain.faces.FaceSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional, off by default. Detects faces with ML Kit's bundled on-device model, builds a small
 * appearance descriptor (eye-aligned 64×64 crop, uniform LBP histograms; see FaceFeatures) and clusters them. Nothing is tagged
 * until the user confirms "Is this Rahul?" in Settings → Face suggestions.
 */
@Singleton
class FaceSuggester @Inject constructor(private val db: AppDatabase, private val mediaStore: MediaStore) {
    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setMinFaceSize(0.08f)
                .build(),
        )
    }

    /** Scans one media item (thumbnail-sized decode keeps it cheap) and folds its faces into the clusters. */
    suspend fun scan(mediaId: Long): Int = withContext(Dispatchers.Default) {
        val media = db.mediaDao().get(mediaId) ?: return@withContext 0
        if (media.kind != "PHOTO") return@withContext 0
        val bytes = runCatching { mediaStore.crypto.decryptToBytes(mediaStore.file(media.encryptedPath)) }.getOrNull() ?: return@withContext 0
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / sample > 1024) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@withContext 0
        try {
            val faces = detector.process(InputImage.fromBitmap(bmp, 0)).await()
            // Suggestions made with the older pixel descriptor cannot be compared with LBP ones: start them afresh
            // (tags already confirmed on photos are kept; they live on the photos, not here).
            db.faceDao().all().filter { it.embedding.size != FaceFeatures.DIMENSIONS * 4 }.forEach { db.faceDao().delete(it.id) }
            var clusters = db.faceDao().all().map { it.toCluster() }
            var nextId = (clusters.maxOfOrNull { it.id } ?: 0L) + 1
            var added = 0
            for (face in faces) {
                val box = face.boundingBox
                if (box.width() < 24 || box.height() < 24) continue
                val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
                val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
                val emb = if (leftEye != null && rightEye != null) alignedEmbedding(bmp, leftEye.x, leftEye.y, rightEye.x, rightEye.y)
                else embed(bmp, box.left, box.top, box.right, box.bottom)
                val s = FaceSample(mediaId, emb, box.left / bmp.width.toFloat(), box.top / bmp.height.toFloat(), box.right / bmp.width.toFloat(), box.bottom / bmp.height.toFloat())
                val (updated, id) = FaceClustering.assign(s, clusters, nextId, FaceFeatures.THRESHOLD)
                clusters = updated
                if (id == nextId) nextId++
                added++
            }
            persist(clusters)
            added
        } finally {
            bmp.recycle()
        }
    }

    /**
     * People whose confirmed face cluster matches a face in a photo that is not in the vault yet (Quick Save's chips).
     * [open] gives the photo's bytes (a content URI the user just picked). Nothing is stored; the user still taps to tag.
     */
    suspend fun suggestPeople(open: () -> java.io.InputStream?): Set<Long> = withContext(Dispatchers.Default) {
        val known = db.faceDao().all().filter { it.confirmed && it.suggestedPersonId != null && it.embedding.size == FaceFeatures.DIMENSIONS * 4 }
        if (known.isEmpty()) return@withContext emptySet()
        val bytes = runCatching { open()?.use { it.readBytes() } }.getOrNull() ?: return@withContext emptySet()
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / sample > 1024) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@withContext emptySet()
        try {
            val faces = runCatching { detector.process(InputImage.fromBitmap(bmp, 0)).await() }.getOrNull() ?: return@withContext emptySet()
            val found = HashSet<Long>()
            for (face in faces) {
                val box = face.boundingBox
                if (box.width() < 24 || box.height() < 24) continue
                val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
                val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
                val emb = if (leftEye != null && rightEye != null) alignedEmbedding(bmp, leftEye.x, leftEye.y, rightEye.x, rightEye.y)
                else embed(bmp, box.left, box.top, box.right, box.bottom)
                val best = known.minByOrNull { FaceClustering.cosineDistance(it.embedding.toFloats(), emb) } ?: continue
                if (FaceClustering.cosineDistance(best.embedding.toFloats(), emb) <= FaceFeatures.THRESHOLD) best.suggestedPersonId?.let(found::add)
            }
            found
        } finally {
            bmp.recycle()
        }
    }

    /**
     * Rotates and scales the face so the eyes are level and a fixed distance apart, then describes the 64×64 grey crop
     * with [FaceFeatures]. Alignment matters more than anything else for telling people apart.
     */
    private fun alignedEmbedding(bmp: Bitmap, lx: Float, ly: Float, rx: Float, ry: Float): FloatArray {
        val n = FaceFeatures.SIZE
        val eyeDist = kotlin.math.hypot(rx - lx, ry - ly).coerceAtLeast(1f)
        val angle = Math.toDegrees(kotlin.math.atan2((ry - ly).toDouble(), (rx - lx).toDouble())).toFloat()
        val midX = (lx + rx) / 2f; val midY = (ly + ry) / 2f
        // Eyes land at 30% / 70% across and 38% down the crop.
        val scale = (n * 0.4f) / eyeDist
        val m = android.graphics.Matrix().apply {
            postTranslate(-midX, -midY)
            postRotate(-angle)
            postScale(scale, scale)
            postTranslate(n / 2f, n * 0.38f)
        }
        val out = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(out).drawBitmap(bmp, m, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        val grey = greyOf(out)
        out.recycle()
        return FaceFeatures.embed(grey)
    }

    /** Fallback when the eyes were not found: the face box, squashed to 64×64. */
    private fun embed(bmp: Bitmap, l: Int, t: Int, r: Int, b: Int): FloatArray {
        val left = l.coerceIn(0, bmp.width - 1); val top = t.coerceIn(0, bmp.height - 1)
        val w = (r - l).coerceIn(1, bmp.width - left); val h = (b - t).coerceIn(1, bmp.height - top)
        val crop = Bitmap.createBitmap(bmp, left, top, w, h)
        val small = Bitmap.createScaledBitmap(crop, FaceFeatures.SIZE, FaceFeatures.SIZE, true)
        val grey = greyOf(small)
        crop.recycle(); small.recycle()
        return FaceFeatures.embed(grey)
    }

    private fun greyOf(bmp: Bitmap): IntArray {
        val n = bmp.width * bmp.height
        val px = IntArray(n); bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return IntArray(n) { i -> val p = px[i]; (0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)).toInt() }
    }

    private suspend fun persist(clusters: List<Cluster>) {
        val existing = db.faceDao().all().associateBy { it.id }
        clusters.forEach { c ->
            val old = existing[c.id]
            db.faceDao().upsert(
                FaceClusterEntity(
                    id = c.id, embedding = c.centroid.toBytes(), sampleMediaId = c.sampleMediaId,
                    sampleLeft = c.sampleBox[0], sampleTop = c.sampleBox[1], sampleRight = c.sampleBox[2], sampleBottom = c.sampleBox[3],
                    suggestedPersonId = old?.suggestedPersonId, confirmed = old?.confirmed ?: false, faceCount = c.count, createdAt = old?.createdAt ?: System.currentTimeMillis(),
                ),
            )
        }
    }

    suspend fun clearAll() = db.faceDao().clear()

    companion object {
        const val SIZE = 32
        fun FloatArray.toBytes(): ByteArray { val bb = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN); forEach { bb.putFloat(it) }; return bb.array() }
        fun ByteArray.toFloats(): FloatArray { val bb = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN); return FloatArray(size / 4) { bb.getFloat() } }
        fun FaceClusterEntity.toCluster() = Cluster(id, embedding.toFloats(), faceCount, sampleMediaId ?: 0L, floatArrayOf(sampleLeft, sampleTop, sampleRight, sampleBottom))
    }
}
