package com.yaadein.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper

object DatabaseFactory {
    @Volatile private var sqlCipherLoaded = false

    private fun ensureSqlCipher() {
        if (!sqlCipherLoaded) {
            synchronized(this) {
                if (!sqlCipherLoaded) {
                    System.loadLibrary("sqlcipher")
                    sqlCipherLoaded = true
                }
            }
        }
    }

    /**
     * Builds the encrypted production database. [rawKey] supplies the 32-byte vault key; SQLCipher receives it as a raw
     * key so no KDF runs on the hot path. It is called on first use, not here, so the database object can exist while
     * the vault is locked.
     */
    fun encrypted(context: Context, rawKey: () -> ByteArray): AppDatabase {
        ensureSqlCipher()
        return builder(context, DeferredKeyOpenHelperFactory(rawKey)).build()
    }

    /** Plain (unencrypted) database used only by JVM/Robolectric tests, where SQLCipher's native library is unavailable. */
    fun plainForTests(context: Context, inMemory: Boolean = true): AppDatabase {
        val b = if (inMemory) {
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        } else {
            Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
        }
        return b.addMigrations(*Migrations.ALL).allowMainThreadQueries().build()
    }

    private fun builder(context: Context, factory: SupportSQLiteOpenHelper.Factory): RoomDatabase.Builder<AppDatabase> =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .openHelperFactory(factory)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(*Migrations.ALL)
}
