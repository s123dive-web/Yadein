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
import com.yaadein.data.db.entity.CaptureStatEntity
import com.yaadein.data.db.entity.FaceEntity
import com.yaadein.data.db.entity.FaceRejectionEntity
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

    /** People on the most recently created memories, most recent first (Quick Save's people chips). */
    @Query(
        """
        SELECT p.* FROM persons p JOIN memory_persons mp ON mp.person_id = p.id JOIN memories m ON m.id = mp.memory_id
        GROUP BY p.id ORDER BY MAX(m.created_at) DESC, p.name COLLATE NOCASE LIMIT :limit
        """,
    )
    suspend fun recentlyTagged(limit: Int): List<PersonEntity>
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

    /** Groups with exactly this name (clearing the sample family). */
    @Query("SELECT id FROM groups WHERE name = :name")
    suspend fun idsByName(name: String): List<Long>

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

    /** Memories with exactly this title on this start day (clearing the sample family). */
    @Query("SELECT id FROM memories WHERE title = :title AND start_epoch_day = :startEpochDay")
    suspend fun idsByTitleAndDay(title: String, startEpochDay: Long): List<Long>

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

    @Query("SELECT place, COUNT(*) AS n FROM memories WHERE place <> '' GROUP BY place COLLATE NOCASE ORDER BY n DESC, place COLLATE NOCASE LIMIT :limit")
    suspend fun placeCounts(limit: Int): List<PlaceCount>

    /**
     * Every memory's year, place and cover media (the chosen cover if it still belongs to the memory, else its first
     * media by position), newest first. Relive picks one per year and per place from these rows.
     */
    @Query(
        """
        SELECT CAST(strftime('%Y', m.start_epoch_day * 86400, 'unixepoch') AS INTEGER) AS year,
               m.place AS place, m.id AS memoryId, m.cover_style AS coverStyle,
               COALESCE(
                   (SELECT md.id FROM media md WHERE md.id = m.cover_media_id AND md.memory_id = m.id),
                   (SELECT md.id FROM media md WHERE md.memory_id = m.id ORDER BY md.position, COALESCE(md.taken_at, 9223372036854775807), md.id LIMIT 1)
               ) AS mediaId
        FROM memories m
        ORDER BY m.start_epoch_day DESC, m.id DESC
        """,
    )
    suspend fun coverRows(): List<CoverRow>

    @Query("SELECT CAST(strftime('%Y', start_epoch_day * 86400, 'unixepoch') AS INTEGER) AS year, COUNT(*) AS n FROM memories GROUP BY year ORDER BY year DESC")
    suspend fun countsByYear(): List<YearCount>

    /**
     * Picks the id first and loads relations in a second, deterministic query. Room's generated code
     * for a relation query steps the statement twice (once to collect keys, once to build rows), so
     * `ORDER BY RANDOM()` in a relation query can return a different row the second time and crash
     * with "Required value was null".
     */
    @Query("SELECT id FROM memories ORDER BY RANDOM() LIMIT 1")
    suspend fun randomId(): Long?

    @Transaction
    suspend fun random(): MemoryWithRelations? = randomId()?.let { getWithRelations(it) }

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

    @Query("SELECT voice_note_path FROM memories WHERE voice_note_path IS NOT NULL")
    suspend fun voicePaths(): List<String>

    @Query("UPDATE memories SET voice_note_path = :path, updated_at = :now WHERE id = :id")
    suspend fun setVoiceNote(id: Long, path: String?, now: Long)

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

    /** Place names with the GPS position of one of their photos (for Quick Save's "nearest known place"). */
    @Query(
        """
        SELECT m.place AS place, md.latitude AS latitude, md.longitude AS longitude FROM media md JOIN memories m ON m.id = md.memory_id
        WHERE md.latitude IS NOT NULL AND md.longitude IS NOT NULL AND m.place != '' ORDER BY md.id DESC LIMIT :limit
        """,
    )
    suspend fun locatedPlaces(limit: Int): List<PlacePoint>
}

@Dao
interface CaptureStatDao {
    @Insert
    suspend fun insert(stat: CaptureStatEntity): Long

    @Query("UPDATE capture_stats SET save_to_encrypted_ms = :ms WHERE id = :id")
    suspend fun setEncrypted(id: Long, ms: Long)

    @Query("SELECT * FROM capture_stats ORDER BY created_at DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<CaptureStatEntity>

    @Query("SELECT COUNT(*) FROM capture_stats")
    suspend fun count(): Int
}

@Dao
interface FaceDao {
    @Insert
    suspend fun insert(face: FaceEntity): Long

    @Query("SELECT * FROM faces WHERE id = :id")
    suspend fun get(id: Long): FaceEntity?

