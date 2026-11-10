package com.yaadein.feature.people

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaadein.core.ui.components.EmptyState
import com.yaadein.core.ui.components.PersonAvatar
import com.yaadein.core.ui.illustrations.IllustrationKind
import com.yaadein.core.ui.media.EncryptedMediaImage
import com.yaadein.core.ui.media.ThumbnailImage
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.model.Person
import kotlin.math.max
import kotlin.math.roundToInt
import com.yaadein.core.ui.R as CoreR

/** Callbacks of the stateless screen. */
data class WhoIsThisCallbacks(
    val onBack: () -> Unit,
    val onSelectPerson: (Long) -> Unit,
    val onAddPerson: () -> Unit,
    val onNameThem: () -> Unit,
    val onNotTheSame: () -> Unit,
    val onSkip: () -> Unit,
)

@Composable
fun WhoIsThisRoute(
    onBack: () -> Unit,
    onAddPerson: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WhoIsThisViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.who_undo)
    val named = state.justNamed
    val message = named?.let { pluralStringResource(R.plurals.who_named, it.faceIds.size, it.faceIds.size, it.person.displayName) }
    LaunchedEffect(named) {
        if (named == null || message == null) return@LaunchedEffect
        val result = snackbar.showSnackbar(message = message, actionLabel = undoLabel, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) viewModel.undoLastNaming() else viewModel.snackbarShown()
    }
    WhoIsThisScreen(
        state = state,
        callbacks = WhoIsThisCallbacks(
            onBack = onBack,
            onSelectPerson = viewModel::selectPerson,
            onAddPerson = onAddPerson,
            onNameThem = viewModel::nameThem,
            onNotTheSame = viewModel::notTheSamePerson,
            onSkip = viewModel::skip,
        ),
        snackbarHostState = snackbar,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhoIsThisScreen(
    state: WhoIsThisUiState,
    callbacks: WhoIsThisCallbacks,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val spacing = Yaadein.spacing
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.who_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = callbacks.onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(CoreR.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val group = state.group
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            group == null -> EmptyState(
                illustration = IllustrationKind.PEOPLE_CIRCLE,
                title = stringResource(R.string.who_empty_title),
                message = stringResource(R.string.who_empty_message),
                actionLabel = stringResource(CoreR.string.action_done),
                onAction = callbacks.onBack,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> GroupContent(state = state, group = group, callbacks = callbacks, modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = spacing.gutter))
        }
    }
}

@Composable
private fun GroupContent(state: WhoIsThisUiState, group: FaceGroup, callbacks: WhoIsThisCallbacks, modifier: Modifier = Modifier) {
    val spacing = Yaadein.spacing
    val sizes = Yaadein.sizes
    val selected = state.people.firstOrNull { it.id == state.selectedPersonId }
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(spacing.sm))
        Text(
            text = stringResource(R.string.who_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.lg))
        val shown = group.faces.take(MAX_SAMPLES)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            shown.forEachIndexed { index, face ->
                FaceCrop(
                    face = face,
                    size = sizes.avatarXl,
                    contentDescription = stringResource(R.string.who_face_cd, index + 1, shown.size),
                )
            }
        }
        Spacer(Modifier.height(spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(
                text = pluralStringResource(R.plurals.who_seen_in, group.photoCount, group.photoCount),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (state.remaining > 0) {
                Text(
                    text = pluralStringResource(R.plurals.who_remaining, state.remaining, state.remaining),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(spacing.xl))
        Text(
            text = stringResource(R.string.who_pick_person),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(spacing.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            state.people.forEach { person ->
                PersonChip(person = person, selected = person.id == state.selectedPersonId, onClick = { callbacks.onSelectPerson(person.id) })
            }
            AssistChip(
                onClick = callbacks.onAddPerson,
                label = { Text(stringResource(R.string.who_add_person)) },
                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(sizes.iconSm)) },
                modifier = Modifier.heightIn(min = sizes.touchTarget),
            )
        }
        Spacer(Modifier.height(spacing.xxl))
        Button(
            onClick = callbacks.onNameThem,
            enabled = selected != null && !state.working,
            modifier = Modifier.fillMaxWidth().heightIn(min = sizes.touchTarget),
        ) {
            Text(
                text = if (selected != null) stringResource(R.string.who_name_as, selected.displayName) else stringResource(R.string.who_name_them),
            )
        }
        Spacer(Modifier.height(spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            OutlinedButton(
                onClick = callbacks.onNotTheSame,
                enabled = group.faces.size >= 2 && !state.working,
                modifier = Modifier.weight(1f).heightIn(min = sizes.touchTarget),
            ) { Text(stringResource(R.string.who_not_same)) }
            TextButton(
                onClick = callbacks.onSkip,
                enabled = !state.working,
                modifier = Modifier.weight(1f).heightIn(min = sizes.touchTarget),
            ) { Text(stringResource(CoreR.string.action_skip)) }
        }
        Spacer(Modifier.height(spacing.xxxl))
    }
}

@Composable
private fun PersonChip(person: Person, selected: Boolean, onClick: () -> Unit) {
    val sizes = Yaadein.sizes
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(person.displayName) },
        leadingIcon = {
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(sizes.iconSm))
            } else {
                PersonAvatar(person = person, size = sizes.avatarSm)
            }
        },
        modifier = Modifier.heightIn(min = sizes.touchTarget),
    )
}

/**
 * The face region of a photo in a rounded square: the thumbnail is laid out at the scale that makes the face box fill
 * the square and placed so the box is centred. Without the photo's size the whole thumbnail is shown instead.
 */
@Composable
private fun FaceCrop(face: FaceSample, size: Dp, contentDescription: String) {
    val shape = RoundedCornerShape(Yaadein.radius.md)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        val boxW = (face.right - face.left).coerceIn(MIN_BOX, 1f)
        val boxH = (face.bottom - face.top).coerceIn(MIN_BOX, 1f)
        if (face.mediaWidth <= 0 || face.mediaHeight <= 0) {
            ThumbnailImage(mediaId = face.mediaId, contentDescription = contentDescription, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            return@Box
        }
        // Grow the box a little so hair and chin are in the crop.
        val pad = FACE_PADDING
        val cx = (face.left + face.right) / 2f
        val cy = (face.top + face.bottom) / 2f
        val cropW = (boxW * (1f + pad)).coerceAtMost(1f)
        val cropH = (boxH * (1f + pad)).coerceAtMost(1f)
        EncryptedMediaImage(
            mediaId = face.mediaId,
            thumbnail = true,
            contentDescription = contentDescription,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.layout { measurable, constraints ->
                val side = constraints.maxWidth
                val sideH = constraints.maxHeight
                // Scale so the crop box fills the square (the longer side of the crop decides).
                val scale = max(side / (cropW * face.mediaWidth), sideH / (cropH * face.mediaHeight))
                val imgW = max(1, (face.mediaWidth * scale).roundToInt())
                val imgH = max(1, (face.mediaHeight * scale).roundToInt())
                val placeable = measurable.measure(Constraints.fixed(imgW, imgH))
                val x = (side / 2f - cx * imgW).roundToInt().coerceIn(side - imgW, 0)
                val y = (sideH / 2f - cy * imgH).roundToInt().coerceIn(sideH - imgH, 0)
                layout(side, sideH) { placeable.place(x, y) }
            },
        )
    }
}

private const val MAX_SAMPLES = 6
private const val MIN_BOX = 0.02f
private const val FACE_PADDING = 0.6f
