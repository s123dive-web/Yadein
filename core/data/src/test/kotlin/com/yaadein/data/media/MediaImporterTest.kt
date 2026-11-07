package com.yaadein.data.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.yaadein.data.crypto.MediaCrypto
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.db.DatabaseFactory
import com.yaadein.data.db.entity.ImportJobEntity
import com.yaadein.data.db.entity.MemoryEntity
import com.yaadein.data.repository.MemoryRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import java.io.ByteArrayOutputStream
import java.io.File
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
class MediaImporterTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var store: MediaStore
    private lateinit var importer: MediaImporter
    private var memoryId = 0L

    @Before
    fun setUp() = runTest {
        context = ApplicationProvider.getApplicationContext()
        db = DatabaseFactory.plainForTests(context)
        store = MediaStore(context, MediaCrypto(SecretKeySpec(ByteArray(32) { 3 }, "AES")))
        val repo = MemoryRepositoryImpl(db) { 1L }
        importer = MediaImporter(context, db, store, repo, { _, _ -> null }) { 42L }
        memoryId = db.memoryDao().insert(
            MemoryEntity(type = "TEXT_ONLY", title = "t", description = "", dateKind = "EXACT", startEpochDay = 1, endEpochDay = null, fuzzyLabel = null, place = "", mood = null, coverMediaId = null, coverStyle = "plain_plum", voiceNotePath = null, createdAt = 1, updatedAt = 1),
        )
    }

    @After
    fun tearDown() {
        db.close()
        store.wipeAll()
    }

    private fun jpegUri(name: String, w: Int, h: Int): Pair<Uri, ByteArray> {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF336699.toInt()) }
        val bytes = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        val uri = Uri.parse("content://test/$name")
        Shadows.shadowOf(context.contentResolver).registerInputStream(uri, bytes.inputStream())
        return uri to bytes
    }

    @Test
    fun `photo import encrypts, thumbnails, verifies and writes the row`() = runTest {
        val (uri, bytes) = jpegUri("a.jpg", 640, 480)
        val picked = PickedMedia(uri, isVideo = false, mimeType = "image/jpeg", sizeBytes = bytes.size.toLong(), takenAtMillis = 1_000L, displayName = "a.jpg")
        val outcome = importer.import(picked, memoryId, optimize = true, position = 0)
        assertTrue(outcome.toString(), outcome is ImportOutcome.Success)
        val media = db.mediaDao().forMemory(memoryId).single()
        assertEquals(640, media.width); assertEquals(480, media.height)
        val enc = store.file(media.encryptedPath)
        assertTrue(enc.exists())
        val head = enc.readBytes().copyOfRange(MediaCrypto.HEADER_SIZE, MediaCrypto.HEADER_SIZE + 4)
        assertFalse("ciphertext must not start with the JPEG header", head.contentEquals(bytes.copyOfRange(0, 4)))
        assertEquals(media.sha256, store.crypto.sha256OfDecrypted(enc))
        assertTrue(store.file(media.thumbPath).exists())
        val memory = db.memoryDao().get(memoryId)!!
        assertEquals(media.id, memory.coverMediaId)
        assertEquals("EVENT", memory.type)
        assertTrue(db.importJobDao().pending().isEmpty())
        assertTrue("temp dir must be empty", store.tmpDir.listFiles().isNullOrEmpty())
    }

    @Test
    fun `oversized files are rejected before any copy`() = runTest {
        val picked = PickedMedia(Uri.parse("content://test/big"), isVideo = false, mimeType = "image/jpeg", sizeBytes = MediaImporter.MAX_PHOTO_BYTES + 1, takenAtMillis = null, displayName = null)
        val outcome = importer.import(picked, memoryId, optimize = true, position = 0)
        assertTrue(outcome is ImportOutcome.Failed && outcome.failure is ImportFailure.TooLarge)
        assertEquals(0, db.mediaDao().count())
        val video = picked.copy(isVideo = true, sizeBytes = MediaImporter.MAX_VIDEO_BYTES + 1)
        // Without compression the keep limit applies to the source file.
        assertNotNull(importer.checkLimit(video, optimize = false))
        assertNull(importer.checkLimit(video.copy(sizeBytes = MediaImporter.MAX_VIDEO_BYTES), optimize = false))
        // With compression a larger source is accepted (the limit applies to the compressed copy), up to 2 GB.
        // (The JVM test disk is tiny, so this may report NotEnoughSpace instead; never TooLarge.)
        assertTrue(importer.checkLimit(video, optimize = true) !is ImportFailure.TooLarge)
        assertTrue(importer.checkLimit(video.copy(sizeBytes = MediaImporter.MAX_SOURCE_VIDEO_BYTES + 1), optimize = true) is ImportFailure.TooLarge)
    }

    @Test
    fun `unreadable source fails cleanly and leaves no files`() = runTest {
        val picked = PickedMedia(Uri.parse("content://test/missing"), isVideo = false, mimeType = "image/jpeg", sizeBytes = 10, takenAtMillis = null, displayName = null)
        val outcome = importer.import(picked, memoryId, optimize = false, position = 0)
        assertTrue(outcome is ImportOutcome.Failed)
        assertEquals(0, db.mediaDao().count())
        assertTrue(store.mediaDir.listFiles().isNullOrEmpty())
        assertTrue(db.importJobDao().pending().isEmpty())
    }

    @Test
    fun `large photos are downscaled when optimising`() = runTest {
        val (uri, bytes) = jpegUri("big.jpg", 3000, 2000)
        val picked = PickedMedia(uri, isVideo = false, mimeType = "image/jpeg", sizeBytes = bytes.size.toLong(), takenAtMillis = null, displayName = null)
        importer.import(picked, memoryId, optimize = true, position = 0)
        val media = db.mediaDao().forMemory(memoryId).single()
        assertEquals(MediaImporter.MAX_PHOTO_EDGE, media.width)
        assertTrue(media.height in 1700..1710)
    }

    @Test
    fun `video date parsing handles retriever formats`() {
        assertNotNull(MediaImporter.parseVideoDate("20240101T120000.000Z"))
        assertNotNull(MediaImporter.parseVideoDate("2024-01-01T12:00:00.000Z"))
        assertNull(MediaImporter.parseVideoDate("nonsense"))
    }

    @Test
    fun `recoverPending discards half-finished jobs and prunes orphans`() = runTest {
        val orphan = File(store.mediaDir, "orphan.jpg.enc").apply { writeBytes(ByteArray(10)) }
        db.importJobDao().insert(ImportJobEntity(sourceUri = "content://x", memoryId = memoryId, stage = MediaImporter.STAGE_COPYING, tempPath = null, targetPath = null, thumbPath = null, sha256 = null, optimize = true, createdAt = 1))
        importer.recoverPending()
        assertFalse(orphan.exists())
        assertTrue(db.importJobDao().pending().isEmpty())
    }
}
