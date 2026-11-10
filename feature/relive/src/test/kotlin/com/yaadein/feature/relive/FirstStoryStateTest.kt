package com.yaadein.feature.relive

import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.Media
import com.yaadein.domain.model.MediaKind
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.repository.MemoryImportProgress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** The first story shows a page as soon as a memory has a picture and counts the photos still on their way. */
class FirstStoryStateTest {
    private fun card(id: Long, photos: Int) = MemoryCard(
        memory = Memory(id, MemoryType.EVENT, "Memory $id", date = Dates.exact(LocalDate.of(2024, 12, id.toInt()))),
        people = emptyList(),
        cover = if (photos > 0) Media(id = id * 10, memoryId = id, kind = MediaKind.PHOTO, encryptedPath = "m", thumbPath = "t", sizeBytes = 1, width = 4, height = 3, sha256 = "x") else null,
        mediaCount = photos,
    )

    @Test
    fun `while photos arrive only memories with a picture are pages`() {
        val cards = listOf(card(3, photos = 2), card(2, photos = 0), card(1, photos = 1))
        val state = FirstStoryViewModel.stateOf(cards, mapOf(3L to MemoryImportProgress(total = 6, done = 2), 2L to MemoryImportProgress(total = 4, done = 0)))

        assertFalse(state.loading)
        assertEquals(listOf(3L, 1L), state.pages.map { it.card.memory.id })
        assertTrue(state.pages.all { it.yearsAgo == null })
        assertEquals(3, state.photosStored)
        assertEquals(11, state.photosTotal)
        assertTrue(state.importing)
    }

    @Test
    fun `when the import is over every memory is a page and nothing is pending`() {
        val cards = (10L downTo 1L).map { card(it, photos = if (it == 4L) 0 else 2) }
        val state = FirstStoryViewModel.stateOf(cards, emptyMap())

        assertEquals(FirstStoryViewModel.MAX_PAGES, state.pages.size)
        assertEquals(10L, state.pages.first().card.memory.id)
        assertFalse(state.importing)
        assertEquals(state.photosStored, state.photosTotal)
    }

    @Test
    fun `nothing imported yet is the preparing state`() {
        val state = FirstStoryViewModel.stateOf(listOf(card(1, photos = 0)), mapOf(1L to MemoryImportProgress(total = 5, done = 0)))

        assertTrue(state.pages.isEmpty())
        assertTrue(state.importing)
        assertEquals(0, state.photosStored)
        assertEquals(5, state.photosTotal)
    }
}
