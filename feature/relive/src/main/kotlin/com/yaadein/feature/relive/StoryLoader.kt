package com.yaadein.feature.relive

import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.Person
import com.yaadein.domain.relive.CoverPick
import com.yaadein.domain.relive.DailyStory
import com.yaadein.domain.relive.Festivals
import com.yaadein.domain.relive.Milestones
import com.yaadein.domain.relive.Rewind
import com.yaadein.domain.relive.RingKind
import com.yaadein.domain.relive.RingPlace
import com.yaadein.domain.relive.StoryPage
import com.yaadein.domain.relive.StoryPicks
import com.yaadein.domain.relive.StoryRing
import com.yaadein.domain.relive.StoryRings
import com.yaadein.domain.relive.StorySeen
import com.yaadein.domain.relive.ThenAndNow
import com.yaadein.domain.repository.MemoryRepository
import com.yaadein.domain.repository.PersonRepository
import com.yaadein.domain.repository.TimelineFilter
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject

/** A ring as Relive draws it: what it is about, the picture inside it and whether it was watched today. */
data class RingUi(val ring: StoryRing, val cover: CoverPick?, val seen: Boolean)

/**
 * Loads the story rings and the pages of each story. The choices themselves (who gets a ring, which memories a story
 * shows, Then & Now pairs, milestones) are the pure rules in core/domain/relive; this class only fetches what they need.
 */
