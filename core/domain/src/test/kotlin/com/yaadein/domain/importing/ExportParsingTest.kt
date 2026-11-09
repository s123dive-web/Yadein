package com.yaadein.domain.importing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ExportParsingTest {
    @Test
    fun `whatsapp and camera names carry the date`() {
        assertEquals(LocalDate.of(2024, 1, 17), ExportParsing.dateFromName("WhatsApp Chat/IMG-20240117-WA0003.jpg"))
        assertEquals(LocalDate.of(2023, 12, 31), ExportParsing.dateFromName("VID-20231231-WA0010.mp4"))
        assertEquals(LocalDate.of(2025, 3, 2), ExportParsing.dateFromName("PXL_20250302_101010123.jpg"))
        assertNull(ExportParsing.dateFromName("holiday.jpg"))
    }

    @Test
    fun `takeout albums and sidecars`() {
        assertEquals("Goa 2024", ExportParsing.albumOf("Takeout/Google Photos/Goa 2024/IMG_1.jpg"))
        assertNull(ExportParsing.albumOf("Takeout/Google Photos/Photos from 2023/IMG_1.jpg"))
        assertEquals("Takeout/Google Photos/Goa 2024/IMG_1.jpg", ExportParsing.sidecarTarget("Takeout/Google Photos/Goa 2024/IMG_1.jpg.supplemental-metadata.json"))
        assertEquals("a/IMG_2.jpg", ExportParsing.sidecarTarget("a/IMG_2.jpg.json"))
        assertNull(ExportParsing.sidecarTarget("a/metadata.json"))
        assertEquals(1_700_000_000_000L, ExportParsing.takenFromSidecar("""{"title":"x","photoTakenTime":{"timestamp":"1700000000","formatted":"..."}}"""))
    }

    @Test
    fun `chat name comes from the zip name`() {
        assertEquals("Aai", ExportParsing.chatName("WhatsApp Chat with Aai.zip"))
        assertNull(ExportParsing.chatName("photos.zip"))
    }

    @Test
    fun `albums become one memory, loose photos one per day`() {
        val items = listOf(
            ExportParsing.Item("Takeout/Google Photos/Goa/a.jpg", ExportParsing.Kind.PHOTO, 1_700_000_000_000L, "Goa"),
            ExportParsing.Item("Takeout/Google Photos/Goa/b.jpg", ExportParsing.Kind.PHOTO, 1_700_200_000_000L, "Goa"),
            ExportParsing.Item("IMG-20240117-WA0003.jpg", ExportParsing.Kind.PHOTO, null, null),
            ExportParsing.Item("IMG-20240117-WA0004.jpg", ExportParsing.Kind.PHOTO, null, null),
            ExportParsing.Item("IMG-20240118-WA0001.jpg", ExportParsing.Kind.PHOTO, null, null),
        )
        val groups = ExportParsing.groups(items, ZoneOffset.UTC, LocalDate.of(2026, 1, 1))
        assertEquals(3, groups.size)
        val goa = groups.first { it.title == "Goa" }
        assertEquals(2, goa.items.size)
        assertEquals(LocalDate.of(2023, 11, 14), goa.start)
        assertEquals(2, groups.first { it.start == LocalDate.of(2024, 1, 17) }.items.size)
    }
}
