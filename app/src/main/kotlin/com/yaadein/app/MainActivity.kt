package com.yaadein.app

import android.content.Intent
import com.yaadein.data.media.SharedMediaInbox
import android.os.Bundle
import com.yaadein.feature.relive.notify.OnThisDayWorker
import kotlinx.coroutines.flow.MutableStateFlow
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.yaadein.app.lock.BiometricHelper
import com.yaadein.app.ui.YaadeinApp
import com.yaadein.data.lock.AppLockState
import com.yaadein.domain.repository.SettingsRepository
import com.yaadein.domain.settings.LockTimeout
import com.yaadein.domain.settings.UserSettings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** FragmentActivity because BiometricPrompt requires one. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var lockState: AppLockState
    @Inject lateinit var settingsRepository: SettingsRepository

    private lateinit var settings: StateFlow<UserSettings?>
    @Volatile private var lockTimeout: LockTimeout = LockTimeout.ONE_MINUTE
    /** Memory to open once unlocked (from an "On this day" notification). */
    private val pendingMemory = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settings = settingsRepository.settings.stateIn(lifecycleScope, SharingStarted.Eagerly, null)
        splash.setKeepOnScreenCondition { settings.value == null }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                settings.collect { s ->
                    if (s == null) return@collect
                    lockTimeout = s.lockTimeout
                    // FLAG_SECURE: no screenshots, no preview in Recents.
                    if (s.secureScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        pendingMemory.value = intent?.getLongExtra(OnThisDayWorker.EXTRA_MEMORY_ID, -1L)?.takeIf { it > 0 }
        // A share from a gallery app; skip it when the activity is only being recreated (rotation, process restore).
        if (savedInstanceState == null) receiveShare(intent)
        val biometricLauncher = BiometricHelper.launcher(this)
        setContent {
            val current by settings.collectAsStateWithLifecycle()
            val locked by lockState.locked.collectAsStateWithLifecycle()
            current?.let { s ->
                val pending by pendingMemory.collectAsStateWithLifecycle()
                val shared by SharedMediaInbox.pending.collectAsStateWithLifecycle()
                YaadeinApp(
                    hasSharedMedia = shared.isNotEmpty(),
                    settings = s,
                    locked = locked,
                    biometricLauncher = biometricLauncher,
                    biometricAvailable = biometricLauncher != null,
                    pendingMemoryId = pending,
                    onPendingConsumed = { pendingMemory.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingMemory.value = intent.getLongExtra(OnThisDayWorker.EXTRA_MEMORY_ID, -1L).takeIf { it > 0 }
        receiveShare(intent)
    }

    /** Copies shared photos/videos into the app cache now, while the sender's read grant is valid. */
    private fun receiveShare(intent: Intent?) {
        if (!SharedMediaInbox.isShare(intent)) return
        lifecycleScope.launch { SharedMediaInbox.receive(applicationContext, intent!!) }
    }

    override fun onStart() {
        super.onStart()
        lockState.onForeground(System.currentTimeMillis(), lockTimeout)
    }

    override fun onStop() {
        super.onStop()
        lockState.onBackground(System.currentTimeMillis())
    }
}
