package com.yaadein.feature.capture

import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import com.yaadein.data.lock.AppLockState
import com.yaadein.domain.capture.CaptureEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** What Home hands to the Quick Save screen after the Photo Picker closes. */
data class QuickSaveRequest(
    val uris: List<Uri>,
    val entry: CaptureEntry,
    /** `SystemClock.elapsedRealtime()` of the + tap: the start of the tap-to-saved clock. */
    val startedAtElapsed: Long,
)

/**
 * Hands picked photos from the + button (Home) to the Quick Save screen, and from Quick Save's "Split" to Photo-first.
 * A route argument cannot carry a hundred URIs; this in-memory inbox is read once by the screen's ViewModel.
 */
object QuickSaveInbox {
    @Volatile private var pending: QuickSaveRequest? = null
    @Volatile private var forSplit: List<Uri>? = null

    fun offer(request: QuickSaveRequest) { pending = request }

    /** The waiting request, or null; cleared on read. */
    fun take(): QuickSaveRequest? = pending.also { pending = null }

    fun offerForSplit(uris: List<Uri>) { forSplit = uris }

    fun takeForSplit(): List<Uri>? = forSplit.also { forSplit = null }
}

/**
 * The + button's helper: remembers when it was tapped, keeps the PIN away while the system Photo Picker is open, and
 * decides where the result goes (photos → Quick Save; nothing picked → Quick Save in words-only mode).
 */
@HiltViewModel
class QuickSaveLaunchViewModel @Inject constructor(private val lockState: AppLockState) : ViewModel() {
    private var tappedAt: Long = 0L

    /** Call right before launching the Photo Picker. */
    fun beforePicker() {
        tappedAt = SystemClock.elapsedRealtime()
        lockState.allowExternalScreen(System.currentTimeMillis())
    }

    /** Stores the picker result for the Quick Save screen; returns true when photos were picked. */
    fun onPicked(uris: List<Uri>, entry: CaptureEntry = CaptureEntry.FAB): Boolean {
        val startedAt = if (tappedAt == 0L) SystemClock.elapsedRealtime() else tappedAt
        QuickSaveInbox.offer(QuickSaveRequest(uris, entry, startedAt))
        tappedAt = 0L
        return uris.isNotEmpty()
    }
}
