package com.yaadein.feature.capture.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.yaadein.core.ui.components.PersonAvatar
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.model.Mood
import com.yaadein.domain.model.Person
import com.yaadein.feature.capture.R
import com.yaadein.core.ui.R as CoreR

@Composable
fun PeoplePicker(
    people: List<Person>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
    onAddPerson: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.who_was_there),
) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(spacing.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            people.forEach { p ->
                FilterChip(
                    selected = p.id in selected,
                    onClick = { onToggle(p.id) },
                    label = { Text(p.displayName) },
                    leadingIcon = { PersonAvatar(person = p, size = Yaadein.sizes.iconLg) },
                )
            }
            AssistChip(onClick = onAddPerson, label = { Text(stringResource(CoreR.string.action_add)) }, leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) })
        }
    }
}

@Composable
fun moodLabel(mood: Mood): String = when (mood) {
    Mood.JOYFUL -> stringResource(CoreR.string.mood_joyful)
    Mood.CALM -> stringResource(CoreR.string.mood_calm)
    Mood.NOSTALGIC -> stringResource(CoreR.string.mood_nostalgic)
    Mood.PROUD -> stringResource(CoreR.string.mood_proud)
    Mood.FUNNY -> stringResource(CoreR.string.mood_funny)
    Mood.SAD -> stringResource(CoreR.string.mood_sad)
    Mood.CELEBRATION -> stringResource(CoreR.string.mood_celebration)
}

@Composable
fun MoodPicker(selected: Mood?, onSelect: (Mood?) -> Unit, modifier: Modifier = Modifier) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier) {
        Text(stringResource(R.string.mood_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(spacing.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Mood.entries.forEach { m ->
                FilterChip(selected = selected == m, onClick = { onSelect(if (selected == m) null else m) }, label = { Text(moodLabel(m)) })
            }
        }
    }
}
