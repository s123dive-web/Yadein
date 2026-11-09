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
    fun `version 1 schema opens and validates against the exported schema`() {
        helper.createDatabase(dbName, 1).close()
        helper.runMigrationsAndValidate(dbName, 1, true, *Migrations.ALL).close()
        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java, dbName)
            .addMigrations(*Migrations.ALL).build()
        db.openHelper.writableDatabase
        db.close()
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
