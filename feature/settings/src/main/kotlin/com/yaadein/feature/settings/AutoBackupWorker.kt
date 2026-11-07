package com.yaadein.feature.settings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters

import com.yaadein.data.backup.AutoBackup
import com.yaadein.data.backup.BackupManager
import com.yaadein.data.crash.CrashLog
import com.yaadein.data.crypto.VaultKeys
import com.yaadein.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import com.yaadein.core.ui.R as CoreR

/**
 * Runs one automatic backup (queued after an unlock when a week has passed, or by "Back up now"). Needs the vault open,
 * so it is only queued from the foreground; if Android restarts it later while locked it waits for the next unlock.
 */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val autoBackup: AutoBackup,
    private val settingsRepository: SettingsRepository,
    private val vaultKeys: VaultKeys,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!vaultKeys.canOpenWithoutUser()) return Result.success()
        val settings = settingsRepository.current()
        val uri = settings.autoBackupUri?.let(Uri::parse) ?: return Result.success()
        if (!settings.autoBackupEnabled || !autoBackup.isConfigured) return Result.success()
        runCatching { setForeground(foregroundInfo(0, 0)) }
        return runCatching {
            autoBackup.run(uri) { p ->
                if (p is BackupManager.Progress.Working && p.total > 0 && p.done % 10 == 0) {
                    runCatching { notificationManager().notify(NOTIFICATION_ID, notification(p.done, p.total)) }
                }
            }
        }.fold(
            onSuccess = {
                settingsRepository.update { it.copy(lastBackupAtMillis = System.currentTimeMillis(), autoBackupFailed = false) }
                Result.success()
            },
            onFailure = { e ->
                CrashLog.recordNonFatal(applicationContext, "automatic backup", e)
                // A revoked or deleted target cannot recover by retrying; the Backup screen shows the problem.
                settingsRepository.update { it.copy(autoBackupFailed = true) }
                Result.failure()
            },
        )
    }

    private fun notificationManager() = applicationContext.getSystemService(NotificationManager::class.java)

    private fun notification(done: Int, total: Int) = NotificationCompat.Builder(applicationContext, CHANNEL)
        .setSmallIcon(CoreR.drawable.ic_notification)
        .setContentTitle(applicationContext.getString(R.string.autobackup_running))
        .setProgress(total, done, total == 0)
        .setOngoing(true)
        .setSilent(true)
        .build()

    // The app manifest declares WorkManager's SystemForegroundService with dataSync|mediaProcessing (tools:node merge);
    // lint only sees this library's manifest.
    @android.annotation.SuppressLint("SpecifyForegroundServiceType")
    private fun foregroundInfo(done: Int, total: Int): ForegroundInfo {
        notificationManager().createNotificationChannel(
            NotificationChannel(CHANNEL, applicationContext.getString(R.string.autobackup_channel), NotificationManager.IMPORTANCE_LOW),
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification(done, total), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification(done, total))
        }
    }

    companion object {
        private const val WORK_NAME = "auto_backup"
        private const val CHANNEL = "auto_backup"
        private const val NOTIFICATION_ID = 4401

        fun enqueue(context: Context, now: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
                .apply { if (!now) setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build()) }
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, if (now) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
        }

        fun observe(context: Context): kotlinx.coroutines.flow.Flow<List<androidx.work.WorkInfo>> =
            runCatching { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WORK_NAME) }.getOrElse { kotlinx.coroutines.flow.flowOf(emptyList()) }
    }
}
