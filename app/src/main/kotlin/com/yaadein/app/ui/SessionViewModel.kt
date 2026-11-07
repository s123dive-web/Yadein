package com.yaadein.app.ui

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaadein.data.backup.AutoBackup
import com.yaadein.data.reminders.ReminderDigestStore
import com.yaadein.feature.settings.AutoBackupWorker
import com.yaadein.domain.settings.UserSettings
import com.yaadein.feature.relive.notify.OnThisDayWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Housekeeping each time the vault is open in the foreground: rebuild the reminder digest (so tomorrow's notification
 * and the widget see today's memories even while locked), arm the daily reminders, and redraw the widget.
 */
@HiltViewModel
class SessionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val digestStore: ReminderDigestStore,
) : ViewModel() {
    // Note: automatic backups are checked here too (vault is open now).
    private var done = false

    fun onUnlocked(settings: UserSettings) {
        if (done) return
        done = true
        viewModelScope.launch {
            runCatching { digestStore.refresh() }
            // WorkManager can be unavailable (tests, a broken install); reminders are best effort.
            runCatching { OnThisDayWorker.sync(context, settings) }
            runCatching { context.sendBroadcast(Intent(OnThisDayWorker.ACTION_REMINDERS_REFRESHED).setPackage(context.packageName)) }
            if (AutoBackup.isDue(settings, System.currentTimeMillis())) runCatching { AutoBackupWorker.enqueue(context) }
        }
    }
}
