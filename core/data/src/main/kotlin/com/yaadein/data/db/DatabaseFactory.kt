package com.yaadein.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

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
     * Opens the encrypted production database. [rawKey] is the 32-byte key unwrapped from the Keystore;
     * SQLCipher receives it as a raw key so no KDF runs on the hot path.
     */
    fun encrypted(context: Context, rawKey: ByteArray): AppDatabase {
        ensureSqlCipher()
        val factory = SupportOpenHelperFactory(rawKey)
        return builder(context, factory).build()
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
