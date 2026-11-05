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
 * appearance embedding (32×32 grey crop + landmark geometry) and clusters them. Nothing is tagged
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
            var clusters = db.faceDao().all().map { it.toCluster() }
            var nextId = (clusters.maxOfOrNull { it.id } ?: 0L) + 1
            var added = 0
            for (face in faces) {
                val box = face.boundingBox
                if (box.width() < 24 || box.height() < 24) continue
                val emb = embed(bmp, box.left, box.top, box.right, box.bottom)
                val s = FaceSample(mediaId, emb, box.left / bmp.width.toFloat(), box.top / bmp.height.toFloat(), box.right / bmp.width.toFloat(), box.bottom / bmp.height.toFloat())
                val (updated, id) = FaceClustering.assign(s, clusters, nextId)
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

    private fun embed(bmp: Bitmap, l: Int, t: Int, r: Int, b: Int): FloatArray {
        val left = l.coerceIn(0, bmp.width - 1); val top = t.coerceIn(0, bmp.height - 1)
        val w = (r - l).coerceIn(1, bmp.width - left); val h = (b - t).coerceIn(1, bmp.height - top)
        val crop = Bitmap.createBitmap(bmp, left, top, w, h)
        val small = Bitmap.createScaledBitmap(crop, SIZE, SIZE, true)
        val px = IntArray(SIZE * SIZE); small.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        val v = FloatArray(SIZE * SIZE)
        var mean = 0f
        for (i in px.indices) { val p = px[i]; val g = (0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)) / 255f; v[i] = g; mean += g }
        mean /= v.size
        for (i in v.indices) v[i] -= mean
        crop.recycle(); small.recycle()
        return FaceClustering.normalise(v)
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
