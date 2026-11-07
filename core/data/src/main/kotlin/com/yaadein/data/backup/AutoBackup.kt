package com.yaadein.data.backup

import android.content.Context
import android.net.Uri
import com.yaadein.data.crypto.VaultKeys
import com.yaadein.domain.settings.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Weekly automatic backup to one document the user picked once (for example a file in their Google Drive folder via
 * the system picker: the Drive app does the upload, so Yaadein still has no internet permission).
 *
 * The backup password is turned into the Argon2id key once, when it is set; the key and its salt are kept in
 * `noBackupFiles/autobackup.key`, sealed with the vault's media key (AES-GCM). A PIN-protected vault therefore has to be
 * open for a backup to run, which is when [isDue] is checked (after each unlock). Every run rewrites the same file with
 * the same password, so restoring works exactly like a manual backup.
 */
@Singleton
class AutoBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backup: BackupManager,
    private val keys: VaultKeys,
) {
    private val file: File get() = File(context.noBackupFilesDir, FILE)

    val isConfigured: Boolean get() = file.exists()

    /** Derives and stores the key for [password]; also takes a persistable write grant on [target]. */
    suspend fun configure(target: Uri, password: CharArray) = withContext(Dispatchers.Default) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                target,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val key = backup.deriveKey(password, salt)
        seal(salt + key)
        key.fill(0)
    }

    /** Writes a full backup to [target] with the stored key. */
    suspend fun run(target: Uri, onProgress: (BackupManager.Progress) -> Unit = {}) = withContext(Dispatchers.IO) {
        val material = unseal() ?: error("automatic backup is not set up")
        try {
            backup.exportWithKey(target, material.copyOfRange(0, SALT_BYTES), material.copyOfRange(SALT_BYTES, material.size), onProgress)
        } finally {
            material.fill(0)
        }
    }

    fun clear(target: Uri?) {
        file.delete()
        target?.let { uri ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
    }

    private fun seal(plain: ByteArray) {
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(GCM).apply {
            init(Cipher.ENCRYPT_MODE, keys.mediaKey(), GCMParameterSpec(TAG_BITS, iv))
            updateAAD(AAD)
        }
        val tmp = File(file.path + ".tmp")
        tmp.writeBytes(iv + cipher.doFinal(plain))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    private fun unseal(): ByteArray? {
        if (!file.exists()) return null
        val blob = file.readBytes()
        if (blob.size <= IV_BYTES) return null
        return runCatching {
            Cipher.getInstance(GCM).apply {
                init(Cipher.DECRYPT_MODE, keys.mediaKey(), GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES))
                updateAAD(AAD)
            }.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
        }.getOrNull()
    }

    companion object {
        const val INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
        /** The "back up your memories" banner shows after this long without any backup. */
        const val NUDGE_AFTER_MS = 30L * 24 * 60 * 60 * 1000
        /** ...and comes back this long after being dismissed. */
        const val NUDGE_SNOOZE_MS = 14L * 24 * 60 * 60 * 1000
        const val NUDGE_MIN_MEMORIES = 5
        const val DEFAULT_NAME = "Yaadein-auto-backup.yaadein"
        private const val FILE = "autobackup.key"
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val GCM = "AES/GCM/NoPadding"
        private val AAD = "yaadein-autobackup-v1".toByteArray()

        fun isDue(s: UserSettings, now: Long): Boolean =
            s.autoBackupEnabled && s.autoBackupUri != null && now - s.lastBackupAtMillis >= INTERVAL_MS

        /** Whether to show the home banner nudging a backup. */
        fun shouldNudge(s: UserSettings, memoryCount: Int, now: Long): Boolean =
            !s.autoBackupEnabled && memoryCount >= NUDGE_MIN_MEMORIES &&
                now - s.lastBackupAtMillis >= NUDGE_AFTER_MS && now - s.backupNudgeDismissedAtMillis >= NUDGE_SNOOZE_MS
    }
}
