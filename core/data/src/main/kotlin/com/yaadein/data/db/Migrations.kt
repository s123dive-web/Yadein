package com.yaadein.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every schema change adds a MIGRATION_n_n1 here plus a MigrationTest case.
 * fallbackToDestructiveMigration is never used: users' memories are irreplaceable.
 */
object Migrations {
    /**
     * v1 → v2 (Fast & Rich, Phase 1): GPS position on media (Quick Save's place suggestion), the picked item's
     * metadata on import jobs (a copied file can finish importing after a restart) and the local `capture_stats` table.
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `media` ADD COLUMN `latitude` REAL")
            db.execSQL("ALTER TABLE `media` ADD COLUMN `longitude` REAL")
            db.execSQL("ALTER TABLE `import_jobs` ADD COLUMN `picked_json` TEXT")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `capture_stats` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `entry` TEXT NOT NULL, " +
                    "`item_count` INTEGER NOT NULL, `tap_to_save_ms` INTEGER NOT NULL, `save_to_encrypted_ms` INTEGER, `created_at` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_capture_stats_created_at` ON `capture_stats` (`created_at`)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
