package com.yaadein.domain.relive

import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.Media
import com.yaadein.domain.model.MediaKind
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.MemoryType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class StoryPicksTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private fun card(id: Long, favorite: Boolean = false, openedDaysAgo: Long? = null, photo: Boolean = true) = MemoryCard(
        memory = Memory(
            id = id, type = MemoryType.EVENT, title = "M$id", date = Dates.exact(LocalDate.of(2020, 1, 1).plusDays(id)),
            favorite = favorite, lastOpenedAt = openedDaysAgo?.let { now - it * day },
        ),
        people = emptyList(),
        cover = if (photo) Media(id = id, memoryId = id, kind = MediaKind.PHOTO, encryptedPath = "m", thumbPath = "t", sizeBytes = 1, width = 4, height = 3, sha256 = "x") else null,
        mediaCount = if (photo) 1 else 0,
    )

    @Test
    fun `favourites and recently opened memories weigh more`() {
        assertEquals(1.0, StoryPicks.weight(card(1), now))
        assertEquals(1.0 + StoryPicks.FAVORITE_WEIGHT, StoryPicks.weight(card(1, favorite = true), now))
        assertEquals(1.0 + StoryPicks.RECENT_WEIGHT, StoryPicks.weight(card(1, openedDaysAgo = 29), now))
        assertEquals(1.0, StoryPicks.weight(card(1, openedDaysAgo = 31), now))
        assertEquals(1.0 + StoryPicks.FAVORITE_WEIGHT + StoryPicks.RECENT_WEIGHT, StoryPicks.weight(card(1, favorite = true, openedDaysAgo = 0), now))
    }

    @Test
    fun `the order is the same all day, changes between days, and keeps every memory`() {
        val cards = (1L..20L).map { card(it) }
        val a = StoryPicks.order(cards, today, now).map { it.memory.id }
        assertEquals(a, StoryPicks.order(cards.reversed(), today, now).map { it.memory.id })
        assertEquals((1L..20L).toSet(), a.toSet())
        assertTrue(a != StoryPicks.order(cards, today.plusDays(1), now).map { it.memory.id })
    }

    @Test
    fun `over many days a favourite comes first far more often than an ordinary memory`() {
        val cards = (1L..10L).map { card(it, favorite = it == 4L) }
        val firsts = (0L until 400L).map { StoryPicks.order(cards, today.plusDays(it), now).first().memory.id }
        val favourite = firsts.count { it == 4L }
        val ordinary = firsts.count { it == 5L }
        // Expected share: 4 / 13 for the favourite against 1 / 13 for any other.
        assertTrue(favourite > 80, "favourite first on $favourite of 400 days")
        assertTrue(favourite > 2 * ordinary, "favourite $favourite vs ordinary $ordinary")
        assertTrue(ordinary > 5, "an ordinary memory still gets its days ($ordinary)")
    }

    @Test
    fun `memories with a picture come before those without, and a story is told oldest first`() {
        val cards = listOf(card(1, photo = false, favorite = true), card(2), card(3), card(4, photo = false), card(5))
        val order = StoryPicks.order(cards, today, now)
        assertEquals(setOf(2L, 3L, 5L), order.take(3).map { it.memory.id }.toSet())
        val story = StoryPicks.choose(cards, limit = 3, today = today, nowMillis = now)
        assertEquals(listOf(2L, 3L, 5L), story.map { it.memory.id })
        assertEquals(5, StoryPicks.choose(cards, limit = 10, today = today, nowMillis = now).size)
    }
}
