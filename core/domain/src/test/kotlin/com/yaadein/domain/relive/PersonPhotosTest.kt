package com.yaadein.domain.relive

import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.Media
import com.yaadein.domain.model.MediaKind
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.model.Person
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** A person's story shows photos they are known to be in, not just any photo of a memory they are tagged on. */
class PersonPhotosTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val now = 1_800_000_000_000L

    private fun card(id: Long, people: Int = 1, photo: Boolean = true) = MemoryCard(
        memory = Memory(id = id, type = MemoryType.EVENT, title = "M$id", date = Dates.exact(LocalDate.of(2018, 1, 1).plusDays(id))),
        people = (1..people).map { Person(id = it.toLong(), name = "P$it") },
        cover = if (photo) Media(id = id * 100, memoryId = id, kind = MediaKind.PHOTO, encryptedPath = "m", thumbPath = "t", sizeBytes = 1, width = 4, height = 3, sha256 = "x") else null,
        mediaCount = if (photo) 5 else 0,
    )

    private fun select(cards: List<MemoryCard>, shownIn: Map<Long, List<Long>> = emptyMap(), withFaces: Set<Long> = emptySet(), limit: Int = 6) =
        PersonPhotos.select(cards, shownIn, withFaces, limit, today, now)

    @Test
    fun `with enough photos that show them the story uses only those, and shows that photo`() {
        val cards = (1L..8L).map { card(it, people = 4) }
        val shownIn = mapOf(2L to listOf(205L, 207L), 5L to listOf(501L), 7L to listOf(703L))

        val story = select(cards, shownIn)

        assertEquals(listOf(2L, 5L, 7L), story.map { it.card.memory.id })
        // The photo they are in, not the memory's cover (ids 200, 500, 700).
        assertEquals(listOf(205L, 501L, 703L), story.map { it.mediaId })
    }

    @Test
    fun `a memory where faces were found and none is theirs is left out`() {
        val cards = (1L..6L).map { card(it) }
        val shownIn = mapOf(1L to listOf(101L))
        // Memories 2 and 3 were scanned and show other people only; 4, 5 and 6 were never scanned.
        val story = select(cards, shownIn, withFaces = setOf(1L, 2L, 3L))

        assertEquals(listOf(1L, 4L, 5L, 6L), story.map { it.card.memory.id })
        assertEquals(101L, story.first().mediaId)
        assertTrue(story.drop(1).all { it.mediaId == null })
    }

    @Test
    fun `without any evidence memories about fewer people come first`() {
        val cards = listOf(card(1, people = 6), card(2, people = 1), card(3, people = 5), card(4, people = 2), card(5, people = 1, photo = false))

        val story = select(cards, limit = 2)

        // The solo memory with a photo and the two-person memory; the big gatherings and the words-only note wait.
        assertEquals(setOf(2L, 4L), story.map { it.card.memory.id }.toSet())
    }

    @Test
    fun `memories that probably do not show them are a last resort, and the story is told oldest first`() {
        val cards = listOf(card(3), card(1), card(2))

        val story = select(cards, withFaces = setOf(1L, 2L, 3L))

        assertEquals(listOf(1L, 2L, 3L), story.map { it.card.memory.id })
        assertTrue(select(emptyList()).isEmpty())
    }

    @Test
    fun `the choice is the same all day`() {
        val cards = (1L..20L).map { card(it, people = (it % 3).toInt() + 1) }
        val a = select(cards).map { it.card.memory.id }
        assertEquals(a, select(cards.reversed()).map { it.card.memory.id })
        assertEquals(6, a.size)
    }
}
