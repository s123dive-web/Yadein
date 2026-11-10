package com.yaadein.feature.recap

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.yaadein.data.crash.CrashLog
import com.yaadein.data.reels.ReelEntry
import com.yaadein.data.reels.ReelKind
import com.yaadein.data.reels.ReelLibrary
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReelsUiState(
    val loading: Boolean = true,
    /** "Made for you": reels Yaadein keeps ready (up to three). */
    val auto: List<ReelEntry> = emptyList(),
    /** "Made by you": the last ten reels the user created. */
    val mine: List<ReelEntry> = emptyList(),
    /** The automatic reels are being made right now. */
    val preparing: Boolean = false,
    /** Id of the reel being decrypted for sharing. */
    val sharing: String? = null,
)

@HiltViewModel
class ReelsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: ReelLibrary,
) : ViewModel() {
    private val loaded = MutableStateFlow(false)
    private val sharing = MutableStateFlow<String?>(null)

    val state: StateFlow<ReelsUiState> = combine(library.reels, loaded, sharing, AutoReelWorker.observeRunning(context)) { reels, isLoaded, sharingId, work ->
        ReelsUiState(
            loading = !isLoaded,
            auto = reels.filter { it.kind == ReelKind.AUTO }.sortedBy { AUTO_SLOT_ORDER.indexOf(it.autoSlot) },
            mine = reels.filter { it.kind == ReelKind.USER },
            preparing = work.any { it.state == WorkInfo.State.RUNNING },
            sharing = sharingId,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReelsUiState())

    init {
        viewModelScope.launch {
            library.load()
            loaded.value = true
            runCatching { AutoReelWorker.ensure(context, library) }
        }
    }

    fun delete(id: String) { viewModelScope.launch { library.delete(id) } }

    fun share(entry: ReelEntry, chooserTitle: String, launch: (Intent) -> Unit) {
        if (sharing.value != null) return
        sharing.value = entry.id
        viewModelScope.launch {
            runCatching { library.shareIntent(entry, chooserTitle) }
                .onSuccess(launch)
                .onFailure { CrashLog.recordNonFatal(context, "reel share", it) }
            sharing.value = null
        }
    }

    /** Decrypts [entry] to the exports folder and hands its shareable URI to [send] (used by "Send to WhatsApp"). */
    fun export(entry: ReelEntry, send: (Uri) -> Unit) {
        if (sharing.value != null) return
        sharing.value = entry.id
        viewModelScope.launch {
            runCatching { library.shareUri(entry) }
                .onSuccess(send)
                .onFailure { CrashLog.recordNonFatal(context, "reel share", it) }
            sharing.value = null
        }
    }

    private companion object {
        val AUTO_SLOT_ORDER = listOf("recent", "person", "throwback")
    }
}

data class ReelPlayerUiState(val loading: Boolean = true, val reel: ReelEntry? = null, val sharing: Boolean = false, val deleted: Boolean = false)

@HiltViewModel
class ReelPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    val library: ReelLibrary,
) : ViewModel() {
    private val reelId: String = savedStateHandle["reelId"] ?: ""
    private val _state = MutableStateFlow(ReelPlayerUiState())
    val state: StateFlow<ReelPlayerUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { _state.update { it.copy(loading = false, reel = library.get(reelId)) } }
    }

    fun share(chooserTitle: String, launch: (Intent) -> Unit) {
        val reel = _state.value.reel ?: return
        if (_state.value.sharing) return
        _state.update { it.copy(sharing = true) }
        viewModelScope.launch {
            runCatching { library.shareIntent(reel, chooserTitle) }
                .onSuccess(launch)
                .onFailure { CrashLog.recordNonFatal(context, "reel share", it) }
            _state.update { it.copy(sharing = false) }
        }
    }

    /** Decrypts the reel to the exports folder and hands its shareable URI to [send] (used by "Send to WhatsApp"). */
    fun export(send: (Uri) -> Unit) {
        val reel = _state.value.reel ?: return
        if (_state.value.sharing) return
        _state.update { it.copy(sharing = true) }
        viewModelScope.launch {
            runCatching { library.shareUri(reel) }
                .onSuccess(send)
                .onFailure { CrashLog.recordNonFatal(context, "reel share", it) }
            _state.update { it.copy(sharing = false) }
        }
    }

    fun delete() {
        val reel = _state.value.reel ?: return
        viewModelScope.launch { library.delete(reel.id); _state.update { it.copy(deleted = true) } }
    }
}

/** Asks for the automatic reels to be refreshed when the app opens (unlocked); does nothing if they are fresh. */
@HiltViewModel
class AutoReelsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: ReelLibrary,
) : ViewModel() {
    private var asked = false
    fun ensure() {
        if (asked) return
        asked = true
        viewModelScope.launch { runCatching { AutoReelWorker.ensure(context, library) } }
    }
}
