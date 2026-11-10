package com.yaadein.data.faces

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.room.withTransaction
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.db.entity.FaceEntity
import com.yaadein.data.db.entity.FaceRejectionEntity
import com.yaadein.data.db.entity.MediaPersonEntity
import com.yaadein.data.db.entity.MemoryPersonEntity
import com.yaadein.data.media.MediaStore
import com.yaadein.domain.faces.FaceAlign
import com.yaadein.domain.faces.FaceMatch
import com.yaadein.domain.faces.FaceMatcher
import com.yaadein.domain.faces.FaceModel
import com.yaadein.domain.faces.PersonCentroid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/** States of a `faces` row. */
object FaceState {
    const val UNKNOWN = "UNKNOWN"
    const val SUGGESTED = "SUGGESTED"
    const val AUTO = "AUTO"
    const val CONFIRMED = "CONFIRMED"
}

/**
 * Face recognition, entirely on the phone and off by default: ML Kit's bundled detector finds faces and landmarks,
 * [FaceAlign] maps them to the 112×112 template, [FaceEmbedder] turns the crop into a 512-d embedding and
 * [FaceMatcher] compares it with each person's centroid of confirmed faces. Matches at or above AUTO are tagged with a
 * visible badge (one tap undoes); SUGGEST matches become chips; the rest wait in "Who is this?".
 */
