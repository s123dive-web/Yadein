package com.yaadein.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yaadein.app.R
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.reels.ReelKind
import com.yaadein.data.reels.ReelLibrary
import com.yaadein.data.seed.DemoSeeder
import com.yaadein.domain.repository.ImportProgressSource
import com.yaadein.domain.repository.MemoryRepository
import com.yaadein.domain.repository.PersonRepository
import com.yaadein.domain.repository.SettingsRepository
import com.yaadein.domain.settings.UserSettings
import com.yaadein.feature.settings.FaceReindexWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.yaadein.feature.settings.R as SettingsR

/** The one card Home shows above the timeline after a photos-first start (never more than one at a time). */
sealed interface HomeCard {
    /** The sample family is loaded: say so, and offer to clear it. */
    data object Sample : HomeCard
    /** There are real memories and still no lock; shown after "Protect these memories?" was put off. */
    data object Lock : HomeCard
    /** Nobody is named yet and face suggestions are off: offer to find the faces (with the privacy text). */
    data object OfferFaces : HomeCard
    /** Face suggestions found [count] faces nobody has named. */
    data class NameFaces(val count: Int) : HomeCard
    /** Nobody is named yet and there are no faces to offer. */
    data object AddSomeone : HomeCard
}

data class HomeNudgesUiState(
    /** Show the "Protect these memories?" sheet (once; putting it off leaves [HomeCard.Lock]). */
    val lockPrompt: Boolean = false,
    val card: HomeCard? = null,
    val clearingSample: Boolean = false,
)

/** What Home offers after the first minute, as a pure rule so every path is unit-tested. */
object HomeNudges {
    fun decide(settings: UserSettings, memories: Int, photos: Int, people: Int, unknownFaces: Int): HomeNudgesUiState {
        if (!settings.onboardingDone) return HomeNudgesUiState()
        // The sample family is not the user's: no lock prompt and no people card while it is loaded.
        if (settings.demoMode) return HomeNudgesUiState(card = HomeCard.Sample)
        if (memories <= 0) return HomeNudgesUiState()
        val needsLock = !settings.pinConfigured
        val peopleCard: HomeCard? = when {
            people > 0 || settings.peopleNudgeDismissed -> null
            settings.faceSuggestions && unknownFaces > 0 -> HomeCard.NameFaces(unknownFaces)
            !settings.faceSuggestions && photos > 0 -> HomeCard.OfferFaces
            else -> HomeCard.AddSomeone
        }
        return HomeNudgesUiState(
            lockPrompt = needsLock && !settings.lockPromptPostponed,
            card = if (needsLock && settings.lockPromptPostponed) HomeCard.Lock else peopleCard,
        )
    }
}

@HiltViewModel
class HomeNudgesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val memoryRepository: MemoryRepository,
    personRepository: PersonRepository,
    importProgress: ImportProgressSource,
    db: AppDatabase,
    private val seeder: DemoSeeder,
    private val reelLibrary: ReelLibrary,
) : ViewModel() {
    /** Memories and photos/clips in the vault; -1 until first counted, so nothing flashes before the answer is known. */
    private val counts = MutableStateFlow(-1 to 0)
    private val clearing = MutableStateFlow(false)

    val state: StateFlow<HomeNudgesUiState> = combine(
        settingsRepository.settings, counts, personRepository.observeAll(), db.faceDao().observeUnknownCount(), clearing,
    ) { settings, (memories, photos), people, unknownFaces, clearingSample ->
        HomeNudges.decide(settings, memories, photos, people.size, unknownFaces).copy(clearingSample = clearingSample)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeNudgesUiState())

    init {
        // Photos finishing their import change the counts while Home is open.
        viewModelScope.launch { importProgress.observeAll().collect { refresh() } }
    }

    /** Recounts; called whenever Home comes back into view (a memory may just have been saved). */
    fun refresh() {
        viewModelScope.launch {
            counts.value = runCatching { memoryRepository.count() to memoryRepository.countMedia() }.getOrDefault(0 to 0)
        }
    }

    /** "Not now" (or opening the lock setup): the sheet is not shown again; the Home card stays until a lock is set. */
    fun postponeLock() {
        viewModelScope.launch { settingsRepository.update { it.copy(lockPromptPostponed = true) } }
    }

    /** One tap: the sample family goes, anything the user added stays, and Home shows its own empty state. */
    fun clearSample() {
        if (clearing.value) return
        clearing.value = true
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    seeder.clear()
                    // Reels made automatically while the sample was loaded show the sample's photos.
                    reelLibrary.load().filter { it.kind == ReelKind.AUTO }.forEach { reelLibrary.delete(it.id) }
                }
            }
            settingsRepository.update { it.copy(demoMode = false) }
            counts.value = runCatching { memoryRepository.count() to memoryRepository.countMedia() }.getOrDefault(0 to 0)
            clearing.value = false
        }
    }

    /** Turns face suggestions on (the card shows the privacy text) and starts looking at the photos already here. */
    fun enableFaces() {
        viewModelScope.launch {
            settingsRepository.update { it.copy(faceSuggestions = true) }
            runCatching { FaceReindexWorker.enqueue(context, now = true) }
        }
    }

    fun dismissPeople() {
        viewModelScope.launch { settingsRepository.update { it.copy(peopleNudgeDismissed = true) } }
    }
}

