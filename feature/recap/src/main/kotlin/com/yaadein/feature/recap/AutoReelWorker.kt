package com.yaadein.feature.recap

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
import com.yaadein.data.crash.CrashLog
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.media.MediaStore
import com.yaadein.data.recap.RecapRenderer
import com.yaadein.data.reels.ReelKind
import com.yaadein.data.reels.ReelLibrary
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.MemoryDetail
import com.yaadein.domain.recap.AutoReelPick
import com.yaadein.domain.recap.AutoReelPicker
import com.yaadein.domain.recap.AutoReelVariant
import com.yaadein.domain.recap.RecapLength
import com.yaadein.domain.recap.RecapPlanner
import com.yaadein.domain.repository.MemoryRepository
import com.yaadein.domain.repository.TimelineFilter
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import java.io.File
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.yaadein.core.ui.R as CoreR

/**
 * Keeps three short reels ready in the library ("Made for you"): recent moments, the person who appears most, and a
 * throwback (see [AutoReelPicker]). A slot whose photos have not changed is not rendered again. Runs only while no
 * reel the user asked for is being made, and gives way to one as soon as it is queued.
 *
 * Encoding video is the heaviest thing the app does, so the routine refresh ([KEY_WHEN_AWAY]) is queued when the user
 * leaves the app and never runs while they are looking at it: it stops before the next reel if they come back. Only
 * opening Your reels asks for the reels straight away.
 */
