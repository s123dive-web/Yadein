package com.yaadein.data.repository

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.db.DatabaseFactory
import com.yaadein.data.db.entity.MemoryEntity
import com.yaadein.data.db.entity.MemoryPersonEntity
import com.yaadein.data.db.entity.PersonEntity
import com.yaadein.domain.repository.TimelineFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.system.measureTimeMillis

/** 2 000-memory dataset: paging, filters and search must stay fast (JVM/Robolectric timings are generous). */
@RunWith(RobolectricTestRunner::class)
class TimelineScaleTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: MemoryRepositoryImpl

    @Before
    fun setUp() = runTest {
        db = DatabaseFactory.plainForTests(ApplicationProvider.getApplicationContext<Context>())
        repo = MemoryRepositoryImpl(db) { 1L }
        val people = (1..10).map { db.personDao().upsert(PersonEntity(name = "P$it", nickname = null, relation = "FAMILY", birthdayEpochDay = null, profileMediaId = null, notes = "", avatarColorIndex = it, createdAt = 1)) }
        db.withTransaction {
            repeat(2000) { i ->
                val id = db.memoryDao().insert(MemoryEntity(type = "QUICK", title = "Memory $i chai", description = "line $i", dateKind = "EXACT", startEpochDay = 20_000L - i, endEpochDay = null, fuzzyLabel = null, place = if (i % 3 == 0) "Pune" else "", mood = null, coverMediaId = null, coverStyle = "plain_plum", voiceNotePath = null, createdAt = 1, updatedAt = 1))
                db.memoryDao().insertPeople(listOf(MemoryPersonEntity(id, people[i % 10])))
                repo.reindex(id)
            }
        }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `first page of 2000 memories loads in well under a second`() = runTest {
        assertEquals(2000, repo.count())
        val source = db.memoryDao().pagingRaw(androidx.sqlite.db.SimpleSQLiteQuery("SELECT m.* FROM memories m ORDER BY m.start_epoch_day DESC, m.id DESC"))
        var size = 0
        val ms = measureTimeMillis {
            val page = source.load(PagingSource.LoadParams.Refresh(null, 24, false))
            size = (page as PagingSource.LoadResult.Page).data.size
        }
        assertEquals(24, size)
        assertTrue("first page took $ms ms", ms < 1000)
    }

    @Test
    fun `filters and search stay fast at scale`() = runTest {
        val ms = measureTimeMillis {
            assertEquals(200, repo.observeCards(TimelineFilter(personIds = setOf(1))).first().size)
            assertTrue(repo.observeCards(TimelineFilter(place = "pune")).first().size > 600)
            assertEquals(50, repo.search("chai", 50).size)
            assertEquals(1, repo.search("Memory 1999").size)
        }
        assertTrue("filters took $ms ms", ms < 5000)
    }
}
