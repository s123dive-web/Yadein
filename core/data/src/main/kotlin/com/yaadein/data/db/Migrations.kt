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

    /**
     * v2 → v3 (Fast & Rich, Phase 2): real face recognition. The old LBP centroids in `face_clusters` cannot be converted
     * (no per-face rows, another descriptor), so the table goes; confirmed tags live in media_persons and are kept. New:
     * `faces` (one row per detected face with its embedding), `face_rejections`, person centroids, and a per-photo scan
     * ledger so the re-index worker can resume.
     */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `face_clusters`")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `faces` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `media_id` INTEGER NOT NULL, `embedding` BLOB NOT NULL, " +
                    "`model_version` INTEGER NOT NULL, `quality` REAL NOT NULL, `box_left` REAL NOT NULL, `box_top` REAL NOT NULL, `box_right` REAL NOT NULL, " +
                    "`box_bottom` REAL NOT NULL, `person_id` INTEGER, `state` TEXT NOT NULL, `similarity` REAL, `created_at` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`media_id`) REFERENCES `media`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_faces_media_id` ON `faces` (`media_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_faces_person_id` ON `faces` (`person_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_faces_state` ON `faces` (`state`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `face_rejections` (`face_id` INTEGER NOT NULL, `person_id` INTEGER NOT NULL, PRIMARY KEY(`face_id`, `person_id`), " +
                    "FOREIGN KEY(`face_id`) REFERENCES `faces`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`person_id`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_face_rejections_person_id` ON `face_rejections` (`person_id`)")
            db.execSQL("ALTER TABLE `persons` ADD COLUMN `centroid` BLOB")
            db.execSQL("ALTER TABLE `persons` ADD COLUMN `centroid_model` INTEGER")
            db.execSQL("ALTER TABLE `persons` ADD COLUMN `confirmed_faces` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `media` ADD COLUMN `face_scan_version` INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** Fast & Rich, Phase 3: kinship terms ("Aaji", "Mama") and a free-text relationship on persons. */
    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `persons` ADD COLUMN `kin_term` TEXT")
            db.execSQL("ALTER TABLE `persons` ADD COLUMN `relation_text` TEXT")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}
