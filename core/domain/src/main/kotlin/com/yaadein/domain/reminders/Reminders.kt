package com.yaadein.domain.reminders

import com.yaadein.domain.model.DateKind
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.Person
import com.yaadein.domain.model.Relation
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields

/**
 * The little the reminders and the home-screen widget need, kept outside the PIN-locked vault so a daily notification
 * can still say "3 years ago today: Goa Getaway" when Android starts the app in the background. Stored encrypted with
 * the Keystore key only (ReminderDigestStore). Holds titles and first names, never photos, descriptions or places.
 */
@Serializable
data class ReminderDigest(
    val generatedAtMillis: Long,
    val memories: List<DigestMemory>,
    val people: List<DigestPerson>,
)

@Serializable
data class DigestMemory(val id: Long, val title: String, val epochDay: Long)

@Serializable
data class DigestPerson(val id: Long, val name: String, val relation: Relation, val birthdayEpochDay: Long? = null)

data class OnThisDayMatch(val memory: DigestMemory, val yearsAgo: Int)

data class UpcomingBirthday(val person: DigestPerson, val inDays: Int, val turning: Int?)

/** A story prompt: [index] into the prompt list for the person's relation, about [person] (null = about the user). */
data class PromptPick(val index: Int, val person: DigestPerson?)

object Reminders {
    /** Memories kept per calendar day; the notification shows one, the widget a few. */
    const val MAX_PER_DAY = 3

    /** Prompt texts per relation (string arrays in the app). Sizes must match the resources. */
    const val FAMILY_PROMPTS = 12
    const val FRIEND_PROMPTS = 6
    const val SELF_PROMPTS = 8

    fun build(cards: List<MemoryCard>, people: List<Person>, nowMillis: Long): ReminderDigest {
        val memories = cards.asSequence()
            .filter { it.memory.date.kind == DateKind.EXACT }
            .groupBy { LocalDate.ofEpochDay(it.memory.date.startEpochDay).let { d -> d.monthValue * 100 + d.dayOfMonth } }
            .flatMap { (_, sameDay) -> sameDay.sortedByDescending { it.memory.date.startEpochDay }.take(MAX_PER_DAY) }
            .map { DigestMemory(it.memory.id, it.memory.title, it.memory.date.startEpochDay) }
        val persons = people.map { DigestPerson(it.id, it.displayName, it.relation, it.birthdayEpochDay) }
        return ReminderDigest(nowMillis, memories, persons)
    }

    /** Exact-day memories from this calendar day in earlier years, most recent first. */
    fun onThisDay(digest: ReminderDigest, today: LocalDate): List<OnThisDayMatch> =
        digest.memories.mapNotNull { m ->
            val d = LocalDate.ofEpochDay(m.epochDay)
            if (d.monthValue == today.monthValue && d.dayOfMonth == today.dayOfMonth && d.year < today.year) OnThisDayMatch(m, today.year - d.year) else null
        }.sortedBy { it.yearsAgo }

    /** Birthdays today (0) up to [daysAhead] days away. 29 February birthdays fall on 28 February in other years. */
    fun birthdays(digest: ReminderDigest, today: LocalDate, daysAhead: Int): List<UpcomingBirthday> =
        digest.people.mapNotNull { p ->
            val born = p.birthdayEpochDay?.let(LocalDate::ofEpochDay) ?: return@mapNotNull null
            val next = nextBirthday(born, today)
            val inDays = ChronoUnit.DAYS.between(today, next).toInt()
            if (inDays in 0..daysAhead) UpcomingBirthday(p, inDays, (next.year - born.year).takeIf { born.year > 1900 && it > 0 }) else null
        }.sortedBy { it.inDays }

    fun nextBirthday(born: LocalDate, today: LocalDate): LocalDate {
        fun inYear(y: Int): LocalDate = if (born.monthValue == 2 && born.dayOfMonth == 29 && !java.time.Year.isLeap(y.toLong())) LocalDate.of(y, 2, 28) else born.withYear(y)
        val thisYear = inYear(today.year)
        return if (thisYear.isBefore(today)) inYear(today.year + 1) else thisYear
    }

    /** Prompts go out on Sundays. */
    fun isPromptDay(today: LocalDate): Boolean = today.dayOfWeek == DayOfWeek.SUNDAY

    /**
     * The prompt for [today]'s week: rotates through family members first (their stories are the ones that get lost),
     * then friends, then questions about the user's own life. Deterministic, so the widget and the notification agree.
     */
    fun weeklyPrompt(digest: ReminderDigest, today: LocalDate): PromptPick {
        val week = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) + today.get(IsoFields.WEEK_BASED_YEAR) * 53
        val family = digest.people.filter { it.relation == Relation.FAMILY }.sortedBy { it.id }
        val friends = digest.people.filter { it.relation == Relation.FRIEND }.sortedBy { it.id }
        // Three weeks out of four ask about someone; every fourth is about the user.
        val slot = week % 4
        return when {
            slot != 3 && family.isNotEmpty() && (slot != 2 || friends.isEmpty()) ->
                PromptPick(index = (week / 4) % FAMILY_PROMPTS, person = family[(week / 4 + slot) % family.size])
            slot == 2 && friends.isNotEmpty() -> PromptPick(index = (week / 4) % FRIEND_PROMPTS, person = friends[(week / 4) % friends.size])
            else -> PromptPick(index = (week / 4) % SELF_PROMPTS, person = null)
        }
    }
}