    @Query("SELECT * FROM faces WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<FaceEntity>

    @Query("SELECT * FROM faces WHERE media_id = :mediaId ORDER BY quality DESC")
    suspend fun forMedia(mediaId: Long): List<FaceEntity>

    /** Faces on any photo of [memoryId] that point at a person without being confirmed (suggestion chips). */
    @Query("SELECT f.* FROM faces f JOIN media m ON m.id = f.media_id WHERE m.memory_id = :memoryId AND f.state IN ('SUGGESTED', 'AUTO') ORDER BY f.similarity DESC")
    fun observeSuggestionsForMemory(memoryId: Long): Flow<List<FaceEntity>>

    @Query("SELECT * FROM faces WHERE state = :state ORDER BY quality DESC")
    suspend fun byState(state: String): List<FaceEntity>

    @Query("SELECT COUNT(*) FROM faces WHERE state = 'UNKNOWN'")
    fun observeUnknownCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM faces WHERE state = 'UNKNOWN'")
    suspend fun unknownCount(): Int

    @Query("SELECT COUNT(*) FROM faces")
    suspend fun count(): Int

    /** Faces not confirmed yet whose current suggestion is [personId] or none (candidates to re-score). */
    @Query("SELECT * FROM faces WHERE state != 'CONFIRMED' AND (person_id = :personId OR person_id IS NULL)")
    suspend fun pendingFor(personId: Long): List<FaceEntity>

    @Query("SELECT * FROM faces WHERE person_id = :personId AND state = 'CONFIRMED'")
    suspend fun confirmedFor(personId: Long): List<FaceEntity>

    @Query("UPDATE faces SET person_id = :personId, state = :state, similarity = :similarity WHERE id = :id")
    suspend fun assign(id: Long, personId: Long?, state: String, similarity: Float?)

    @Query("DELETE FROM faces WHERE media_id = :mediaId")
    suspend fun deleteForMedia(mediaId: Long)

    @Query("DELETE FROM faces")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun reject(rejection: FaceRejectionEntity)

    @Query("SELECT person_id FROM face_rejections WHERE face_id = :faceId")
    suspend fun rejectedPeople(faceId: Long): List<Long>

    @Query("SELECT * FROM face_rejections")
    suspend fun allRejections(): List<FaceRejectionEntity>

    @Query("DELETE FROM face_rejections")
    suspend fun clearRejections()

    // ---- person centroids and the scan ledger live on their own tables ----

    @Query("UPDATE persons SET centroid = :centroid, centroid_model = :model, confirmed_faces = :count WHERE id = :personId")
    suspend fun setCentroid(personId: Long, centroid: ByteArray?, model: Int?, count: Int)

    @Query("UPDATE persons SET centroid = NULL, centroid_model = NULL, confirmed_faces = 0")
    suspend fun clearCentroids()

    @Query("SELECT * FROM persons WHERE centroid IS NOT NULL AND centroid_model = :model")
    suspend fun peopleWithCentroids(model: Int): List<PersonEntity>

    @Query("SELECT * FROM media WHERE kind = 'PHOTO' AND face_scan_version < :model ORDER BY id DESC LIMIT :limit")
    suspend fun nextUnscanned(model: Int, limit: Int): List<MediaEntity>

    @Query("SELECT COUNT(*) FROM media WHERE kind = 'PHOTO' AND face_scan_version < :model")
    fun observeUnscannedCount(model: Int): Flow<Int>

    @Query("SELECT COUNT(*) FROM media WHERE kind = 'PHOTO' AND face_scan_version < :model")
    suspend fun unscannedCount(model: Int): Int

    @Query("UPDATE media SET face_scan_version = :model WHERE id = :mediaId")
    suspend fun markScanned(mediaId: Long, model: Int)

    @Query("UPDATE media SET face_scan_version = 0")
    suspend fun resetScanLedger()
}

@Dao
interface ImportJobDao {
    @Insert
    suspend fun insert(job: ImportJobEntity): Long

    @Update
    suspend fun update(job: ImportJobEntity)

    @Query("SELECT * FROM import_jobs WHERE stage NOT IN ('DONE', 'FAILED')")
    suspend fun pending(): List<ImportJobEntity>

    @Query("SELECT * FROM import_jobs WHERE id = :id")
    suspend fun get(id: Long): ImportJobEntity?

    @Query("DELETE FROM import_jobs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM import_jobs WHERE stage IN ('DONE', 'FAILED')")
    suspend fun clearFinished()
}

/** Row of [MemoryDao.coverRows]. */
data class CoverRow(val year: Int, val place: String, val memoryId: Long, val coverStyle: String, val mediaId: Long?)

/** Row of [MemoryDao.placeCounts]. */
data class PlaceCount(val place: String, val n: Int)

/** Row of [MediaDao.locatedPlaces]. */
data class PlacePoint(val place: String, val latitude: Double, val longitude: Double)
