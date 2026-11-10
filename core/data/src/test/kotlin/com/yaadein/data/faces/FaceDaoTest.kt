package com.yaadein.data.faces

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.db.DatabaseFactory
import com.yaadein.data.db.entity.FaceEntity
import com.yaadein.data.db.entity.FaceRejectionEntity
import com.yaadein.data.db.entity.MediaEntity
import com.yaadein.data.db.entity.MemoryEntity
import com.yaadein.data.db.entity.PersonEntity
import com.yaadein.data.faces.FaceSuggester.Companion.toBytes
import com.yaadein.data.faces.FaceSuggester.Companion.toFloats
import com.yaadein.domain.faces.FaceModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The face tables and the scan ledger (the detector and the model themselves need a device). */
@RunWith(RobolectricTestRunner::class)
class FaceDaoTest {
    private lateinit var db: AppDatabase
    private var aai = 0L
    private var photo1 = 0L
    private var photo2 = 0L

    @Before
    fun setUp() = runTest {
        db = DatabaseFactory.plainForTests(ApplicationProvider.getApplicationContext<Context>())
        aai = db.personDao().upsert(PersonEntity(name = "Aai", nickname = null, relation = "FAMILY", birthdayEpochDay = null, profileMediaId = null, notes = "", avatarColorIndex = 0, createdAt = 1))
        val memory = db.memoryDao().insert(MemoryEntity(type = "EVENT", title = "t", description = "", dateKind = "EXACT", startEpochDay = 1, endEpochDay = null, fuzzyLabel = null, place = "", mood = null, coverMediaId = null, coverStyle = "plain_plum", voiceNotePath = null, createdAt = 1, updatedAt = 1))
        photo1 = db.mediaDao().insert(MediaEntity(memoryId = memory, kind = "PHOTO", encryptedPath = "a", thumbPath = "ta", takenAt = null, durationMs = null, sizeBytes = 1, caption = "", width = 1, height = 1, sha256 = "x", position = 0, createdAt = 1))
        photo2 = db.mediaDao().insert(MediaEntity(memoryId = memory, kind = "PHOTO", encryptedPath = "b", thumbPath = "tb", takenAt = null, durationMs = null, sizeBytes = 1, caption = "", width = 1, height = 1, sha256 = "y", position = 1, createdAt = 1))
    }

    @After
    fun tearDown() = db.close()

    private fun face(mediaId: Long, state: String, personId: Long? = null, v: Float = 1f) = FaceEntity(
        mediaId = mediaId, embedding = floatArrayOf(v, 0f, 0f, 0f).toBytes(), modelVersion = FaceModel.VERSION, quality = 0.5f,
        boxLeft = 0.1f, boxTop = 0.1f, boxRight = 0.4f, boxBottom = 0.5f, personId = personId, state = state, similarity = null, createdAt = 1,
    )

    @Test
    fun `scan ledger lists unscanned photos newest first and marks them`() = runTest {
        assertEquals(listOf(photo2, photo1), db.faceDao().nextUnscanned(FaceModel.VERSION, 10).map { it.id })
        assertEquals(2, db.faceDao().unscannedCount(FaceModel.VERSION))
        db.faceDao().markScanned(photo2, FaceModel.VERSION)
        assertEquals(listOf(photo1), db.faceDao().nextUnscanned(FaceModel.VERSION, 10).map { it.id })
        assertEquals(1, db.faceDao().observeUnscannedCount(FaceModel.VERSION).first())
        db.faceDao().resetScanLedger()
        assertEquals(2, db.faceDao().unscannedCount(FaceModel.VERSION))
    }

    @Test
    fun `faces, rejections, centroids and cascade on delete`() = runTest {
        val unknown = db.faceDao().insert(face(photo1, FaceState.UNKNOWN))
        val suggested = db.faceDao().insert(face(photo2, FaceState.SUGGESTED, aai))
        assertEquals(1, db.faceDao().observeUnknownCount().first())
        assertEquals(listOf(suggested), db.faceDao().pendingFor(aai).map { it.id }.filter { it == suggested })
        assertEquals(2, db.faceDao().pendingFor(aai).size) // the unknown one (person null) is a candidate too
        db.faceDao().reject(FaceRejectionEntity(unknown, aai))
        assertEquals(listOf(aai), db.faceDao().rejectedPeople(unknown))
        db.faceDao().setCentroid(aai, floatArrayOf(1f, 0f, 0f, 0f).toBytes(), FaceModel.VERSION, 1)
        val people = db.faceDao().peopleWithCentroids(FaceModel.VERSION)
        assertEquals(1, people.size)
        assertEquals(1f, people.single().centroid!!.toFloats()[0], 0f)
        assertEquals(1, people.single().confirmedFaces)
        // Deleting the photo removes its faces (and the rejection with it).
        db.mediaDao().delete(photo1)
        assertNull(db.faceDao().get(unknown))
        assertTrue(db.faceDao().allRejections().isEmpty())
        // Deleting the person clears the suggestion but keeps the face.
        db.personDao().delete(aai)
        assertNull(db.faceDao().get(suggested)!!.personId)
        db.faceDao().clear(); db.faceDao().clearCentroids()
        assertEquals(0, db.faceDao().count())
    }
}
