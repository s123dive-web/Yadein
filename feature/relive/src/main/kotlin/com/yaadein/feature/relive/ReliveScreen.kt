package com.yaadein.feature.relive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaadein.core.ui.components.EmptyState
import com.yaadein.core.ui.components.MemoryCardView
import com.yaadein.core.ui.components.PersonAvatar
import com.yaadein.core.ui.illustrations.IllustrationKind
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.core.ui.R as CoreR

@Composable
fun ReliveRoute(
    onOpenMemory: (Long) -> Unit,
    onOpenPerson: (Long) -> Unit,
    onRecap: () -> Unit,
    onYear: (Int) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: ReliveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReliveScreen(
        state = state,
        onOpenMemory = onOpenMemory,
        onOpenPerson = onOpenPerson,
        onSurprise = viewModel::surpriseMe,
        onDismissRandom = viewModel::dismissRandom,
        onRecap = onRecap,
        onYear = onYear,
        contentPadding = contentPadding,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReliveScreen(
    state: ReliveUiState,
    onOpenMemory: (Long) -> Unit,
    onOpenPerson: (Long) -> Unit,
    onSurprise: () -> Unit,
    onDismissRandom: () -> Unit,
    onRecap: () -> Unit,
    onYear: (Int) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.relive_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() }) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.totalMemories == 0 -> EmptyState(
                illustration = IllustrationKind.KITE,
                title = stringResource(R.string.relive_empty_title),
                message = stringResource(R.string.relive_empty_message),
                modifier = Modifier.fillMaxSize().padding(contentPadding),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = spacing.gutter, end = spacing.gutter, bottom = spacing.xxxl + contentPadding.calculateBottomPadding() + Yaadein.sizes.fabSize),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                item(key = "actions") {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        FilledTonalButton(onClick = onSurprise) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = null)
                            Spacer(Modifier.width(spacing.sm))
                            Text(stringResource(R.string.relive_surprise))
                        }
                        Button(onClick = onRecap) {
                            Icon(Icons.Rounded.Movie, contentDescription = null)
                            Spacer(Modifier.width(spacing.sm))
                            Text(stringResource(R.string.relive_make_recap))
                        }
                    }
                }
                if (state.upcomingBirthdays.isNotEmpty()) {
                    item(key = "birthdays") {
                        Column {
                            SectionTitle(stringResource(R.string.relive_birthdays))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                items(state.upcomingBirthdays.size, key = { state.upcomingBirthdays[it].first.id }) { i ->
                                    val (person, days) = state.upcomingBirthdays[i]
                                    AssistChip(
                                        onClick = { onOpenPerson(person.id) },
                                        leadingIcon = { PersonAvatar(person = person, size = Yaadein.sizes.avatarSm) },
                                        label = {
                                            Text(
                                                when (days) {
                                                    0 -> stringResource(R.string.relive_birthday_today, person.displayName)
                                                    else -> stringResource(R.string.relive_birthday_in, person.displayName, days)
                                                },
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "otd-title") { SectionTitle(stringResource(R.string.relive_on_this_day)) }
                if (state.onThisDay.isEmpty()) {
                    item(key = "otd-empty") {
                        Text(
                            text = stringResource(R.string.relive_on_this_day_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.onThisDay.size, key = { "otd-" + state.onThisDay[it].memory.id }) { i ->
                        val card = state.onThisDay[i]
                        MemoryCardView(card = card, onClick = { onOpenMemory(card.memory.id) }, compact = true)
                    }
                }
                if (state.years.isNotEmpty()) {
                    item(key = "years") {
                        Column {
                            SectionTitle(stringResource(R.string.relive_years))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                items(state.years.size, key = { state.years[it].year }) { i ->
                                    val y = state.years[i]
                                    AssistChip(
                                        onClick = { onYear(y.year) },
                                        label = { Text("${y.year} · " + pluralStringResource(CoreR.plurals.memory_count, y.count, y.count)) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    state.random?.let { card ->
        ModalBottomSheet(onDismissRequest = onDismissRandom) {
            Column(modifier = Modifier.padding(horizontal = spacing.gutter).padding(bottom = spacing.xxl)) {
                Text(stringResource(R.string.relive_random_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(spacing.md))
                MemoryCardView(card = card, onClick = { onDismissRandom(); onOpenMemory(card.memory.id) })
                Spacer(Modifier.height(spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    FilledTonalButton(onClick = onSurprise) { Text(stringResource(R.string.relive_another)) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Yaadein.spacing.sm, bottom = Yaadein.spacing.sm).semantics { heading() },
    )
}
