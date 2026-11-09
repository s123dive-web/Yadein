package com.yaadein.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * SQLCipher open-helper factory that asks for the key only when the database is first used, not when Room builds the
 * database object. Hilt can therefore construct repositories, workers and the image loader while the vault is still
 * locked; the first real query before unlock fails with VaultLockedException instead of the app crashing at startup.
 */
class DeferredKeyOpenHelperFactory(private val key: () -> ByteArray) : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper = Deferred(configuration)

    private inner class Deferred(private val configuration: SupportSQLiteOpenHelper.Configuration) : SupportSQLiteOpenHelper {
        @Volatile private var real: SupportSQLiteOpenHelper? = null
        @Volatile private var writeAheadLogging: Boolean? = null

        private fun real(): SupportSQLiteOpenHelper = real ?: synchronized(this) {
            real ?: SupportOpenHelperFactory(key().copyOf()).create(configuration).also { helper ->
                writeAheadLogging?.let(helper::setWriteAheadLoggingEnabled)
                real = helper
            }
        }

        override val databaseName: String? get() = configuration.name

        override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
            writeAheadLogging = enabled
            real?.setWriteAheadLoggingEnabled(enabled)
        }

        override val writableDatabase: SupportSQLiteDatabase get() = real().writableDatabase
        override val readableDatabase: SupportSQLiteDatabase get() = real().readableDatabase

        override fun close() {
            real?.close()
        }
    }
}
