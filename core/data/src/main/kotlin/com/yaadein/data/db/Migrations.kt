package com.yaadein.data.db

import androidx.room.migration.Migration

/**
 * Every schema change adds a MIGRATION_n_n1 here plus a MigrationTest case.
 * fallbackToDestructiveMigration is never used: users' memories are irreplaceable.
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
