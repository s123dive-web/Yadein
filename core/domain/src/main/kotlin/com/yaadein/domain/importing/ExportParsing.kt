package com.yaadein.domain.importing

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Understands the two exports people already have: a Google Photos Takeout zip (album folders plus a JSON "sidecar"
 * per photo holding the date it was taken) and a WhatsApp "Export chat" zip (dates only in file names like
 * IMG-20240117-WA0003.jpg). Pure, so it is unit-tested; the zip streaming lives in the data layer.
 */
object ExportParsing {
    enum class Kind { PHOTO, VIDEO }

    data class Item(val path: String, val kind: Kind, val takenAtMillis: Long?, val album: String?)

    /** A memory to create: [title] is the album name (null: build one from the date), dates inclusive. */
    data class Group(val title: String?, val items: List<Item>, val start: LocalDate, val end: LocalDate)

    private val photoExt = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "gif")
    private val videoExt = setOf("mp4", "3gp", "mov", "m4v", "webm", "mkv")

    fun kindOf(path: String): Kind? = when (path.substringAfterLast('.', "").lowercase()) {
        in photoExt -> Kind.PHOTO
        in videoExt -> Kind.VIDEO
        else -> null
    }

    private val whatsApp = Regex("""(?:IMG|VID|PTT|AUD|STK)-(\d{4})(\d{2})(\d{2})-WA\d+""", RegexOption.IGNORE_CASE)
    private val cameraName = Regex("""(?:IMG|VID|PXL|Screenshot)[_-](\d{4})(\d{2})(\d{2})[_-]""", RegexOption.IGNORE_CASE)

    /** Date encoded in a WhatsApp or camera file name. */
    fun dateFromName(path: String): LocalDate? {
        val name = path.substringAfterLast('/')
        val m = whatsApp.find(name) ?: cameraName.find(name) ?: return null
        return runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
            ?.takeIf { it.year in 1990..2100 }
    }

    /** Takeout album folder ("Takeout/Google Photos/Goa 2024/x.jpg" → "Goa 2024"); year folders are not albums. */
    fun albumOf(path: String): String? {
        val parts = path.split('/').filter { it.isNotBlank() }
        val i = parts.indexOfFirst { it.equals("Google Photos", ignoreCase = true) || it.equals("Google Foto", ignoreCase = true) }
        val folder = if (i >= 0 && i + 2 < parts.size) parts[i + 1] else if (parts.size >= 2) parts[parts.size - 2] else return null
        if (Regex("""(?i)^photos from \d{4}$""").matches(folder)) return null
        if (folder.equals("Takeout", true) || folder.startsWith("WhatsApp", true)) return null
        return folder.take(60)
    }

    /** "IMG_1.jpg.json" / "IMG_1.jpg.supplemental-metadata.json" (and truncated variants) → "IMG_1.jpg". */
    fun sidecarTarget(path: String): String? {
        if (!path.endsWith(".json", ignoreCase = true)) return null
        val base = path.removeSuffix(".json").removeSuffix(".JSON")
        val i = base.indexOf(".supplemental", ignoreCase = true)
        return if (i >= 0) base.substring(0, i) else base.takeIf { kindOf(it) != null }
    }

    private val takenTime = Regex(""""photoTakenTime"\s*:\s*\{[^}]*"timestamp"\s*:\s*"?(\d+)""")

    fun takenFromSidecar(json: String): Long? = takenTime.find(json)?.groupValues?.get(1)?.toLongOrNull()?.times(1000)

    /** "WhatsApp Chat with Aai.zip" → "Aai". */
    fun chatName(fileName: String?): String? {
        val n = fileName?.substringBeforeLast('.') ?: return null
        val m = Regex("""(?i)WhatsApp Chat (?:with|-)\s*(.+)""").find(n) ?: return null
        return m.groupValues[1].trim().takeIf { it.isNotBlank() }?.take(40)
    }

    /**
     * Albums become one memory each; everything else is grouped by day. Items without any date go into one "undated"
     * group dated [fallback].
     */
    fun groups(items: List<Item>, zone: ZoneId, fallback: LocalDate): List<Group> {
        fun dateOf(it: Item): LocalDate? = it.takenAtMillis?.let { ms -> Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() } ?: dateFromName(it.path)
        val (albums, loose) = items.partition { it.album != null }
        val result = ArrayList<Group>()
        albums.groupBy { it.album!! }.forEach { (album, list) ->
            val dates = list.mapNotNull(::dateOf)
            result += Group(album, list, dates.minOrNull() ?: fallback, dates.maxOrNull() ?: fallback)
        }
        loose.groupBy { dateOf(it) }.forEach { (day, list) ->
            result += Group(null, list, day ?: fallback, day ?: fallback)
        }
        return result.sortedBy { it.start }
    }
}
