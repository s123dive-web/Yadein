package com.yaadein.feature.relive

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.media.MediaStore
import com.yaadein.data.recap.MusicLibrary
import com.yaadein.data.recap.OwnSong
import com.yaadein.domain.relive.RingKind
import com.yaadein.domain.relive.StoryPage
import com.yaadein.domain.relive.StorySeen
import com.yaadein.domain.relive.memoryId
import com.yaadein.domain.repository.MemoryRepository
import com.yaadein.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class StoryPlayerUiState(
    val loading: Boolean = true,
    val rings: List<RingUi> = emptyList(),
    /** Index into [rings] of the story on screen. */
    val ringIndex: Int = 0,
    /** Pages of the story on screen; empty while they load. */
    val pages: List<StoryPage> = emptyList(),
    val pagesLoading: Boolean = true,
    /** Memory ids marked as favourites (kept in step with the heart without reloading the story). */
    val favorites: Set<Long> = emptySet(),
    /** Music is off by the user's choice (remembered in the settings). */
    val muted: Boolean = false,
    /** The song from "Your songs" that plays under stories, or null for the bundled music. */
    val musicUri: String? = null,
    val songs: List<OwnSong> = emptyList(),
    /** Set after the last story ends, or when there is nothing to play: the screen closes. */
    val finished: Boolean = false,
) {
    val ring: RingUi? get() = rings.getOrNull(ringIndex)
    val hasNextRing: Boolean get() = ringIndex < rings.lastIndex
    val hasPreviousRing: Boolean get() = ringIndex > 0
}

/** What "Make a reel from this story" opens the reel screen with: the same person, the same year, or one memory. */
data class ReelScope(val personId: Long = 0L, val year: Int = 0, val memoryId: Long = 0L)

/**
 * The full-screen story player: one story per ring, moving on to the next ring when a story ends. Watching a ring
 * marks it seen for today; the heart, "Add what you remember" and the music choice are saved straight away.
 */
@HiltViewModel
class StoryPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val stories: StoryLoader,
    private val settingsRepository: SettingsRepository,
    private val memoryRepository: MemoryRepository,
    private val musicLibrary: MusicLibrary,
    /** For the route's encrypted video player (muted clips on video pages). */
    val db: AppDatabase,
    val mediaStore: MediaStore,
) : ViewModel() {
    private val startKey: String = savedStateHandle["ringKey"] ?: ""
    private val today: LocalDate = LocalDate.now()
    private val _state = MutableStateFlow(StoryPlayerUiState())
    val state: StateFlow<StoryPlayerUiState> = _state.asStateFlow()
    private var pagesJob: Job? = null

    init {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            val rings = runCatching { stories.rings(today, settings.storySeen) }.getOrDefault(emptyList())
            val songs = runCatching { musicLibrary.load() }.getOrDefault(emptyList())
            if (rings.isEmpty()) {
                _state.update { it.copy(loading = false, finished = true) }
                return@launch
            }
            val index = rings.indexOfFirst { it.ring.key == startKey }.coerceAtLeast(0)
            _state.update {
                it.copy(loading = false, rings = rings, muted = settings.storyMuted, songs = songs, musicUri = settings.storyMusicUri?.takeIf { uri -> songs.any { s -> s.uri == uri } })
            }
            open(index)
        }
    }

    private fun open(index: Int) {
        val ring = _state.value.rings.getOrNull(index) ?: return
        _state.update { it.copy(ringIndex = index, pages = emptyList(), pagesLoading = true) }
        pagesJob?.cancel()
        pagesJob = viewModelScope.launch {
            val pages = runCatching { stories.pages(ring.ring, today) }.getOrDefault(emptyList())
            val favorites = pages.mapNotNull { page ->
                when (page) {
                    is StoryPage.Moment -> page.card.memory.takeIf { it.favorite }?.id
                    is StoryPage.Spotlight -> page.card?.memory?.takeIf { it.favorite }?.id
                    is StoryPage.Milestone -> page.card?.memory?.takeIf { it.favorite }?.id
                    is StoryPage.ThenNow -> null
                }
            }
            _state.update { s ->
                s.copy(
                    pages = pages, pagesLoading = false, favorites = s.favorites + favorites,
                    rings = s.rings.mapIndexed { i, r -> if (i == index) r.copy(seen = true) else r },
                )
            }
            settingsRepository.update { it.copy(storySeen = StorySeen.mark(it.storySeen, ring.ring.key, today)) }
        }
    }

    /** The story ended or the user swiped on: the next ring, or the end of the player after the last one. */
    fun nextRing() {
        val s = _state.value
        if (s.hasNextRing) open(s.ringIndex + 1) else _state.update { it.copy(finished = true) }
    }

    fun previousRing() {
        val s = _state.value
        if (s.hasPreviousRing) open(s.ringIndex - 1)
    }

    fun toggleFavorite(memoryId: Long) {
        val favorite = memoryId !in _state.value.favorites
        _state.update { it.copy(favorites = if (favorite) it.favorites + memoryId else it.favorites - memoryId) }
        viewModelScope.launch { runCatching { memoryRepository.setFavorite(memoryId, favorite) } }
    }

    /** "Add what you remember": [text] becomes a new paragraph of the memory's description. */
    fun addNote(memoryId: Long, text: String) {
        val note = text.trim()
        if (note.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val detail = memoryRepository.observeDetail(memoryId).first() ?: return@runCatching
                val description = listOf(detail.memory.description.trim(), note).filter { it.isNotEmpty() }.joinToString("\n\n")
                memoryRepository.update(detail.memory.copy(description = description), detail.people.map { it.id })
            }
        }
    }

    fun setMuted(muted: Boolean) {
        _state.update { it.copy(muted = muted) }
        viewModelScope.launch { settingsRepository.update { it.copy(storyMuted = muted) } }
    }

    /** Null picks the bundled music. Choosing music also turns it on. */
    fun chooseMusic(uri: String?) {
        _state.update { it.copy(musicUri = uri, muted = false) }
        viewModelScope.launch { settingsRepository.update { it.copy(storyMusicUri = uri, storyMuted = false) } }
    }

    /** Songs picked with the system file picker: remembered in "Your songs"; the first one starts playing. */
    fun addSongs(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val added = runCatching { musicLibrary.add(uris) }.getOrDefault(emptyList())
            val songs = runCatching { musicLibrary.load() }.getOrDefault(_state.value.songs)
            _state.update { it.copy(songs = songs) }
            added.firstOrNull()?.let { chooseMusic(it.uri) }
        }
    }

    /** The reel screen's scope for the story on screen; [page] is the page being looked at. */
    fun reelScope(page: StoryPage?): ReelScope {
        val ring = _state.value.ring?.ring
        return when (ring?.kind) {
            RingKind.PERSON -> ReelScope(personId = ring.person?.id ?: 0L)
            RingKind.YEAR -> ReelScope(year = ring.year ?: 0)
            else -> ReelScope(memoryId = page?.memoryId ?: 0L)
        }
    }
}
