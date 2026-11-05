package com.yaadein.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yaadein.core.ui.illustrations.Illustration
import com.yaadein.core.ui.illustrations.IllustrationKind
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.yaadein.core.ui.R as CoreR

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.update { it.copy(onboardingDone = true) }
            onDone()
        }
    }
}

private data class Page(val illustration: IllustrationKind, val title: Int, val body: Int)

private val pages = listOf(
    Page(IllustrationKind.PHOTO_STACK, R.string.onboarding_1_title, R.string.onboarding_1_body),
    Page(IllustrationKind.LOCK_HEART, R.string.onboarding_2_title, R.string.onboarding_2_body),
    Page(IllustrationKind.PEOPLE_CIRCLE, R.string.onboarding_3_title, R.string.onboarding_3_body),
)

@Composable
fun OnboardingRoute(
    onAddFirstPerson: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    OnboardingScreen(onFinish = { viewModel.complete(onAddFirstPerson) }, modifier = modifier)
}

@Composable
fun OnboardingScreen(onFinish: () -> Unit, modifier: Modifier = Modifier, initialPage: Int = 0) {
    val spacing = Yaadein.spacing
    val pagerState = rememberPagerState(initialPage = initialPage) { pages.size }
    val scope = rememberCoroutineScope()
    val last = pagerState.currentPage == pages.lastIndex
    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Row(modifier = Modifier.fillMaxWidth().padding(spacing.md), horizontalArrangement = Arrangement.End) {
                if (!last) TextButton(onClick = onFinish) { Text(stringResource(CoreR.string.action_skip)) }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
                val page = pages[index]
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = spacing.xxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Illustration(kind = page.illustration, modifier = Modifier.size(Yaadein.sizes.illustration * 1.3f))
                    Spacer(Modifier.height(spacing.xxl))
                    Text(
                        text = stringResource(page.title),
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 480.dp).semantics { heading() },
                    )
                    Spacer(Modifier.height(spacing.md))
                    Text(
                        text = stringResource(page.body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 480.dp),
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(pages.size) { i ->
                    val active = i == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(spacing.xs)
                            .height(8.dp)
                            .width(if (active) 24.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                }
            }
            Spacer(Modifier.height(spacing.lg))
            Button(
                onClick = { if (last) onFinish() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.xl).widthIn(max = 480.dp),
            ) {
                Text(stringResource(if (last) R.string.onboarding_add_first_person else CoreR.string.action_next))
            }
            Spacer(Modifier.height(spacing.xl))
        }
    }
}
