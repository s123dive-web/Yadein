package com.yaadein.domain.repository

import com.yaadein.domain.capture.CaptureStat
import com.yaadein.domain.capture.KnownPlace
import com.yaadein.domain.model.DateKind
import com.yaadein.domain.model.Group
import com.yaadein.domain.model.Media
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.MemoryDetail
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.model.Person
import com.yaadein.domain.model.PersonStory
import com.yaadein.domain.relive.Festival
import com.yaadein.domain.settings.UserSettings
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

/** Filters applied to the timeline. All fields optional; empty filter = everything. */
data class TimelineFilter(
    val personIds: Set<Long> = emptySet(),
    /** When true, every person in [personIds] must appear (Together view). */
    val requireAllPeople: Boolean = false,
    val groupId: Long? = null,
    val place: String? = null,
    val fromEpochDay: Long? = null,
    val toEpochDay: Long? = null,
    val types: Set<MemoryType> = emptySet(),
    val query: String = "",
    /** Only memories dated within this festival's window in any year (Festivals.windows). */
    val festival: Festival? = null,
) {
    val isEmpty: Boolean
        get() = personIds.isEmpty() && groupId == null && place.isNullOrBlank() &&
            fromEpochDay == null && toEpochDay == null && types.isEmpty() && query.isBlank() && festival == null
}

interface PersonRepository {
    fun observeAll(): Flow<List<Person>>
    fun observe(id: Long): Flow<Person?>
    suspend fun get(id: Long): Person?
    suspend fun upsert(person: Person): Long
    suspend fun delete(id: Long)
    fun observeStory(id: Long, todayEpochDay: Long): Flow<PersonStory?>
    suspend fun count(): Int
    /** Pairs of person ids with the number of memories they share; for the relationships map. */
    suspend fun coAppearances(): List<Triple<Long, Long, Int>>
    suspend fun memoryCounts(): Map<Long, Int>
    /** People tagged on the most recently created memories, most recent first; Quick Save offers them as chips. */
    suspend fun recentlyTagged(limit: Int): List<Person>
    /** Person id → number of their memories that have a photo or video; a story ring needs a few of these. */
    suspend fun photoMemoryCounts(): Map<Long, Int>
}

interface GroupRepository {
    fun observeAll(): Flow<List<Group>>
    fun observe(id: Long): Flow<Group?>
    suspend fun upsert(group: Group): Long
    suspend fun delete(id: Long)
}

interface MemoryRepository {
    /** Cards ordered newest first; the paging source is the UI's responsibility to wrap. */
    fun observeCards(filter: TimelineFilter = TimelineFilter()): Flow<List<MemoryCard>>
    /** Paged timeline, newest first, for large libraries. */
    fun pagedCards(filter: TimelineFilter = TimelineFilter()): Flow<PagingData<MemoryCard>>
    fun observeCard(id: Long): Flow<MemoryCard?>
    fun observeDetail(id: Long): Flow<MemoryDetail?>
    suspend fun get(id: Long): Memory?
    suspend fun create(memory: Memory, personIds: List<Long>): Long
    suspend fun update(memory: Memory, personIds: List<Long>)
    suspend fun delete(id: Long)
    suspend fun count(): Int
    suspend fun countMedia(): Int
    /** Memories whose month/day matches today (any year), for "On this day". */
    suspend fun onThisDay(monthDay: Int, excludeYear: Int? = null): List<MemoryCard>
    suspend fun random(): MemoryCard?
    /** Memories whose start day falls in any of [ranges] (epoch days), newest first; feeds [com.yaadein.domain.relive.Rewind]. */
    suspend fun inDayRanges(ranges: List<LongRange>): List<MemoryCard>
    /** One cover per year (newest memory with a photo or video, else the newest memory), for Relive's year tiles. */
    suspend fun yearCovers(): Map<Int, com.yaadein.domain.relive.CoverPick>
    /** One cover per place, keyed by the place lower-cased and trimmed, for Relive's place tiles. */
    suspend fun placeCovers(): Map<String, com.yaadein.domain.relive.CoverPick>
    /** Year → number of memories, for the "Fill in the past" wizard and yearly summaries. */
    suspend fun countsByYear(): Map<Int, Int>
    suspend fun cardsForYear(year: Int, personId: Long? = null): List<MemoryCard>
    suspend fun distinctPlaces(): List<String>
    /** Places with the most memories, most first. */
    suspend fun topPlaces(limit: Int = 12): List<Pair<String, Int>>
    suspend fun setCover(memoryId: Long, mediaId: Long?)
    suspend fun search(query: String, limit: Int = 50): List<MemoryCard>
    suspend fun details(ids: List<Long>): List<MemoryDetail>
    suspend fun idsFor(filter: TimelineFilter): List<Long>
    /** Every place name in the vault with a GPS position from one of its photos (for Quick Save's place suggestion). */
    suspend fun knownPlaces(): List<KnownPlace>
    suspend fun setFavorite(id: Long, favorite: Boolean)
    /** Remembers that the memory's page was opened now (stories lean towards recently opened memories). */
    suspend fun markOpened(id: Long)
    /** Photos of memories [personId] is tagged in (or photos tagged with them), with their memory's day; for Then & Now. */
    suspend fun datedPhotosOfPerson(personId: Long): List<com.yaadein.domain.relive.DatedPhoto>
    /** Photos of memories at [place] (compared trimmed, ignoring case), with their memory's day; for Then & Now. */
    suspend fun datedPhotosAtPlace(place: String): List<com.yaadein.domain.relive.DatedPhoto>
}

interface MediaRepository {
    fun observeForMemory(memoryId: Long): Flow<List<Media>>
    suspend fun get(id: Long): Media?
    suspend fun forPerson(personId: Long): List<Media>
    suspend fun updateCaption(id: Long, caption: String)
    suspend fun setPeople(mediaId: Long, personIds: List<Long>)
    suspend fun delete(id: Long)
    suspend fun totalBytes(): Long
    suspend fun reorder(memoryId: Long, orderedMediaIds: List<Long>)
}

/** Capture timings kept on the phone only (see [CaptureStat]). */
interface CaptureStatsRepository {
    suspend fun record(stat: CaptureStat): Long
    /** Sets the Save → last item encrypted duration once the background import of a capture finishes. */
    suspend fun markEncrypted(id: Long, saveToEncryptedMs: Long)
    /** Newest first. */
    suspend fun recent(limit: Int): List<CaptureStat>
}

/** How many photos of a just-saved memory are still being encrypted, and how many failed; drives the card's progress ring. */
data class MemoryImportProgress(
    val total: Int,
    val done: Int,
    val failures: List<ImportFailureKind> = emptyList(),
) {
    val running: Boolean get() = done < total
    val fraction: Float get() = if (total == 0) 1f else done.toFloat() / total
}

/** Why a photo or video could not be added, in domain terms so every screen can explain it. */
enum class ImportFailureKind { TOO_LARGE, UNSUPPORTED, UNREADABLE, INTEGRITY_CHECK_FAILED, NOT_ENOUGH_SPACE }

/** Background import progress per memory, observable by the timeline. */
interface ImportProgressSource {
    fun observeAll(): Flow<Map<Long, MemoryImportProgress>>
    /** Forgets the failures recorded for [memoryId] (the user has seen them). */
    fun dismissFailures(memoryId: Long)
}

interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun current(): UserSettings
    suspend fun update(transform: (UserSettings) -> UserSettings)
}

/** Helpers shared by repositories and the UI for fuzzy-date presentation. */
object MemoryDates {
    fun isFuzzy(kind: DateKind) = kind != DateKind.EXACT
}
