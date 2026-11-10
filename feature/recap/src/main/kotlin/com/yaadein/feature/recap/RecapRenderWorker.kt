package com.yaadein.feature.recap

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yaadein.data.crash.CrashLog
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.media.MediaStore
import com.yaadein.data.recap.MusicSource
import com.yaadein.data.recap.RecapOutput
import com.yaadein.data.recap.RecapRenderer
import com.yaadein.data.reels.ReelKind
import com.yaadein.data.reels.ReelLibrary
import com.yaadein.domain.recap.RecapPlan
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.json.Json
import java.io.File
import com.yaadein.core.ui.R as CoreR

/**
 * Renders a recap in the background. Survives the app being closed (foreground service with a
 * progress notification), is cancellable from the notification or the app, and reports a clear
 * failure message. Output goes to Movies/Yaadein; the result data carries the shareable URI.
 */
@HiltWorker
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RecapRenderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val db: AppDatabase,
    private val mediaStore: MediaStore,
    private val library: ReelLibrary,
    private val vaultKeys: com.yaadein.data.crypto.VaultKeys,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Android restarted the app in the background while the PIN-protected vault is locked: wait for an unlock.
        if (!vaultKeys.canOpenWithoutUser()) return Result.retry()
        val planFile = inputData.getString(KEY_PLAN_FILE)?.let(::File)
        val planJson = planFile?.takeIf { it.exists() }?.readText() ?: inputData.getString(KEY_PLAN)
            ?: return Result.failure(workDataOf(KEY_ERROR to "missing plan"))
        val plan = Json.decodeFromString(RecapPlan.serializer(), planJson)
        val music = when (inputData.getString(KEY_MUSIC_KIND)) {
            MUSIC_BUNDLED -> MusicSource.Bundled(inputData.getInt(KEY_MUSIC_RES, 0))
            MUSIC_USER -> MusicSource.UserFile(inputData.getString(KEY_MUSIC_URI) ?: "")
            else -> MusicSource.None
        }
        val title = inputData.getString(KEY_TITLE) ?: "Yaadein"
        ensureChannel(context)
        setForeground(foregroundInfo(0, title))
        val output = File(mediaStore.renderDir, "recap-${id}.mp4")
        return try {
            RecapRenderer(context, db, mediaStore).render(plan, music, ReelRendering.style(context), output) { pct ->
                setProgressAsync(workDataOf(KEY_PROGRESS to pct))
                runCatching { setForegroundAsync(foregroundInfo(pct, title)) }
            }
            // Keep it in the app's reel library (encrypted, last 10 kept) first, then put a copy in Movies/Yaadein.
            val entry = runCatching {
                library.add(output, title, ReelKind.USER, plan.totalDurationMs, ReelRendering.coverMediaId(plan), plan.theme.name)
            }.onFailure { CrashLog.recordNonFatal(context, "reel library", it) }.getOrNull()
            val fileName = title.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().ifBlank { "Yaadein" } + "-" + System.currentTimeMillis() / 1000
            // Size before save(): on Android 10+ the rendered file is moved into MediaStore and deleted.
            val sizeBytes = output.length()
            val uri = RecapOutput.save(context, output, fileName)
            notifyDone(context, uri.toString(), title)
            Result.success(
                workDataOf(
                    KEY_OUTPUT_URI to uri.toString(), KEY_REEL_ID to entry?.id,
                    KEY_SIZE_BYTES to sizeBytes, KEY_DURATION_MS to plan.totalDurationMs,
                ),
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            output.delete()
            throw e
        } catch (e: Exception) {
            output.delete()
            CrashLog.recordNonFatal(context, "recap export", e)
            Result.failure(workDataOf(KEY_ERROR to RecapRenderer.describe(e)))
        } finally {
            planFile?.delete()
        }
    }

    private fun foregroundInfo(progress: Int, title: String): ForegroundInfo {
        val cancel = WorkManager.getInstance(context).createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(CoreR.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.render_notification_title, title))
            .setContentText(context.getString(R.string.render_notification_text, progress))
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, context.getString(CoreR.string.action_cancel), cancel)
            .build()
        val type = when {
            Build.VERSION.SDK_INT >= 35 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else -> 0
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(NOTIFICATION_ID, notification, type) else ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun notifyDone(context: Context, uri: String, title: String) {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse(uri), "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val pending = PendingIntent.getActivity(context, 0, view, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(CoreR.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.render_done_title, title))
            .setContentText(context.getString(R.string.render_done_text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID + 1, n) }
    }

    companion object {
        const val WORK_NAME = "recap_render"
        const val CHANNEL_ID = "recap_render"
        const val NOTIFICATION_ID = 4201
        /** Only read from work queued by older versions; the plan now travels in a file (WorkManager data is capped at 10 KB). */
        const val KEY_PLAN = "plan"
        const val KEY_PLAN_FILE = "planFile"
        const val KEY_REEL_ID = "reelId"
        /** Output: the MP4's size in bytes and length in ms (for the file-size line and the WhatsApp status check). */
        const val KEY_SIZE_BYTES = "sizeBytes"
        const val KEY_DURATION_MS = "durationMs"
        const val KEY_TITLE = "title"
        const val KEY_MUSIC_KIND = "musicKind"
        const val KEY_MUSIC_RES = "musicRes"
        const val KEY_MUSIC_URI = "musicUri"
        const val KEY_PROGRESS = "progress"
        const val KEY_OUTPUT_URI = "outputUri"
        const val KEY_ERROR = "error"
        const val MUSIC_BUNDLED = "bundled"
        const val MUSIC_USER = "user"
        const val MUSIC_NONE = "none"

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, context.getString(R.string.render_channel), NotificationManager.IMPORTANCE_LOW))
        }

        fun enqueue(context: Context, plan: RecapPlan, music: MusicSource, title: String) {
            // A reel with many photos does not fit in WorkManager's 10 KB input data, so the plan goes in a file.
            val planFile = File(File(context.noBackupFilesDir, "reel-jobs").apply { mkdirs() }, "plan-${java.util.UUID.randomUUID()}.json")
            planFile.writeText(Json.encodeToString(RecapPlan.serializer(), plan))
            val data = Data.Builder()
                .putString(KEY_PLAN_FILE, planFile.path)
                .putString(KEY_TITLE, title)
            when (music) {
                is MusicSource.Bundled -> data.putString(KEY_MUSIC_KIND, MUSIC_BUNDLED).putInt(KEY_MUSIC_RES, music.rawResId)
                is MusicSource.UserFile -> data.putString(KEY_MUSIC_KIND, MUSIC_USER).putString(KEY_MUSIC_URI, music.uri)
                MusicSource.None -> data.putString(KEY_MUSIC_KIND, MUSIC_NONE)
            }
            val request = OneTimeWorkRequestBuilder<RecapRenderWorker>().setInputData(data.build()).addTag(WORK_NAME).build()
            // One export at a time: the user's reel goes first, the automatic ones are made again later.
            AutoReelWorker.cancel(context)
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
