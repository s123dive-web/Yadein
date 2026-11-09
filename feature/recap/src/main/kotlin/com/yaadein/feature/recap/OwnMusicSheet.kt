package com.yaadein.feature.recap

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.data.recap.MusicLibrary
import com.yaadein.data.recap.OwnSong

/**
 * "Your songs": the music on the phone the user added for reels. Search, pick one, add more through the system
 * picker (every song on the phone, no permission), or remove. Popular Hindi and Marathi reel songs are suggested
 * underneath the search box as a starting point; tapping one looks for it in the list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnMusicSheet(
    state: OwnMusicState,
    selectedUri: String?,
    onQuery: (String) -> Unit,
    onSelect: (OwnSong) -> Unit,
    onRemove: (OwnSong) -> Unit,
    onAddFromPhone: () -> Unit,
    onDismissMissing: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = Yaadein.spacing
    val hindi = stringArrayResource(R.array.suggested_songs_hindi)
    val marathi = stringArrayResource(R.array.suggested_songs_marathi)
    var showSuggestions by remember { mutableStateOf(state.songs.isEmpty()) }
    val filtered = state.filtered

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.music_library_title), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                FilledTonalButton(onClick = onAddFromPhone, enabled = !state.adding) {
                    if (state.adding) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                    }
                    Spacer(Modifier.width(spacing.xs))
                    Text(stringResource(if (state.adding) R.string.music_adding else R.string.music_add_from_phone))
                }
            }
            Spacer(Modifier.height(spacing.sm))
            OutlinedTextField(
                value = state.query, onValueChange = onQuery, singleLine = true, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.music_search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.music_clear_search)) }
                    }
                },
            )
            if (state.missingTitle != null) {
                Spacer(Modifier.height(spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.music_song_missing, state.missingTitle), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismissMissing) { Text(stringResource(R.string.music_dismiss)) }
                }
            }
            Spacer(Modifier.height(spacing.sm))
            // ---- suggestions ----
            TextButton(onClick = { showSuggestions = !showSuggestions }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null)
                Spacer(Modifier.width(spacing.xs))
                Text(stringResource(if (showSuggestions) R.string.music_suggested_hide else R.string.music_suggested))
            }
            if (showSuggestions) {
                Text(stringResource(R.string.music_suggested_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(spacing.xs))
                SuggestionRow(stringResource(R.string.music_suggested_hindi), hindi, onQuery)
                Spacer(Modifier.height(spacing.xs))
                SuggestionRow(stringResource(R.string.music_suggested_marathi), marathi, onQuery)
                Spacer(Modifier.height(spacing.sm))
            }
            // ---- songs ----
            when {
                state.songs.isEmpty() -> Text(
                    stringResource(R.string.music_library_empty), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = spacing.lg),
                )
                filtered.isEmpty() -> Text(
                    stringResource(R.string.music_not_on_phone, state.query), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = spacing.lg),
                )
                else -> LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(filtered, key = { it.uri }) { song ->
                        SongRow(song = song, selected = song.uri == selectedUri, onSelect = { onSelect(song) }, onRemove = { onRemove(song) })
                    }
                }
            }
            Spacer(Modifier.height(spacing.xl))
        }
    }
}

@Composable
private fun SuggestionRow(label: String, songs: Array<String>, onQuery: (String) -> Unit) {
    val spacing = Yaadein.spacing
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // Entries are "Title — Artist"; the search uses the title only so a file named after the song is found.
        songs.forEach { entry ->
            val title = entry.substringBefore(" — ").trim()
            SuggestionChip(onClick = { onQuery(title) }, label = { Text(entry, maxLines = 1, overflow = TextOverflow.Ellipsis) })
        }
    }
}

@Composable
private fun SongRow(song: OwnSong, selected: Boolean, onSelect: () -> Unit, onRemove: () -> Unit) {
    val spacing = Yaadein.spacing
    val removeLabel = stringResource(R.string.music_remove_named, song.title)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onSelect).padding(vertical = spacing.xs),
    ) {
        Icon(
            if (selected) Icons.Rounded.Check else Icons.Rounded.MusicNote, contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = listOfNotNull(song.artist?.takeIf { it.isNotBlank() }, MusicLibrary.formatDuration(song.durationMs).takeIf { it.isNotEmpty() }).joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, contentDescription = removeLabel) }
    }
}