@HiltWorker
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AutoReelWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val db: AppDatabase,
    private val mediaStore: MediaStore,
    private val library: ReelLibrary,
    private val memoryRepository: MemoryRepository,
    private val vaultKeys: com.yaadein.data.crypto.VaultKeys,
    private val lockState: com.yaadein.data.lock.AppLockState,
) : CoroutineWorker(context, params) {

    /** A routine refresh must not run while the user is in the app; leaving the app queues it again. */
    private val mustWait: Boolean get() = inputData.getBoolean(KEY_WHEN_AWAY, false) && lockState.inForeground

    override suspend fun doWork(): Result {
        // PIN-protected vault not opened in this process: nothing can be read. The next unlock queues this again.
        if (!vaultKeys.canOpenWithoutUser()) return Result.success()
        if (mustWait) return Result.success()
        if (userReelInProgress(context)) return Result.retry()
        val details = memoryRepository.details(memoryRepository.idsFor(TimelineFilter()))
        val picks = AutoReelPicker.pick(details, LocalDate.now())
        library.retainAutoSlots(picks.mapTo(HashSet()) { it.slot.key })
        val existing = library.load().filter { it.kind == ReelKind.AUTO }
        val todo = picks.filter { p -> existing.none { it.autoSlot == p.slot.key && it.signature == p.signature } }
        if (todo.isNotEmpty()) {
            RecapRenderWorker.ensureChannel(context)
            // Best effort: Android may refuse a foreground service when the app is in the background; the work still runs.
            runCatching { setForeground(foregroundInfo()) }
        }
        val style = ReelRendering.style(context)
        for (pick in todo) {
            if (isStopped) break
            // The user came back: finish here without marking the run, so the rest is made the next time they leave.
            if (mustWait) return Result.success()
            if (userReelInProgress(context)) return Result.retry()
            val title = titleFor(pick)
            val plan = RecapPlanner.plan(
                memories = pick.memories, theme = pick.theme, length = RecapLength.SHORT, title = title,
                subtitle = subtitleFor(pick.memories), outroText = context.getString(R.string.recap_outro),
                outroSubtitle = context.getString(R.string.recap_outro_credit),
                dateLabel = { Dates.label(it.memory.date, Locale.getDefault()).text },
                includeVideos = false, selectedMediaIds = pick.mediaIds,
                beatMs = bundledTracks.first { it.theme == pick.theme }.beatMs,
            )
            val output = File(mediaStore.renderDir, "auto-${pick.slot.key}-$id.mp4")
            try {
                RecapRenderer(context, db, mediaStore).render(plan, ReelRendering.bundledMusic(pick.theme), style, output) {}
                library.add(
                    output, title, ReelKind.AUTO, plan.totalDurationMs, ReelRendering.coverMediaId(plan), pick.theme.name,
                    autoSlot = pick.slot.key, signature = pick.signature,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // One reel failing should not stop the others; the next run tries it again.
                CrashLog.recordNonFatal(context, "automatic reel", e)
            } finally {
                output.delete()
            }
        }
        library.markAutoRun(System.currentTimeMillis())
        return Result.success()
    }

    private fun titleFor(pick: AutoReelPick): String = when (pick.variant) {
        AutoReelVariant.RECENT_DAYS -> context.getString(R.string.auto_reel_recent)
        AutoReelVariant.LATEST -> context.getString(R.string.auto_reel_latest)
        AutoReelVariant.PERSON -> context.getString(R.string.recap_title_person, pick.person?.displayName.orEmpty())
        AutoReelVariant.EVERYTHING -> context.getString(R.string.auto_reel_everything)
        AutoReelVariant.THIS_MONTH_YEARS_AGO -> context.getString(R.string.auto_reel_this_month)
        AutoReelVariant.THROUGH_THE_YEARS -> context.getString(R.string.auto_reel_years)
    }

    private fun subtitleFor(details: List<MemoryDetail>): String {
        val years = details.map { Dates.year(it.memory.date) }
        val min = years.minOrNull() ?: return ""
        val max = years.maxOrNull() ?: min
        return if (min == max) min.toString() else "$min – $max"
    }

    private fun foregroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, RecapRenderWorker.CHANNEL_ID)
            .setSmallIcon(CoreR.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.auto_reel_notification))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        val type = when {
            Build.VERSION.SDK_INT >= 35 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else -> 0
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ForegroundInfo(NOTIFICATION_ID, notification, type) else ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        const val WORK_NAME = "auto_reels"
        private const val NOTIFICATION_ID = 4203
        /** Look again at most this often once all three reels exist... */
        private const val REFRESH_MS = 24L * 60 * 60 * 1000
        /** ...and this often while fewer than three exist (a small library may never fill all three). */
        private const val RETRY_MISSING_MS = 60L * 60 * 1000

        /** Input flag: a routine refresh, which only runs while the app is not on screen. */
        const val KEY_WHEN_AWAY = "when_away"
        /** A routine refresh starts this long after the user leaves, so a quick trip to another app does not start it. */
        private const val AWAY_DELAY_MS = 10_000L

        /**
         * Queues a refresh when the automatic reels are missing or a day old. Cheap; safe to call often.
         * [whenAway] = the routine refresh, queued as the user leaves the app; false = the user opened Your reels and
         * wants them now.
         */
        suspend fun ensure(context: Context, library: ReelLibrary, whenAway: Boolean = false, now: Long = System.currentTimeMillis()) {
            val autos = library.load().count { it.kind == ReelKind.AUTO }
            val since = now - library.lastAutoRunAt()
            val due = since >= REFRESH_MS || (autos < ReelLibrary.MAX_AUTO_REELS && since >= RETRY_MISSING_MS)
            if (!due) return
            val manager = WorkManager.getInstance(context)
            val request = OneTimeWorkRequestBuilder<AutoReelWorker>()
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build())
                .setInputData(workDataOf(KEY_WHEN_AWAY to whenAway))
                .apply { if (whenAway) setInitialDelay(AWAY_DELAY_MS, TimeUnit.MILLISECONDS) }
                .addTag(WORK_NAME)
                .build()
            // A waiting routine refresh would not start while the app is open, so asking for the reels now replaces it;
            // a refresh that is already rendering is left alone.
            val running = manager.getWorkInfosForUniqueWorkFlow(WORK_NAME).first().any { it.state == WorkInfo.State.RUNNING }
            val policy = if (whenAway || running) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE
            manager.enqueueUniqueWork(WORK_NAME, policy, request)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)

        /** State of the automatic-reel work; empty if WorkManager is unavailable (e.g. JVM tests without it). */
        fun observeRunning(context: Context): Flow<List<WorkInfo>> =
            flow { emitAll(WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WORK_NAME)) }.catch { emit(emptyList()) }

        private suspend fun userReelInProgress(context: Context): Boolean =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(RecapRenderWorker.WORK_NAME).first()
                .any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    }
}
