package com.yaadein.domain.relive

import com.yaadein.domain.model.MemoryCard
import java.time.LocalDate

/** A memory in a person's story with the photo that actually shows them ([mediaId]); null means the memory's cover. */
data class PersonMoment(val card: MemoryCard, val mediaId: Long?)

/**
 * Which memories, and which photo of each, tell a person's story.
 *
 * Tagging someone on a memory says they were part of it, not that they are in every photo: a wedding album tagged
 * "Shreyas" has hundreds of pictures of other people. So a person's story looks for evidence first:
 *
 * 1. **Shown**: memories with a photo the person is known to be in (tagged on that photo, or their face confirmed or
 *    recognised there). The story shows that photo, not the memory's cover.
 * 2. **Unknown**: memories whose photos nobody has looked at for faces. They may or may not show the person; memories
 *    tagged with fewer people come first, because a memory about one person usually shows that person.
 * 3. **Probably not shown**: memories where faces were found and none of them is this person. Left out unless there
 *    is nothing else at all.
 *
 * With [ENOUGH_SHOWN] or more memories of the first kind the story uses only those.
 */
object PersonPhotos {
    /** With this many memories that certainly show the person, guesses are not needed. */
    const val ENOUGH_SHOWN = 3

    /**
     * @param shownIn memory id → ids of that memory's photos the person is known to be in (best first)
     * @param memoriesWithFaces ids of memories in which face recognition found at least one face
     */
    fun select(
        cards: List<MemoryCard>,
        shownIn: Map<Long, List<Long>>,
        memoriesWithFaces: Set<Long>,
        limit: Int,
        today: LocalDate,
        nowMillis: Long,
    ): List<PersonMoment> {
        val ordered = StoryPicks.order(cards, today, nowMillis)
        val shown = ordered.filter { !shownIn[it.memory.id].isNullOrEmpty() }
        val unknown = ordered.filter { shownIn[it.memory.id].isNullOrEmpty() && it.memory.id !in memoriesWithFaces }
            // Stable sort: fewer people first, today's preference order within the same count; pictures before words.
            .sortedWith(compareBy<MemoryCard> { if (it.cover != null) 0 else 1 }.thenBy { it.people.size })
        val unlikely = ordered.filter { shownIn[it.memory.id].isNullOrEmpty() && it.memory.id in memoriesWithFaces }
        val picked = when {
            shown.size >= ENOUGH_SHOWN -> shown.take(limit)
            shown.isNotEmpty() || unknown.isNotEmpty() -> (shown + unknown).take(limit)
            else -> unlikely.take(limit)
        }
        return picked
            .map { card -> PersonMoment(card, shownIn[card.memory.id]?.firstOrNull()) }
            .sortedWith(compareBy<PersonMoment> { it.card.memory.date.startEpochDay }.thenBy { it.card.memory.id })
    }
}
