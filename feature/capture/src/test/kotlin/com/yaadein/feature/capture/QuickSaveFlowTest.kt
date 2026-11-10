package com.yaadein.feature.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.yaadein.core.ui.theme.YaadeinTheme
import com.yaadein.data.crypto.MediaCrypto
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.db.DatabaseFactory
import com.yaadein.data.faces.FaceSuggester
import com.yaadein.data.lock.AppLockState
import com.yaadein.data.media.FaceIndexer
import com.yaadein.data.media.ImportCoordinator
import com.yaadein.data.media.MediaImporter
import com.yaadein.data.media.MediaStore
import com.yaadein.data.media.SharedMediaInbox
import com.yaadein.data.repository.CaptureStatsRepositoryImpl
import com.yaadein.data.repository.MemoryRepositoryImpl
import com.yaadein.data.repository.PersonRepositoryImpl
import com.yaadein.domain.capture.CaptureEntry
import com.yaadein.domain.capture.DateSource
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.model.Person
import com.yaadein.domain.repository.SettingsRepository
import com.yaadein.domain.repository.TimelineFilter
import com.yaadein.domain.settings.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import javax.crypto.spec.SecretKeySpec

/**
 * Quick Save end to end on the JVM: ten fixture photos over two days → the date is pre-filled from EXIF, a title is
 * suggested, Save returns in well under half a second and the photos are encrypted in the background. Also the
 * Share → Yaadein hand-over and the words-only mode.
 */
