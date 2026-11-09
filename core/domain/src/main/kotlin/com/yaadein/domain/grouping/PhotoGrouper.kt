package com.yaadein.domain.grouping

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A picked photo or video before import. */
data class PickedItem(
    val key: String,
    val takenAtMillis: Long?,
    val isVideo: Boolean = false,
    /** Face-cluster ids present in the item when face suggestions are on; empty otherwise. */
    val faces: Set<Long> = emptySet(),
    /** EXIF GPS position, when the photo has one (used by Quick Save's place suggestion). */
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class SuggestedGroup(
    val items: List<PickedItem>,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
) {
    val isUndated: Boolean get() = startDate == null
    val isMultiDay: Boolean get() = startDate != null && endDate != null && endDate != startDate
}

/**
 * Groups picked media into suggested memories by date gap:
 * - photos on the same day belong together;
 * - consecutive days chain into one trip (e.g. a three-day wedding);
 * - a gap of more than [maxGapDays] starts a new memory;
 * - when faces are available, a same-day run splits where the set of faces changes completely
 *   and at least [faceSplitGapMinutes] passed (two different gatherings on one day).
 * Undated items go into one trailing group so the user can place them.
 */
object PhotoGrouper {
    fun group(
        items: List<PickedItem>,
        zone: ZoneId = ZoneId.systemDefault(),
        maxGapDays: Long = 1,
        useFaces: Boolean = false,
        faceSplitGapMinutes: Long = 180,
    ): List<SuggestedGroup> {
        val (dated, undated) = items.partition { it.takenAtMillis != null }
        val sorted = dated.sortedBy { it.takenAtMillis }
        val groups = ArrayList<MutableList<PickedItem>>()
        var current: MutableList<PickedItem>? = null
        var lastDate: LocalDate? = null
        var lastMillis = 0L
        for (item in sorted) {
            val millis = item.takenAtMillis!!
            val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            val startNew = when {
                current == null || lastDate == null -> true
                date.toEpochDay() - lastDate.toEpochDay() > maxGapDays -> true
                useFaces && item.faces.isNotEmpty() && current.lastOrNull()?.faces?.isNotEmpty() == true &&
                    current.last().faces.intersect(item.faces).isEmpty() &&
                    (millis - lastMillis) >= faceSplitGapMinutes * 60_000L -> true
                else -> false
            }
            if (startNew) {
                current = ArrayList()
                groups += current
            }
            current!!.add(item)
            lastDate = date
            lastMillis = millis
        }
        val result = groups.map { g ->
            val first = Instant.ofEpochMilli(g.first().takenAtMillis!!).atZone(zone).toLocalDate()
            val last = Instant.ofEpochMilli(g.last().takenAtMillis!!).atZone(zone).toLocalDate()
            SuggestedGroup(g, first, last)
        }.sortedByDescending { it.startDate }
        return if (undated.isEmpty()) result else result + SuggestedGroup(undated, null, null)
    }

    /** Merges two groups (user tapped "merge"). */
    fun merge(a: SuggestedGroup, b: SuggestedGroup): SuggestedGroup {
        val items = (a.items + b.items).sortedBy { it.takenAtMillis ?: Long.MAX_VALUE }
        val dates = listOfNotNull(a.startDate, a.endDate, b.startDate, b.endDate)
        return SuggestedGroup(items, dates.minOrNull(), dates.maxOrNull())
    }

    /** Splits a group at [index] (items before the index stay, the rest become a new group). */
    fun split(group: SuggestedGroup, index: Int, zone: ZoneId = ZoneId.systemDefault()): Pair<SuggestedGroup, SuggestedGroup> {
        val first = group.items.take(index.coerceIn(1, group.items.size - 1))
        val second = group.items.drop(first.size)
        fun dates(list: List<PickedItem>): Pair<LocalDate?, LocalDate?> {
            val d = list.mapNotNull { it.takenAtMillis }.map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            return d.minOrNull() to d.maxOrNull()
        }
        val (fs, fe) = dates(first); val (ss, se) = dates(second)
        return SuggestedGroup(first, fs, fe) to SuggestedGroup(second, ss, se)
    }
}
