package com.yaadein.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.yaadein.app.R
import com.yaadein.app.ui.PendingNav
import com.yaadein.data.reminders.ReminderDigestStore
import com.yaadein.domain.reminders.ReminderDigest
import com.yaadein.domain.reminders.Reminders
import com.yaadein.domain.repository.SettingsRepository
import com.yaadein.feature.relive.notify.OnThisDayWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Home-screen widget: what happened on this day, a birthday, or this week's story prompt, plus a + button that opens
 * "Today, in one line". It reads only the reminder digest (no photos), and by default shows counts rather than titles
 * (Settings → Reminders → "Show memories on the home-screen widget"), because anyone holding the phone can see it.
 */
@AndroidEntryPoint
class RemindersWidget : AppWidgetProvider() {
    @Inject lateinit var digestStore: ReminderDigestStore
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == OnThisDayWorker.ACTION_REMINDERS_REFRESHED) refresh(context)
    }

    private fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, RemindersWidget::class.java))
        if (ids.isEmpty()) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val digest = runCatching { digestStore.load() }.getOrNull()
                val showTitles = runCatching { settingsRepository.current().widgetShowsMemories }.getOrDefault(false)
                val views = render(context, digest, showTitles, LocalDate.now())
                ids.forEach { manager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_QUICK_ADD = "widgetQuickAdd"

        fun render(context: Context, digest: ReminderDigest?, showTitles: Boolean, today: LocalDate): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_reminders)
            val res = context.resources
            var openMemory: Long? = null
            val (title, text) = when {
                digest == null -> res.getString(R.string.widget_title_default) to res.getString(R.string.widget_open_app)
                else -> {
                    val matches = Reminders.onThisDay(digest, today)
                    val birthday = Reminders.birthdays(digest, today, daysAhead = 1).firstOrNull()
                    when {
                        matches.isNotEmpty() -> {
                            openMemory = matches.first().memory.id.takeIf { showTitles }
                            res.getString(R.string.widget_title_on_this_day) to
                                if (showTitles) res.getQuantityString(R.plurals.widget_years_ago, matches.first().yearsAgo, matches.first().yearsAgo, matches.first().memory.title)
                                else res.getQuantityString(R.plurals.widget_memories_waiting, matches.size, matches.size)
                        }
                        birthday != null -> res.getString(R.string.widget_title_birthday) to
                            if (showTitles) {
                                if (birthday.inDays == 0) res.getString(R.string.widget_birthday_today, birthday.person.name) else res.getString(R.string.widget_birthday_tomorrow, birthday.person.name)
                            } else {
                                res.getString(R.string.widget_birthday_hidden)
                            }
                        else -> {
                            val pick = Reminders.weeklyPrompt(digest, today)
                            res.getString(R.string.widget_title_prompt) to
                                if (showTitles) OnThisDayWorker.promptText(context, pick.index, pick.person?.name, pick.person?.relation)
                                else res.getString(R.string.widget_prompt_hidden)
                        }
                    }
                }
            }
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_text, text)
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                openMemory?.let { putExtra(OnThisDayWorker.EXTRA_MEMORY_ID, it) }
            }
            if (launch != null) {
                views.setOnClickPendingIntent(R.id.widget_body, PendingIntent.getActivity(context, 1, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                val add = Intent(launch).putExtra(EXTRA_QUICK_ADD, true).apply { removeExtra(OnThisDayWorker.EXTRA_MEMORY_ID) }
                views.setOnClickPendingIntent(R.id.widget_add, PendingIntent.getActivity(context, 2, add, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            }
            return views
        }
    }
}
