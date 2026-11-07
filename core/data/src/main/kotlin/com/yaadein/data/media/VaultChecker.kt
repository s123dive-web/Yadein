package com.yaadein.data.media

import com.yaadein.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/** Result of [VaultChecker.check]: how many files were read, and which memories have a damaged or missing file. */
data class VaultReport(val checked: Int, val damaged: Int, val missing: Int, val affectedMemoryIds: List<Long>) {
    val healthy: Boolean get() = damaged == 0 && missing == 0
}

/**
 * Settings → Storage → Check my memories: decrypts every photo and video and compares it with the SHA-256 recorded at
 * import, and checks each thumbnail is present. Read-only; it never deletes anything.
 */
class VaultChecker @Inject constructor(private val db: AppDatabase, private val store: MediaStore) {
    suspend fun check(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): VaultReport = withContext(Dispatchers.IO) {
        val all = db.mediaDao().all()
        var damaged = 0
        var missing = 0
        val affected = LinkedHashSet<Long>()
        all.forEachIndexed { i, m ->
            coroutineContext.ensureActive()
            val file = store.file(m.encryptedPath)
            val thumb = store.file(m.thumbPath)
            when {
                !file.exists() || !thumb.exists() -> { missing++; m.memoryId?.let(affected::add) }
                runCatching { store.crypto.sha256OfDecrypted(file) }.getOrNull() != m.sha256 -> { damaged++; m.memoryId?.let(affected::add) }
            }
            onProgress(i + 1, all.size)
        }
        VaultReport(all.size, damaged, missing, affected.toList())
    }
}
