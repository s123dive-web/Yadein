package com.yaadein.app.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaadein.data.reminders.ReminderDigestStore
import com.yaadein.domain.settings.UserSettings
import com.yaadein.feature.relive.notify.OnThisDayWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Light housekeeping each time the vault is open in the foreground: rebuild the reminder digest (so tomorrow's
 * notification and the widget see today's memories even while locked), arm the daily reminders, and redraw the widget.
 *
 * It waits a few seconds and runs off the main thread, so the timeline's first page is read and drawn first. The heavy
 * jobs (automatic reels, weekly backup, face re-index) are not started here at all: see [com.yaadein.app.BackgroundWork].
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val digestStore: ReminderDigestStore,
) : ViewModel() {
    private var done = false

    fun onUnlocked(settings: UserSettings) {
        if (done) return
        done = true
        viewModelScope.launch(Dispatchers.Default) {
            delay(SETTLE_MS)
            runCatching { digestStore.refresh() }
            // WorkManager can be unavailable (tests, a broken install); reminders are best effort.
            runCatching { OnThisDayWorker.sync(context, settings) }
            runCatching { context.sendBroadcast(Intent(OnThisDayWorker.ACTION_REMINDERS_REFRESHED).setPackage(context.packageName)) }
        }
    }

    private companion object {
        /** Long enough for the first screen to load and settle. */
        const val SETTLE_MS = 3_000L
    }
}
