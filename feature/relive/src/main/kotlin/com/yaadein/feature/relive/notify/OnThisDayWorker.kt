package com.yaadein.feature.relive.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.repository.MemoryRepository
import com.yaadein.domain.repository.SettingsRepository
import com.yaadein.feature.relive.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import com.yaadein.core.ui.R as CoreR

/**
 * Daily "On this day" reminder. Scheduled as a one-time job at the user's chosen minute of the day
 * and re-armed after each run, so changing the time takes effect immediately and nothing fires
 * while the feature is off. Requires POST_NOTIFICATIONS (asked in Settings → Relive reminders).
 */
@HiltWorker
class OnThisDayWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val memoryRepository: MemoryRepository,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.current()
        if (!settings.onThisDayEnabled) return Result.success()
        val today = LocalDate.now()
        val memories = memoryRepository.onThisDay(Dates.monthDayKey(today), excludeYear = today.year)
        val card = memories.firstOrNull()
        if (card != null) {
            val years = today.year - Dates.year(card.memory.date)
            notify(applicationContext, card.memory.id, card.memory.title, applicationContext.resources.getQuantityString(R.plurals.notification_years_ago, years, years))
        }
        schedule(applicationContext, settings.onThisDayMinuteOfDay)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "on_this_day"
        const val CHANNEL_ID = "on_this_day"
        const val EXTRA_MEMORY_ID = "memoryId"

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_on_this_day), NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }

        fun canNotify(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        fun schedule(context: Context, minuteOfDay: Int) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next)
            val request = OneTimeWorkRequestBuilder<OnThisDayWorker>()
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        private fun notify(context: Context, memoryId: Long, title: String, text: String) {
            if (!canNotify(context)) return
            ensureChannel(context)
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_MEMORY_ID, memoryId)
            } ?: return
            val pending = PendingIntent.getActivity(context, memoryId.toInt(), launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(CoreR.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notification_on_this_day_title, title))
                .setContentText(text)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET) // never show memory titles on the lock screen
                .build()
            try {
                NotificationManagerCompat.from(context).notify(memoryId.toInt(), notification)
            } catch (_: SecurityException) { }
        }
    }
}
