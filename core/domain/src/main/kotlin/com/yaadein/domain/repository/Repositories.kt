package com.yaadein.domain.repository

import com.yaadein.domain.model.DateKind
import com.yaadein.domain.model.Group
import com.yaadein.domain.model.Media
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.MemoryDetail
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.model.Person
import com.yaadein.domain.model.PersonStory
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
) {
    val isEmpty: Boolean
        get() = personIds.isEmpty() && groupId == null && place.isNullOrBlank() &&
            fromEpochDay == null && toEpochDay == null && types.isEmpty() && query.isBlank()
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
    /** Year → number of memories, for the "Fill in the past" wizard and yearly summaries. */
    suspend fun countsByYear(): Map<Int, Int>
    suspend fun cardsForYear(year: Int, personId: Long? = null): List<MemoryCard>
    suspend fun distinctPlaces(): List<String>
    suspend fun setCover(memoryId: Long, mediaId: Long?)
    suspend fun search(query: String, limit: Int = 50): List<MemoryCard>
    suspend fun details(ids: List<Long>): List<MemoryDetail>
    suspend fun idsFor(filter: TimelineFilter): List<Long>
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

interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun current(): UserSettings
    suspend fun update(transform: (UserSettings) -> UserSettings)
}

/** Helpers shared by repositories and the UI for fuzzy-date presentation. */
object MemoryDates {
    fun isFuzzy(kind: DateKind) = kind != DateKind.EXACT
}
