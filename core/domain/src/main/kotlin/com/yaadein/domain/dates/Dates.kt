package com.yaadein.domain.dates

import com.yaadein.domain.model.DateKind
import com.yaadein.domain.model.MemoryDate
import java.time.LocalDate
import java.time.Month
import java.time.Year
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pure date helpers. Everything on the timeline is keyed by epoch day so that exact, month-only,
 * year-only and fuzzy memories interleave in a sensible order.
 */
object Dates {
    fun today(): LocalDate = LocalDate.now()
    fun todayEpochDay(): Long = today().toEpochDay()

    fun exact(date: LocalDate, end: LocalDate? = null) = MemoryDate(
        kind = DateKind.EXACT,
        startEpochDay = date.toEpochDay(),
        endEpochDay = end?.takeIf { it.isAfter(date) }?.toEpochDay(),
    )

    fun month(yearMonth: YearMonth) = MemoryDate(
        kind = DateKind.MONTH,
        startEpochDay = yearMonth.atDay(1).toEpochDay(),
        endEpochDay = yearMonth.atEndOfMonth().toEpochDay(),
    )

    fun year(year: Int) = MemoryDate(
        kind = DateKind.YEAR,
        startEpochDay = LocalDate.of(year, 1, 1).toEpochDay(),
        endEpochDay = LocalDate.of(year, 12, 31).toEpochDay(),
    )

    /** A fuzzy label such as "Summer 2015" anchored to the season's first day when it can be parsed. */
    fun fuzzy(label: String, anchorYear: Int, anchorMonth: Int = 1) = MemoryDate(
        kind = DateKind.FUZZY,
        startEpochDay = LocalDate.of(anchorYear, anchorMonth.coerceIn(1, 12), 1).toEpochDay(),
        endEpochDay = null,
        label = label.trim(),
    )

    fun start(date: MemoryDate): LocalDate = LocalDate.ofEpochDay(date.startEpochDay)
    fun year(date: MemoryDate): Int = start(date).year
    fun month(date: MemoryDate): Month = start(date).month

    private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
    private val dayNoYearFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
    private val monthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

    /**
     * Human label for a memory date. Fuzzy kinds return a soft phrase; callers can wrap with
     * localized templates ("sometime in 2015") using [DateLabel].
     */
    fun label(date: MemoryDate, locale: Locale = Locale.getDefault()): DateLabel {
        val start = start(date)
        return when (date.kind) {
            DateKind.EXACT -> {
                val end = date.endEpochDay?.let { LocalDate.ofEpochDay(it) }
                if (end == null || end == start) {
                    DateLabel.Exact(start.format(dayFormatter.withLocale(locale)))
                } else if (end.year == start.year) {
                    DateLabel.Exact(
                        start.format(dayNoYearFormatter.withLocale(locale)) + " – " + end.format(dayFormatter.withLocale(locale)),
                    )
                } else {
                    DateLabel.Exact(start.format(dayFormatter.withLocale(locale)) + " – " + end.format(dayFormatter.withLocale(locale)))
                }
            }
            DateKind.MONTH -> DateLabel.Month(YearMonth.from(start).format(monthFormatter.withLocale(locale)))
            DateKind.YEAR -> DateLabel.Year(start.year.toString())
            DateKind.FUZZY -> DateLabel.Fuzzy(date.label ?: start.year.toString())
        }
    }

    fun monthName(month: Month, locale: Locale = Locale.getDefault()): String =
        month.getDisplayName(TextStyle.FULL, locale)

    fun daysUntilBirthday(birthdayEpochDay: Long, today: LocalDate): Int {
        val birthday = LocalDate.ofEpochDay(birthdayEpochDay)
        var next = birthday.withYear(today.year)
        if (next.isBefore(today)) next = next.plusYears(1)
        return (next.toEpochDay() - today.toEpochDay()).toInt()
    }

    fun age(birthdayEpochDay: Long, today: LocalDate): Int {
        val birthday = LocalDate.ofEpochDay(birthdayEpochDay)
        var age = today.year - birthday.year
        if (today.withYear(birthday.year).isBefore(birthday)) age--
        return age
    }

    /** Month-day key (MMDD) used by "On this day". */
    fun monthDayKey(date: LocalDate): Int = date.monthValue * 100 + date.dayOfMonth

    fun isValidYear(year: Int): Boolean = year in 1900..(Year.now().value + 1)
}

sealed interface DateLabel {
    val text: String

    data class Exact(override val text: String) : DateLabel
    data class Month(override val text: String) : DateLabel
    data class Year(override val text: String) : DateLabel
    data class Fuzzy(override val text: String) : DateLabel
}
