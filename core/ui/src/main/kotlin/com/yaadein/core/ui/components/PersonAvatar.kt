package com.yaadein.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yaadein.core.ui.R
import com.yaadein.core.ui.media.ThumbnailImage
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.model.Person
import androidx.compose.ui.res.stringResource

@Composable
fun avatarColor(person: Person): Color {
    val palette = Yaadein.colors.avatarPalette
    return palette[Math.floorMod(person.avatarColorIndex, palette.size)]
}

/** Circular avatar: profile photo when available, otherwise coloured initials. */
@Composable
fun PersonAvatar(
    person: Person,
    modifier: Modifier = Modifier,
    size: Dp = Yaadein.sizes.avatarMd,
    borderColor: Color? = null,
) {
    val color = avatarColor(person)
    val description = stringResource(R.string.cd_avatar_of, person.displayName)
    Box(
        modifier = modifier
            .size(size)
            .then(if (borderColor != null) Modifier.border(2.dp, borderColor, CircleShape) else Modifier)
            .clip(CircleShape)
            .background(color)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val profileMediaId = person.profileMediaId
        if (profileMediaId != null) {
            ThumbnailImage(mediaId = profileMediaId, contentDescription = null, modifier = Modifier.size(size))
        } else {
            val fontSize = (size.value * 0.4f).sp
            Text(
                text = person.initials.ifEmpty { "?" },
                color = Color.White,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** Overlapping row of avatars ("who was there"), with a +N bubble when there are more. */
@Composable
fun FaceStack(
    people: List<Person>,
    modifier: Modifier = Modifier,
    size: Dp = Yaadein.sizes.avatarSm,
    max: Int = 4,
) {
    if (people.isEmpty()) return
    val overlap = size * 0.3f
    val ring = MaterialTheme.colorScheme.surface
    val shown = people.take(max)
    val extra = people.size - shown.size
    val names = people.joinToString { it.displayName }
    val description = stringResource(R.string.cd_people_in_memory, names)
    Row(modifier = modifier.semantics { contentDescription = description }) {
        shown.forEachIndexed { index, person ->
            PersonAvatar(
                person = person,
                size = size,
                borderColor = ring,
                modifier = Modifier.offset(x = -(overlap * index)),
            )
        }
        if (extra > 0) {
            Box(
                modifier = Modifier
                    .offset(x = -(overlap * shown.size))
                    .size(size)
                    .border(2.dp, ring, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$extra",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}
