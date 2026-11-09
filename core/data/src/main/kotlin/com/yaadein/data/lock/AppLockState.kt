package com.yaadein.data.lock

import com.yaadein.domain.settings.LockTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory lock state. The vault starts locked on every cold start and re-locks after the
 * configured background timeout. Pure logic so it is unit-testable; the Activity feeds it
 * lifecycle events.
 */
@Singleton
class AppLockState @Inject constructor() {
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    @Volatile private var backgroundedAt: Long? = null
    @Volatile private var externalScreenUntil: Long = 0L

    fun unlock() {
        _locked.value = false
        backgroundedAt = null
    }

    fun lock() {
        _locked.value = true
    }

    fun onBackground(now: Long) {
        if (!_locked.value) backgroundedAt = now
    }

    fun onForeground(now: Long, timeout: LockTimeout) {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        // Coming back from a system screen the app itself opened (file picker, install permission, installer).
        if (now <= externalScreenUntil) { externalScreenUntil = 0L; return }
        if (now - since >= timeout.millis) lock()
    }

    /**
     * The app is about to open a system screen as part of a flow (e.g. Update app). Returning within [graceMillis]
     * does not ask for the PIN again, so the flow is not interrupted. Only callable while unlocked.
     */
    fun allowExternalScreen(now: Long, graceMillis: Long = EXTERNAL_SCREEN_GRACE_MS) {
        if (!_locked.value) externalScreenUntil = now + graceMillis
    }

    companion object {
        const val EXTERNAL_SCREEN_GRACE_MS = 5 * 60_000L
    }
}
