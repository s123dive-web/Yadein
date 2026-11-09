package com.yaadein.feature.capture

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.yaadein.core.ui.components.PersonAvatar
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.core.ui.util.memoryDateText
import com.yaadein.domain.capture.DateSource
import com.yaadein.domain.model.MemoryDate
import com.yaadein.domain.model.Person
import com.yaadein.feature.capture.components.DateChips
import com.yaadein.feature.capture.components.PeoplePicker
import com.yaadein.core.ui.R as CoreR

@Composable
fun QuickSaveRoute(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onAddPerson: () -> Unit,
    /** The photos look like several events: open Photo-first with them (the inbox already holds the URIs). */
    onSplit: () -> Unit,
    /** The other ways of adding a memory, shown in the hub sheet from "More ways". */
    hubActions: CaptureActions,
    modifier: Modifier = Modifier,
    viewModel: QuickSaveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris -> viewModel.onPicked(uris) }
    var hubOpen by rememberSaveable { mutableStateOf(false) }
    QuickSaveScreen(
        state = state,
        onBack = onBack,
        onTitle = viewModel::setTitle,
        onDate = viewModel::setDate,
        onTogglePerson = viewModel::togglePerson,
        onAddPerson = onAddPerson,
        onPlace = viewModel::setPlace,
        onOpenPlace = viewModel::openPlace,
        onSetCover = viewModel::setCover,
        onRemove = viewModel::remove,
        onSplit = { if (viewModel.prepareSplit()) onSplit() },
        onDismissSplit = viewModel::dismissSplit,
        onWords = viewModel::setWords,
        onAddPhotos = {
            viewModel.beforePicker()
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
        },
        onMoreWays = { hubOpen = true },
        onDismissRejected = viewModel::dismissRejected,
        onSave = { viewModel.save(onSaved) },
        modifier = modifier,
    )
    if (hubOpen) CaptureHubSheet(actions = hubActions, onDismiss = { hubOpen = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSaveScreen(
    state: QuickSaveUiState,
    onBack: () -> Unit,
    onTitle: (String) -> Unit,
    onDate: (MemoryDate) -> Unit,
    onTogglePerson: (Long) -> Unit,
    onAddPerson: () -> Unit,
    onPlace: (String) -> Unit,
    onOpenPlace: () -> Unit,
    onSetCover: (Int) -> Unit,
    onRemove: (Uri) -> Unit,
    onSplit: () -> Unit,
    onDismissSplit: () -> Unit,
    onWords: (String) -> Unit,
    onAddPhotos: () -> Unit,
    onMoreWays: () -> Unit,
    onDismissRejected: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(if (state.wordsOnly) R.string.quick_save_words_title else R.string.quick_save_title), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, contentDescription = stringResource(CoreR.string.action_close)) } },
            actions = { TextButton(onClick = onSave, enabled = state.canSave) { Text(stringResource(CoreR.string.action_save)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.gutter).widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            if (state.inspecting && state.items.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md), modifier = Modifier.padding(top = spacing.md)) {
                    CircularProgressIndicator(modifier = Modifier.size(Yaadein.sizes.icon))
                    Text(stringResource(R.string.photos_inspecting), style = MaterialTheme.typography.bodyMedium)
                }
            } else if (state.wordsOnly) {
                WordsOnly(state = state, onWords = onWords, onAddPhotos = onAddPhotos, onSave = onSave)
            } else {
                PhotoStrip(state = state, onSetCover = onSetCover, onRemove = onRemove, onAddPhotos = onAddPhotos)
                state.cappedTo?.let { Text(stringResource(R.string.quick_save_capped, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (state.splitInto != null && !state.splitDismissed) {
                    SplitBanner(count = state.splitInto, onSplit = onSplit, onKeep = onDismissSplit)
                }
                DateRow(state = state, onDate = onDate)
                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitle,
                    placeholder = { Text(state.suggestedTitle.ifBlank { stringResource(R.string.event_title) }) },
                    label = { Text(stringResource(R.string.event_title)) },
                    supportingText = if (state.title.isBlank() && state.suggestedTitle.isNotBlank()) ({ Text(stringResource(R.string.quick_save_title_suggested)) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!state.wordsOnly) {
                PeopleChips(state = state, onToggle = onTogglePerson, onAddPerson = onAddPerson)
                PlaceRow(state = state, onPlace = onPlace, onOpenPlace = onOpenPlace)
            }
            Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.fillMaxWidth().height(Yaadein.sizes.touchTarget + spacing.sm)) {
                Text(stringResource(CoreR.string.action_save))
            }
            TextButton(onClick = onMoreWays, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.quick_save_more_ways)) }
            Spacer(Modifier.height(spacing.xxxl))
        }
    }
    if (state.rejected.isNotEmpty()) RejectedDialog(failures = state.rejected, onDismiss = onDismissRejected)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoStrip(state: QuickSaveUiState, onSetCover: (Int) -> Unit, onRemove: (Uri) -> Unit, onAddPhotos: () -> Unit) {
    val spacing = Yaadein.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            items(state.items.size, key = { state.items[it].uri.toString() }) { i ->
                val p = state.items[i]
                val isCover = i == state.coverIndex
                val description = stringResource(R.string.quick_save_photo_cd, i + 1, state.items.size)
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .clip(RoundedCornerShape(Yaadein.radius.md))
                        .combinedClickable(role = Role.Image, onClick = {}, onLongClick = { onSetCover(i) })
                        .semantics { contentDescription = description },
                ) {
                    AsyncImage(model = p.uri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    IconButton(onClick = { onRemove(p.uri) }, modifier = Modifier.align(Alignment.TopEnd).size(Yaadein.sizes.touchTarget)) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.quick_save_remove), tint = Color.White)
                    }
                    if (isCover) {
                        Text(
                            stringResource(R.string.quick_save_cover_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.align(Alignment.BottomStart).padding(spacing.xs)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(Yaadein.radius.sm)).padding(horizontal = spacing.xs, vertical = spacing.xxs),
                        )
                    }
                    if (p.isVideo) {
                        Text(stringResource(R.string.event_video_badge), style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(spacing.xs))
                    }
                }
            }
            item(key = "add") {
                FilledTonalButton(onClick = onAddPhotos, modifier = Modifier.height(112.dp)) {
                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(spacing.xs))
                    Text(stringResource(R.string.quick_save_add_photos))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(pluralStringResource(CoreR.plurals.media_count, state.items.size, state.items.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.quick_save_cover_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SplitBanner(count: Int, onSplit: () -> Unit, onKeep: () -> Unit) {
    val spacing = Yaadein.spacing
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape = RoundedCornerShape(Yaadein.radius.lg)) {
        Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(stringResource(R.string.quick_save_split_title, count), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.quick_save_split_body), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                FilledTonalButton(onClick = onSplit) { Text(stringResource(R.string.quick_save_split_action, count)) }
                TextButton(onClick = onKeep) { Text(stringResource(R.string.quick_save_keep_one)) }
            }
        }
    }
}

