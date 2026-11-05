package com.yaadein.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yaadein.core.ui.components.PersonAvatar
import com.yaadein.core.ui.media.ThumbnailImage
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.data.db.AppDatabase
import com.yaadein.data.db.entity.FaceClusterEntity
import com.yaadein.data.faces.FaceSuggester
import com.yaadein.domain.model.Person
import com.yaadein.domain.repository.MediaRepository
import com.yaadein.domain.repository.PersonRepository
import com.yaadein.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.yaadein.core.ui.R as CoreR

data class FaceSuggestion(val cluster: FaceClusterEntity, val suggested: Person?)

data class FacesUiState(
    val enabled: Boolean = false,
    val people: List<Person> = emptyList(),
    val suggestions: List<FaceSuggestion> = emptyList(),
    val scanning: Boolean = false,
    val scanned: Int = 0,
    val scanTotal: Int = 0,
)

@HiltViewModel
class FacesViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val personRepository: PersonRepository,
    private val mediaRepository: MediaRepository,
    private val suggester: FaceSuggester,
    private val db: AppDatabase,
) : ViewModel() {
    private val scan = MutableStateFlow(Triple(false, 0, 0))

    val state: StateFlow<FacesUiState> = combine(settingsRepository.settings, personRepository.observeAll(), db.faceDao().observeAll(), scan) { s, people, clusters, sc ->
        FacesUiState(
            enabled = s.faceSuggestions, people = people,
            suggestions = clusters.filter { !it.confirmed }.map { c -> FaceSuggestion(c, people.firstOrNull { it.id == c.suggestedPersonId }) },
            scanning = sc.first, scanned = sc.second, scanTotal = sc.third,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FacesUiState())

    fun setEnabled(v: Boolean) { viewModelScope.launch { settingsRepository.update { it.copy(faceSuggestions = v) }; if (!v) suggester.clearAll() } }

    fun scanLibrary() {
        viewModelScope.launch {
            val all = db.mediaDao().all().filter { it.kind == "PHOTO" }
            scan.value = Triple(true, 0, all.size)
            all.forEachIndexed { i, m -> runCatching { suggester.scan(m.id) }; scan.value = Triple(true, i + 1, all.size) }
            scan.value = Triple(false, all.size, all.size)
        }
    }

    /** "Yes, this is Rahul": confirm and tag the sample photo with the person. */
    fun confirm(cluster: FaceClusterEntity, person: Person) {
        viewModelScope.launch {
            db.faceDao().assign(cluster.id, person.id, confirmed = true)
            cluster.sampleMediaId?.let { mediaId ->
                val existing = db.mediaDao().personIds(mediaId)
                if (person.id !in existing) mediaRepository.setPeople(mediaId, existing + person.id)
            }
        }
    }

    fun dismiss(cluster: FaceClusterEntity) { viewModelScope.launch { db.faceDao().delete(cluster.id) } }
}

@Composable
fun FacesRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: FacesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FacesScreen(state = state, onBack = onBack, onEnabled = viewModel::setEnabled, onScan = viewModel::scanLibrary, onConfirm = viewModel::confirm, onDismiss = viewModel::dismiss, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FacesScreen(state: FacesUiState, onBack: () -> Unit, onEnabled: (Boolean) -> Unit, onScan: () -> Unit, onConfirm: (FaceClusterEntity, Person) -> Unit, onDismiss: (FaceClusterEntity) -> Unit, modifier: Modifier = Modifier) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_faces), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(CoreR.string.action_back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = spacing.xxxl), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            item(key = "toggle") {
                Column {
                    SettingsSwitchRow(icon = Icons.Rounded.Face, title = stringResource(R.string.faces_enable), subtitle = stringResource(R.string.faces_enable_help), checked = state.enabled, onCheckedChange = onEnabled)
                    Text(stringResource(R.string.faces_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = spacing.gutter))
                }
            }
            if (state.enabled) {
                item(key = "scan") {
                    Column(Modifier.padding(horizontal = spacing.gutter), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        if (state.scanning) {
                            Text(stringResource(R.string.faces_scanning, state.scanned, state.scanTotal), style = MaterialTheme.typography.bodyMedium)
                            LinearProgressIndicator(progress = { if (state.scanTotal == 0) 0f else state.scanned / state.scanTotal.toFloat() }, modifier = Modifier.fillMaxWidth())
                        } else {
                            OutlinedButton(onClick = onScan) { Text(stringResource(R.string.faces_scan)) }
                        }
                        Text(if (state.suggestions.isEmpty()) stringResource(R.string.faces_none) else pluralStringResource(R.plurals.faces_pending, state.suggestions.size, state.suggestions.size), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    }
                }
                items(state.suggestions.size, key = { state.suggestions[it].cluster.id }) { i ->
                    SuggestionCard(s = state.suggestions[i], people = state.people, onConfirm = onConfirm, onDismiss = onDismiss, modifier = Modifier.padding(horizontal = spacing.gutter))
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(s: FaceSuggestion, people: List<Person>, onConfirm: (FaceClusterEntity, Person) -> Unit, onDismiss: (FaceClusterEntity) -> Unit, modifier: Modifier = Modifier) {
    val spacing = Yaadein.spacing
    var picked by remember(s.cluster.id) { mutableStateOf(s.suggested) }
    Surface(shape = RoundedCornerShape(Yaadein.radius.lg), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                s.cluster.sampleMediaId?.let { ThumbnailImage(mediaId = it, contentDescription = null, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(Yaadein.radius.md))) }
                Spacer(Modifier.width(spacing.md))
                Column {
                    Text(picked?.let { stringResource(R.string.faces_is_this, it.displayName) } ?: stringResource(R.string.faces_who), style = MaterialTheme.typography.titleMedium)
                    Text(pluralStringResource(R.plurals.faces_seen, s.cluster.faceCount, s.cluster.faceCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                people.forEach { p -> FilterChip(selected = picked?.id == p.id, onClick = { picked = p }, label = { Text(p.displayName) }, leadingIcon = { PersonAvatar(person = p, size = Yaadein.sizes.iconLg) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Button(enabled = picked != null, onClick = { picked?.let { onConfirm(s.cluster, it) } }) { Text(stringResource(R.string.faces_yes)) }
                TextButton(onClick = { onDismiss(s.cluster) }) { Text(stringResource(R.string.faces_not_sure)) }
            }
            Spacer(Modifier.height(0.dp))
        }
    }
}
