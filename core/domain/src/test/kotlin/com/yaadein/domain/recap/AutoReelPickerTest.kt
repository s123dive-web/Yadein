package com.yaadein.domain.recap

import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.Media
import com.yaadein.domain.model.MediaKind
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryDetail
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.model.Person
import com.yaadein.domain.model.Relation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AutoReelPickerTest {
    private val today = LocalDate.of(2026, 11, 7)
    private val rahul = Person(id = 1, name = "Rahul", relation = Relation.FAMILY, avatarColorIndex = 0)
    private val asha = Person(id = 2, name = "Asha", relation = Relation.FAMILY, avatarColorIndex = 1)

    private fun memory(id: Long, date: LocalDate, photos: Int, people: List<Person> = emptyList(), video: Boolean = false): MemoryDetail {
        val media = (1..photos).map { Media(id = id * 100 + it, memoryId = id, kind = MediaKind.PHOTO, encryptedPath = "", thumbPath = "", sizeBytes = 1, width = 1, height = 1, sha256 = "", position = it) } +
            if (video) listOf(Media(id = id * 100 + 99, memoryId = id, kind = MediaKind.VIDEO, encryptedPath = "", thumbPath = "", sizeBytes = 1, width = 1, height = 1, sha256 = "")) else emptyList()
        return MemoryDetail(Memory(id = id, type = MemoryType.EVENT, title = "m$id", date = Dates.exact(date)), people, media)
    }

    @Test
    fun `fills all three slots for a varied library`() {
        val library = listOf(
            memory(1, LocalDate.of(2020, 11, 2), 4, listOf(rahul)),
            memory(2, LocalDate.of(2022, 3, 9), 5, listOf(rahul, asha)),
            memory(3, LocalDate.of(2024, 6, 1), 3, listOf(asha)),
            memory(4, LocalDate.of(2026, 10, 20), 6, listOf(rahul)),
            memory(5, LocalDate.of(2026, 11, 1), 2),
        )
        val picks = AutoReelPicker.pick(library, today)
        assertEquals(listOf(AutoReelSlot.RECENT, AutoReelSlot.PERSON, AutoReelSlot.THROWBACK), picks.map { it.slot })
        assertEquals(AutoReelVariant.RECENT_DAYS, picks[0].variant)
        assertEquals(listOf(4L, 5L), picks[0].memories.map { it.memory.id })
        assertEquals(AutoReelVariant.PERSON, picks[1].variant)
        assertEquals(rahul, picks[1].person)
        assertEquals(AutoReelVariant.THIS_MONTH_YEARS_AGO, picks[2].variant)
        assertEquals(listOf(1L), picks[2].memories.map { it.memory.id })
        picks.forEach { assertTrue(it.mediaIds.size <= AutoReelPicker.ITEMS_PER_REEL) }
    }

    @Test
    fun `falls back when nothing is recent, nobody is tagged and no memory is from this month`() {
        val library = listOf(memory(1, LocalDate.of(2019, 1, 5), 3), memory(2, LocalDate.of(2021, 4, 5), 3), memory(3, LocalDate.of(2023, 8, 5), 30))
        val picks = AutoReelPicker.pick(library, today)
        assertEquals(listOf(AutoReelVariant.LATEST, AutoReelVariant.EVERYTHING, AutoReelVariant.THROUGH_THE_YEARS), picks.map { it.variant })
        assertEquals(AutoReelPicker.ITEMS_PER_REEL, picks[1].mediaIds.size)
    }

    @Test
    fun `never makes the same reel twice and skips videos`() {
        val library = listOf(memory(1, LocalDate.of(2026, 11, 1), 3, listOf(rahul), video = true))
        val picks = AutoReelPicker.pick(library, today)
        assertEquals(1, picks.size)
        assertTrue(picks.single().mediaIds.none { it % 100 == 99L })
        assertEquals(AutoReelPicker.pick(library, today).single().signature, picks.single().signature)
    }

    @Test
    fun `an empty or words-only library has no automatic reels`() {
        assertTrue(AutoReelPicker.pick(emptyList(), today).isEmpty())
        assertTrue(AutoReelPicker.pick(listOf(memory(1, today, 0)), today).isEmpty())
    }
}
