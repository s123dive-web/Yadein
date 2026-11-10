package com.yaadein.domain.relive

import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.Person
import java.time.LocalDate

/** One full-screen page of the daily story at the top of Relive. */
sealed interface StoryPage {
    /** A memory from years ago ([yearsAgo] set) or the memory of the day ([yearsAgo] null). */
    data class Moment(val card: MemoryCard, val yearsAgo: Int?) : StoryPage

    /** "Remember these with Nana?": someone the story brings back today, with one of their memories as the picture. */
    data class Spotlight(val person: Person, val memoryCount: Int, val card: MemoryCard?) : StoryPage

    /** Then & Now: two photos of the same person ([person]) or the same place ([place]) at least three years apart. */
    data class ThenNow(val pair: ThenNowPair, val person: Person? = null, val place: String? = null) : StoryPage

    /** A milestone worth a page: the 10th/50th/100th memory, or "N years with Aaji" ([person]); [card] gives the picture. */
    data class Milestone(val milestone: com.yaadein.domain.relive.Milestone, val card: MemoryCard?, val person: Person? = null) : StoryPage
}

/** The memory a page is about (what "Open", the heart and "Add what you remember" act on), or null for a page without one. */
val StoryPage.memoryId: Long?
    get() = when (this) {
        is StoryPage.Moment -> card.memory.id
        is StoryPage.Spotlight -> card?.memory?.id
        is StoryPage.ThenNow -> pair.then.memoryId
        is StoryPage.Milestone -> card?.memory?.id
    }

/**
 * The daily story: up to [MAX_REWIND_PAGES] memories from years ago (photos first), then a person spotlight, then the
 * memory of the day. Spotlight and memory of the day rotate by date (same all day, different tomorrow), so opening
 * Relive twice shows the same story and coming back tomorrow shows a new one.
 */
object DailyStory {
    const val MAX_REWIND_PAGES = 4
    /** A spotlight needs a few memories to be worth a page. */
    const val SPOTLIGHT_MIN_MEMORIES = 2

    fun build(rewind: RewindResult?, spotlight: StoryPage.Spotlight?, memoryOfTheDay: MemoryCard?): List<StoryPage> {
        val moments = rewind?.items.orEmpty()
            .sortedWith(compareBy<RewindItem> { if (it.card.cover != null) 0 else 1 }) // stable: keeps newest-first within each group
            .take(MAX_REWIND_PAGES)
            .map { StoryPage.Moment(it.card, it.yearsAgo) }
        val shown = moments.map { it.card.memory.id }.toSet()
        return buildList {
            addAll(moments)
            spotlight?.let { add(it) }
            memoryOfTheDay?.takeIf { it.memory.id !in shown }?.let { add(StoryPage.Moment(it, yearsAgo = null)) }
        }
    }

    /** Today's spotlight among people with at least [SPOTLIGHT_MIN_MEMORIES] memories; rotates daily in id order. */
    fun spotlightPerson(people: List<Person>, memoryCounts: Map<Long, Int>, today: LocalDate): Person? {
        val eligible = people.filter { (memoryCounts[it.id] ?: 0) >= SPOTLIGHT_MIN_MEMORIES }.sortedBy { it.id }
        if (eligible.isEmpty()) return null
        return eligible[Math.floorMod(today.toEpochDay(), eligible.size.toLong()).toInt()]
    }

    /** Today's memory of the day from all memory ids; spread out so consecutive days are not neighbouring memories. */
    fun memoryOfTheDayId(ids: List<Long>, today: LocalDate): Long? {
        if (ids.isEmpty()) return null
        val sorted = ids.distinct().sorted()
        return sorted[Math.floorMod(mix(today.toEpochDay()), sorted.size.toLong()).toInt()]
    }

    /** SplitMix64 finaliser: a cheap, well-spread hash of the day. */
    private fun mix(x: Long): Long {
        var z = x + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }
}
