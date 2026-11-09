package com.yaadein.domain.capture

import com.yaadein.domain.grouping.PickedItem
import com.yaadein.domain.model.DateKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

class QuickSavePrefillTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 9)

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, lat: Double? = null, lon: Double? = null) =
        PickedItem("$y-$m-$d-$h", LocalDateTime.of(y, m, d, h, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), latitude = lat, longitude = lon)

    private fun day(date: com.yaadein.domain.model.MemoryDate) = LocalDate.ofEpochDay(date.startEpochDay)

    @Test
    fun `ten photos over two days become one exact day, the median`() {
        val items = (1..6).map { at(2024, 12, 20, 8 + it) } + (1..4).map { at(2024, 12, 21, 8 + it) }
        val (date, source) = QuickSavePrefill.date(items, today, zone)
        assertEquals(DateKind.EXACT, date.kind)
        assertEquals(LocalDate.of(2024, 12, 20), day(date))
        assertEquals(DateSource.PHOTOS, source)
        assertNull(date.endEpochDay)
    }

    @Test
    fun `a span of three days is still one exact day`() {
        val items = listOf(at(2024, 12, 20), at(2024, 12, 22), at(2024, 12, 23))
        val (date, _) = QuickSavePrefill.date(items, today, zone)
        assertEquals(DateKind.EXACT, date.kind)
        assertEquals(LocalDate.of(2024, 12, 22), day(date))
    }

    @Test
    fun `a span inside one month becomes that month`() {
        val items = listOf(at(2024, 10, 2), at(2024, 10, 15), at(2024, 10, 29))
        val (date, _) = QuickSavePrefill.date(items, today, zone)
        assertEquals(DateKind.MONTH, date.kind)
        assertEquals(LocalDate.of(2024, 10, 1), day(date))
    }

    @Test
    fun `a short span across a month boundary uses the median photo's month`() {
        val items = listOf(at(2024, 10, 28), at(2024, 11, 3), at(2024, 11, 5))
        val (date, _) = QuickSavePrefill.date(items, today, zone)
        assertEquals(DateKind.MONTH, date.kind)
        assertEquals(LocalDate.of(2024, 11, 1), day(date))
    }

    @Test
    fun `a span over a month stays one memory on the median day and suggests a split`() {
        val items = listOf(at(2024, 6, 1), at(2024, 6, 2), at(2024, 8, 15), at(2024, 12, 25))
        val (date, _) = QuickSavePrefill.date(items, today, zone)
        assertEquals(DateKind.EXACT, date.kind)
        assertEquals(LocalDate.of(2024, 8, 15), day(date))
        assertEquals(3, QuickSavePrefill.splitCount(items, zone))
        assertEquals(3, QuickSavePrefill.suggest(items, today, zone).splitInto)
    }

    @Test
    fun `no EXIF means today, marked as a guess, and no split`() {
        val items = listOf(PickedItem("a", null), PickedItem("b", null))
        val s = QuickSavePrefill.suggest(items, today, zone, locale = Locale.ENGLISH)
        assertEquals(today, day(s.date))
        assertEquals(DateSource.TODAY, s.dateSource)
        assertNull(s.splitInto)
    }

    @Test
    fun `mixed dated and undated items use the dated ones and do not count undated as an event`() {
        val items = listOf(at(2024, 12, 20), PickedItem("x", null), at(2024, 12, 21))
        val s = QuickSavePrefill.suggest(items, today, zone, locale = Locale.ENGLISH)
        assertEquals(DateSource.PHOTOS, s.dateSource)
        assertEquals(DateKind.EXACT, s.date.kind)
        assertNull(s.splitInto)
    }

    @Test
    fun `place comes from the nearest known place within two kilometres`() {
        val pune = KnownPlace("Pune", 18.5204, 73.8567)
        val mumbai = KnownPlace("Mumbai", 19.0760, 72.8777)
        val near = listOf(at(2024, 1, 1, lat = 18.5300, lon = 73.8600)) // ~1.1 km from Pune
        assertEquals("Pune", QuickSavePrefill.place(near, listOf(mumbai, pune)))
        val far = listOf(at(2024, 1, 1, lat = 18.6000, lon = 73.9000)) // ~10 km away
        assertNull(QuickSavePrefill.place(far, listOf(mumbai, pune)))
        assertNull(QuickSavePrefill.place(listOf(at(2024, 1, 1)), listOf(pune)))
    }

    @Test
    fun `haversine distance is sane`() {
        val km = QuickSavePrefill.distanceKm(18.5204, 73.8567, 19.0760, 72.8777)
        assertTrue(km in 115.0..125.0, "Pune to Mumbai is about 120 km, got $km")
        assertEquals(0.0, QuickSavePrefill.distanceKm(1.0, 2.0, 1.0, 2.0), 1e-9)
    }

    @Test
    fun `title combines the date with people, else place`() {
        val items = listOf(at(2024, 10, 2), at(2024, 10, 29))
        val withPeople = QuickSavePrefill.suggest(items, today, zone, peopleNames = listOf("Aaji"), locale = Locale.ENGLISH)
        assertEquals("October 2024 with Aaji", withPeople.title)
        val withPlace = QuickSavePrefill.suggest(listOf(at(2024, 12, 20)), today, zone, knownPlaces = listOf(KnownPlace("Pune", 18.52, 73.85)), locale = Locale.ENGLISH)
        assertEquals("20 Dec 2024", withPlace.title) // no GPS on the item, so no place
        val located = QuickSavePrefill.suggest(listOf(at(2024, 12, 20, lat = 18.52, lon = 73.85)), today, zone, knownPlaces = listOf(KnownPlace("Pune", 18.52, 73.85)), locale = Locale.ENGLISH)
        assertEquals("20 Dec 2024 in Pune", located.title)
        assertEquals("Pune", located.place)
    }
}
