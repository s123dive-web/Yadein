package com.yaadein.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Room migration tests. Every new schema version must (1) ship an exported schema JSON under
 * core/data/schemas, (2) add its migration to Migrations.ALL and (3) add a case here that creates the
 * previous version, runs the migration and validates against the exported schema.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun `version 1 schema migrates to the current version and opens`() {
        helper.createDatabase(dbName, 1).close()
        helper.runMigrationsAndValidate(dbName, AppDatabase.VERSION, true, *Migrations.ALL).close()
        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java, dbName)
            .addMigrations(*Migrations.ALL).build()
        db.openHelper.writableDatabase
        db.close()
    }

    @Test
    fun `migration 1 to 2 keeps every row and adds the new columns and table`() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL("INSERT INTO persons (id, name, nickname, relation, birthday_epoch_day, profile_media_id, notes, avatar_color_index, created_at) VALUES (1, 'Aai', NULL, 'FAMILY', NULL, NULL, '', 0, 1)")
            db.execSQL("INSERT INTO memories (id, type, title, description, date_kind, start_epoch_day, end_epoch_day, fuzzy_label, place, mood, cover_media_id, cover_style, voice_note_path, created_at, updated_at) VALUES (7, 'EVENT', 'Diwali', '', 'EXACT', 20027, NULL, NULL, 'Pune', NULL, NULL, 'diwali', NULL, 5, 5)")
            db.execSQL("INSERT INTO media (id, memory_id, kind, encrypted_path, thumb_path, taken_at, duration_ms, size_bytes, caption, width, height, sha256, position, created_at) VALUES (3, 7, 'PHOTO', 'media/a', 'thumbs/a', 1000, NULL, 10, '', 4, 3, 'x', 0, 6)")
            db.execSQL("INSERT INTO memory_persons (memory_id, person_id) VALUES (7, 1)")
            db.execSQL("INSERT INTO import_jobs (id, source_uri, memory_id, stage, temp_path, target_path, thumb_path, sha256, optimize, created_at) VALUES (9, 'content://x', 7, 'COPYING', NULL, NULL, NULL, NULL, 1, 8)")
        }
        helper.runMigrationsAndValidate(dbName, 2, true, Migrations.MIGRATION_1_2).use { db ->
            db.query("SELECT id, latitude, longitude, memory_id FROM media").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(3L, c.getLong(0))
                assertTrue("latitude starts null", c.isNull(1))
                assertTrue("longitude starts null", c.isNull(2))
                assertEquals(7L, c.getLong(3))
            }
            db.query("SELECT picked_json, stage FROM import_jobs WHERE id = 9").use { c -> assertTrue(c.moveToFirst()); assertTrue(c.isNull(0)); assertEquals("COPYING", c.getString(1)) }
            db.query("SELECT COUNT(*) FROM memory_persons").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
            db.execSQL("INSERT INTO capture_stats (entry, item_count, tap_to_save_ms, save_to_encrypted_ms, created_at) VALUES ('FAB', 10, 6500, NULL, 1)")
            db.query("SELECT entry, item_count FROM capture_stats").use { c -> assertTrue(c.moveToFirst()); assertEquals("FAB", c.getString(0)); assertEquals(10, c.getInt(1)) }
        }
    }

    @Test
    fun `migration 2 to 3 drops the LBP clusters, keeps tags and adds the face tables`() {
        helper.createDatabase(dbName, 2).use { db ->
            db.execSQL("INSERT INTO persons (id, name, nickname, relation, birthday_epoch_day, profile_media_id, notes, avatar_color_index, created_at) VALUES (1, 'Aai', NULL, 'FAMILY', NULL, NULL, '', 0, 1)")
            db.execSQL("INSERT INTO memories (id, type, title, description, date_kind, start_epoch_day, end_epoch_day, fuzzy_label, place, mood, cover_media_id, cover_style, voice_note_path, created_at, updated_at) VALUES (7, 'EVENT', 'Diwali', '', 'EXACT', 20027, NULL, NULL, 'Pune', NULL, NULL, 'diwali', NULL, 5, 5)")
            db.execSQL("INSERT INTO media (id, memory_id, kind, encrypted_path, thumb_path, taken_at, duration_ms, size_bytes, caption, width, height, sha256, position, created_at, latitude, longitude) VALUES (3, 7, 'PHOTO', 'media/a', 'thumbs/a', 1000, NULL, 10, '', 4, 3, 'x', 0, 6, NULL, NULL)")
            db.execSQL("INSERT INTO media_persons (media_id, person_id) VALUES (3, 1)")
            db.execSQL("INSERT INTO face_clusters (id, embedding, sample_media_id, sample_left, sample_top, sample_right, sample_bottom, suggested_person_id, confirmed, face_count, created_at) VALUES (1, X'00000000', 3, 0, 0, 1, 1, 1, 1, 2, 1)")
        }
        helper.runMigrationsAndValidate(dbName, 3, true, Migrations.MIGRATION_2_3).use { db ->
            db.query("SELECT COUNT(*) FROM media_persons WHERE media_id = 3 AND person_id = 1").use { c -> c.moveToFirst(); assertEquals("confirmed tags survive", 1, c.getInt(0)) }
            db.query("SELECT centroid, centroid_model, confirmed_faces FROM persons WHERE id = 1").use { c -> assertTrue(c.moveToFirst()); assertTrue(c.isNull(0)); assertTrue(c.isNull(1)); assertEquals(0, c.getInt(2)) }
            db.query("SELECT face_scan_version FROM media WHERE id = 3").use { c -> assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0)) }
            db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'face_clusters'").use { c -> assertEquals("LBP table gone", 0, c.count) }
            db.execSQL("INSERT INTO faces (media_id, embedding, model_version, quality, box_left, box_top, box_right, box_bottom, person_id, state, similarity, created_at) VALUES (3, X'0000803F', 2, 0.5, 0.1, 0.1, 0.4, 0.5, 1, 'CONFIRMED', 1.0, 9)")
            db.execSQL("INSERT INTO face_rejections (face_id, person_id) VALUES (1, 1)")
            db.query("SELECT COUNT(*) FROM faces").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        }
    }

    @Test
    fun `migration 3 to 4 adds kin term and relation text with nulls`() {
        helper.createDatabase(dbName, 3).use { db ->
            db.execSQL("INSERT INTO persons (id, name, nickname, relation, birthday_epoch_day, profile_media_id, notes, avatar_color_index, created_at, centroid, centroid_model, confirmed_faces) VALUES (1, 'Aaji', NULL, 'FAMILY', NULL, NULL, '', 2, 1, NULL, NULL, 0)")
        }
        helper.runMigrationsAndValidate(dbName, 4, true, Migrations.MIGRATION_3_4).use { db ->
            db.query("SELECT name, kin_term, relation_text, confirmed_faces FROM persons").use { c ->
                assertTrue(c.moveToFirst()); assertEquals("Aaji", c.getString(0)); assertTrue(c.isNull(1)); assertTrue(c.isNull(2)); assertEquals(0, c.getInt(3))
            }
            db.execSQL("UPDATE persons SET kin_term = 'AAJI' WHERE id = 1")
            db.query("SELECT kin_term FROM persons").use { c -> c.moveToFirst(); assertEquals("AAJI", c.getString(0)) }
        }
    }

    @Test
    fun `every version from 1 to current has a migration path`() {
        val covered = Migrations.ALL.map { it.startVersion to it.endVersion }.toSet()
        for (v in 1 until AppDatabase.VERSION) {
            assertTrue("missing migration $v -> ${v + 1}", (v to v + 1) in covered)
        }
        assertEquals(AppDatabase.VERSION - 1, Migrations.ALL.size)
    }
}
