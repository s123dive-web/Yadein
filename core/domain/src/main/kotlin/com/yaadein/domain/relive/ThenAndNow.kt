package com.yaadein.domain.relive

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A photo with the day of the memory it belongs to (fuzzy dates are left out by the query). */
data class DatedPhoto(val mediaId: Long, val memoryId: Long, val epochDay: Long)

/** Two photos of the same person or place: [then] is at least [ThenAndNow.MIN_YEARS] years older than [now]. */
data class ThenNowPair(val then: DatedPhoto, val now: DatedPhoto, val yearsApart: Int)

/**
 * Then & Now: pairs an early photo with a recent one of the same person (or the same place). The pair is the same
 * all day and changes from day to day when there is a choice: "now" comes from the most recent year of photos,
 * "then" from the earliest year, and the two are never from the same memory.
 */
object ThenAndNow {
    const val MIN_YEARS = 3
    /** "Now" is picked among photos this close to the newest one, "then" among photos this close to the oldest. */
    const val WINDOW_DAYS = 365L

    fun pick(photos: List<DatedPhoto>, today: LocalDate, minYears: Int = MIN_YEARS): ThenNowPair? {
        val sorted = photos.distinctBy { it.mediaId }.sortedWith(compareBy<DatedPhoto> { it.epochDay }.thenBy { it.mediaId })
        if (sorted.size < 2) return null
        val newest = sorted.last().epochDay
        val recent = sorted.filter { it.epochDay >= newest - WINDOW_DAYS }
        // Try each recent photo as "now", starting from today's pick, until one has an old enough partner.
        val start = Math.floorMod(StoryPicks.mix(today.toEpochDay()), recent.size.toLong()).toInt()
        for (offset in recent.indices) {
            val now = recent[(start + offset) % recent.size]
            val nowDate = LocalDate.ofEpochDay(now.epochDay)
            val old = sorted.filter { it.memoryId != now.memoryId && !LocalDate.ofEpochDay(it.epochDay).plusYears(minYears.toLong()).isAfter(nowDate) }
            if (old.isEmpty()) continue
            val early = old.filter { it.epochDay <= old.first().epochDay + WINDOW_DAYS }
            val then = early[Math.floorMod(StoryPicks.mix(today.toEpochDay() + 1), early.size.toLong()).toInt()]
            val years = ChronoUnit.YEARS.between(LocalDate.ofEpochDay(then.epochDay), nowDate).toInt()
            return ThenNowPair(then, now, years)
        }
        return null
    }
}
