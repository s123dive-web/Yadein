package com.yaadein.domain.relive

import com.yaadein.domain.model.DateKind
import com.yaadein.domain.model.MemoryCard
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/** How close to today's date the memories in a [RewindResult] are. */
enum class RewindScope { DAY, WEEK, MONTH }

data class RewindItem(val card: MemoryCard, val yearsAgo: Int)

/** What Relive shows under "On this day": the closest scope that has anything, newest first. */
data class RewindResult(val scope: RewindScope, val items: List<RewindItem>)

/**
 * "On this day" that is rarely empty. Exact same day first; if nothing, the same week (±[WEEK_DAYS]) in earlier years;
 * if still nothing, the same month in earlier years (exact dates and month-only dates). Fuzzy and year-only dates have
 * no day or month to match and are left out.
 */
object Rewind {
    const val WEEK_DAYS = 3
    const val MAX_ITEMS = 12
    /** Bounds the size of the query built from [windows]. */
    const val MAX_YEARS_BACK = 120

    /**
     * Epoch-day ranges, one per earlier year, that together contain every memory [pick] could return: the same month in
     * each earlier year, widened by [WEEK_DAYS] on both sides so a week that crosses into another month is covered.
     */
    fun windows(today: LocalDate, earliestYear: Int): List<LongRange> {
        val first = maxOf(earliestYear, today.year - MAX_YEARS_BACK)
        return (today.year - 1 downTo first).map { year ->
            val month = YearMonth.of(year, today.month)
            val anchor = sameDayIn(today, year)
            val from = minOf(month.atDay(1), anchor.minusDays(WEEK_DAYS.toLong())).toEpochDay()
            val to = maxOf(month.atEndOfMonth(), anchor.plusDays(WEEK_DAYS.toLong())).toEpochDay()
            from..to
        }
    }

    /** Picks from [cards] (any superset of what [windows] covers); null when nothing matches at any scope. */
    fun pick(cards: List<MemoryCard>, today: LocalDate): RewindResult? {
        val day = ArrayList<RewindItem>()
        val week = ArrayList<RewindItem>()
        val month = ArrayList<RewindItem>()
        for (card in cards) {
            val date = LocalDate.ofEpochDay(card.memory.date.startEpochDay)
            when (card.memory.date.kind) {
                DateKind.EXACT -> {
                    val yearsAgo = today.year - date.year
                    if (yearsAgo >= 1 && isSameDay(date, today)) day += RewindItem(card, yearsAgo)
                    weekYearsAgo(date, today)?.let { week += RewindItem(card, it) }
                    if (yearsAgo >= 1 && date.month == today.month) month += RewindItem(card, yearsAgo)
                }
                DateKind.MONTH -> {
                    val yearsAgo = today.year - date.year
                    if (yearsAgo >= 1 && date.month == today.month) month += RewindItem(card, yearsAgo)
                }
                DateKind.YEAR, DateKind.FUZZY -> Unit
            }
        }
        val (scope, items) = when {
            day.isNotEmpty() -> RewindScope.DAY to day
            week.isNotEmpty() -> RewindScope.WEEK to week
            month.isNotEmpty() -> RewindScope.MONTH to month
            else -> return null
        }
        val sorted = items
            .distinctBy { it.card.memory.id }
            .sortedWith(compareBy<RewindItem> { it.yearsAgo }.thenByDescending { it.card.memory.date.startEpochDay }.thenByDescending { it.card.memory.id })
            .take(MAX_ITEMS)
        return RewindResult(scope, sorted)
    }

    /** Same month and day; a 29 February memory also counts on 28 February in years without a 29th. */
    private fun isSameDay(date: LocalDate, today: LocalDate): Boolean {
        if (date.month != today.month) return false
        if (date.dayOfMonth == today.dayOfMonth) return true
        return date.monthValue == 2 && date.dayOfMonth == 29 && today.dayOfMonth == 28 && !today.isLeapYear
    }

    /**
     * Years ago when [date] falls within [WEEK_DAYS] of today's day in some earlier year, else null. Looks at the
     * anniversary in today's year and the years either side, so 30 December still counts on 2 January.
     */
    private fun weekYearsAgo(date: LocalDate, today: LocalDate): Int? {
        for (offset in -1..1) {
            val anniversary = sameDayIn(date, today.year + offset)
            val distance = abs(anniversary.toEpochDay() - today.toEpochDay())
            if (distance <= WEEK_DAYS) {
                val yearsAgo = today.year + offset - date.year
                return yearsAgo.takeIf { it >= 1 }
            }
        }
        return null
    }

    /** [date]'s month and day in [year] (29 February becomes 28 February in other years). */
    private fun sameDayIn(date: LocalDate, year: Int): LocalDate =
        YearMonth.of(year, date.month).let { ym -> ym.atDay(minOf(date.dayOfMonth, ym.lengthOfMonth())) }
}

/** A memory standing for a group (a year or a place): its cover photo, or its cover style when it has no media. */
data class CoverPick(val memoryId: Long, val mediaId: Long?, val coverStyle: String)

object Covers {
    /**
     * One cover per key from rows ordered by preference (newest first): the first row with a photo or video, else the
     * first row (shown as cover art).
     */
    fun <K> pick(rows: List<Pair<K, CoverPick>>): Map<K, CoverPick> {
        val result = LinkedHashMap<K, CoverPick>()
        for ((key, cover) in rows) {
            val current = result[key]
            if (current == null || (current.mediaId == null && cover.mediaId != null)) result[key] = cover
        }
        return result
    }
}
