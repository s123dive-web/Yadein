package com.yaadein.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yaadein.core.ui.components.EmptyState
import com.yaadein.core.ui.illustrations.IllustrationKind
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.capture.CaptureEntry
import com.yaadein.domain.repository.CaptureStatsRepository
import com.yaadein.domain.usage.UsageSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.yaadein.core.ui.R as CoreR

/** Settings → How I use Yaadein: the local `capture_stats` timings, summarised on the phone. Nothing is sent anywhere. */
@HiltViewModel
class UsageViewModel @Inject constructor(private val stats: CaptureStatsRepository) : ViewModel() {
    private val _state = MutableStateFlow<UsageSummary?>(null)
    val state: StateFlow<UsageSummary?> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch { _state.value = UsageSummary.from(stats.recent(LIMIT), System.currentTimeMillis()) }
    }

    private companion object { const val LIMIT = 500 }
}

@Composable
fun UsageRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: UsageViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UsageScreen(summary = state, onBack = onBack, modifier = modifier)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(summary: UsageSummary?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = Yaadein.spacing
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_usage), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(CoreR.string.action_back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
        when {
            summary == null -> Unit
            summary.isEmpty -> EmptyState(
                illustration = IllustrationKind.PEOPLE_CIRCLE,
                title = stringResource(R.string.usage_empty_title),
                message = stringResource(R.string.usage_empty_message),
                modifier = Modifier.fillMaxSize(),
            )
            else -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                item(key = "intro") { Text(stringResource(R.string.usage_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item(key = "counts") {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                        StatTile(pluralStringResource(R.plurals.usage_captures, summary.captures, summary.captures), stringResource(R.string.usage_captures_help), Modifier.weight(1f))
                        StatTile(pluralStringResource(R.plurals.usage_items, summary.photosAndClips, summary.photosAndClips), stringResource(R.string.usage_items_help), Modifier.weight(1f))
                    }
                }
                item(key = "tap") {
                    TimingCard(
                        title = stringResource(R.string.usage_tap_to_save),
                        median = summary.tapToSaveMedianMs, p90 = summary.tapToSaveP90Ms,
                        footer = summary.withinBudgetPercent?.let { stringResource(R.string.usage_within_budget, it) },
                        sparkline = summary.recentTapToSaveMs,
                    )
                }
                item(key = "enc") {
                    TimingCard(title = stringResource(R.string.usage_save_to_encrypted), median = summary.saveToEncryptedMedianMs, p90 = summary.saveToEncryptedP90Ms, footer = null, sparkline = emptyList())
                }
                item(key = "weeks") { WeeksCard(summary.perWeek) }
                item(key = "entries") {
                    Surface(shape = RoundedCornerShape(Yaadein.radius.lg), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            Text(stringResource(R.string.usage_by_entry), style = MaterialTheme.typography.titleMedium)
                            summary.byEntry.forEach { (entry, count) ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(entryLabel(entry), style = MaterialTheme.typography.bodyMedium)
                                    Text(count.toString(), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                item(key = "privacy") { Text(stringResource(R.string.usage_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun entryLabel(entry: CaptureEntry): String = when (entry) {
    CaptureEntry.FAB -> stringResource(R.string.usage_entry_fab)
    CaptureEntry.SHARE -> stringResource(R.string.usage_entry_share)
    CaptureEntry.HUB -> stringResource(R.string.usage_entry_hub)
    CaptureEntry.WIDGET -> stringResource(R.string.usage_entry_widget)
}

@Composable
private fun StatTile(value: String, help: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(Yaadein.radius.lg), color = MaterialTheme.colorScheme.primaryContainer, modifier = modifier) {
        Column(Modifier.padding(Yaadein.spacing.lg)) {
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(help, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Seconds with one decimal under 10 s, whole seconds after; minutes past 90 s. */
@Composable
internal fun durationLabel(ms: Long): String = when {
    ms < 10_000 -> stringResource(R.string.usage_seconds_decimal, ms / 1000f)
    ms < 90_000 -> stringResource(R.string.usage_seconds, (ms / 1000).toInt())
    else -> stringResource(R.string.usage_minutes, (ms / 60_000).toInt())
}

@Composable
private fun TimingCard(title: String, median: Long?, p90: Long?, footer: String?, sparkline: List<Long>) {
    val spacing = Yaadein.spacing
    Surface(shape = RoundedCornerShape(Yaadein.radius.lg), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (median == null) {
                Text(stringResource(R.string.usage_not_yet), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xl)) {
                    Column { Text(durationLabel(median), style = MaterialTheme.typography.headlineSmall); Text(stringResource(R.string.usage_typical), style = MaterialTheme.typography.bodySmall) }
                    if (p90 != null) Column { Text(durationLabel(p90), style = MaterialTheme.typography.headlineSmall); Text(stringResource(R.string.usage_slowest), style = MaterialTheme.typography.bodySmall) }
                }
                if (sparkline.size >= 2) Sparkline(sparkline, Modifier.fillMaxWidth().height(40.dp).padding(top = spacing.xs))
                if (footer != null) Text(footer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Sparkline(values: List<Long>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.usage_sparkline_cd, values.size)
    Canvas(modifier.semantics { contentDescription = description }) {
        val max = (values.maxOrNull() ?: 1L).coerceAtLeast(1L).toFloat()
        val stepX = if (values.size > 1) size.width / (values.size - 1) else 0f
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - (v / max) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx()))
        values.lastOrNull()?.let { v -> drawCircle(color, radius = 3.dp.toPx(), center = Offset(size.width, size.height - (v / max) * size.height)) }
    }
}

@Composable
private fun WeeksCard(perWeek: List<Int>) {
    val spacing = Yaadein.spacing
    val bar = MaterialTheme.colorScheme.secondary
    val description = stringResource(R.string.usage_weeks_cd, perWeek.sum())
    Surface(shape = RoundedCornerShape(Yaadein.radius.lg), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(stringResource(R.string.usage_weeks), style = MaterialTheme.typography.titleMedium)
            Canvas(Modifier.fillMaxWidth().height(56.dp).semantics { contentDescription = description }) {
                val max = (perWeek.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
                val gap = 4.dp.toPx()
                val w = (size.width - gap * (perWeek.size - 1)) / perWeek.size
                perWeek.forEachIndexed { i, n ->
                    val h = (n / max) * size.height
                    drawRoundRect(bar, topLeft = Offset(i * (w + gap), size.height - h), size = androidx.compose.ui.geometry.Size(w, h.coerceAtLeast(2.dp.toPx())), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.usage_weeks_ago, perWeek.size), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.usage_this_week), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
