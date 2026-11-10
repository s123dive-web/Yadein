package com.yaadein.feature.capture

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.yaadein.data.crypto.MediaCrypto
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.db.DatabaseFactory
import com.yaadein.data.media.FaceIndexer
import com.yaadein.data.media.ImportCoordinator
import com.yaadein.data.media.MediaImporter
import com.yaadein.data.media.MediaStore
import com.yaadein.data.repository.MemoryRepositoryImpl
import com.yaadein.data.repository.PersonRepositoryImpl
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.repository.SettingsRepository
import com.yaadein.domain.settings.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import java.io.File
import java.time.LocalDate
import javax.crypto.spec.SecretKeySpec

/**
 * Picking the same photo twice (the system picker allows it) must keep it once: the media strips are keyed by URI and
 * crash on a duplicate key. "Pick more" on Photo-first must add to the drafts instead of replacing them.
 */
@RunWith(RobolectricTestRunner::class)
class PickMoreTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var store: MediaStore
    private lateinit var memories: MemoryRepositoryImpl
    private lateinit var people: PersonRepositoryImpl
    private lateinit var importer: MediaImporter
    private lateinit var coordinator: ImportCoordinator
    private lateinit var serve: (String, ByteArray) -> Uri
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
        QuickSaveInbox.take(); QuickSaveInbox.takeForSplit()
        serve = FakeMediaProvider.install(context)
    }

    @After
    fun tearDown() {
        db.close()
        store.wipeAll()
    }

    private fun photo(name: String, date: String): Uri {
        val bmp = Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF4466AA.toInt()) }
        val file = File(context.cacheDir, name).apply { outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
        ExifInterface(file).apply { setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, date); saveAttributes() }
        return serve(name, file.readBytes())
    }

    /** Pumps the main looper (viewModelScope and Room resume there) until [condition] holds. */
    private fun await(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("condition not met within $timeoutMs ms")
            Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    @Test
    fun `event capture keeps a photo picked twice only once`() {
        val a = photo("ev_a.jpg", "2024:12:20 10:00:00")
        val b = photo("ev_b.jpg", "2024:12:20 11:00:00")
        val vm = EventCaptureViewModel(SavedStateHandle(), memories, people, settings, importer, coordinator)
        await { !vm.state.value.loading }
        vm.onPicked(listOf(a, a, b))
        await { vm.state.value.pending.size == 2 }
        vm.onPicked(listOf(b, a))
        // Nothing fresh: the strip's URI keys stay unique.
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        Thread.sleep(200)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(listOf(a, b), vm.state.value.pending.map { it.uri })
        assertEquals(2, vm.state.value.pending.map { it.uri.toString() }.toSet().size)
        // Dropping a photo and picking it again brings it back.
        vm.removePending(a)
        vm.onPicked(listOf(a))
        await { vm.state.value.pending.size == 2 }
        assertEquals(listOf(b, a), vm.state.value.pending.map { it.uri })
    }

    @Test
    fun `photo-first Pick more adds to the drafts, keeps edits and skips photos already there`() {
        val goa1 = photo("pf_goa1.jpg", "2024:12:20 10:00:00")
        val goa2 = photo("pf_goa2.jpg", "2024:12:21 10:00:00")
        val diwali = photo("pf_diwali.jpg", "2024:10:31 19:00:00")
        val vm = PhotoFirstViewModel(memories, people, settings, importer, coordinator)
        vm.onPicked(listOf(goa1, diwali))
        await { !vm.state.value.inspecting && vm.state.value.drafts.size == 2 }
        val goaKey = vm.state.value.drafts[0].key
        vm.setTitle(goaKey, "Goa with Aaji")

        // Pick more: one photo already there, one from the day after the Goa draft, one from a new day.
        val holi = photo("pf_holi.jpg", "2025:03:14 12:00:00")
        vm.onPicked(listOf(goa1, goa2, holi))
        await { !vm.state.value.inspecting && vm.state.value.drafts.size == 3 }
        val drafts = vm.state.value.drafts
        // Newest first: Holi, Goa, Diwali.
        assertEquals(listOf(holi), drafts[0].items.map { it.uri })
        assertEquals(goaKey, drafts[1].key)
        assertEquals("Goa with Aaji", drafts[1].title)
        assertEquals(listOf(goa1, goa2), drafts[1].items.map { it.uri })
        assertEquals(Dates.exact(LocalDate.of(2024, 12, 20), LocalDate.of(2024, 12, 21)), drafts[1].date)
        assertEquals(listOf(diwali), drafts[2].items.map { it.uri })
        // Every URI appears once across all strips.
        val all = drafts.flatMap { it.items }.map { it.uri.toString() }
        assertEquals(all.size, all.toSet().size)
        assertEquals(4, all.size)
    }

    private class FakeSettings : SettingsRepository {
        private val flow = MutableStateFlow(UserSettings(onboardingDone = true, pinConfigured = true))
        override val settings: Flow<UserSettings> = flow
        override suspend fun current(): UserSettings = flow.value
        override suspend fun update(transform: (UserSettings) -> UserSettings) = flow.update(transform)
    }
}
