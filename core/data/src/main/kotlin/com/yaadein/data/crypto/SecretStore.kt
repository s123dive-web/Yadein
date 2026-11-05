package com.yaadein.data.crypto

/** Small persistent key/value store for wrapped secrets and the PIN record. */
interface SecretStore {
    fun getBytes(key: String): ByteArray?
    fun putBytes(key: String, value: ByteArray)
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getLong(key: String, default: Long): Long
    fun putLong(key: String, value: Long)
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
    fun remove(key: String)
    fun clear()
}

/** In-memory implementation for tests. */
class InMemorySecretStore : SecretStore {
    private val map = HashMap<String, Any>()
    override fun getBytes(key: String): ByteArray? = map[key] as? ByteArray
    override fun putBytes(key: String, value: ByteArray) { map[key] = value }
    override fun getString(key: String): String? = map[key] as? String
    override fun putString(key: String, value: String) { map[key] = value }
    override fun getLong(key: String, default: Long): Long = map[key] as? Long ?: default
    override fun putLong(key: String, value: Long) { map[key] = value }
    override fun getInt(key: String, default: Int): Int = map[key] as? Int ?: default
    override fun putInt(key: String, value: Int) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
    override fun clear() = map.clear()
}
