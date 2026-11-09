package com.yaadein.data.recap

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.media.EncryptedDataSource
import com.yaadein.data.media.MediaStore

/** Routes `yaadein://` URIs to the decrypting source and everything else (raw resources, content) to the default one. */
@UnstableApi
class VaultDataSourceFactory(private val context: Context, private val db: AppDatabase, private val mediaStore: MediaStore) : DataSource.Factory {
    private val fallback = DefaultDataSource.Factory(context)
    override fun createDataSource(): DataSource = RoutingDataSource(EncryptedDataSource.Factory(db, mediaStore).createDataSource(), fallback.createDataSource())
}

@UnstableApi
private class RoutingDataSource(private val vault: DataSource, private val other: DataSource) : DataSource {
    private var active: DataSource? = null
    override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
        val chosen = if (dataSpec.uri.scheme == EncryptedDataSource.SCHEME) vault else other
        active = chosen
        return chosen.open(dataSpec)
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = active!!.read(buffer, offset, length)
    override fun getUri(): android.net.Uri? = active?.uri
    override fun close() { active?.close(); active = null }
    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
        vault.addTransferListener(transferListener); other.addTransferListener(transferListener)
    }
}