class StoryLoader @Inject constructor(
    private val memoryRepository: MemoryRepository,
    private val personRepository: PersonRepository,
    private val festivals: Festivals,
    private val clock: () -> Long,
) {
    suspend fun rings(today: LocalDate, seenStored: String): List<RingUi> {
        val total = memoryRepository.count()
        if (total == 0) return emptyList()
        val people = personRepository.observeAll().first()
        val festival = StoryRings.festival(festivals, today)
        val festivalCards = festival?.let { memoryRepository.inDayRanges(festivals.windows(it.festival)) }.orEmpty()
        val rings = StoryRings.build(
            today = today,
            hasToday = true,
            people = people,
            photoMemoryCounts = personRepository.photoMemoryCounts(),
            memoriesThisYear = memoryRepository.countsByYear()[today.year] ?: 0,
            festival = festival,
            festivalHasMemories = festivalCards.isNotEmpty(),
            places = memoryRepository.topPlaces().map { (place, n) -> RingPlace(place, n) },
        )
        val yearCovers = runCatching { memoryRepository.yearCovers() }.getOrDefault(emptyMap())
        val placeCovers = runCatching { memoryRepository.placeCovers() }.getOrDefault(emptyMap())
        val seen = StorySeen.seenToday(seenStored, today)
        return rings.map { ring ->
            val cover = when (ring.kind) {
                RingKind.TODAY -> yearCovers.maxByOrNull { it.key }?.value
                RingKind.PERSON -> ring.person?.let { person -> cardsOf(person).let { cards -> cards.firstOrNull { it.cover != null } ?: cards.firstOrNull() } }?.toCover()
                RingKind.YEAR -> yearCovers[ring.year]
                RingKind.FESTIVAL -> (festivalCards.firstOrNull { it.cover != null } ?: festivalCards.firstOrNull())?.toCover()
                RingKind.PLACE -> ring.place?.let { placeCovers[it.trim().lowercase()] }
            }
            RingUi(ring, cover, seen = ring.key in seen)
        }
    }

    /** The pages of [ring]'s story for [today]; empty when there is nothing to show. */
    suspend fun pages(ring: StoryRing, today: LocalDate): List<StoryPage> {
        val now = clock()
        return when (ring.kind) {
            RingKind.TODAY -> todayPages(today, now)
            RingKind.PERSON -> ring.person?.let { personPages(it, today, now) }.orEmpty()
            RingKind.YEAR -> moments(memoryRepository.cardsForYear(ring.year ?: today.year), today, now)
            RingKind.FESTIVAL -> ring.festival?.let { moments(memoryRepository.inDayRanges(festivals.windows(it)), today, now) }.orEmpty()
            RingKind.PLACE -> ring.place?.let { placePages(it, today, now) }.orEmpty()
        }
    }

    private fun moments(cards: List<MemoryCard>, today: LocalDate, now: Long, limit: Int = STORY_MEMORIES): List<StoryPage> =
        StoryPicks.choose(cards, limit, today, now).map { StoryPage.Moment(it, yearsAgo = null) }

    private suspend fun cardsOf(person: Person): List<MemoryCard> =
        memoryRepository.observeCards(TimelineFilter(personIds = setOf(person.id))).first()

    /**
     * Today: memories from years ago, a person spotlight, the memory of the day (leaning towards favourites and recently
     * opened memories), a Then & Now pair when someone or somewhere has one, and any milestone that falls today.
     */
    private suspend fun todayPages(today: LocalDate, now: Long): List<StoryPage> {
        val counts = memoryRepository.countsByYear()
        val rewind = counts.keys.minOrNull()?.let { earliest -> Rewind.pick(memoryRepository.inDayRanges(Rewind.windows(today, earliest)), today) }
        val people = personRepository.observeAll().first()
        val memoryCounts = personRepository.memoryCounts()
        val spotlightPerson = DailyStory.spotlightPerson(people, memoryCounts, today)
        val spotlight = spotlightPerson?.let { person ->
            val cards = cardsOf(person)
            StoryPage.Spotlight(person, memoryCounts[person.id] ?: cards.size, cards.firstOrNull { it.cover != null } ?: cards.firstOrNull())
        }
        val all = memoryRepository.observeCards(TimelineFilter()).first()
        val ofTheDay = StoryPicks.order(all, today, now).firstOrNull()
        val base = DailyStory.build(rewind, spotlight, ofTheDay)
        return buildList {
            addAll(base)
            // Then & Now: the spotlight person first, then anyone with a ring, then today's place.
            val candidates = listOfNotNull(spotlightPerson) + StoryRings.people(people, personRepository.photoMemoryCounts(), today)
            val personPair = candidates.distinctBy { it.id }.firstNotNullOfOrNull { person ->
                ThenAndNow.pick(memoryRepository.datedPhotosOfPerson(person.id), today)?.let { StoryPage.ThenNow(it, person = person) }
            }
            val pair = personPair ?: StoryRings.place(memoryRepository.topPlaces().map { (place, n) -> RingPlace(place, n) }, today)?.let { place ->
                ThenAndNow.pick(memoryRepository.datedPhotosAtPlace(place), today)?.let { StoryPage.ThenNow(it, place = place) }
            }
            pair?.let { add(it) }
            Milestones.memoryCount(all.size)?.let { add(StoryPage.Milestone(it, all.firstOrNull { c -> c.cover != null } ?: all.firstOrNull())) }
            for (person in candidates.distinctBy { it.id }) {
                val story = personRepository.observeStory(person.id, today.toEpochDay()).first() ?: continue
                val milestone = Milestones.anniversaryToday(person.id, story.firstMemory?.date?.startEpochDay, today) ?: continue
                add(StoryPage.Milestone(milestone, cardsOf(person).firstOrNull { it.cover != null }, person = person))
                break
            }
        }
    }

    private suspend fun personPages(person: Person, today: LocalDate, now: Long): List<StoryPage> {
        val cards = cardsOf(person)
        return buildList {
            addAll(moments(cards, today, now, limit = PERSON_MEMORIES))
            ThenAndNow.pick(memoryRepository.datedPhotosOfPerson(person.id), today)?.let { add(StoryPage.ThenNow(it, person = person)) }
            val first = personRepository.observeStory(person.id, today.toEpochDay()).first()?.firstMemory?.date?.startEpochDay
            Milestones.yearsWith(person.id, first, today)?.let { add(StoryPage.Milestone(it, cards.lastOrNull { c -> c.cover != null } ?: cards.lastOrNull(), person = person)) }
        }
    }

    private suspend fun placePages(place: String, today: LocalDate, now: Long): List<StoryPage> {
        val wanted = place.trim().lowercase()
        // The timeline's place filter is a "contains" search; a place ring is about exactly this place.
        val cards = memoryRepository.observeCards(TimelineFilter(place = place)).first().filter { it.memory.place.trim().lowercase() == wanted }
        return buildList {
            addAll(moments(cards, today, now))
            ThenAndNow.pick(memoryRepository.datedPhotosAtPlace(place), today)?.let { add(StoryPage.ThenNow(it, place = place)) }
        }
    }

    private fun MemoryCard.toCover() = CoverPick(memoryId = memory.id, mediaId = cover?.id, coverStyle = memory.coverStyle)

    companion object {
        /** Memories in a year, festival or place story. */
        const val STORY_MEMORIES = 8
        /** Memories in a person's story (their Then & Now and "years with" pages follow). */
        const val PERSON_MEMORIES = 6
    }
}
