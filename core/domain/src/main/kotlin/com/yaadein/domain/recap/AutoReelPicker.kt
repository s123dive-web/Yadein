package com.yaadein.domain.recap

import com.yaadein.domain.model.DateKind
import com.yaadein.domain.model.MediaKind
import com.yaadein.domain.model.MemoryDetail
import com.yaadein.domain.model.Person
import java.time.LocalDate
import kotlin.random.Random

/** The three reels Yaadein keeps ready for the user. Each slot is filled with the first variant that has enough photos. */
enum class AutoReelSlot(val key: String) { RECENT("recent"), PERSON("person"), THROWBACK("throwback") }

enum class AutoReelVariant {
    /** Memories from the last [AutoReelPicker.RECENT_DAYS] days. */
    RECENT_DAYS,
    /** The latest memories, when nothing is that recent. */
    LATEST,
    /** Memories with the person who appears in the most memories. */
    PERSON,
    /** A sample across the whole library, when nobody is tagged. */
    EVERYTHING,
    /** This calendar month in earlier years. */
    THIS_MONTH_YEARS_AGO,
    /** One memory from each year. */
    THROUGH_THE_YEARS,
}

data class AutoReelPick(
    val slot: AutoReelSlot,
    val variant: AutoReelVariant,
    val memories: List<MemoryDetail>,
    val mediaIds: Set<Long>,
    val person: Person? = null,
    val theme: RecapTheme,
) {
    /** Changes only when the photos (or the reel's kind) change, so an unchanged reel is not rendered again. */
    val signature: String get() = "${variant.name}:${person?.id ?: 0}:${mediaIds.sorted().joinToString(",")}".hashCode().toUInt().toString(16)
}

/** Chooses what the automatic reels show. Pure and deterministic for a given library and day. */
object AutoReelPicker {
    const val RECENT_DAYS = 60L
    /** Photos per automatic reel: a short (~30 s) reel. */
    const val ITEMS_PER_REEL = 15
    private const val LATEST_MEMORIES = 10
    private const val MAX_YEARS = 12

    fun pick(memories: List<MemoryDetail>, today: LocalDate): List<AutoReelPick> {
        val withPhotos = memories.filter { m -> m.media.any { it.kind == MediaKind.PHOTO } }
            .sortedWith(compareBy({ it.memory.date.startEpochDay }, { it.memory.id }))
        if (withPhotos.isEmpty()) return emptyList()
        val todayDay = today.toEpochDay()
        val picks = ArrayList<AutoReelPick>()
        fun add(slot: AutoReelSlot, variant: AutoReelVariant, chosen: List<MemoryDetail>, theme: RecapTheme, person: Person? = null, seed: Long = 0): Boolean {
            if (chosen.isEmpty()) return false
            // A seed picks a different (but repeatable) set of photos from big albums, so overlapping reels still differ.
            val pool = if (seed == 0L) chosen else chosen.map { m ->
                m.copy(media = m.media.shuffled(Random(seed * 31 + m.memory.id)).mapIndexed { i, md -> md.copy(position = i) })
            }
            val ids = RecapPlanner.defaultSelection(pool, includeVideos = false, maxItems = ITEMS_PER_REEL)
            // The same photos twice would be the same reel twice.
            if (ids.isEmpty() || picks.any { it.mediaIds == ids }) return false
            picks += AutoReelPick(slot, variant, chosen, ids, person, theme)
            return true
        }

        // 1. Recent moments.
        val recent = withPhotos.filter { todayDay - it.memory.date.startEpochDay in 0..RECENT_DAYS }
        if (recent.size < 2 || !add(AutoReelSlot.RECENT, AutoReelVariant.RECENT_DAYS, recent, RecapTheme.WARM)) {
            add(AutoReelSlot.RECENT, AutoReelVariant.LATEST, withPhotos.takeLast(LATEST_MEMORIES), RecapTheme.WARM)
        }

        // 2. The person who appears most, else a sample of everything.
        val top = withPhotos.flatMap { it.people }.groupingBy { it.id }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<Long, Int>> { it.value }.thenBy { it.key }).firstOrNull()
        val person = top?.let { e -> withPhotos.firstNotNullOf { m -> m.people.firstOrNull { it.id == e.key } } }
        val personDone = person != null &&
            add(AutoReelSlot.PERSON, AutoReelVariant.PERSON, withPhotos.filter { m -> m.people.any { it.id == person.id } }, RecapTheme.FESTIVE, person)
        if (!personDone) add(AutoReelSlot.PERSON, AutoReelVariant.EVERYTHING, withPhotos, RecapTheme.FESTIVE, seed = 1)

        // 3. Throwback: this month in earlier years, else one memory from each year.
        val dated = withPhotos.filter { it.memory.date.kind == DateKind.EXACT || it.memory.date.kind == DateKind.MONTH }
        val thisMonth = dated.filter {
            val d = LocalDate.ofEpochDay(it.memory.date.startEpochDay)
            d.monthValue == today.monthValue && d.year < today.year
        }
        if (thisMonth.isEmpty() || !add(AutoReelSlot.THROWBACK, AutoReelVariant.THIS_MONTH_YEARS_AGO, thisMonth, RecapTheme.CLASSIC_FILM)) {
            val perYear = withPhotos.groupBy { LocalDate.ofEpochDay(it.memory.date.startEpochDay).year }
                .toSortedMap().values.map { it.first() }.takeLast(MAX_YEARS)
            if (perYear.size >= 2) add(AutoReelSlot.THROWBACK, AutoReelVariant.THROUGH_THE_YEARS, perYear, RecapTheme.CLASSIC_FILM, seed = 2)
        }
        return picks
    }
}
