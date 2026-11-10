package com.yaadein.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.yaadein.data.db.dao.CaptureStatDao
import com.yaadein.data.db.dao.FaceDao
import com.yaadein.data.db.dao.GroupDao
import com.yaadein.data.db.dao.ImportJobDao
import com.yaadein.data.db.dao.MediaDao
import com.yaadein.data.db.dao.MemoryDao
import com.yaadein.data.db.dao.PersonDao
import com.yaadein.data.db.entity.CaptureStatEntity
import com.yaadein.data.db.entity.FaceEntity
import com.yaadein.data.db.entity.FaceRejectionEntity
import com.yaadein.data.db.entity.GroupEntity
import com.yaadein.data.db.entity.GroupMemberEntity
import com.yaadein.data.db.entity.ImportJobEntity
import com.yaadein.data.db.entity.MediaEntity
import com.yaadein.data.db.entity.MediaPersonEntity
import com.yaadein.data.db.entity.MemoryEntity
import com.yaadein.data.db.entity.MemoryPersonEntity
import com.yaadein.data.db.entity.MemorySearchEntity
import com.yaadein.data.db.entity.PersonEntity

@Database(
    entities = [
        PersonEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        MemoryEntity::class,
        MediaEntity::class,
        MediaPersonEntity::class,
        MemoryPersonEntity::class,
        FaceEntity::class,
        FaceRejectionEntity::class,
        MemorySearchEntity::class,
        ImportJobEntity::class,
        CaptureStatEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun groupDao(): GroupDao
    abstract fun memoryDao(): MemoryDao
    abstract fun mediaDao(): MediaDao
    abstract fun faceDao(): FaceDao
    abstract fun importJobDao(): ImportJobDao
    abstract fun captureStatDao(): CaptureStatDao

    companion object {
        /**
         * v2 (Fast & Rich round, Phase 1): capture_stats, media GPS columns, import_jobs.picked_json.
         * v3 (Phase 2): per-face embeddings (`faces`, `face_rejections`), person centroids, media.face_scan_version;
         * the LBP `face_clusters` table is dropped.
         * v4 (Phase 3): persons.kin_term, persons.relation_text.
         * v5 (Relive 2): memories.favorite, memories.last_opened_at.
         */
        const val VERSION = 5
        const val NAME = "yaadein.db"
    }
}