/**
 * Home, right under the crash notice: the sample-family banner, the lock card, or the people card (one at a time), and
 * the "Protect these memories?" sheet once the first real memory exists. Nothing here blocks the timeline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeNudges(
    onSetUpLock: () -> Unit,
    onWhoIsThis: () -> Unit,
    onAddPerson: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeNudgesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    HomeNudgeCard(
        state = state,
        onClearSample = viewModel::clearSample,
        onSetUpLock = onSetUpLock,
        onEnableFaces = viewModel::enableFaces,
        onWhoIsThis = onWhoIsThis,
        onAddPerson = onAddPerson,
        onDismissPeople = viewModel::dismissPeople,
        modifier = modifier,
    )
    if (state.lockPrompt) {
        ModalBottomSheet(onDismissRequest = viewModel::postponeLock) {
            LockPromptContent(
                onSetUp = { viewModel.postponeLock(); onSetUpLock() },
                onLater = viewModel::postponeLock,
            )
        }
    }
}

/** The sheet's content: stateless so it can be previewed and checked for labels. */
@Composable
fun LockPromptContent(onSetUp: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = Yaadein.spacing
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = spacing.xl).padding(bottom = spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(stringResource(R.string.lock_prompt_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.lock_prompt_text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onSetUp, modifier = Modifier.fillMaxWidth().heightIn(min = Yaadein.sizes.touchTarget)) {
            Text(stringResource(R.string.lock_prompt_action))
        }
        TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth().heightIn(min = Yaadein.sizes.touchTarget)) {
            Text(stringResource(R.string.nudge_not_now))
        }
    }
}

@Composable
fun HomeNudgeCard(
    state: HomeNudgesUiState,
    onClearSample: () -> Unit,
    onSetUpLock: () -> Unit,
    onEnableFaces: () -> Unit,
    onWhoIsThis: () -> Unit,
    onAddPerson: () -> Unit,
    onDismissPeople: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val card = state.card) {
        null -> Unit
        HomeCard.Sample -> NudgeCard(
            title = stringResource(R.string.sample_banner_title),
            text = stringResource(R.string.sample_banner_text),
            modifier = modifier,
            highlighted = true,
        ) {
            if (state.clearingSample) CircularProgressIndicator() else Button(onClick = onClearSample) { Text(stringResource(R.string.sample_banner_clear)) }
        }
        HomeCard.Lock -> NudgeCard(
            title = stringResource(R.string.lock_banner_title),
            text = stringResource(R.string.lock_banner_text),
            modifier = modifier,
        ) {
            Button(onClick = onSetUpLock) { Text(stringResource(R.string.lock_prompt_action)) }
        }
        HomeCard.OfferFaces -> NudgeCard(
            title = stringResource(R.string.people_nudge_faces_title),
            // The same words as Settings → Face suggestions: what is looked at, where it stays, how to delete it.
            text = stringResource(SettingsR.string.faces_privacy),
            modifier = modifier,
        ) {
            Button(onClick = onEnableFaces) { Text(stringResource(R.string.people_nudge_faces_action)) }
            TextButton(onClick = onAddPerson) { Text(stringResource(R.string.people_nudge_add_action)) }
            TextButton(onClick = onDismissPeople) { Text(stringResource(R.string.nudge_not_now)) }
        }
        is HomeCard.NameFaces -> NudgeCard(
            title = stringResource(SettingsR.string.faces_who),
            text = pluralStringResource(SettingsR.plurals.faces_pending, card.count, card.count),
            modifier = modifier,
        ) {
            Button(onClick = onWhoIsThis) { Text(stringResource(R.string.people_nudge_who_action)) }
            TextButton(onClick = onDismissPeople) { Text(stringResource(R.string.nudge_not_now)) }
        }
        HomeCard.AddSomeone -> NudgeCard(
            title = stringResource(R.string.people_nudge_add_title),
            text = stringResource(R.string.people_nudge_add_text),
            modifier = modifier,
        ) {
            Button(onClick = onAddPerson) { Text(stringResource(R.string.people_nudge_add_action)) }
            TextButton(onClick = onDismissPeople) { Text(stringResource(R.string.nudge_not_now)) }
        }
    }
}

@Composable
private fun NudgeCard(title: String, text: String, modifier: Modifier = Modifier, highlighted: Boolean = false, actions: @Composable () -> Unit) {
    val spacing = Yaadein.spacing
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = spacing.gutter, vertical = spacing.sm),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) colors.tertiaryContainer else colors.secondaryContainer,
            contentColor = if (highlighted) colors.onTertiaryContainer else colors.onSecondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium)
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) { actions() }
        }
    }
}