@Singleton
class FaceSuggester @Inject constructor(
    private val db: AppDatabase,
    private val mediaStore: MediaStore,
    private val embedder: FaceEmbedder,
) {
    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setMinFaceSize(MIN_FACE_FRACTION)
                .build(),
        )
    }

    /** Detects, embeds and matches every face of one vault photo; records the scan. Returns the number of faces found. */
    suspend fun scan(mediaId: Long): Int = withContext(Dispatchers.Default) {
        val media = db.mediaDao().get(mediaId) ?: return@withContext 0
        if (media.kind != "PHOTO") { db.faceDao().markScanned(mediaId, FaceModel.VERSION); return@withContext 0 }
        val bytes = runCatching { mediaStore.crypto.decryptToBytes(mediaStore.file(media.encryptedPath)) }.getOrNull()
            ?: run { db.faceDao().markScanned(mediaId, FaceModel.VERSION); return@withContext 0 }
        val bmp = decode(bytes) ?: run { db.faceDao().markScanned(mediaId, FaceModel.VERSION); return@withContext 0 }
        try {
            val started = SystemClock.elapsedRealtime()
            val found = embedAll(bmp)
            val centroids = centroids()
            val tagged = db.mediaDao().personIds(mediaId).toSet()
            db.withTransaction {
                db.faceDao().deleteForMedia(mediaId)
                for (f in found) {
                    val match = FaceMatcher.match(f.embedding, centroids)
                    val row = FaceEntity(
                        mediaId = mediaId, embedding = f.embedding.toBytes(), modelVersion = FaceModel.VERSION, quality = f.quality,
                        boxLeft = f.box[0], boxTop = f.box[1], boxRight = f.box[2], boxBottom = f.box[3],
                        personId = match.personId, state = stateOf(match, alreadyTagged = match.personId in tagged),
                        similarity = (match as? FaceMatch.Auto)?.similarity ?: (match as? FaceMatch.Suggest)?.similarity,
                        createdAt = System.currentTimeMillis(),
                    )
                    db.faceDao().insert(row)
                    if (row.state == FaceState.AUTO && row.personId != null) tag(mediaId, media.memoryId, row.personId)
                }
                db.faceDao().markScanned(mediaId, FaceModel.VERSION)
            }
            if (found.isNotEmpty()) {
                Log.i(TAG, "embed_ms=${(SystemClock.elapsedRealtime() - started) / found.size} faces=${found.size} media=$mediaId")
            }
            found.size
        } finally {
            bmp.recycle()
        }
    }

    /**
     * People likely in a photo that is not in the vault yet (Quick Save's chips). [open] gives the photo's bytes.
     * Nothing is stored; the user still taps to tag.
     */
    suspend fun suggestPeople(open: () -> InputStream?): Set<Long> = withContext(Dispatchers.Default) {
        val centroids = centroids()
        if (centroids.isEmpty()) return@withContext emptySet()
        val bytes = runCatching { open()?.use { it.readBytes() } }.getOrNull() ?: return@withContext emptySet()
        val bmp = decode(bytes) ?: return@withContext emptySet()
        try {
            embedAll(bmp).mapNotNullTo(HashSet()) { FaceMatcher.match(it.embedding, centroids).personId }
        } finally {
            bmp.recycle()
        }
    }

    /** "Yes, this is Aaji": confirms the face, folds it into the centroid, tags the photo and re-scores pending faces. */
    suspend fun confirm(faceId: Long, personId: Long) = withContext(Dispatchers.Default) {
        val face = db.faceDao().get(faceId) ?: return@withContext
        val media = db.mediaDao().get(face.mediaId)
        db.withTransaction {
            db.faceDao().assign(faceId, personId, FaceState.CONFIRMED, 1f)
            val person = db.personDao().get(personId) ?: return@withTransaction
            val current = person.centroid?.takeIf { person.centroidModel == FaceModel.VERSION }?.let { PersonCentroid(personId, it.toFloats(), person.confirmedFaces) }
            val next = FaceMatcher.addToCentroid(current, personId, face.embedding.toFloats())
            db.faceDao().setCentroid(personId, next.centroid.toBytes(), FaceModel.VERSION, next.count)
            tag(face.mediaId, media?.memoryId, personId)
        }
        rescore(personId)
    }

    /** Names every face of a "Who is this?" group in one go. */
    suspend fun confirmAll(faceIds: List<Long>, personId: Long) { faceIds.forEach { confirm(it, personId) } }

    /** "Not this person": remembers the rejection; an automatic tag is undone. */
    suspend fun reject(faceId: Long, personId: Long) = withContext(Dispatchers.Default) {
        val face = db.faceDao().get(faceId) ?: return@withContext
        db.withTransaction {
            db.faceDao().reject(FaceRejectionEntity(faceId, personId))
            if (face.personId == personId && face.state != FaceState.CONFIRMED) {
                if (face.state == FaceState.AUTO) untag(face.mediaId, personId)
                db.faceDao().assign(faceId, null, FaceState.UNKNOWN, null)
            }
            if (face.state == FaceState.CONFIRMED && face.personId == personId) {
                // Undo of a confirmation: take the face back out of the centroid.
                val person = db.personDao().get(personId)
                val current = person?.let { p -> p.centroid?.takeIf { p.centroidModel == FaceModel.VERSION }?.let { PersonCentroid(personId, it.toFloats(), p.confirmedFaces) } }
                val next = current?.let { FaceMatcher.removeFromCentroid(it, face.embedding.toFloats()) }
                db.faceDao().setCentroid(personId, next?.centroid?.toBytes(), next?.let { FaceModel.VERSION }, next?.count ?: 0)
                untag(face.mediaId, personId)
                db.faceDao().assign(faceId, null, FaceState.UNKNOWN, null)
            }
        }
    }

    /** Groups of unknown faces that look alike (largest first), for "Who is this?". */
    suspend fun unknownGroups(): List<List<FaceEntity>> {
        val unknown = db.faceDao().byState(FaceState.UNKNOWN).filter { it.modelVersion == FaceModel.VERSION }
        val byId = unknown.associateBy { it.id }
        return FaceMatcher.cluster(unknown.map { it.id to it.embedding.toFloats() }).map { ids -> ids.mapNotNull { byId[it] } }
    }

    /** Re-scores faces that are not confirmed against [personId]'s centroid (after a confirm or reject). */
    suspend fun rescore(personId: Long) = withContext(Dispatchers.Default) {
        val person = db.personDao().get(personId) ?: return@withContext
        val centroid = person.centroid?.takeIf { person.centroidModel == FaceModel.VERSION }?.let { PersonCentroid(personId, it.toFloats(), person.confirmedFaces) }
        val pending = db.faceDao().pendingFor(personId)
        db.withTransaction {
            for (f in pending) {
                val rejected = db.faceDao().rejectedPeople(f.id).toSet()
                val match = centroid?.let { FaceMatcher.match(f.embedding.toFloats(), listOf(it), rejected) } ?: FaceMatch.Unknown
                val media = db.mediaDao().get(f.mediaId)
                when (match) {
                    is FaceMatch.Auto -> {
                        db.faceDao().assign(f.id, personId, FaceState.AUTO, match.similarity)
                        tag(f.mediaId, media?.memoryId, personId)
                    }
                    is FaceMatch.Suggest -> {
                        if (f.state == FaceState.AUTO && f.personId == personId) untag(f.mediaId, personId)
                        db.faceDao().assign(f.id, personId, FaceState.SUGGESTED, match.similarity)
                    }
                    FaceMatch.Unknown -> if (f.personId == personId) {
                        if (f.state == FaceState.AUTO) untag(f.mediaId, personId)
                        db.faceDao().assign(f.id, null, FaceState.UNKNOWN, null)
                    }
                }
            }
        }
    }

    /** Settings → Privacy → Delete all face data: every embedding, rejection and centroid; photos are scanned again only if asked. */
    suspend fun deleteAll() = db.withTransaction {
        db.faceDao().clearRejections()
        db.faceDao().clear()
        db.faceDao().clearCentroids()
        db.faceDao().resetScanLedger()
    }

    suspend fun unscannedCount(): Int = db.faceDao().unscannedCount(FaceModel.VERSION)

    // ---- internals ----

    private class Found(val embedding: FloatArray, val quality: Float, val box: FloatArray)

    private suspend fun embedAll(bmp: Bitmap): List<Found> {
        val faces: List<Face> = runCatching { detector.process(InputImage.fromBitmap(bmp, 0)).await() }.getOrNull() ?: return emptyList()
        val longest = maxOf(bmp.width, bmp.height).toFloat()
        return faces.mapNotNull { face ->
            val box = face.boundingBox
            if (maxOf(box.width(), box.height()) < FaceModel.MIN_FACE_PX) return@mapNotNull null
            val points = listOf(FaceLandmark.LEFT_EYE, FaceLandmark.RIGHT_EYE, FaceLandmark.NOSE_BASE, FaceLandmark.MOUTH_LEFT, FaceLandmark.MOUTH_RIGHT)
                .map { face.getLandmark(it)?.position }
            val align = if (points.all { it != null }) {
                FaceAlign.similarity(points.map { it!!.x to it.y })
            } else {
                val l = points[0]; val r = points[1]
                if (l != null && r != null) FaceAlign.fromEyes(l.x, l.y, r.x, r.y) else boxAlign(box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat())
            } ?: return@mapNotNull null
            val embedding = runCatching { embedder.embed(bmp, align) }.getOrNull() ?: return@mapNotNull null
            Found(
                embedding = embedding,
                quality = (maxOf(box.width(), box.height()) / longest).coerceIn(0f, 1f),
                box = floatArrayOf(box.left / bmp.width.toFloat(), box.top / bmp.height.toFloat(), box.right / bmp.width.toFloat(), box.bottom / bmp.height.toFloat()),
            )
        }
    }

    /** No landmarks: scale the box onto the 112 square (eyes roughly where the template expects them). */
    private fun boxAlign(l: Float, t: Float, r: Float, b: Float) =
        FaceAlign.similarity(listOf(l to t, r to t), listOf(8f to 8f, 104f to 8f))

    private suspend fun centroids(): List<PersonCentroid> =
        db.faceDao().peopleWithCentroids(FaceModel.VERSION).mapNotNull { p -> p.centroid?.let { PersonCentroid(p.id, it.toFloats(), p.confirmedFaces) } }

    private fun stateOf(match: FaceMatch, alreadyTagged: Boolean): String = when (match) {
        is FaceMatch.Auto -> if (alreadyTagged) FaceState.CONFIRMED else FaceState.AUTO
        is FaceMatch.Suggest -> if (alreadyTagged) FaceState.CONFIRMED else FaceState.SUGGESTED
        FaceMatch.Unknown -> FaceState.UNKNOWN
    }

    private suspend fun tag(mediaId: Long, memoryId: Long?, personId: Long) {
        db.mediaDao().insertPeople(listOf(MediaPersonEntity(mediaId, personId)))
        if (memoryId != null) db.memoryDao().insertPeople(listOf(MemoryPersonEntity(memoryId, personId)))
    }

    private suspend fun untag(mediaId: Long, personId: Long) {
        val remaining = db.mediaDao().personIds(mediaId).filter { it != personId }
        db.mediaDao().clearPeople(mediaId)
        db.mediaDao().insertPeople(remaining.map { MediaPersonEntity(mediaId, it) })
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / sample > MAX_DECODE_PX) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    companion object {
        private const val TAG = "Yaadein.Faces"
        private const val MIN_FACE_FRACTION = 0.05f
        private const val MAX_DECODE_PX = 1600
        fun FloatArray.toBytes(): ByteArray { val bb = ByteBuffer.allocate(size * 4).order(ByteOrder.LITTLE_ENDIAN); forEach { bb.putFloat(it) }; return bb.array() }
        fun ByteArray.toFloats(): FloatArray { val bb = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN); return FloatArray(size / 4) { bb.getFloat() } }
    }
}
