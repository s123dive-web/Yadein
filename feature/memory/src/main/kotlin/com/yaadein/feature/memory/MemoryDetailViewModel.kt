package com.yaadein.feature.memory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaadein.domain.model.MemoryDetail
import com.yaadein.domain.repository.MediaRepository
import com.yaadein.domain.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemoryDetailUiState(
    val loading: Boolean = true,
    val detail: MemoryDetail? = null,
)

@HiltViewModel
class MemoryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val memoryRepository: MemoryRepository,
    private val mediaRepository: MediaRepository,
) : ViewModel() {
    val memoryId: Long = checkNotNull(savedStateHandle["memoryId"])

    val uiState: StateFlow<MemoryDetailUiState> = memoryRepository.observeDetail(memoryId)
        .map { MemoryDetailUiState(loading = false, detail = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemoryDetailUiState())

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            val detail = uiState.value.detail
            detail?.media?.forEach { mediaRepository.delete(it.id) }
            memoryRepository.delete(memoryId)
            onDone()
        }
    }

    fun setCover(mediaId: Long) {
        viewModelScope.launch { memoryRepository.setCover(memoryId, mediaId) }
    }

    fun updateCaption(mediaId: Long, caption: String) {
        viewModelScope.launch { mediaRepository.updateCaption(mediaId, caption) }
    }

    fun removeMedia(mediaId: Long) {
        viewModelScope.launch { mediaRepository.delete(mediaId) }
    }
}
