package com.yaadein.domain.relive

import com.yaadein.domain.model.MemoryCard
import java.time.LocalDate

/**
 * Which memories a story shows when there are more than fit. The choice is the same all day and different tomorrow,
 * and it leans towards favourites and memories opened recently (they are the ones people come back to), and towards
 * memories with a picture.
 */
object StoryPicks {
    const val FAVORITE_WEIGHT = 3.0
    const val RECENT_WEIGHT = 2.0
    /** "Recently opened" means within this many days. */
    const val RECENT_DAYS = 30L
    private const val DAY_MILLIS = 86_400_000L

    /** How strongly a memory is preferred: 1 for an ordinary one, more for a favourite or a recently opened one. */
    fun weight(card: MemoryCard, nowMillis: Long): Double {
        var w = 1.0
        if (card.memory.favorite) w += FAVORITE_WEIGHT
        val opened = card.memory.lastOpenedAt
        if (opened != null && nowMillis - opened in 0..RECENT_DAYS * DAY_MILLIS) w += RECENT_WEIGHT
        return w
    }

    /**
     * [cards] in today's order of preference (weighted sampling without replacement: every memory can come first,
     * heavier ones do so more often). Memories with a picture come before those without.
     */
    fun order(cards: List<MemoryCard>, today: LocalDate, nowMillis: Long): List<MemoryCard> =
        cards.distinctBy { it.memory.id }
            .map { card ->
                // Efraimidis–Spirakis key u^(1/w) with u from a hash of (day, id): deterministic for the day.
                val u = unit(mix(today.toEpochDay() * 1_000_003L + card.memory.id))
                card to Math.pow(u, 1.0 / weight(card, nowMillis))
            }
            .sortedWith(compareBy<Pair<MemoryCard, Double>> { if (it.first.cover != null) 0 else 1 }.thenByDescending { it.second }.thenBy { it.first.memory.id })
            .map { it.first }

    /** Up to [limit] memories for a story, chosen by [order] and then told oldest first. */
    fun choose(cards: List<MemoryCard>, limit: Int, today: LocalDate, nowMillis: Long): List<MemoryCard> =
        order(cards, today, nowMillis).take(limit)
            .sortedWith(compareBy<MemoryCard> { it.memory.date.startEpochDay }.thenBy { it.memory.id })

    /** SplitMix64 finaliser: a cheap, well-spread hash. */
    fun mix(x: Long): Long {
        var z = x + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** A hash as a number in (0, 1). */
    private fun unit(hash: Long): Double = ((hash ushr 11) + 1).toDouble() / ((1L shl 53) + 1).toDouble()
}
