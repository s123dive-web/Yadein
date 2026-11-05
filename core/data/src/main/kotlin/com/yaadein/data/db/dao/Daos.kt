package com.yaadein.data.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.yaadein.data.db.entity.FaceClusterEntity
import com.yaadein.data.db.entity.GroupEntity
import com.yaadein.data.db.entity.GroupMemberEntity
import com.yaadein.data.db.entity.GroupWithMembers
import com.yaadein.data.db.entity.IdCount
import com.yaadein.data.db.entity.ImportJobEntity
import com.yaadein.data.db.entity.MediaEntity
import com.yaadein.data.db.entity.MediaPersonEntity
import com.yaadein.data.db.entity.MemoryEntity
import com.yaadein.data.db.entity.MemoryPersonEntity
import com.yaadein.data.db.entity.MemorySearchEntity
import com.yaadein.data.db.entity.MemoryWithRelations
import com.yaadein.data.db.entity.PairCount
import com.yaadein.data.db.entity.PersonEntity
import com.yaadein.data.db.entity.YearCount
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM persons ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM persons ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<PersonEntity>

    @Query("SELECT * FROM persons WHERE id = :id")
    fun observe(id: Long): Flow<PersonEntity?>

    @Query("SELECT * FROM persons WHERE id = :id")
    suspend fun get(id: Long): PersonEntity?

    @Query("SELECT * FROM persons WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<PersonEntity>

    @Upsert
    suspend fun upsert(person: PersonEntity): Long

    @Query("DELETE FROM persons WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM persons")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM persons")
    fun observeCount(): Flow<Int>

    @Query("SELECT person_id AS id, COUNT(*) AS n FROM memory_persons GROUP BY person_id")
    suspend fun memoryCounts(): List<IdCount>

    @Query("SELECT person_id AS id, COUNT(*) AS n FROM memory_persons GROUP BY person_id")
    fun observeMemoryCounts(): Flow<List<IdCount>>

    @Query(
        """
        SELECT a.person_id AS a, b.person_id AS b, COUNT(*) AS n
        FROM memory_persons a JOIN memory_persons b
          ON a.memory_id = b.memory_id AND a.person_id < b.person_id
        GROUP BY a.person_id, b.person_id
        ORDER BY n DESC
        """,
    )
    suspend fun coAppearances(): List<PairCount>

    @Query(
        """
        SELECT m.* FROM memories m JOIN memory_persons mp ON mp.memory_id = m.id
        WHERE mp.person_id = :personId ORDER BY m.start_epoch_day ASC, m.id ASC LIMIT 1
        """,
    )
    fun observeFirstMemory(personId: Long): Flow<MemoryEntity?>

    @Query(
        """
        SELECT m.* FROM memories m JOIN memory_persons mp ON mp.memory_id = m.id
        WHERE mp.person_id = :personId ORDER BY m.start_epoch_day DESC, m.id DESC LIMIT 1
        """,
    )
    fun observeLastMemory(personId: Long): Flow<MemoryEntity?>

    @Query("SELECT COUNT(*) FROM memory_persons WHERE person_id = :personId")
    fun observeMemoryCount(personId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_persons WHERE person_id = :personId")
    fun observeMediaCount(personId: Long): Flow<Int>

    @Query("SELECT * FROM persons WHERE birthday_epoch_day IS NOT NULL")
    suspend fun withBirthdays(): List<PersonEntity>
}

@Dao
interface GroupDao {
    @Transaction
    @Query("SELECT * FROM groups ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<GroupWithMembers>>

    @Transaction
    @Query("SELECT * FROM groups WHERE id = :id")
    fun observe(id: Long): Flow<GroupWithMembers?>

    @Transaction
    @Query("SELECT * FROM groups WHERE id IN (SELECT group_id FROM group_members WHERE person_id = :personId) ORDER BY name COLLATE NOCASE")
    fun observeForPerson(personId: Long): Flow<List<GroupWithMembers>>

    @Upsert
    suspend fun upsert(group: GroupEntity): Long

    @Query("DELETE FROM group_members WHERE group_id = :groupId")
    suspend fun clearMembers(groupId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMembers(members: List<GroupMemberEntity>)

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT person_id FROM group_members WHERE group_id = :groupId")
    suspend fun memberIds(groupId: Long): List<Long>
}

@Dao
interface MemoryDao {
    @Transaction
    @RawQuery(observedEntities = [MemoryEntity::class, MemoryPersonEntity::class, MediaEntity::class, PersonEntity::class, MemorySearchEntity::class])
    fun observeRaw(query: SupportSQLiteQuery): Flow<List<MemoryWithRelations>>

    @Transaction
    @RawQuery(observedEntities = [MemoryEntity::class, MemoryPersonEntity::class, MediaEntity::class, PersonEntity::class])
    suspend fun raw(query: SupportSQLiteQuery): List<MemoryWithRelations>

    @Transaction
    @RawQuery(observedEntities = [MemoryEntity::class, MemoryPersonEntity::class, MediaEntity::class, PersonEntity::class, MemorySearchEntity::class])
    fun pagingRaw(query: SupportSQLiteQuery): PagingSource<Int, MemoryWithRelations>

    @Transaction
    @Query("SELECT * FROM memories WHERE id = :id")
    fun observeWithRelations(id: Long): Flow<MemoryWithRelations?>

    @Transaction
    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getWithRelations(id: Long): MemoryWithRelations?

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun get(id: Long): MemoryEntity?

    @Insert
    suspend fun insert(memory: MemoryEntity): Long

    @Update
    suspend fun update(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM memories")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM memories")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM memory_persons WHERE memory_id = :memoryId")
    suspend fun clearPeople(memoryId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPeople(rows: List<MemoryPersonEntity>)

    @Query("SELECT person_id FROM memory_persons WHERE memory_id = :memoryId")
    suspend fun personIds(memoryId: Long): List<Long>

    @Query("UPDATE memories SET cover_media_id = :mediaId, updated_at = :now WHERE id = :memoryId")
    suspend fun setCover(memoryId: Long, mediaId: Long?, now: Long)

    @Query("SELECT DISTINCT place FROM memories WHERE place <> '' ORDER BY place COLLATE NOCASE")
    suspend fun distinctPlaces(): List<String>

    @Query("SELECT CAST(strftime('%Y', start_epoch_day * 86400, 'unixepoch') AS INTEGER) AS year, COUNT(*) AS n FROM memories GROUP BY year ORDER BY year DESC")
    suspend fun countsByYear(): List<YearCount>

    @Transaction
    @Query("SELECT * FROM memories ORDER BY RANDOM() LIMIT 1")
    suspend fun random(): MemoryWithRelations?

    @Transaction
    @Query(
        """
        SELECT * FROM memories
        WHERE date_kind = 'EXACT'
          AND CAST(strftime('%m%d', start_epoch_day * 86400, 'unixepoch') AS INTEGER) = :monthDay
          AND (:excludeYear IS NULL OR CAST(strftime('%Y', start_epoch_day * 86400, 'unixepoch') AS INTEGER) <> :excludeYear)
        ORDER BY start_epoch_day DESC
        """,
    )
    suspend fun onThisDay(monthDay: Int, excludeYear: Int?): List<MemoryWithRelations>

    @Transaction
    @Query(
        """
        SELECT * FROM memories
        WHERE start_epoch_day BETWEEN :fromDay AND :toDay
          AND (:personId IS NULL OR id IN (SELECT memory_id FROM memory_persons WHERE person_id = :personId))
        ORDER BY start_epoch_day ASC, id ASC
        """,
    )
    suspend fun inRange(fromDay: Long, toDay: Long, personId: Long?): List<MemoryWithRelations>

    // ---- search index ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearch(row: MemorySearchEntity)

    @Query("DELETE FROM memory_search WHERE rowid = :memoryId")
    suspend fun deleteSearch(memoryId: Long)

    @Query("SELECT rowid FROM memory_search WHERE memory_search MATCH :match LIMIT :limit")
    suspend fun searchIds(match: String, limit: Int): List<Long>

    @Transaction
    @Query("SELECT * FROM memories WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<MemoryWithRelations>

    @Query("SELECT id FROM memories")
    suspend fun allIds(): List<Long>
}

@Dao
interface MediaDao {
    @Query("SELECT * FROM media WHERE memory_id = :memoryId ORDER BY position ASC, taken_at ASC, id ASC")
    fun observeForMemory(memoryId: Long): Flow<List<MediaEntity>>

    @Query("SELECT * FROM media WHERE memory_id = :memoryId ORDER BY position ASC, taken_at ASC, id ASC")
    suspend fun forMemory(memoryId: Long): List<MediaEntity>

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun get(id: Long): MediaEntity?

    @Query("SELECT * FROM media WHERE id = :id")
    fun observe(id: Long): Flow<MediaEntity?>

    @Insert
    suspend fun insert(media: MediaEntity): Long

    @Update
    suspend fun update(media: MediaEntity)

    @Query("DELETE FROM media WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE media SET caption = :caption WHERE id = :id")
    suspend fun updateCaption(id: Long, caption: String)

    @Query("UPDATE media SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    @Query("DELETE FROM media_persons WHERE media_id = :mediaId")
    suspend fun clearPeople(mediaId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPeople(rows: List<MediaPersonEntity>)

    @Query("SELECT person_id FROM media_persons WHERE media_id = :mediaId")
    suspend fun personIds(mediaId: Long): List<Long>

    @Query("SELECT m.* FROM media m JOIN media_persons mp ON mp.media_id = m.id WHERE mp.person_id = :personId ORDER BY m.taken_at DESC")
    suspend fun forPerson(personId: Long): List<MediaEntity>

    @Query("SELECT COALESCE(SUM(size_bytes), 0) FROM media")
    suspend fun totalBytes(): Long

    @Query("SELECT COALESCE(SUM(size_bytes), 0) FROM media")
    fun observeTotalBytes(): Flow<Long>

    @Query("SELECT COUNT(*) FROM media")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM media WHERE kind = :kind")
    fun observeCountOfKind(kind: String): Flow<Int>

    @Query("SELECT * FROM media")
    suspend fun all(): List<MediaEntity>

    @Query("SELECT encrypted_path FROM media UNION SELECT thumb_path FROM media")
    suspend fun allPaths(): List<String>
}

@Dao
interface FaceDao {
    @Query("SELECT * FROM face_clusters ORDER BY face_count DESC")
    fun observeAll(): Flow<List<FaceClusterEntity>>

    @Query("SELECT * FROM face_clusters")
    suspend fun all(): List<FaceClusterEntity>

    @Upsert
    suspend fun upsert(cluster: FaceClusterEntity): Long

    @Query("UPDATE face_clusters SET suggested_person_id = :personId, confirmed = :confirmed WHERE id = :id")
    suspend fun assign(id: Long, personId: Long?, confirmed: Boolean)

    @Query("DELETE FROM face_clusters WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM face_clusters")
    suspend fun clear()
}

@Dao
interface ImportJobDao {
    @Insert
    suspend fun insert(job: ImportJobEntity): Long

    @Update
    suspend fun update(job: ImportJobEntity)

    @Query("SELECT * FROM import_jobs WHERE stage NOT IN ('DONE', 'FAILED')")
    suspend fun pending(): List<ImportJobEntity>

    @Query("DELETE FROM import_jobs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM import_jobs WHERE stage IN ('DONE', 'FAILED')")
    suspend fun clearFinished()
}
