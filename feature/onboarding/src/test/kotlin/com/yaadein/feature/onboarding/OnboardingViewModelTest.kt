package com.yaadein.feature.onboarding

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import com.yaadein.data.crypto.MediaCrypto
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.db.DatabaseFactory
import com.yaadein.data.media.FaceIndexer
import com.yaadein.data.media.ImportCoordinator
import com.yaadein.data.media.MediaImporter
import com.yaadein.data.media.MediaStore
import com.yaadein.data.repository.MemoryRepositoryImpl
import com.yaadein.data.seed.DemoSeeder
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryType
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import java.io.File
import java.time.LocalDate
import javax.crypto.spec.SecretKeySpec

/**
 * The three ways out of the welcome screen, on the JVM with the real importer and database: picked photos become
 * memories by day and the first story is next; the sample family is seeded and marked; skip changes nothing but the
 * onboarding flag. None of them asks for a person or a PIN.
 */
@RunWith(RobolectricTestRunner::class)
class OnboardingViewModelTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var store: MediaStore
    private lateinit var memories: MemoryRepositoryImpl
    private lateinit var importer: MediaImporter
    private lateinit var coordinator: ImportCoordinator
    private lateinit var seeder: DemoSeeder
    private lateinit var serve: (String, ByteArray) -> Uri
    private val settings = FakeSettings()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = DatabaseFactory.plainForTests(context)
        store = MediaStore(context, MediaCrypto(SecretKeySpec(ByteArray(32) { 7 }, "AES")))
        memories = MemoryRepositoryImpl(db) { System.currentTimeMillis() }
        importer = MediaImporter(context, db, store, memories, { _, _ -> null }) { System.currentTimeMillis() }
        coordinator = ImportCoordinator(importer, settings, FaceIndexer { })
        seeder = DemoSeeder(db, store, memories)
        serve = FakeMediaProvider.install(context)
    }

    @After
    fun tearDown() {
        db.close()
        store.wipeAll()
    }

    private fun viewModel() = OnboardingViewModel(settings, { memories }, { importer }, { coordinator }, { seeder })

    private fun photo(name: String, date: String?): Uri {
        val bmp = Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF4466AA.toInt()) }
        val file = File(context.cacheDir, name).apply { outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
        if (date != null) ExifInterface(file).apply { setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, date); saveAttributes() }
        return serve(name, file.readBytes())
    }

    /** Waits for [condition], pumping the main looper (the ViewModel and Room resume coroutines there). */
    private fun await(timeoutMs: Long = 30_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("condition not met within $timeoutMs ms")
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    @Test
    fun `picked photos become one memory per day and the story is next`() {
        val uris = (1..3).map { photo("goa_$it.jpg", "2024:12:20 1$it:00:00") } + (1..2).map { photo("home_$it.jpg", "2023:05:0$it 09:00:00") }
        val vm = viewModel()

        vm.onPicked(uris)
        await { vm.state.value.exit != null }

        assertEquals(OnboardingExit.STORY, vm.state.value.exit)
        assertNull(vm.state.value.busy)
        val cards = runBlocking { memories.observeCards(TimelineFilter()).first() }
        // 20 Dec 2024 on its own; 1 and 2 May 2023 are consecutive days, so one memory.
        assertEquals(2, cards.size)
        assertEquals(Dates.exact(LocalDate.of(2024, 12, 20)), cards[0].memory.date)
        assertEquals(Dates.exact(LocalDate.of(2023, 5, 1), LocalDate.of(2023, 5, 2)), cards[1].memory.date)
        assertTrue(cards.all { it.memory.type == MemoryType.EVENT && it.memory.title.isNotBlank() && it.people.isEmpty() })
        val s = runBlocking { settings.current() }
        assertTrue(s.onboardingDone)
        assertFalse("no PIN is asked for before the first story", s.pinConfigured)
        assertFalse(s.demoMode)
        // The files are copied and encrypted in the background; every photo arrives and each memory gets a cover.
        await { runBlocking { db.mediaDao().count() } == 5 }
        await { runBlocking { memories.observeCards(TimelineFilter()).first() }.all { it.cover != null } }
    }

    @Test
    fun `photos without a date become one memory dated today`() {
        val vm = viewModel()

        vm.onPicked(listOf(photo("a.jpg", null), photo("b.jpg", null)))
        await { vm.state.value.exit != null }

        val cards = runBlocking { memories.observeCards(TimelineFilter()).first() }
        assertEquals(1, cards.size)
        assertEquals(Dates.exact(LocalDate.now()), cards[0].memory.date)
    }

    @Test
    fun `a cancelled picker changes nothing`() {
        val vm = viewModel()

        vm.onPicked(emptyList())
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(OnboardingUiState(), vm.state.value)
        assertFalse(runBlocking { settings.current() }.onboardingDone)
    }

    @Test
    fun `files that cannot be read keep the welcome screen with a message`() {
        val vm = viewModel()

        vm.onPicked(listOf(Uri.parse("content://${FakeMediaProvider.AUTHORITY}/notes.txt")))
        await { vm.state.value.busy == null }

        assertTrue(vm.state.value.nothingAdded)
        assertNull(vm.state.value.exit)
        assertFalse(runBlocking { settings.current() }.onboardingDone)
        assertEquals(0, runBlocking { memories.count() })
    }

    @Test
    fun `the sample family is seeded, marked as sample and can be cleared without touching the user's own memory`() {
        val vm = viewModel()

        vm.seeSample()
        await(60_000) { vm.state.value.exit != null }

        assertEquals(OnboardingExit.HOME, vm.state.value.exit)
        val s = runBlocking { settings.current() }
        assertTrue(s.onboardingDone)
        assertTrue(s.demoMode)
        assertFalse(s.pinConfigured)
        val seeded = runBlocking { memories.count() }
        assertTrue(seeded > 10)
        assertTrue(runBlocking { db.personDao().getAll() }.isNotEmpty())

        // "Clear sample and start mine" keeps what the user added while looking around.
        val mine = runBlocking { memories.create(Memory(type = MemoryType.TEXT_ONLY, title = "My own note", date = Dates.exact(LocalDate.of(2026, 1, 2))), emptyList()) }
        val removed = runBlocking { seeder.clear() }
        assertEquals(seeded, removed)
        assertEquals(listOf(mine), runBlocking { memories.observeCards(TimelineFilter()).first() }.map { it.memory.id })
        assertTrue(runBlocking { db.personDao().getAll() }.isEmpty())
        assertEquals(0, runBlocking { db.mediaDao().count() })
    }

    @Test
    fun `skip goes home with nothing added`() {
        val vm = viewModel()

        vm.skip()
        await { vm.state.value.exit != null }

        assertEquals(OnboardingExit.HOME, vm.state.value.exit)
        val s = runBlocking { settings.current() }
        assertTrue(s.onboardingDone)
        assertFalse(s.demoMode)
        assertFalse(s.pinConfigured)
        assertEquals(0, runBlocking { memories.count() })
    }

    private class FakeSettings : SettingsRepository {
        private val flow = MutableStateFlow(UserSettings())
        override val settings: Flow<UserSettings> = flow
        override suspend fun current(): UserSettings = flow.value
        override suspend fun update(transform: (UserSettings) -> UserSettings) = flow.update(transform)
    }
}
