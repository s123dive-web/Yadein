package com.yaadein.data.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** EncryptedSharedPreferences-backed secret store (keys and values both encrypted at rest). */
class EncryptedPrefsSecretStore(context: Context, fileName: String = FILE_NAME) : SecretStore {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun getBytes(key: String): ByteArray? =
        prefs.getString(key, null)?.let { Base64.decode(it, Base64.NO_WRAP) }

    override fun putBytes(key: String, value: ByteArray) {
        prefs.edit().putString(key, Base64.encodeToString(value, Base64.NO_WRAP)).commit()
    }

    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) { prefs.edit().putString(key, value).commit() }
    override fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)
    override fun putLong(key: String, value: Long) { prefs.edit().putLong(key, value).commit() }
    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    override fun putInt(key: String, value: Int) { prefs.edit().putInt(key, value).commit() }
    override fun remove(key: String) { prefs.edit().remove(key).commit() }
    override fun clear() { prefs.edit().clear().commit() }

    companion object {
        const val FILE_NAME = "yaadein_secrets"
    }
}
