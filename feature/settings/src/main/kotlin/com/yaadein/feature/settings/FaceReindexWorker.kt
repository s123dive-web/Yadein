package com.yaadein.feature.settings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yaadein.data.crypto.VaultKeys
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.faces.FaceSuggester
import com.yaadein.data.media.ImportCoordinator
import com.yaadein.domain.faces.FaceModel
import com.yaadein.domain.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import com.yaadein.core.ui.R as CoreR

/**
 * Scans every vault photo the current face model has not seen yet (`media.face_scan_version < FaceModel.VERSION`),
 * newest first, in batches, so a library re-indexes itself in the background after the model changes or after a
 * restore. Needs the vault open (queued from the foreground after an unlock, like the automatic backup), prefers
 * charging or a healthy battery, and shares the import pipeline's one heavy-work slot so encryption always comes first.
 */
@HiltWorker
class FaceReindexWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val suggester: FaceSuggester,
    private val db: AppDatabase,
    private val coordinator: ImportCoordinator,
    private val settingsRepository: SettingsRepository,
    private val vaultKeys: VaultKeys,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!vaultKeys.canOpenWithoutUser()) return Result.success()
        if (!settingsRepository.current().faceSuggestions) return Result.success()
        val total = db.faceDao().unscannedCount(FaceModel.VERSION)
        if (total == 0) return Result.success()
        runCatching { setForeground(foregroundInfo(0, total)) }
        var done = 0
        while (!isStopped) {
            val batch = db.faceDao().nextUnscanned(FaceModel.VERSION, BATCH)
            if (batch.isEmpty()) break
            for (media in batch) {
                if (isStopped) return Result.retry()
                // Photos being encrypted right now come first; wait a moment rather than compete for memory.
                while (coordinator.importing) delay(IMPORT_BACKOFF_MS)
                coordinator.withImportLock {
                    runCatching { suggester.scan(media.id) }.onFailure { db.faceDao().markScanned(media.id, FaceModel.VERSION) }
                }
                done++
                setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
                if (done % 10 == 0) runCatching { notificationManager().notify(NOTIFICATION_ID, notification(done, total)) }
            }
        }
        return Result.success()
    }

    private fun notificationManager() = applicationContext.getSystemService(NotificationManager::class.java)

    private fun notification(done: Int, total: Int) = NotificationCompat.Builder(applicationContext, CHANNEL)
        .setSmallIcon(CoreR.drawable.ic_notification)
        .setContentTitle(applicationContext.getString(R.string.faces_reindex_running))
        .setProgress(total, done, total == 0)
        .setOngoing(true)
        .setSilent(true)
        .build()

    @android.annotation.SuppressLint("SpecifyForegroundServiceType")
    private fun foregroundInfo(done: Int, total: Int): ForegroundInfo {
        notificationManager().createNotificationChannel(
            NotificationChannel(CHANNEL, applicationContext.getString(R.string.faces_reindex_channel), NotificationManager.IMPORTANCE_LOW),
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification(done, total), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification(done, total))
        }
    }

    companion object {
        const val WORK_NAME = "face_reindex"
        private const val CHANNEL = "face_reindex"
        private const val NOTIFICATION_ID = 4402
        private const val BATCH = 25
        private const val IMPORT_BACKOFF_MS = 2_000L
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"

        /** Queues a re-index unless one is already queued or running. [now] skips the battery constraint ("Scan now"). */
        fun enqueue(context: Context, now: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<FaceReindexWorker>()
                .apply { if (!now) setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build()) }
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, if (now) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) = runCatching { WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME) }

        fun observe(context: Context): Flow<List<WorkInfo>> =
            runCatching { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WORK_NAME) }.getOrElse { flowOf(emptyList()) }
    }
}
