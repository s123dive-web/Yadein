package com.yaadein.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.yaadein.data.db.dao.FaceDao
import com.yaadein.data.db.dao.GroupDao
import com.yaadein.data.db.dao.ImportJobDao
import com.yaadein.data.db.dao.MediaDao
import com.yaadein.data.db.dao.MemoryDao
import com.yaadein.data.db.dao.PersonDao
import com.yaadein.data.db.entity.FaceClusterEntity
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
        FaceClusterEntity::class,
        MemorySearchEntity::class,
        ImportJobEntity::class,
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

    companion object {
        const val VERSION = 1
        const val NAME = "yaadein.db"
    }
}
