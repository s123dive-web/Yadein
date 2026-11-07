package com.yaadein.domain.grouping

import java.time.Instant
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId

/** How a bulk upload is split into memories. */
enum class SplitBy { YEAR, MONTH }

/**
 * Which photos of a bulk upload to keep, by the date they were taken.
 * - [years]: keep only these years (e.g. 2023, or 2019 and 2023); empty keeps every year.
 * - [months]: keep only these months (e.g. every December); empty keeps all twelve.
 * Both together keep e.g. "December 2023" only.
 */
data class PeriodFilter(
    val years: Set<Int> = emptySet(),
    val months: Set<Month> = emptySet(),
) {
    val isEmpty: Boolean get() = years.isEmpty() && months.isEmpty()

    fun matches(yearMonth: YearMonth): Boolean =
        (years.isEmpty() || yearMonth.year in years) && (months.isEmpty() || yearMonth.month in months)
}

/** One memory-to-be: every photo of [year] (when split by year) or of [year]/[month] (when split by month). */
data class PeriodBucket(
    val year: Int,
    /** Null when split by year. */
    val month: Month?,
    val items: List<PickedItem>,
) {
    val yearMonth: YearMonth? get() = month?.let { YearMonth.of(year, it) }
}

data class PeriodSplit(
    /** Newest first; each bucket becomes one memory. */
    val buckets: List<PeriodBucket>,
    /** Items without a date: they cannot be placed in a year or month. */
    val undated: List<PickedItem>,
    /** Dated items left out by the filter. */
    val filteredOut: Int,
) {
    val memoryCount: Int get() = buckets.size
    val keptCount: Int get() = buckets.sumOf { it.items.size }
}

/**
 * Splits a bulk upload into one memory per year or per month, after keeping only the years and months the user asked
 * for. Within a bucket items are ordered oldest first, so the memory reads in the order the photos were taken.
 */
object PeriodGrouper {
    fun split(
        items: List<PickedItem>,
        splitBy: SplitBy,
        filter: PeriodFilter = PeriodFilter(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): PeriodSplit {
        val (dated, undated) = items.partition { it.takenAtMillis != null }
        var filteredOut = 0
        val byKey = LinkedHashMap<YearMonth, MutableList<PickedItem>>()
        for (item in dated.sortedBy { it.takenAtMillis }) {
            val ym = yearMonthOf(item.takenAtMillis!!, zone)
            if (!filter.matches(ym)) { filteredOut++; continue }
            val key = if (splitBy == SplitBy.YEAR) YearMonth.of(ym.year, 1) else ym
            byKey.getOrPut(key) { ArrayList() } += item
        }
        val buckets = byKey.entries
            .sortedByDescending { it.key }
            .map { (key, list) -> PeriodBucket(key.year, if (splitBy == SplitBy.MONTH) key.month else null, list) }
        return PeriodSplit(buckets, undated, filteredOut)
    }

    /** Years present in [items], newest first; used to offer the year filter. */
    fun years(items: List<PickedItem>, zone: ZoneId = ZoneId.systemDefault()): List<Int> =
        items.mapNotNull { it.takenAtMillis }.map { yearMonthOf(it, zone).year }.distinct().sortedDescending()

    private fun yearMonthOf(millis: Long, zone: ZoneId): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(millis).atZone(zone))
}
