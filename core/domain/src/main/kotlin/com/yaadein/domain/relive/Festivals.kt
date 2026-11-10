package com.yaadein.domain.relive

import java.time.LocalDate

/** A festival the app knows, with the key used in the data table and string resources (`festival_<key>`). */
enum class Festival(val key: String, val windowDays: Int = 2) {
    DIWALI("diwali", windowDays = 3), HOLI("holi"), GANESH_CHATURTHI("ganesh_chaturthi", windowDays = 5), DUSSEHRA("dussehra"),
    RAKSHA_BANDHAN("raksha_bandhan"), JANMASHTAMI("janmashtami"), GUDI_PADWA("gudi_padwa"), MAKAR_SANKRANTI("makar_sankranti"),
    EID_AL_FITR("eid_al_fitr"), EID_AL_ADHA("eid_al_adha"), GURU_NANAK_JAYANTI("guru_nanak_jayanti"), CHRISTMAS("christmas"),
    NEW_YEAR("new_year", windowDays = 1), INDEPENDENCE_DAY("independence_day", windowDays = 0), REPUBLIC_DAY("republic_day", windowDays = 0),
    ;

    companion object {
        fun fromKey(key: String): Festival? = entries.firstOrNull { it.key == key }
    }
}

/** One festival day in one year. */
data class FestivalDate(val festival: Festival, val date: LocalDate) {
    /** The days on which a memory counts as "this festival" (± the festival's window). */
    val window: ClosedRange<Long>
        get() = (date.toEpochDay() - festival.windowDays)..(date.toEpochDay() + festival.windowDays)
}

/** A festival that is on right now (or within its window), with the memories' day ranges in earlier years. */
data class FestivalRewind(val festival: Festival, val year: Int, val yearsBack: List<LongRange>)

/**
 * Bundled festival calendar, 2010–2035. The dates come from the table `festivals/festivals.csv` on the classpath
 * (`festival,year,yyyy-mm-dd`, sources in docs/FESTIVALS.md); lunar festivals are never computed here.
 */
class Festivals(private val dates: List<FestivalDate>) {
    private val byYear: Map<Int, List<FestivalDate>> = dates.groupBy { it.date.year }

    val years: IntRange = if (dates.isEmpty()) IntRange.EMPTY else dates.minOf { it.date.year }..dates.maxOf { it.date.year }

    /** The festivals whose window contains [day], Diwali first when two overlap (e.g. Diwali and Bhai Dooj season). */
    fun on(day: LocalDate): List<FestivalDate> {
        val epoch = day.toEpochDay()
        return dates.filter { epoch in it.window }.sortedBy { it.festival.ordinal }
    }

    /** The festival to name a memory after, or null. Prefers the festival whose exact day is closest. */
    fun primary(day: LocalDate): FestivalDate? = on(day).minByOrNull { kotlin.math.abs(it.date.toEpochDay() - day.toEpochDay()) }

    /** The date of [festival] in [year], if the table has it. */
    fun date(festival: Festival, year: Int): FestivalDate? = byYear[year]?.firstOrNull { it.festival == festival }

    /** Every festival in [year], in calendar order. */
    fun inYear(year: Int): List<FestivalDate> = byYear[year].orEmpty().sortedBy { it.date }

    /** The next festival on or after [today] (within the table). */
    fun next(today: LocalDate, limit: Int = 3): List<FestivalDate> =
        dates.filter { !it.date.isBefore(today) }.sortedBy { it.date }.take(limit)

    /**
     * For Relive: if [today] is inside a festival's window, the same festival's windows in earlier years (newest first),
     * so "This Diwali, years ago" can load memories with one range query.
     */
    fun rewind(today: LocalDate): FestivalRewind? {
        val current = primary(today) ?: return null
        val earlier = dates.filter { it.festival == current.festival && it.date.year < current.date.year }
            .sortedByDescending { it.date }
            .map { LongRange(it.window.start, it.window.endInclusive) }
        return FestivalRewind(current.festival, current.date.year, earlier)
    }

    /** Day ranges for a timeline filter chip: every year's window of [festival]. */
    fun windows(festival: Festival): List<LongRange> =
        dates.filter { it.festival == festival }.map { LongRange(it.window.start, it.window.endInclusive) }

    companion object {
        const val RESOURCE = "festivals/festivals.csv"

        /** Parses `festival,year,date` lines; blank lines and `#` comments are skipped; unknown festivals are ignored. */
        fun parse(csv: String): Festivals = Festivals(
            csv.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.mapNotNull { line ->
                val parts = line.split(',').map { it.trim() }
                if (parts.size < 3) return@mapNotNull null
                val festival = Festival.fromKey(parts[0]) ?: return@mapNotNull null
                val date = runCatching { LocalDate.parse(parts[2]) }.getOrNull() ?: return@mapNotNull null
                if (date.year.toString() != parts[1]) return@mapNotNull null
                FestivalDate(festival, date)
            }.toList(),
        )

        /** Loads the bundled table from the classpath (core/domain resources). */
        fun bundled(): Festivals {
            val stream = Festivals::class.java.classLoader?.getResourceAsStream(RESOURCE) ?: return Festivals(emptyList())
            return stream.use { parse(it.readBytes().toString(Charsets.UTF_8)) }
        }
    }
}
