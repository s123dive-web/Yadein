package com.yaadein.app.screenshots

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import androidx.compose.ui.unit.dp
import com.yaadein.core.ui.util.asPagedItems
import com.yaadein.domain.repository.TimelineFilter
import com.yaadein.domain.settings.AppTheme
import com.yaadein.feature.lock.LockScreen
import com.yaadein.feature.lock.LockUiState
import com.yaadein.feature.memory.MemoryDetailScreen
import com.yaadein.feature.memory.MemoryDetailUiState
import com.yaadein.feature.onboarding.OnboardingScreen
import com.yaadein.feature.people.PeopleScreen
import com.yaadein.feature.people.PeopleUiState
import com.yaadein.feature.people.PersonDetailScreen
import com.yaadein.feature.people.PersonDetailUiState
import com.yaadein.feature.people.PersonTile
import com.yaadein.feature.timeline.TimelineItem
import com.yaadein.feature.timeline.TimelineScreen
import com.yaadein.feature.timeline.TimelineSidebar
import com.yaadein.feature.timeline.TimelineUiState
import com.yaadein.feature.timeline.TimelineViewModel

private const val PHONE = "spec:width=411dp,height=891dp"
private const val TABLET = "spec:width=1280dp,height=800dp,dpi=240"

private fun timelineItems(): List<TimelineItem> {
    val cards = Fixtures.cards.sortedByDescending { it.memory.date.startEpochDay }.map { TimelineItem.Card(it) }
    val out = ArrayList<TimelineItem>()
    var prev: TimelineItem? = null
    for (c in cards) {
        TimelineViewModel.separatorBetween(prev, c)?.let { out += it }
        out += c
        prev = c
    }
    return out
}

@Composable
private fun TimelinePreviewContent(modifier: Modifier = Modifier) {
    val items = timelineItems().asPagedItems()
    TimelineScreen(
        state = TimelineUiState(people = Fixtures.people, totalMemories = Fixtures.cards.size),
        items = items,
        onOpenMemory = {}, onCreate = {}, onQueryChange = {}, onSearchActive = {}, onFilterChange = {}, onClearFilter = {},
        contentPadding = PaddingValues(),
        modifier = modifier,
    )
}

@PreviewTest
@Preview(name = "Timeline phone", device = PHONE, showBackground = true)
@Composable
fun TimelinePhone() = ScreenshotTheme { Surface { TimelinePreviewContent() } }

@PreviewTest
@Preview(name = "Timeline phone dark", device = PHONE, showBackground = true)
@Composable
fun TimelinePhoneDark() = ScreenshotTheme(dark = true) { Surface { TimelinePreviewContent() } }

@PreviewTest
@Preview(name = "Timeline tablet", device = TABLET, showBackground = true)
@Composable
fun TimelineTablet() = ScreenshotTheme {
    Surface(Modifier.fillMaxSize()) {
        Row {
            TimelineSidebar(people = Fixtures.people, filter = TimelineFilter(personIds = setOf(3)), onFilterChange = {}, modifier = Modifier.width(360.dp))
            TimelinePreviewContent(Modifier.weight(1f))
        }
    }
}

@PreviewTest
@Preview(name = "Timeline Midnight", device = PHONE, showBackground = true)
@Composable
fun TimelineMidnight() = ScreenshotTheme(theme = AppTheme.MIDNIGHT, dark = true) { Surface { TimelinePreviewContent() } }

@PreviewTest
@Preview(name = "Timeline Festive", device = PHONE, showBackground = true)
@Composable
fun TimelineFestive() = ScreenshotTheme(theme = AppTheme.FESTIVE) { Surface { TimelinePreviewContent() } }

@PreviewTest
@Preview(name = "Timeline Easy mode", device = PHONE, showBackground = true)
@Composable
fun TimelineEasyMode() = ScreenshotTheme(easy = true) { Surface { TimelinePreviewContent() } }

@PreviewTest
@Preview(name = "Memory detail phone", device = PHONE, showBackground = true)
@Composable
fun MemoryDetailPhone() = ScreenshotTheme {
    Surface { MemoryDetailScreen(state = MemoryDetailUiState(false, Fixtures.detail), onBack = {}, onOpenMedia = {}, onOpenPerson = {}, onEdit = {}, onAddMedia = {}, onRecap = {}, onDelete = {}, onSetCover = {}) }
}

@PreviewTest
@Preview(name = "Memory detail tablet", device = TABLET, showBackground = true)
@Composable
fun MemoryDetailTablet() = ScreenshotTheme {
    Surface { MemoryDetailScreen(state = MemoryDetailUiState(false, Fixtures.detail), onBack = {}, onOpenMedia = {}, onOpenPerson = {}, onEdit = {}, onAddMedia = {}, onRecap = {}, onDelete = {}, onSetCover = {}) }
}

@PreviewTest
@Preview(name = "People phone", device = PHONE, showBackground = true)
@Composable
fun PeoplePhone() = ScreenshotTheme {
    Surface {
        PeopleScreen(
            state = PeopleUiState(false, Fixtures.people.mapIndexed { i, p -> PersonTile(p, 20 - i * 2) }, Fixtures.story.groups),
            onOpenPerson = {}, onAddPerson = {}, onOpenGroup = {}, onAddGroup = {}, onOpenRelationships = {}, contentPadding = PaddingValues(),
        )
    }
}

@PreviewTest
@Preview(name = "People tablet", device = TABLET, showBackground = true)
@Composable
fun PeopleTablet() = ScreenshotTheme {
    Surface {
        PeopleScreen(
            state = PeopleUiState(false, Fixtures.people.mapIndexed { i, p -> PersonTile(p, 20 - i * 2) }, Fixtures.story.groups),
            onOpenPerson = {}, onAddPerson = {}, onOpenGroup = {}, onAddGroup = {}, onOpenRelationships = {}, contentPadding = PaddingValues(),
        )
    }
}

@PreviewTest
@Preview(name = "Person story phone", device = PHONE, showBackground = true)
@Composable
fun PersonStoryPhone() = ScreenshotTheme {
    val items = Fixtures.cards.asPagedItems()
    Surface {
        PersonDetailScreen(state = PersonDetailUiState(false, Fixtures.story, listOf(2024, 2023, 2022)), memories = items, onBack = {}, onOpenMemory = {}, onEdit = {}, onTogether = {}, onYearSummary = {}, onRecap = {})
    }
}

@PreviewTest
@Preview(name = "Lock screen", device = PHONE, showBackground = true)
@Composable
fun LockPhone() = ScreenshotTheme {
    LockScreen(state = LockUiState(pin = "12", biometricAvailable = true), onDigit = {}, onBackspace = {}, onBiometric = {})
}

@PreviewTest
@Preview(name = "Onboarding", device = PHONE, showBackground = true)
@Composable
fun OnboardingPhone() = ScreenshotTheme { OnboardingScreen(onFinish = {}) }

@PreviewTest
@Preview(name = "Empty timeline", device = PHONE, showBackground = true)
@Composable
fun EmptyTimeline() = ScreenshotTheme {
    val items = emptyList<TimelineItem>().asPagedItems()
    Surface {
        TimelineScreen(
            state = TimelineUiState(totalMemories = 0), items = items,
            onOpenMemory = {}, onCreate = {}, onQueryChange = {}, onSearchActive = {}, onFilterChange = {}, onClearFilter = {},
            contentPadding = PaddingValues(),
        )
    }
}
