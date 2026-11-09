package com.yaadein.data.repository

import androidx.room.withTransaction
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.db.entity.MediaPersonEntity
import com.yaadein.data.media.MediaStore
import com.yaadein.domain.model.Media
import com.yaadein.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val mediaStore: MediaStore,
    private val memoryRepository: MemoryRepositoryImpl,
) : MediaRepository {
    private val dao get() = db.mediaDao()

    override fun observeForMemory(memoryId: Long): Flow<List<Media>> =
        dao.observeForMemory(memoryId).map { list -> list.map { it.toDomain() } }

    override suspend fun get(id: Long): Media? = dao.get(id)?.toDomain()

    override suspend fun forPerson(personId: Long): List<Media> = dao.forPerson(personId).map { it.toDomain() }

    override suspend fun updateCaption(id: Long, caption: String) {
        db.withTransaction {
            dao.updateCaption(id, caption.trim())
            dao.get(id)?.memoryId?.let { memoryRepository.reindex(it) }
        }
    }

    override suspend fun setPeople(mediaId: Long, personIds: List<Long>) = db.withTransaction {
        dao.clearPeople(mediaId)
        dao.insertPeople(personIds.distinct().map { MediaPersonEntity(mediaId, it) })
        // People tagged on a photo are also part of the memory.
        val media = dao.get(mediaId)
        val memoryId = media?.memoryId
        if (memoryId != null) {
            val existing = db.memoryDao().personIds(memoryId).toSet()
            val missing = personIds.filter { it !in existing }
            if (missing.isNotEmpty()) {
                db.memoryDao().insertPeople(missing.map { com.yaadein.data.db.entity.MemoryPersonEntity(memoryId, it) })
            }
            memoryRepository.reindex(memoryId)
        }
    }

    override suspend fun delete(id: Long) {
        val media = dao.get(id) ?: return
        db.withTransaction {
            val memoryId = media.memoryId
            dao.delete(id)
            if (memoryId != null) {
                val memory = db.memoryDao().get(memoryId)
                if (memory?.coverMediaId == id) {
                    val next = dao.forMemory(memoryId).firstOrNull()
                    db.memoryDao().setCover(memoryId, next?.id, memory.updatedAt)
                }
                memoryRepository.reindex(memoryId)
            }
        }
        mediaStore.deleteFiles(media.encryptedPath, media.thumbPath)
    }

    override suspend fun totalBytes(): Long = dao.totalBytes()

    override suspend fun reorder(memoryId: Long, orderedMediaIds: List<Long>) = db.withTransaction {
        orderedMediaIds.forEachIndexed { index, id -> dao.updatePosition(id, index) }
    }
}
