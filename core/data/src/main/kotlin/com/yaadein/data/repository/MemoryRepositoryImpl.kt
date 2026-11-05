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
        memoryDao.update(memory.copy(createdAt = existing.createdAt, updatedAt = clock()).toEntity())
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

    override suspend fun countsByYear(): Map<Int, Int> = memoryDao.countsByYear().associate { it.year to it.n }

    override suspend fun cardsForYear(year: Int, personId: Long?): List<MemoryCard> {
        val from = LocalDate.of(year, 1, 1).toEpochDay()
        val to = LocalDate.of(year, 12, 31).toEpochDay()
        return memoryDao.inRange(from, to, personId).map { it.toCard() }
    }

    override suspend fun distinctPlaces(): List<String> = memoryDao.distinctPlaces()

    override suspend fun setCover(memoryId: Long, mediaId: Long?) = memoryDao.setCover(memoryId, mediaId, clock())

    override suspend fun search(query: String, limit: Int): List<MemoryCard> {
        val match = ftsQuery(query) ?: return emptyList()
        val ids = memoryDao.searchIds(match, limit)
        if (ids.isEmpty()) return emptyList()
        val order = ids.withIndex().associate { it.value to it.index }
        return memoryDao.getByIds(ids).map { it.toCard() }.sortedBy { order[it.memory.id] ?: Int.MAX_VALUE }
    }

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
        val sql = buildString {
            append("SELECT m.* FROM memories m")
            if (where.isNotEmpty()) append(" WHERE ").append(where.joinToString(" AND "))
            append(" ORDER BY m.start_epoch_day DESC, m.id DESC")
        }
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    companion object {
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