@RunWith(RobolectricTestRunner::class)
class QuickSaveFlowTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var store: MediaStore
    private lateinit var memories: MemoryRepositoryImpl
    private lateinit var people: PersonRepositoryImpl
    private lateinit var importer: MediaImporter
    private lateinit var coordinator: ImportCoordinator
    private lateinit var stats: CaptureStatsRepositoryImpl
    private val settings = FakeSettings()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = DatabaseFactory.plainForTests(context)
        store = MediaStore(context, MediaCrypto(SecretKeySpec(ByteArray(32) { 7 }, "AES")))
        memories = MemoryRepositoryImpl(db) { System.currentTimeMillis() }
        people = PersonRepositoryImpl(db) { System.currentTimeMillis() }
        importer = MediaImporter(context, db, store, memories, { _, _ -> null }) { System.currentTimeMillis() }
        coordinator = ImportCoordinator(importer, settings, FaceIndexer { })
        stats = CaptureStatsRepositoryImpl(db)
        QuickSaveInbox.take(); QuickSaveInbox.takeForSplit()
        serve = FakeMediaProvider.install(context)
        // FileProvider caches its roots per authority while every Robolectric test gets a fresh cache dir.
        runCatching { androidx.core.content.FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }.get(null).let { (it as HashMap<*, *>).clear() } }
    }

    @After
    fun tearDown() {
        db.close()
        store.wipeAll()
    }

    private fun viewModel(fromShare: Boolean = false) = QuickSaveViewModel(
        context = context,
        savedStateHandle = SavedStateHandle(mapOf("fromShare" to fromShare)),
        memoryRepository = memories,
        personRepository = people,
        settingsRepository = settings,
        importer = importer,
        coordinator = coordinator,
        captureStats = stats,
        faceSuggester = FaceSuggester(db, store, com.yaadein.data.faces.FaceEmbedder(context)),
        lockState = AppLockState(),
        festivals = com.yaadein.domain.relive.Festivals.bundled(),
    )

    private lateinit var serve: (String, ByteArray) -> Uri

    /** A small JPEG with an EXIF date, served by a gallery-like content provider. */
    private fun photo(name: String, date: String): Uri {
        val bmp = Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF4466AA.toInt()) }
        val file = File(context.cacheDir, name).apply { outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
        ExifInterface(file).apply { setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, date); saveAttributes() }
        return serve(name, file.readBytes())
    }

    /** Waits for [condition], pumping both Compose and the main looper (Room resumes coroutines there). */
    private fun await(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("condition not met within $timeoutMs ms")
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.waitForIdle()
            Thread.sleep(10)
        }
    }

    private fun tenPhotosOverTwoDays(): List<Uri> =
        (1..6).map { photo("day1_$it.jpg", "2024:12:20 1$it:00:00") } + (1..4).map { photo("day2_$it.jpg", "2024:12:21 1$it:00:00") }

    @Test
    fun `ten photos over two days pre-fill an exact date and a title, and Save returns fast`() {
        val aaji = runBlocking { people.upsert(Person(name = "Shalini Joshi", nickname = "Aaji")) }
        runBlocking { memories.create(com.yaadein.domain.model.Memory(type = MemoryType.EVENT, title = "Old", date = Dates.exact(LocalDate.of(2024, 1, 1))), listOf(aaji)) }
        val uris = tenPhotosOverTwoDays()
        QuickSaveInbox.offer(QuickSaveRequest(uris, CaptureEntry.FAB, SystemClock.elapsedRealtime() - 4_000))
        val vm = viewModel()
        var savedId: Long? = null
        compose.setContent {
            YaadeinTheme {
                QuickSaveRoute(onBack = {}, onSaved = { savedId = it }, onAddPerson = {}, onSplit = {}, hubActions = CaptureActions({}, {}, {}, {}, {}), viewModel = vm)
            }
        }
        await(10_000) { vm.state.value.items.size == 10 && !vm.state.value.inspecting }
        val state = vm.state.value
        assertEquals(DateSource.PHOTOS, state.dateSource)
        assertEquals(Dates.exact(LocalDate.of(2024, 12, 20)), state.date)
        assertEquals("20 Dec 2024", state.suggestedTitle)
        assertEquals(listOf("Shalini Joshi"), state.shortlist.map { it.name })
        compose.onAllNodesWithText("20 Dec 2024")[0].assertIsDisplayed()

        // Tap-to-saved budget: from the Save call to the screen being told the memory exists. Pumped tightly so the
        // measurement is the app's work (one Room transaction + a stats row), not the test harness's polling.
        val started = System.nanoTime()
        vm.save { savedId = it }
        val deadline = started + 5_000_000_000L
        while (savedId == null && System.nanoTime() < deadline) {
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("Save took $elapsedMs ms", savedId != null && elapsedMs < 500)

        val saved = runBlocking { memories.get(savedId!!) }
        assertNotNull(saved)
        assertEquals("20 Dec 2024", saved!!.title)
        assertEquals(MemoryType.EVENT, saved.type)
        val stat = runBlocking { stats.recent(1) }.single()
        assertEquals(CaptureEntry.FAB, stat.entry)
        assertEquals(10, stat.itemCount)
        assertTrue(stat.tapToSaveMs >= 4_000)

        // Encryption finishes in the background and marks the capture complete.
        await(30_000) { runBlocking { db.mediaDao().forMemory(savedId!!).size == 10 } }
        await(10_000) { runBlocking { stats.recent(1).single().saveToEncryptedMs != null } }
        assertEquals(savedId, runBlocking { memories.observeCards(TimelineFilter()).first() }.first().memory.id)
    }

    @Test
    fun `photos shared from another app land in Quick Save without a picker`() {
        val uris = listOf(photo("share_1.jpg", "2024:10:31 18:00:00"), photo("share_2.jpg", "2024:10:31 19:00:00"))
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        runBlocking { assertEquals(2, SharedMediaInbox.receive(context, intent)) }
        val vm = viewModel(fromShare = true)
        compose.setContent { YaadeinTheme { QuickSaveRoute(onBack = {}, onSaved = {}, onAddPerson = {}, onSplit = {}, hubActions = CaptureActions({}, {}, {}, {}, {}), viewModel = vm) } }
        await(10_000) { vm.state.value.items.size == 2 && !vm.state.value.inspecting }
        assertEquals(CaptureEntry.SHARE, vm.state.value.entry)
        assertEquals(Dates.exact(LocalDate.of(2024, 10, 31)), vm.state.value.date)
        assertTrue(SharedMediaInbox.pending.value.isEmpty())
    }

    @Test
    fun `an empty picker result opens words-only mode and saves a polished card`() {
        QuickSaveInbox.offer(QuickSaveRequest(emptyList(), CaptureEntry.FAB, SystemClock.elapsedRealtime()))
        val vm = viewModel()
        var savedId: Long? = null
        compose.setContent { YaadeinTheme { QuickSaveRoute(onBack = {}, onSaved = { savedId = it }, onAddPerson = {}, onSplit = {}, hubActions = CaptureActions({}, {}, {}, {}, {}), viewModel = vm) } }
        compose.onNodeWithText("No photos? Write it instead").assertIsDisplayed()
        assertTrue(vm.state.value.wordsOnly)
        vm.setWords("goa trip 2024")
        compose.waitForIdle()
        compose.onNodeWithText("Goa Getaway, 2024").assertIsDisplayed()
        vm.save { savedId = it }
        await(5_000) { savedId != null }
        val saved = runBlocking { memories.get(savedId!!) }!!
        assertEquals("Goa Getaway, 2024", saved.title)
        assertEquals(MemoryType.TEXT_ONLY, saved.type)
        assertEquals("Goa", saved.place)
    }

    private class FakeSettings : SettingsRepository {
        private val flow = MutableStateFlow(UserSettings(onboardingDone = true, pinConfigured = true))
        override val settings: Flow<UserSettings> = flow
        override suspend fun current(): UserSettings = flow.value
        override suspend fun update(transform: (UserSettings) -> UserSettings) = flow.update(transform)
    }
}
