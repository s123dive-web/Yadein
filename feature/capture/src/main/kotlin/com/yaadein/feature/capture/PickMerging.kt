package com.yaadein.feature.capture

/**
 * Pure helpers for adding a second pick to a screen that already holds photos. The system Photo Picker happily returns
 * a photo the user already chose; every strip on these screens is a `LazyRow` keyed by the URI, so a duplicate must be
 * dropped at the source (never by changing the key).
 */
internal object PickMerging {
    /**
     * The items of [incoming] whose [key] is not in [known], in order, with the first occurrence of a repeated key kept
     * and the later ones dropped.
     */
    fun <T> fresh(incoming: List<T>, known: Set<String>, key: (T) -> String): List<T> {
        val seen = HashSet(known)
        return incoming.filter { seen.add(key(it)) }
    }

    /**
     * Index of the first range in [ranges] (inclusive epoch-day ranges; `null` = undated) that the span
     * [start]..[end] touches, allowing a gap of up to [gapDays] days on either side, so photos from the same day or
     * the next day fold into an existing draft the way [com.yaadein.domain.grouping.PhotoGrouper] chains consecutive
     * days. Returns -1 when nothing matches.
     */
    fun foldTarget(ranges: List<LongRange?>, start: Long, end: Long, gapDays: Long = 1): Int =
        ranges.indexOfFirst { r -> r != null && start <= r.last + gapDays && end >= r.first - gapDays }
}
