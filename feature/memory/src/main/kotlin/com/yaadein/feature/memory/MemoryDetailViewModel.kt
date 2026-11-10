package com.yaadein.feature.memory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.faces.FaceState
import com.yaadein.data.faces.FaceSuggester
import com.yaadein.data.media.MediaDownloader
import com.yaadein.data.media.VoiceNotes
import com.yaadein.data.backup.BackupManager
import com.yaadein.data.recap.RenderStyle
import com.yaadein.data.share.MemoryExporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.yaadein.domain.model.MemoryDetail
import com.yaadein.domain.model.Person
import com.yaadein.domain.repository.MediaRepository
import com.yaadein.domain.repository.MemoryRepository
import com.yaadein.domain.repository.PersonRepository
import kotlinx.coroutines.flow.combine
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

/** "Is this Aaji?": a face on one of this memory's photos that looks like [person]; [auto] when it was tagged automatically. */
data class FaceSuggestionUi(val faceId: Long, val person: Person, val auto: Boolean)

/** Progress and outcome of a share/export started from the memory screen. */
sealed interface ShareStatus {
    data object Idle : ShareStatus
    data object Working : ShareStatus
    data object Saved : ShareStatus
    data class Failed(val message: String?) : ShareStatus
}

@HiltViewModel
class MemoryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val memoryRepository: MemoryRepository,
    private val mediaRepository: MediaRepository,
    val voiceNotes: VoiceNotes,
    private val exporter: MemoryExporter,
    private val backup: BackupManager,
    downloader: MediaDownloader,
    private val personRepository: PersonRepository,
    private val suggester: FaceSuggester,
    db: AppDatabase,
) : ViewModel() {
    /** Saves this memory's photos and videos to the phone (gallery, or a picked folder on Android 8–9). */
    val download = DownloadController(viewModelScope, downloader)

    private val _share = MutableStateFlow<ShareStatus>(ShareStatus.Idle)
    val share: StateFlow<ShareStatus> = _share.asStateFlow()

    fun resetShare() { _share.value = ShareStatus.Idle }

    /** Picture card for WhatsApp and friends; [onIntent] receives the share sheet. */
    fun shareCard(labels: MemoryExporter.Labels, style: RenderStyle, chooserTitle: String, onIntent: (android.content.Intent) -> Unit) {
        val detail = uiState.value.detail ?: return
        viewModelScope.launch {
            _share.value = ShareStatus.Working
            runCatching { exporter.shareCard(detail, labels, style, chooserTitle) }
                .onSuccess { _share.value = ShareStatus.Idle; onIntent(it) }
                .onFailure { _share.value = ShareStatus.Failed(it.message) }
        }
    }

    fun savePdf(target: android.net.Uri, dateText: String, subtitle: String, style: RenderStyle, footer: String) {
        val detail = uiState.value.detail ?: return
        viewModelScope.launch {
            _share.value = ShareStatus.Working
            runCatching { exporter.writeBook(target, detail.memory.title, subtitle, listOf(MemoryExporter.BookEntry(detail, dateText)), style, footer) }
                .onSuccess { _share.value = ShareStatus.Saved }
                .onFailure { _share.value = ShareStatus.Failed(it.message) }
        }
    }

    /** Encrypted family pack with just this memory. */
    fun savePack(target: android.net.Uri, password: String) {
        viewModelScope.launch {
            _share.value = ShareStatus.Working
            runCatching { backup.exportPack(target, password.toCharArray(), setOf(memoryId)) }
                .onSuccess { _share.value = ShareStatus.Saved }
                .onFailure { _share.value = ShareStatus.Failed(it.message) }
        }
    }

    val memoryId: Long = checkNotNull(savedStateHandle["memoryId"])

    val uiState: StateFlow<MemoryDetailUiState> = memoryRepository.observeDetail(memoryId)
        .map { MemoryDetailUiState(loading = false, detail = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemoryDetailUiState())

    /** Face suggestions for this memory's photos, one chip per person (the best-matching face), best first. */
    val suggestions: StateFlow<List<FaceSuggestionUi>> = combine(
        db.faceDao().observeSuggestionsForMemory(memoryId),
        personRepository.observeAll(),
    ) { faces, people ->
        val byId = people.associateBy { it.id }
        faces.sortedByDescending { it.similarity ?: 0f }
            .distinctBy { it.personId }
            .mapNotNull { face ->
                val person = face.personId?.let { byId[it] } ?: return@mapNotNull null
                FaceSuggestionUi(faceId = face.id, person = person, auto = face.state == FaceState.AUTO)
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** "Yes, that's them": tags the person and teaches the model. */
    fun confirmSuggestion(faceId: Long, personId: Long) {
        viewModelScope.launch { runCatching { suggester.confirm(faceId, personId) } }
    }

    /** "Not them": the suggestion goes away for good (an automatic tag is removed). */
    fun rejectSuggestion(faceId: Long, personId: Long) {
        viewModelScope.launch { runCatching { suggester.reject(faceId, personId) } }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            val detail = uiState.value.detail
            detail?.media?.forEach { mediaRepository.delete(it.id) }
            detail?.memory?.voiceNotePath?.let(voiceNotes::discard)
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

    fun removeVoice() {
        viewModelScope.launch { voiceNotes.attach(memoryId, null) }
    }

    fun removeMedia(mediaId: Long) {
        viewModelScope.launch { mediaRepository.delete(mediaId) }
    }
}
