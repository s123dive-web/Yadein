package com.yaadein.data.repository

import com.yaadein.data.db.AppDatabase
import com.yaadein.data.db.entity.CaptureStatEntity
import com.yaadein.domain.capture.CaptureEntry
import com.yaadein.domain.capture.CaptureStat
import com.yaadein.domain.repository.CaptureStatsRepository
import javax.inject.Inject
import javax.inject.Singleton

/** `capture_stats` lives in the encrypted database and never leaves the phone. */
@Singleton
class CaptureStatsRepositoryImpl @Inject constructor(private val db: AppDatabase) : CaptureStatsRepository {
    private val dao get() = db.captureStatDao()

    override suspend fun record(stat: CaptureStat): Long = dao.insert(
        CaptureStatEntity(
            entry = stat.entry.name, itemCount = stat.itemCount, tapToSaveMs = stat.tapToSaveMs,
            saveToEncryptedMs = stat.saveToEncryptedMs, createdAt = stat.createdAt,
        ),
    )

    override suspend fun markEncrypted(id: Long, saveToEncryptedMs: Long) = dao.setEncrypted(id, saveToEncryptedMs)

    override suspend fun recent(limit: Int): List<CaptureStat> = dao.recent(limit).map {
        CaptureStat(
            id = it.id, entry = CaptureEntry.entries.firstOrNull { e -> e.name == it.entry } ?: CaptureEntry.HUB,
            itemCount = it.itemCount, tapToSaveMs = it.tapToSaveMs, saveToEncryptedMs = it.saveToEncryptedMs, createdAt = it.createdAt,
        )
    }
}
