package com.yaadein.data.repository

import androidx.room.withTransaction
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.db.entity.GroupMemberEntity
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.Group
import com.yaadein.domain.model.Person
import com.yaadein.domain.model.PersonStory
import com.yaadein.domain.repository.GroupRepository
import com.yaadein.domain.repository.PersonRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val clock: () -> Long,
) : PersonRepository {
    private val dao get() = db.personDao()

    override fun observeAll(): Flow<List<Person>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override fun observe(id: Long): Flow<Person?> = dao.observe(id).map { it?.toDomain() }
    override suspend fun get(id: Long): Person? = dao.get(id)?.toDomain()

    override suspend fun upsert(person: Person): Long {
        val entity = person.toEntity().let { if (it.createdAt == 0L) it.copy(createdAt = clock()) else it }
        val id = dao.upsert(entity)
        return if (person.id != 0L) person.id else id
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    override fun observeStory(id: Long, todayEpochDay: Long): Flow<PersonStory?> {
        val today = LocalDate.ofEpochDay(todayEpochDay)
        return combine(
            dao.observe(id),
            dao.observeMemoryCount(id),
            dao.observeMediaCount(id),
            dao.observeFirstMemory(id),
            dao.observeLastMemory(id),
            db.groupDao().observeForPerson(id),
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            val person = (values[0] as? com.yaadein.data.db.entity.PersonEntity)?.toDomain() ?: return@combine null
            PersonStory(
                person = person,
                memoryCount = values[1] as Int,
                mediaCount = values[2] as Int,
                firstMemory = (values[3] as? com.yaadein.data.db.entity.MemoryEntity)?.toDomain(),
                lastMemory = (values[4] as? com.yaadein.data.db.entity.MemoryEntity)?.toDomain(),
                groups = (values[5] as List<com.yaadein.data.db.entity.GroupWithMembers>).map { it.toDomain() },
                daysToBirthday = person.birthdayEpochDay?.let { Dates.daysUntilBirthday(it, today) },
            )
        }
    }

    override suspend fun count(): Int = dao.count()

    override suspend fun coAppearances(): List<Triple<Long, Long, Int>> = dao.coAppearances().map { Triple(it.a, it.b, it.n) }

    override suspend fun memoryCounts(): Map<Long, Int> = dao.memoryCounts().associate { it.id to it.n }
}

@Singleton
class GroupRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val clock: () -> Long,
) : GroupRepository {
    private val dao get() = db.groupDao()

    override fun observeAll(): Flow<List<Group>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override fun observe(id: Long): Flow<Group?> = dao.observe(id).map { it?.toDomain() }

    override suspend fun upsert(group: Group): Long = db.withTransaction {
        val entity = group.toEntity().let { if (it.createdAt == 0L) it.copy(createdAt = clock()) else it }
        val rowId = dao.upsert(entity)
        val id = if (group.id != 0L) group.id else rowId
        dao.clearMembers(id)
        dao.insertMembers(group.memberIds.distinct().map { GroupMemberEntity(id, it) })
        id
    }

    override suspend fun delete(id: Long) = dao.delete(id)
}
