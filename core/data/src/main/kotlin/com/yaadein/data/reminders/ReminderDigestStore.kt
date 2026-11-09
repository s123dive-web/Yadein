package com.yaadein.data.reminders

import android.content.Context
import com.yaadein.data.crypto.KeyWrapper
import com.yaadein.data.crypto.VaultKeys
import com.yaadein.domain.reminders.ReminderDigest
import com.yaadein.domain.reminders.Reminders
import com.yaadein.domain.repository.MemoryRepository
import com.yaadein.domain.repository.PersonRepository
import com.yaadein.domain.repository.TimelineFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the [ReminderDigest] in `noBackupFiles/reminders.enc`, sealed with the Keystore key (not the PIN), so daily
 * reminders and the widget work while the vault is locked. Refreshed whenever the vault is open: at unlock and in the
 * daily worker. Erase everything deletes it with the rest of the app's files.
 */
@Singleton
class ReminderDigestStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val wrapper: KeyWrapper,
    private val keys: VaultKeys,
    private val memories: dagger.Lazy<MemoryRepository>,
    private val people: dagger.Lazy<PersonRepository>,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val file: File get() = File(context.noBackupFilesDir, FILE)
    private val _digest = MutableStateFlow<ReminderDigest?>(null)
    val digest: StateFlow<ReminderDigest?> = _digest.asStateFlow()

    suspend fun load(): ReminderDigest? = withContext(Dispatchers.IO) {
        _digest.value ?: runCatching {
            if (!file.exists()) null else json.decodeFromString(ReminderDigest.serializer(), wrapper.unwrap(file.readBytes()).decodeToString())
        }.getOrNull()?.also { _digest.value = it }
    }

    /** Rebuilds from the vault when it can be read; otherwise returns the last saved digest. */
    suspend fun refreshOrLoad(): ReminderDigest? = withContext(Dispatchers.IO) {
        if (!keys.canOpenWithoutUser()) return@withContext load()
        runCatching { refresh() }.getOrNull() ?: load()
    }

    suspend fun refresh(): ReminderDigest = withContext(Dispatchers.IO) {
        val cards = memories.get().observeCards(TimelineFilter()).first()
        val persons = people.get().observeAll().first()
        val digest = Reminders.build(cards, persons, System.currentTimeMillis())
        val tmp = File(file.path + ".tmp")
        tmp.writeBytes(wrapper.wrap(json.encodeToString(ReminderDigest.serializer(), digest).encodeToByteArray()))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        _digest.value = digest
        digest
    }

    fun clear() {
        file.delete()
        _digest.value = null
    }

    private companion object {
        const val FILE = "reminders.enc"
    }
}
