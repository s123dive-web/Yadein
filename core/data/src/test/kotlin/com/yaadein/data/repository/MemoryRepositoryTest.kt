package com.yaadein.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.db.DatabaseFactory
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.model.Person
import com.yaadein.domain.model.Relation
import com.yaadein.domain.repository.TimelineFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
class MemoryRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var memories: MemoryRepositoryImpl
    private lateinit var people: PersonRepositoryImpl
    private var now = 1_700_000_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = DatabaseFactory.plainForTests(context)
        memories = MemoryRepositoryImpl(db) { now }
        people = PersonRepositoryImpl(db) { now }
    }

    @After
    fun tearDown() = db.close()

    private fun memory(title: String, date: LocalDate, place: String = "", description: String = "", type: MemoryType = MemoryType.EVENT) =
        Memory(type = type, title = title, description = description, date = Dates.exact(date), place = place)

    @Test
    fun `create tags people and is searchable by title description place and names`() = runTest {
        val rahul = people.upsert(Person(name = "Rahul Deshmukh", nickname = "Rahul", relation = Relation.FAMILY))
        val id = memories.create(memory("Goa Getaway", LocalDate.of(2024, 12, 20), place = "Palolem", description = "beach shacks"), listOf(rahul))
        val card = memories.observeCard(id).first()
        assertNotNull(card)
        assertEquals(listOf("Rahul Deshmukh"), card!!.people.map { it.name })
        assertEquals(1, memories.search("goa").size)
        assertEquals(1, memories.search("palol").size)
        assertEquals(1, memories.search("shack").size)
        assertEquals(1, memories.search("deshmukh").size)
        assertEquals(0, memories.search("wedding").size)
    }

    @Test
    fun `timeline orders newest first and fuzzy dates anchor sensibly`() = runTest {
        memories.create(memory("Exact Dec 2024", LocalDate.of(2024, 12, 20)), emptyList())
        memories.create(Memory(type = MemoryType.TEXT_ONLY, title = "Year 2015", date = Dates.year(2015)), emptyList())
        memories.create(Memory(type = MemoryType.TEXT_ONLY, title = "Month May 2020", date = Dates.month(YearMonth.of(2020, 5))), emptyList())
        memories.create(Memory(type = MemoryType.TEXT_ONLY, title = "Summer 2018", date = Dates.fuzzy("Summer 2018", 2018, 5)), emptyList())
        val titles = memories.observeCards().first().map { it.memory.title }
        assertEquals(listOf("Exact Dec 2024", "Month May 2020", "Summer 2018", "Year 2015"), titles)
    }

    @Test
    fun `filters by person group place type date and together`() = runTest {
        val a = people.upsert(Person(name = "Aai"))
        val b = people.upsert(Person(name = "Baba"))
        val c = people.upsert(Person(name = "Neha", relation = Relation.FRIEND))
        memories.create(memory("Family dinner", LocalDate.of(2024, 1, 5), place = "Pune"), listOf(a, b))
        memories.create(memory("Trek", LocalDate.of(2024, 6, 5), place = "Lonavala"), listOf(c))
        memories.create(Memory(type = MemoryType.TEXT_ONLY, title = "Chai", date = Dates.exact(LocalDate.of(2023, 3, 3))), listOf(a))
        val groups = GroupRepositoryImpl(db) { now }
        val gid = groups.upsert(com.yaadein.domain.model.Group(name = "Family", memberIds = listOf(a, b)))

        assertEquals(2, memories.observeCards(TimelineFilter(personIds = setOf(a))).first().size)
        assertEquals(1, memories.observeCards(TimelineFilter(personIds = setOf(a, b), requireAllPeople = true)).first().size)
        assertEquals(3, memories.observeCards(TimelineFilter(personIds = setOf(a, c))).first().size)
        assertEquals(2, memories.observeCards(TimelineFilter(groupId = gid)).first().size)
        assertEquals(1, memories.observeCards(TimelineFilter(place = "lona")).first().size)
        assertEquals(1, memories.observeCards(TimelineFilter(types = setOf(MemoryType.TEXT_ONLY))).first().size)
        assertEquals(2, memories.observeCards(TimelineFilter(fromEpochDay = LocalDate.of(2024, 1, 1).toEpochDay())).first().size)
        assertEquals(1, memories.observeCards(TimelineFilter(query = "trek")).first().size)
    }

    @Test
    fun `update replaces people and reindexes`() = runTest {
        val a = people.upsert(Person(name = "Aai"))
        val b = people.upsert(Person(name = "Baba"))
        val id = memories.create(memory("Old title", LocalDate.of(2024, 1, 5)), listOf(a))
        val stored = memories.get(id)!!
        memories.update(stored.copy(title = "New title"), listOf(b))
        val card = memories.observeCard(id).first()!!
        assertEquals("New title", card.memory.title)
        assertEquals(listOf("Baba"), card.people.map { it.name })
        assertEquals(0, memories.search("old").size)
        assertEquals(1, memories.search("new").size)
    }

    @Test
    fun `delete removes memory and search row`() = runTest {
        val id = memories.create(memory("Bye", LocalDate.of(2024, 1, 5)), emptyList())
        memories.delete(id)
        assertNull(memories.get(id))
        assertTrue(memories.search("bye").isEmpty())
        assertEquals(0, memories.count())
    }

    @Test
    fun `on this day matches month and day across years`() = runTest {
        memories.create(memory("2019", LocalDate.of(2019, 9, 9)), emptyList())
        memories.create(memory("2023", LocalDate.of(2023, 9, 9)), emptyList())
        memories.create(memory("Other day", LocalDate.of(2023, 9, 10)), emptyList())
        val hits = memories.onThisDay(909, excludeYear = 2023)
        assertEquals(listOf("2019"), hits.map { it.memory.title })
        assertEquals(mapOf(2019 to 1, 2023 to 2), memories.countsByYear())
    }

    @Test
    fun `person story aggregates counts first and last`() = runTest {
        val a = people.upsert(Person(name = "Aai", birthdayEpochDay = LocalDate.of(1966, 8, 14).toEpochDay()))
        memories.create(memory("First", LocalDate.of(2010, 1, 1)), listOf(a))
        memories.create(memory("Last", LocalDate.of(2024, 1, 1)), listOf(a))
        val story = people.observeStory(a, LocalDate.of(2026, 8, 1).toEpochDay()).first()!!
        assertEquals(2, story.memoryCount)
        assertEquals("First", story.firstMemory?.title)
        assertEquals("Last", story.lastMemory?.title)
        assertEquals(13, story.daysToBirthday)
        assertEquals(mapOf(a to 2), people.memoryCounts())
    }

    @Test
    fun `fts query builder escapes and prefixes tokens`() {
        assertEquals("goa* tr*", MemoryRepositoryImpl.ftsQuery("Goa tr"))
        assertEquals("rahul*", MemoryRepositoryImpl.ftsQuery("  rahul's "))
        assertNull(MemoryRepositoryImpl.ftsQuery("   "))
    }
}
