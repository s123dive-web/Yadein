package com.yaadein.app

import android.content.Context
import com.yaadein.data.backup.AutoBackup
import com.yaadein.data.crypto.VaultKeys
import com.yaadein.data.reels.ReelLibrary
import com.yaadein.domain.repository.SettingsRepository
import com.yaadein.feature.recap.AutoReelWorker
import com.yaadein.feature.settings.AutoBackupWorker
import com.yaadein.feature.settings.FaceReindexWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The heavy routine jobs: the three automatic reels (video encoding), the weekly backup and the face re-index. They
 * used to be queued the moment the app opened and then ran next to whatever the user was doing, which made the app
 * (and the phone) slow right when it was being used. They are queued here instead, as the user leaves the app, and
 * each worker stops again if the user comes back ([com.yaadein.data.lock.AppLockState.inForeground]).
 */
@Singleton
class BackgroundWork @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val reelLibrary: dagger.Lazy<ReelLibrary>,
    private val vaultKeys: VaultKeys,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The app's screen is no longer showing. Nothing is queued unless the vault is open and there is something due. */
    fun onAppLeft() {
        scope.launch {
            try {
                // A locked vault cannot be read, and the welcome screen has nothing to work on yet.
                if (!vaultKeys.isOpen) return@launch
                val settings = settingsRepository.current()
                if (!settings.onboardingDone) return@launch
                // WorkManager can be unavailable (tests, a broken install); all three are best effort.
                runCatching { AutoReelWorker.ensure(context, reelLibrary.get(), whenAway = true) }
                if (AutoBackup.isDue(settings, System.currentTimeMillis())) runCatching { AutoBackupWorker.enqueue(context, whenAway = true) }
                // Photos added while face suggestions were off, or scanned with an older model.
                if (settings.faceSuggestions) runCatching { FaceReindexWorker.enqueue(context, whenAway = true) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best effort: the next time the app is left it is tried again.
            }
        }
    }
}
