package com.yaadein.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.db.entity.MemoryPersonEntity
import com.yaadein.data.db.entity.MemorySearchEntity
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.MemoryDetail
import com.yaadein.domain.relive.CoverPick
import com.yaadein.domain.relive.Covers
import com.yaadein.domain.relive.Festivals
import com.yaadein.data.db.dao.CoverRow
import com.yaadein.domain.capture.KnownPlace
import com.yaadein.domain.repository.MemoryRepository
import com.yaadein.domain.repository.TimelineFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    /** Bundled festival calendar for [TimelineFilter.festival]; defaulted so tests can keep `MemoryRepositoryImpl(db) { now }`. */
    private val festivals: Festivals = Festivals.bundled(),
    private val clock: () -> Long,
) : MemoryRepository {
    private val memoryDao get() = db.memoryDao()
    private val mediaDao get() = db.mediaDao()
    private val personDao get() = db.personDao()

    override fun observeCards(filter: TimelineFilter): Flow<List<MemoryCard>> =
        memoryDao.observeRaw(buildQuery(filter)).map { rows -> rows.map { it.toCard() } }

    override fun pagedCards(filter: TimelineFilter): Flow<PagingData<MemoryCard>> =
        Pager(PagingConfig(pageSize = 24, prefetchDistance = 48, enablePlaceholders = false)) {
            memoryDao.pagingRaw(buildQuery(filter))
        }.flow.map { data -> data.map { it.toCard() } }

    override fun observeCard(id: Long): Flow<MemoryCard?> =
        memoryDao.observeWithRelations(id).map { it?.toCard() }

    override fun observeDetail(id: Long): Flow<MemoryDetail?> =
        memoryDao.observeWithRelations(id).map { it?.toDetail() }

    override suspend fun get(id: Long): Memory? = memoryDao.get(id)?.toDomain()

    override suspend fun create(memory: Memory, personIds: List<Long>): Long = db.withTransaction {
        val now = clock()
        val id = memoryDao.insert(memory.copy(createdAt = now, updatedAt = now).toEntity())
        memoryDao.insertPeople(personIds.distinct().map { MemoryPersonEntity(id, it) })
        reindex(id)
        id
    }

    override suspend fun update(memory: Memory, personIds: List<Long>) = db.withTransaction {
        val existing = memoryDao.get(memory.id) ?: return@withTransaction
        // The heart and the last-opened time have their own setters; an edit never resets them.
        memoryDao.update(memory.copy(createdAt = existing.createdAt, updatedAt = clock(), favorite = existing.favorite, lastOpenedAt = existing.lastOpenedAt).toEntity())
        memoryDao.clearPeople(memory.id)
        memoryDao.insertPeople(personIds.distinct().map { MemoryPersonEntity(memory.id, it) })
        reindex(memory.id)
    }

    override suspend fun delete(id: Long) = db.withTransaction {
        memoryDao.deleteSearch(id)
        memoryDao.delete(id)
    }

    override suspend fun count(): Int = memoryDao.count()
    override suspend fun countMedia(): Int = mediaDao.count()

    override suspend fun onThisDay(monthDay: Int, excludeYear: Int?): List<MemoryCard> =
        memoryDao.onThisDay(monthDay, excludeYear).map { it.toCard() }

    override suspend fun random(): MemoryCard? = memoryDao.random()?.toCard()

    override suspend fun inDayRanges(ranges: List<LongRange>): List<MemoryCard> {
        if (ranges.isEmpty()) return emptyList()
        val where = ranges.joinToString(" OR ") { "m.start_epoch_day BETWEEN ? AND ?" }
        val args = ranges.flatMap { listOf<Any>(it.first, it.last) }.toTypedArray()
        val query = SimpleSQLiteQuery("SELECT m.* FROM memories m WHERE $where ORDER BY m.start_epoch_day DESC, m.id DESC", args)
        return memoryDao.raw(query).map { it.toCard() }
    }

    override suspend fun yearCovers(): Map<Int, CoverPick> =
        Covers.pick(memoryDao.coverRows().map { it.year to it.toPick() })

    override suspend fun placeCovers(): Map<String, CoverPick> =
        Covers.pick(memoryDao.coverRows().filter { it.place.isNotBlank() }.map { it.place.trim().lowercase() to it.toPick() })

    private fun CoverRow.toPick() = CoverPick(memoryId = memoryId, mediaId = mediaId, coverStyle = coverStyle)

    override suspend fun countsByYear(): Map<Int, Int> = memoryDao.countsByYear().associate { it.year to it.n }

    override suspend fun cardsForYear(year: Int, personId: Long?): List<MemoryCard> {
        val from = LocalDate.of(year, 1, 1).toEpochDay()
        val to = LocalDate.of(year, 12, 31).toEpochDay()
        return memoryDao.inRange(from, to, personId).map { it.toCard() }
    }

    override suspend fun distinctPlaces(): List<String> = memoryDao.distinctPlaces()
    override suspend fun topPlaces(limit: Int): List<Pair<String, Int>> = memoryDao.placeCounts(limit).map { it.place to it.n }

    override suspend fun setCover(memoryId: Long, mediaId: Long?) = memoryDao.setCover(memoryId, mediaId, clock())

    override suspend fun search(query: String, limit: Int): List<MemoryCard> {
        val match = ftsQuery(query) ?: return emptyList()
        val ids = memoryDao.searchIds(match, limit)
        if (ids.isEmpty()) return emptyList()
        val order = ids.withIndex().associate { it.value to it.index }
        return memoryDao.getByIds(ids).map { it.toCard() }.sortedBy { order[it.memory.id] ?: Int.MAX_VALUE }
    }

    override suspend fun details(ids: List<Long>): List<MemoryDetail> {
        if (ids.isEmpty()) return emptyList()
        return ids.chunked(500).flatMap { chunk -> memoryDao.getByIds(chunk).map { it.toDetail() } }
    }

    override suspend fun idsFor(filter: TimelineFilter): List<Long> = memoryDao.raw(buildQuery(filter)).map { it.memory.id }

    override suspend fun knownPlaces(): List<KnownPlace> =
        mediaDao.locatedPlaces(KNOWN_PLACES_LIMIT).map { KnownPlace(it.place.trim(), it.latitude, it.longitude) }.distinct()

    override suspend fun setFavorite(id: Long, favorite: Boolean) = memoryDao.setFavorite(id, favorite)

    override suspend fun markOpened(id: Long) = memoryDao.setLastOpened(id, clock())

    override suspend fun datedPhotosOfPerson(personId: Long): List<com.yaadein.domain.relive.DatedPhoto> =
        mediaDao.datedPhotosOfPerson(personId, THEN_NOW_LIMIT).map { com.yaadein.domain.relive.DatedPhoto(it.mediaId, it.memoryId, it.epochDay) }

    override suspend fun photosShowingPerson(personId: Long): Map<Long, List<Long>> =
        mediaDao.photosShowingPerson(personId, THEN_NOW_LIMIT).groupBy({ it.memoryId }, { it.mediaId })

    override suspend fun memoriesWithFaces(): Set<Long> = mediaDao.memoryIdsWithFaces().toSet()

    override suspend fun datedPhotosAtPlace(place: String): List<com.yaadein.domain.relive.DatedPhoto> =
        mediaDao.datedPhotosAtPlace(place, THEN_NOW_LIMIT).map { com.yaadein.domain.relive.DatedPhoto(it.mediaId, it.memoryId, it.epochDay) }

    /** Rebuilds the FTS row for a memory from title, description, place, captions and tagged names. */
    suspend fun reindex(memoryId: Long) {
        val row = memoryDao.getWithRelations(memoryId) ?: return
        val captions = row.media.map { it.caption }.filter { it.isNotBlank() }
        val names = row.people.flatMap { listOfNotNull(it.name, it.nickname) }
        val body = (listOf(row.memory.title, row.memory.description, row.memory.place, row.memory.fuzzyLabel ?: "") + captions + names)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        memoryDao.upsertSearch(MemorySearchEntity(rowId = memoryId, body = body))
    }

    suspend fun reindexAll() = db.withTransaction {
        memoryDao.allIds().forEach { reindex(it) }
    }

    private fun buildQuery(filter: TimelineFilter): SimpleSQLiteQuery {
        val where = ArrayList<String>()
        val args = ArrayList<Any>()
        if (filter.personIds.isNotEmpty()) {
            val ids = filter.personIds.toList()
            val placeholders = ids.joinToString(",") { "?" }
            if (filter.requireAllPeople) {
                where += "(SELECT COUNT(DISTINCT person_id) FROM memory_persons mp WHERE mp.memory_id = m.id AND mp.person_id IN ($placeholders)) = ?"
                args.addAll(ids); args.add(ids.size)
            } else {
                where += "m.id IN (SELECT memory_id FROM memory_persons WHERE person_id IN ($placeholders))"
                args.addAll(ids)
            }
        }
        filter.groupId?.let {
            where += "m.id IN (SELECT memory_id FROM memory_persons WHERE person_id IN (SELECT person_id FROM group_members WHERE group_id = ?))"
            args += it
        }
        filter.place?.takeIf { it.isNotBlank() }?.let {
            where += "m.place LIKE ? COLLATE NOCASE"; args += "%${it.trim()}%"
        }
        filter.fromEpochDay?.let { where += "COALESCE(m.end_epoch_day, m.start_epoch_day) >= ?"; args += it }
        filter.toEpochDay?.let { where += "m.start_epoch_day <= ?"; args += it }
        if (filter.types.isNotEmpty()) {
            where += "m.type IN (${filter.types.joinToString(",") { "?" }})"
            args.addAll(filter.types.map { it.name })
        }
        ftsQuery(filter.query)?.let {
            where += "m.id IN (SELECT rowid FROM memory_search WHERE memory_search MATCH ?)"; args += it
        }
        filter.festival?.let { festival ->
            // Every year's window of the festival (e.g. Diwali ± 3 days, 2010–2035); a festival without dates matches nothing.
            val windows = festivals.windows(festival)
            if (windows.isEmpty()) {
                where += "0"
            } else {
                where += windows.joinToString(" OR ", prefix = "(", postfix = ")") { "m.start_epoch_day BETWEEN ? AND ?" }
                windows.forEach { args += it.first; args += it.last }
            }
        }
        val sql = buildString {
            append("SELECT m.* FROM memories m")
            if (where.isNotEmpty()) append(" WHERE ").append(where.joinToString(" AND "))
            append(" ORDER BY m.start_epoch_day DESC, m.id DESC")
        }
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    companion object {
        /** Most located photos considered for "nearest known place" (one row per photo with GPS). */
        const val KNOWN_PLACES_LIMIT = 2000
        /** Most photos looked at for one Then & Now pair (oldest first, so the early years are always in). */
        const val THEN_NOW_LIMIT = 4000

        /** Turns free text into an FTS prefix query: "goa tr" → "goa* tr*". Returns null for blank input. */
        fun ftsQuery(text: String): String? {
            val all = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
            // Drop one-letter fragments such as the "s" in "rahul's" unless that is all we have.
            val tokens = all.filter { it.length > 1 }.ifEmpty { all }
            if (tokens.isEmpty()) return null
            return tokens.joinToString(" ") { "$it*" }
        }
    }
}
