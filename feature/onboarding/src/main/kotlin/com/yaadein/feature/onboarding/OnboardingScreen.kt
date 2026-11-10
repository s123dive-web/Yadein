package com.yaadein.feature.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yaadein.core.ui.illustrations.Illustration
import com.yaadein.core.ui.illustrations.IllustrationKind
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.core.ui.util.AppLanguage
import com.yaadein.core.ui.R as CoreR

/**
 * First run: one welcome screen, then the system Photo Picker, then the user's own memories playing ([onStory]).
 * "See a sample family" and "Skip" go straight to Home ([onHome]).
 */
@Composable
fun OnboardingRoute(
    onStory: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris -> viewModel.onPicked(uris) }
    LaunchedEffect(state.exit) {
        when (state.exit) {
            OnboardingExit.STORY -> onStory()
            OnboardingExit.HOME -> onHome()
            null -> Unit
        }
    }
    WelcomeScreen(
        state = state,
        language = remember { AppLanguage.current(context) },
        onLanguage = { AppLanguage.set(context, it) },
        onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
        onSample = viewModel::seeSample,
        onSkip = viewModel::skip,
        modifier = modifier,
    )
}

/**
 * The only screen before the user's own photos: the promise in two lines, the language, and three ways on.
 * [language] is the chosen language tag, null while the app follows the phone.
 */
@Composable
fun WelcomeScreen(
    state: OnboardingUiState,
    language: String?,
    onLanguage: (String?) -> Unit,
    onPick: () -> Unit,
    onSample: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = Yaadein.spacing
    val busy = state.busy
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = spacing.sm), horizontalArrangement = Arrangement.End) {
                LanguagePicker(language = language, onLanguage = onLanguage, enabled = busy == null)
            }
            Spacer(Modifier.height(spacing.lg))
            Illustration(kind = IllustrationKind.PHOTO_STACK, modifier = Modifier.size(Yaadein.sizes.illustration))
            Spacer(Modifier.height(spacing.xl))
            Text(
                text = stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 480.dp).semantics { heading() },
            )
            Spacer(Modifier.height(spacing.md))
            Text(
                text = stringResource(R.string.welcome_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 480.dp),
            )
            Spacer(Modifier.height(spacing.xxl))
            if (busy != null) {
                Column(
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(if (busy == OnboardingBusy.IMPORTING) R.string.welcome_importing else R.string.welcome_sample_loading),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                if (state.nothingAdded) {
                    Text(
                        text = stringResource(R.string.welcome_none_added),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = spacing.md).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                Button(onClick = onPick, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(min = Yaadein.sizes.touchTarget)) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(spacing.sm))
                    Text(stringResource(R.string.welcome_pick))
                }
                Text(
                    text = stringResource(R.string.welcome_pick_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = spacing.xs, bottom = spacing.md),
                )
                OutlinedButton(onClick = onSample, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(min = Yaadein.sizes.touchTarget)) {
                    Text(stringResource(R.string.welcome_sample))
                }
                Spacer(Modifier.height(spacing.xs))
                TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = Yaadein.sizes.touchTarget)) {
                    Text(stringResource(CoreR.string.action_skip))
                }
            }
            Spacer(Modifier.height(spacing.xl))
        }
    }
}

/** "Language: हिन्दी": the eight languages in their own script, plus "Phone language". */
@Composable
private fun LanguagePicker(language: String?, onLanguage: (String?) -> Unit, enabled: Boolean) {
    var open by remember { mutableStateOf(false) }
    val system = stringResource(R.string.welcome_language_system)
    val currentName = language?.let { AppLanguage.endonym(it) } ?: system
    Box {
        TextButton(onClick = { open = true }, enabled = enabled, modifier = Modifier.heightIn(min = Yaadein.sizes.touchTarget)) {
            Icon(Icons.Rounded.Translate, contentDescription = null)
            Spacer(Modifier.width(Yaadein.spacing.sm))
            Text(stringResource(R.string.welcome_language, currentName))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (listOf<String?>(null) + AppLanguage.TAGS).forEach { tag ->
                DropdownMenuItem(
                    text = { Text(tag?.let { AppLanguage.endonym(it) } ?: system) },
                    onClick = { open = false; onLanguage(tag) },
                    trailingIcon = { if (tag == language) Icon(Icons.Rounded.Check, contentDescription = null) },
                )
            }
        }
    }
}
