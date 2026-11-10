package com.yaadein.domain.relive

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Something worth a page in a story. There are no streaks: nothing here punishes a day away. */
sealed interface Milestone {
    /** The vault just reached its [count]th memory (10, 50, 100…). */
    data class MemoryCount(val count: Int) : Milestone

    /** [years] whole years since the first memory with [personId]. */
    data class YearsWith(val personId: Long, val years: Int) : Milestone
}

object Milestones {
    /** The counts that are celebrated. */
    val COUNTS = listOf(10, 50, 100, 250, 500, 1000)

    /**
     * The count milestone [total] has just reached, or null. A milestone stays for a few more memories (a tenth of
     * it, at least three), so the page is not missed by saving one memory too many: 10 → 10..12, 100 → 100..109.
     */
    fun memoryCount(total: Int): Milestone.MemoryCount? {
        val reached = COUNTS.lastOrNull { it <= total } ?: return null
        val stays = maxOf(3, reached / 10)
        return if (total < reached + stays) Milestone.MemoryCount(reached) else null
    }

    /** Whole years between the first memory with someone and [today]; null before the first anniversary. */
    fun yearsWith(personId: Long, firstMemoryEpochDay: Long?, today: LocalDate): Milestone.YearsWith? {
        firstMemoryEpochDay ?: return null
        val years = ChronoUnit.YEARS.between(LocalDate.ofEpochDay(firstMemoryEpochDay), today).toInt()
        return if (years >= 1) Milestone.YearsWith(personId, years) else null
    }

    /**
     * For the Today story, "N years with Aaji" only around the anniversary of that first memory
     * (within [ANNIVERSARY_DAYS] days), so it is an occasion rather than a daily line.
     */
    fun anniversaryToday(personId: Long, firstMemoryEpochDay: Long?, today: LocalDate): Milestone.YearsWith? {
        val first = firstMemoryEpochDay?.let(LocalDate::ofEpochDay) ?: return null
        for (year in listOf(today.year - 1, today.year, today.year + 1)) {
            val years = year - first.year
            if (years < 1) continue
            val anniversary = first.withYear(year) // 29 February becomes 28 February in other years
            if (kotlin.math.abs(ChronoUnit.DAYS.between(anniversary, today)) <= ANNIVERSARY_DAYS) return Milestone.YearsWith(personId, years)
        }
        return null
    }

    const val ANNIVERSARY_DAYS = 3L
}
