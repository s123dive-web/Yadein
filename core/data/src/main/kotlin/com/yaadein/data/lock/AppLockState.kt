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
        if (now - since >= timeout.millis) lock()
    }
}
