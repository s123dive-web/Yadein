package com.yaadein.feature.relive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.relive.StoryPage
import com.yaadein.domain.repository.ImportProgressSource
import com.yaadein.domain.repository.MemoryImportProgress
import com.yaadein.domain.repository.MemoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class FirstStoryUiState(
    val loading: Boolean = true,
    /** The memories just made from the picked photos, newest first; a page appears as soon as its first photo is stored. */
    val pages: List<StoryPage.Moment> = emptyList(),
    val photosStored: Int = 0,
    val photosTotal: Int = 0,
) {
    val importing: Boolean get() = photosStored < photosTotal
}

/**
 * The first thing a new user sees after picking photos: those memories as a story, while the files are still being
 * copied and encrypted in the background. Pages appear as covers arrive; nothing here waits for the import to finish.
 */
@HiltViewModel
class FirstStoryViewModel @Inject constructor(
    memoryRepository: MemoryRepository,
    importProgress: ImportProgressSource,
) : ViewModel() {
    val state: StateFlow<FirstStoryUiState> = combine(memoryRepository.observeCards(), importProgress.observeAll()) { cards, progress -> stateOf(cards, progress) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FirstStoryUiState())

    companion object {
        /** A first story stays short even after a large pick. */
        const val MAX_PAGES = 8

        internal fun stateOf(cards: List<MemoryCard>, progress: Map<Long, MemoryImportProgress>): FirstStoryUiState {
            val waiting = progress.values.sumOf { (it.total - it.done).coerceAtLeast(0) }
            val stored = cards.sumOf { it.mediaCount }
            // While photos are still arriving only memories that already have a picture are shown; afterwards all of them.
            val shown = if (waiting > 0) cards.filter { it.cover != null } else cards
            return FirstStoryUiState(
                loading = false,
                pages = shown.take(MAX_PAGES).map { StoryPage.Moment(it, yearsAgo = null) },
                photosStored = stored,
                photosTotal = stored + waiting,
            )
        }
    }
}

@Composable
fun FirstStoryRoute(
    onDone: () -> Unit,
    onOpenMemory: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FirstStoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FirstStoryScreen(state = state, onDone = onDone, onOpenMemory = onOpenMemory, modifier = modifier)
}

@Composable
fun FirstStoryScreen(state: FirstStoryUiState, onDone: () -> Unit, onOpenMemory: (Long) -> Unit, modifier: Modifier = Modifier) {
    val spacing = Yaadein.spacing
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = spacing.gutter, vertical = spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = stringResource(R.string.first_story_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { heading() },
            )
            if (state.pages.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(420.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.first_story_preparing), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    }
                }
            } else {
                DailyStoryCard(
                    pages = state.pages,
                    onOpenMemory = onOpenMemory,
                    onOpenPerson = {},
                    momentLabel = stringResource(R.string.first_story_new),
                )
            }
            if (state.importing) {
                Text(
                    text = stringResource(R.string.first_story_progress, state.photosStored, state.photosTotal),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Spacer(Modifier.height(spacing.xs))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = Yaadein.sizes.touchTarget)) {
                Text(stringResource(R.string.first_story_continue))
            }
        }
    }
}
