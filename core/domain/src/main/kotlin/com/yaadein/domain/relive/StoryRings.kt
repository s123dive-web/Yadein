package com.yaadein.domain.relive

import com.yaadein.domain.model.Person
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What a story ring at the top of Relive is about. */
enum class RingKind { TODAY, PERSON, YEAR, FESTIVAL, PLACE }

/**
 * One ring: a story that opens full screen with one tap. [key] is stable for the day's seen state and for navigation
 * ("today", "person-12", "year-2026", "festival-diwali", "place-pune").
 */
data class StoryRing(
    val kind: RingKind,
    val key: String,
    val person: Person? = null,
    val year: Int? = null,
    val festival: Festival? = null,
    /** For a festival ring: days until the festival, 0 while it is on. */
    val festivalInDays: Int = 0,
    val place: String? = null,
)

/** A place that can have a ring: its name as written and how many memories happened there. */
data class RingPlace(val place: String, val memories: Int)

/**
 * Chooses the rings: Today, up to three people (rotating daily among those with enough memories with photos), this
 * year, the festival that is on or coming up, and one place. Every choice is the same all day.
 */
object StoryRings {
    const val MAX_PEOPLE = 3
    /** A person needs this many memories with a photo or video to carry a story. */
    const val PERSON_MIN_PHOTO_MEMORIES = 3
    /** A festival gets a ring from this many days before it. */
    const val FESTIVAL_AHEAD_DAYS = 14
    /** A place needs a few memories, and the ring rotates among the top few places. */
    const val PLACE_MIN_MEMORIES = 2
    const val PLACE_CANDIDATES = 6

    fun build(
        today: LocalDate,
        hasToday: Boolean,
        people: List<Person>,
        photoMemoryCounts: Map<Long, Int>,
        memoriesThisYear: Int,
        festival: FestivalDate?,
        festivalHasMemories: Boolean,
        places: List<RingPlace>,
    ): List<StoryRing> = buildList {
        if (hasToday) add(StoryRing(RingKind.TODAY, TODAY_KEY))
        for (person in people(people, photoMemoryCounts, today)) add(StoryRing(RingKind.PERSON, personKey(person.id), person = person))
        if (memoriesThisYear > 0) add(StoryRing(RingKind.YEAR, yearKey(today.year), year = today.year))
        if (festival != null && festivalHasMemories) {
            val inDays = ChronoUnit.DAYS.between(today, festival.date).toInt().coerceAtLeast(0)
            add(StoryRing(RingKind.FESTIVAL, festivalKey(festival.festival), festival = festival.festival, festivalInDays = inDays))
        }
        place(places, today)?.let { add(StoryRing(RingKind.PLACE, placeKey(it), place = it)) }
    }

    /** Up to [MAX_PEOPLE] people with enough photo memories; the window moves on by one person every day. */
    fun people(people: List<Person>, photoMemoryCounts: Map<Long, Int>, today: LocalDate): List<Person> {
        val eligible = people.filter { (photoMemoryCounts[it.id] ?: 0) >= PERSON_MIN_PHOTO_MEMORIES }.sortedBy { it.id }
        if (eligible.size <= MAX_PEOPLE) return eligible
        val start = Math.floorMod(today.toEpochDay(), eligible.size.toLong()).toInt()
        return List(MAX_PEOPLE) { eligible[(start + it) % eligible.size] }
    }

    /** The festival whose window holds [today], else the next one within [FESTIVAL_AHEAD_DAYS] days, else null. */
    fun festival(festivals: Festivals, today: LocalDate): FestivalDate? =
        festivals.primary(today) ?: festivals.next(today, limit = 1).firstOrNull()
            ?.takeIf { ChronoUnit.DAYS.between(today, it.date) <= FESTIVAL_AHEAD_DAYS }

    /** Today's place among the [PLACE_CANDIDATES] places with the most memories (at least [PLACE_MIN_MEMORIES] each). */
    fun place(places: List<RingPlace>, today: LocalDate): String? {
        val eligible = places.filter { it.place.isNotBlank() && it.memories >= PLACE_MIN_MEMORIES }
            .sortedWith(compareByDescending<RingPlace> { it.memories }.thenBy { it.place.lowercase() })
            .take(PLACE_CANDIDATES)
        if (eligible.isEmpty()) return null
        return eligible[Math.floorMod(today.toEpochDay(), eligible.size.toLong()).toInt()].place
    }

    /**
     * One cover per ring so that no two rings show the same picture: each ring takes its first candidate (in its own
     * order of preference) that no earlier ring has taken. A ring whose candidates are all taken gets null, and the
     * caller decides what stands in (a person's avatar, cover art). Candidates without a photo are skipped.
     */
    fun distinctCovers(candidates: List<List<CoverPick>>): List<CoverPick?> {
        val taken = HashSet<Long>()
        return candidates.map { options ->
            options.firstOrNull { it.mediaId != null && it.mediaId !in taken }?.also { taken += it.mediaId!! }
        }
    }

    const val TODAY_KEY = "today"
    fun personKey(id: Long) = "person-$id"
    fun yearKey(year: Int) = "year-$year"
    fun festivalKey(festival: Festival) = "festival-${festival.key}"
    fun placeKey(place: String) = "place-${place.trim().lowercase()}"
}

/**
 * Which rings were watched today, kept as one small string in the settings: `<epochDay>|key1\nkey2`. A new day starts
 * empty, so every ring is "unseen today" again each morning.
 */
object StorySeen {
    fun seenToday(stored: String, today: LocalDate): Set<String> {
        val bar = stored.indexOf('|')
        if (bar <= 0 || stored.substring(0, bar).toLongOrNull() != today.toEpochDay()) return emptySet()
        return stored.substring(bar + 1).split('\n').filter { it.isNotEmpty() }.toSet()
    }

    fun isSeen(stored: String, key: String, today: LocalDate): Boolean = key in seenToday(stored, today)

    /** [stored] with [key] marked as watched today (yesterday's keys are dropped). */
    fun mark(stored: String, key: String, today: LocalDate): String =
        "${today.toEpochDay()}|" + (seenToday(stored, today) + key.replace("\n", " ")).sorted().joinToString("\n")
}
