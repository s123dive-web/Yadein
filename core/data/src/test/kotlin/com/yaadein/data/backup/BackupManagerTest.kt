package com.yaadein.data.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.yaadein.data.crypto.MediaCrypto
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.db.DatabaseFactory
import com.yaadein.data.db.entity.MediaEntity
import com.yaadein.data.media.MediaStore
import com.yaadein.data.repository.MemoryRepositoryImpl
import com.yaadein.data.repository.PersonRepositoryImpl
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.model.Person
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var store: MediaStore
    private lateinit var manager: BackupManager
    private val fastKdf = BackupManager.KeyDeriver { pw, salt -> MessageDigest.getInstance("SHA-256").digest(salt + String(pw).toByteArray()) }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = DatabaseFactory.plainForTests(context)
        store = MediaStore(context, MediaCrypto(SecretKeySpec(ByteArray(32) { 9 }, "AES")))
        manager = BackupManager(context, db, store, MemoryRepositoryImpl(db) { 1L }, fastKdf)
    }

    @After
    fun tearDown() { db.close(); store.wipeAll() }

    @Test
    fun `export then restore round-trips people, memories, tags and media bytes`() = runTest {
        val people = PersonRepositoryImpl(db) { 1L }
        val memories = MemoryRepositoryImpl(db) { 1L }
        val aai = people.upsert(Person(name = "Aai", nickname = "Sunita", avatarColorIndex = 3))
        val memId = memories.create(Memory(type = MemoryType.EVENT, title = "Diwali", description = "lights", date = Dates.exact(LocalDate.of(2024, 10, 31)), place = "Pune"), listOf(aai))
        val plain = ByteArray(5000) { (it % 251).toByte() }
        val f = store.newMediaFile("jpg"); val t = store.newThumbFile()
        val sha = store.crypto.encryptBytes(plain, f); store.crypto.encryptBytes(ByteArray(64) { 1 }, t)
        val mediaId = db.mediaDao().insert(MediaEntity(memoryId = memId, kind = "PHOTO", encryptedPath = store.relativize(f), thumbPath = store.relativize(t), takenAt = 5, durationMs = null, sizeBytes = 5000, caption = "cap", width = 10, height = 10, sha256 = sha, position = 0, createdAt = 1))
        db.memoryDao().setCover(memId, mediaId, 2)

        val outUri = Uri.fromFile(File(context.cacheDir, "b.yaadein"))
        val bytes = ByteArrayOutputStream()
        Shadows.shadowOf(context.contentResolver).registerOutputStream(outUri, bytes)
        manager.export(outUri, "secret".toCharArray())
        val blob = bytes.toByteArray()
        assertTrue(blob.size > 200)
        assertTrue("payload must be encrypted", String(blob, Charsets.ISO_8859_1).indexOf("manifest.json") < 0)

        db.clearAllTables(); store.wipeAll()
        val inUri = Uri.parse("content://test/backup")
        Shadows.shadowOf(context.contentResolver).registerInputStream(inUri, blob.inputStream())
        val manifest = manager.peek(inUri, "secret".toCharArray())
        assertEquals(1, manifest.memories); assertEquals(1, manifest.media)
        Shadows.shadowOf(context.contentResolver).registerInputStream(inUri, blob.inputStream())
        manager.restore(inUri, "secret".toCharArray())

        assertEquals(listOf("Aai"), people.observeAll().first().map { it.name })
        val card = memories.observeCards().first().single()
        assertEquals("Diwali", card.memory.title)
        assertEquals(listOf("Aai"), card.people.map { it.name })
        assertEquals(1, card.mediaCount)
        val media = db.mediaDao().all().single()
        assertEquals(card.memory.coverMediaId, media.id)
        assertArrayEquals(plain, store.crypto.decryptToBytes(store.file(media.encryptedPath)))
        assertEquals(1, memories.search("diwali").size)
    }

    @Test
    fun `wrong password is rejected before touching the vault`() = runTest {
        val people = PersonRepositoryImpl(db) { 1L }
        people.upsert(Person(name = "Keep me"))
        val outUri = Uri.fromFile(File(context.cacheDir, "c.yaadein"))
        val bytes = ByteArrayOutputStream()
        Shadows.shadowOf(context.contentResolver).registerOutputStream(outUri, bytes)
        manager.export(outUri, "right".toCharArray())
        val inUri = Uri.parse("content://test/backup2")
        Shadows.shadowOf(context.contentResolver).registerInputStream(inUri, bytes.toByteArray().inputStream())
        try { manager.restore(inUri, "wrong".toCharArray()); fail("expected failure") } catch (_: Exception) { }
        assertEquals(listOf("Keep me"), people.observeAll().first().map { it.name })
    }
}
