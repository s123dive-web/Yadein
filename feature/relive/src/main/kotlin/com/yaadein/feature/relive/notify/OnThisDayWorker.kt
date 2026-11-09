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
import com.yaadein.data.reminders.ReminderDigestStore
import com.yaadein.domain.reminders.Reminders
import com.yaadein.domain.repository.SettingsRepository
import com.yaadein.domain.settings.UserSettings
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
 * The daily reminder run (class name kept so work queued by older versions still resolves): "On this day", birthdays
 * today or tomorrow, and on Sundays a story prompt. Scheduled as a one-time job at the user's chosen minute and re-armed
 * after each run, so changing the time takes effect immediately and nothing fires while every reminder is off.
 *
 * Reads the [ReminderDigestStore], not the database, so it works when Android starts the app in the background while
 * the PIN-protected vault is still locked. Requires POST_NOTIFICATIONS (asked in Settings → Reminders).
 */
@HiltWorker
class OnThisDayWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val digestStore: ReminderDigestStore,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = settingsRepository.current()
        if (!anyEnabled(settings)) return Result.success()
        val digest = digestStore.refreshOrLoad()
        val today = LocalDate.now()
        val context = applicationContext
        if (digest != null) {
            if (settings.onThisDayEnabled) {
                Reminders.onThisDay(digest, today).firstOrNull()?.let { match ->
                    val text = context.resources.getQuantityString(R.plurals.notification_years_ago, match.yearsAgo, match.yearsAgo)
                    notify(context, match.memory.id.toInt(), context.getString(R.string.notification_on_this_day_title, match.memory.title), text,
                        Intent().putExtra(EXTRA_MEMORY_ID, match.memory.id))
                }
            }
            if (settings.birthdayReminders) {
                Reminders.birthdays(digest, today, daysAhead = 1).forEach { b ->
                    val title = if (b.inDays == 0) context.getString(R.string.notification_birthday_today, b.person.name)
                    else context.getString(R.string.notification_birthday_tomorrow, b.person.name)
                    notify(context, BIRTHDAY_ID_BASE + b.person.id.toInt(), title, context.getString(R.string.notification_birthday_text),
                        Intent().putExtra(EXTRA_REEL_PERSON_ID, b.person.id))
                }
            }
            if (settings.storyPrompts && Reminders.isPromptDay(today)) {
                val pick = Reminders.weeklyPrompt(digest, today)
                val text = promptText(context, pick.index, pick.person?.name, pick.person?.relation)
                notify(context, PROMPT_ID, context.getString(R.string.notification_prompt_title), text,
                    Intent().putExtra(EXTRA_PROMPT, text).putExtra(EXTRA_PROMPT_PERSON_ID, pick.person?.id ?: 0L))
            }
        }
        // The home-screen widget shows the same digest.
        context.sendBroadcast(Intent(ACTION_REMINDERS_REFRESHED).setPackage(context.packageName))
        schedule(context, settings.onThisDayMinuteOfDay)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "on_this_day"
        const val CHANNEL_ID = "on_this_day"
        const val EXTRA_MEMORY_ID = "memoryId"
        /** Birthday notification: open "Create a reel" for this person. */
        const val EXTRA_REEL_PERSON_ID = "reelPersonId"
        /** Story prompt notification: open "Add a memory" with this question. */
        const val EXTRA_PROMPT = "prompt"
        const val EXTRA_PROMPT_PERSON_ID = "promptPersonId"
        /** Sent after each run so the widget can redraw. */
        const val ACTION_REMINDERS_REFRESHED = "com.yaadein.app.REMINDERS_REFRESHED"
        private const val BIRTHDAY_ID_BASE = 500_000
        private const val PROMPT_ID = 499_999

        fun anyEnabled(s: UserSettings) = s.onThisDayEnabled || s.birthdayReminders || s.storyPrompts

        /** Arms or cancels the daily run to match the settings. */
        fun sync(context: Context, settings: UserSettings) {
            if (anyEnabled(settings)) schedule(context, settings.onThisDayMinuteOfDay) else cancel(context)
        }

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

        /** Prompt text for [index]; [name] fills the person placeholder. Shared with the Relive prompt card. */
        fun promptText(context: Context, index: Int, name: String?, relation: com.yaadein.domain.model.Relation?): String {
            val res = context.resources
            return when {
                name == null -> res.getStringArray(R.array.prompts_self).let { it[index % it.size] }
                relation == com.yaadein.domain.model.Relation.FAMILY -> res.getStringArray(R.array.prompts_family).let { it[index % it.size] }.format(name)
                else -> res.getStringArray(R.array.prompts_friend).let { it[index % it.size] }.format(name)
            }
        }

        private fun notify(context: Context, id: Int, title: String, text: String, extras: Intent) {
            if (!canNotify(context)) return
            ensureChannel(context)
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtras(extras)
            } ?: return
            val pending = PendingIntent.getActivity(context, id, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(CoreR.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET) // never show titles or names on the lock screen
                .build()
            try {
                NotificationManagerCompat.from(context).notify(id, notification)
            } catch (_: SecurityException) { }
        }
    }
}