@Composable
private fun DateRow(state: QuickSaveUiState, onDate: (MemoryDate) -> Unit) {
    val spacing = Yaadein.spacing
    var open by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            AssistChip(
                onClick = { open = !open },
                label = { Text(memoryDateText(state.date)) },
                leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(Yaadein.sizes.iconSm)) },
                modifier = Modifier.height(Yaadein.sizes.touchTarget - spacing.sm),
            )
            Text(
                text = stringResource(
                    when {
                        state.dateEdited -> R.string.quick_save_date_edited
                        state.dateSource == DateSource.TODAY -> R.string.quick_save_date_guess
                        else -> R.string.quick_save_date_from_photos
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (!state.dateEdited && state.dateSource == DateSource.TODAY) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (open) DateChips(value = state.date, onChange = { onDate(it); open = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeopleChips(state: QuickSaveUiState, onToggle: (Long) -> Unit, onAddPerson: () -> Unit) {
    val spacing = Yaadein.spacing
    var showAll by rememberSaveable { mutableStateOf(false) }
    val hidden = state.people.size - state.shortlist.size
    if (showAll || state.shortlist.isEmpty()) {
        PeoplePicker(people = state.people, selected = state.selectedPeople, onToggle = onToggle, onAddPerson = onAddPerson, title = stringResource(R.string.quick_save_people))
        return
    }
    Column {
        Text(stringResource(R.string.quick_save_people), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(spacing.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            state.shortlist.forEach { p -> PersonChip(p, selected = p.id in state.selectedPeople, suggested = p.id in state.suggestedPeople, onToggle = { onToggle(p.id) }) }
            if (hidden > 0) {
                AssistChip(onClick = { showAll = true }, label = { Text(stringResource(R.string.quick_save_all_people, hidden)) }, modifier = Modifier.height(Yaadein.sizes.touchTarget - spacing.sm))
            }
            AssistChip(
                onClick = onAddPerson,
                label = { Text(stringResource(R.string.quick_save_add_person)) },
                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(Yaadein.sizes.iconSm)) },
                modifier = Modifier.height(Yaadein.sizes.touchTarget - spacing.sm),
            )
        }
    }
}

@Composable
private fun PersonChip(person: Person, selected: Boolean, suggested: Boolean, onToggle: () -> Unit) {
    val spacing = Yaadein.spacing
    val suggestedLabel = stringResource(R.string.quick_save_suggested)
    FilterChip(
        selected = selected,
        onClick = onToggle,
        label = { Text(person.displayName) },
        leadingIcon = { PersonAvatar(person = person, size = Yaadein.sizes.avatarSm) },
        trailingIcon = if (suggested && !selected) ({ Icon(Icons.Rounded.AutoAwesome, contentDescription = suggestedLabel, modifier = Modifier.size(Yaadein.sizes.iconSm)) }) else null,
        modifier = Modifier.height(Yaadein.sizes.touchTarget - spacing.sm),
    )
}

@Composable
private fun PlaceRow(state: QuickSaveUiState, onPlace: (String) -> Unit, onOpenPlace: () -> Unit) {
    if (state.placeOpen || state.place.isNotBlank()) {
        OutlinedTextField(
            value = state.place, onValueChange = onPlace, label = { Text(stringResource(R.string.event_place)) }, singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Place, contentDescription = null) }, modifier = Modifier.fillMaxWidth(),
        )
    } else {
        TextButton(onClick = onOpenPlace) {
            Icon(Icons.Rounded.Place, contentDescription = null, modifier = Modifier.size(Yaadein.sizes.iconSm))
            Spacer(Modifier.width(Yaadein.spacing.xs))
            Text(stringResource(R.string.quick_save_place_add))
        }
    }
}

/** No photos were picked: one line of words, polished live, with a way back to the picker. Never a dead end. */
@Composable
private fun WordsOnly(state: QuickSaveUiState, onWords: (String) -> Unit, onAddPhotos: () -> Unit, onSave: () -> Unit) {
    val spacing = Yaadein.spacing
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Text(stringResource(R.string.quick_save_words_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = state.words,
            onValueChange = onWords,
            placeholder = { Text(stringResource(R.string.text_placeholder)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave() }),
            minLines = 2,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        state.polished?.let { polished ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(Yaadein.radius.lg)) {
                Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(polished.title, style = MaterialTheme.typography.titleLarge)
                    Text(memoryDateText(polished.date), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (!polished.dateDetected) Text(stringResource(R.string.text_no_date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        FilledTonalButton(onClick = onAddPhotos) {
            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
            Spacer(Modifier.width(spacing.sm))
            Text(stringResource(R.string.quick_save_add_photos))
        }
    }
}
