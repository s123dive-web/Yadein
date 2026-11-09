package com.yaadein.domain.capture

import com.yaadein.domain.dates.Dates
import com.yaadein.domain.grouping.PhotoGrouper
import com.yaadein.domain.grouping.PickedItem
import com.yaadein.domain.model.MemoryDate
import com.yaadein.domain.polish.PolishEngine
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A place the vault already knows, with where its photos were taken. */
data class KnownPlace(val name: String, val latitude: Double, val longitude: Double)

/** Where Quick Save's date came from, so the screen can mark a guess. */
enum class DateSource {
    /** EXIF dates of the picked items. */
    PHOTOS,
    /** No item had a date: today, visibly marked as a guess. */
    TODAY,
}

/** Everything Quick Save pre-fills; every field stays editable. */
data class QuickSaveSuggestion(
    val date: MemoryDate,
    val dateSource: DateSource,
    /** How many separate events the photos look like (PhotoGrouper); null when they look like one. */
    val splitInto: Int?,
    val place: String,
    val title: String,
)

/**
 * Pure pre-fill rules for Quick Save (tap + → pick photos → Save). Nothing here touches Android.
 *
 * - Date: EXIF dates of all items. A span of at most [Rules.EXACT_SPAN_DAYS] days is one exact day (the median day);
 *   at most [Rules.MONTH_SPAN_DAYS] days inside one month is that month; otherwise the median day with a split hint.
 *   Without any EXIF date the date is today, marked [DateSource.TODAY].
 * - Split hint: [PhotoGrouper] groups (same day or consecutive days). Two or more groups → "Looks like N events".
 * - Place: the nearest place the vault already knows within [Rules.PLACE_RADIUS_KM] of any item's GPS position.
 * - Title: [PolishEngine.titleFor] from date, place and people ("October 2024 with Aaji").
 */
object QuickSavePrefill {
    object Rules {
        /** Photos spanning this many days or fewer are one exact day. */
        const val EXACT_SPAN_DAYS = 3
        /** Photos spanning this many days or fewer (and within one month) are a month-only date. */
        const val MONTH_SPAN_DAYS = 31
        /** A known place closer than this to a photo's GPS position is suggested. */
        const val PLACE_RADIUS_KM = 2.0
        /** Recently tagged people offered as chips. */
        const val RECENT_PEOPLE = 6
        /** Most photos Quick Save accepts in one go. */
        const val MAX_ITEMS = 100
    }

    fun suggest(
        items: List<PickedItem>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        knownPlaces: List<KnownPlace> = emptyList(),
        peopleNames: List<String> = emptyList(),
        festival: String? = null,
        locale: Locale = Locale.getDefault(),
    ): QuickSaveSuggestion {
        val (date, source) = date(items, today, zone)
        val place = place(items, knownPlaces) ?: ""
        return QuickSaveSuggestion(
            date = date,
            dateSource = source,
            splitInto = splitCount(items, zone).takeIf { it >= 2 },
            place = place,
            title = PolishEngine.titleFor(date, place, peopleNames, festival, locale),
        )
    }

    /** The date rule on its own. */
    fun date(items: List<PickedItem>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Pair<MemoryDate, DateSource> {
        val days = items.mapNotNull { it.takenAtMillis }.map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.sorted()
        if (days.isEmpty()) return Dates.exact(today) to DateSource.TODAY
        val first = days.first()
        val last = days.last()
        val span = (last.toEpochDay() - first.toEpochDay()).toInt()
        val median = days[days.size / 2]
        val date = when {
            span <= Rules.EXACT_SPAN_DAYS -> Dates.exact(median)
            span <= Rules.MONTH_SPAN_DAYS && YearMonth.from(first) == YearMonth.from(last) -> Dates.month(YearMonth.from(first))
            span <= Rules.MONTH_SPAN_DAYS -> Dates.month(YearMonth.from(median))
            else -> Dates.exact(median)
        }
        return date to DateSource.PHOTOS
    }

    /** How many memories [PhotoGrouper] would make of these items (undated items do not count as an event). */
    fun splitCount(items: List<PickedItem>, zone: ZoneId = ZoneId.systemDefault()): Int =
        PhotoGrouper.group(items, zone).count { !it.isUndated }

    /** The nearest known place within [Rules.PLACE_RADIUS_KM] of any item that has a GPS position. */
    fun place(items: List<PickedItem>, knownPlaces: List<KnownPlace>, radiusKm: Double = Rules.PLACE_RADIUS_KM): String? {
        if (knownPlaces.isEmpty()) return null
        var best: KnownPlace? = null
        var bestKm = radiusKm
        for (item in items) {
            val lat = item.latitude ?: continue
            val lon = item.longitude ?: continue
            for (p in knownPlaces) {
                val km = distanceKm(lat, lon, p.latitude, p.longitude)
                if (km <= bestKm) { bestKm = km; best = p }
            }
        }
        return best?.name
    }

    /** Great-circle distance (haversine), in kilometres. */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0088
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}
