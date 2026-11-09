package com.yaadein.app.screenshots

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.yaadein.domain.settings.UserSettings
import com.yaadein.feature.people.GraphEdge
import com.yaadein.feature.people.GraphNode
import com.yaadein.feature.people.RelationshipsScreen
import com.yaadein.feature.people.RelationshipsUiState
import com.yaadein.feature.people.RelationshipsViewModel
import com.yaadein.feature.people.TogetherScreen
import com.yaadein.feature.people.TogetherUiState
import com.yaadein.domain.relive.RewindItem
import com.yaadein.domain.relive.RewindResult
import com.yaadein.domain.relive.RewindScope
import com.yaadein.feature.relive.PlaceHighlight
import com.yaadein.feature.relive.ReliveScreen
import com.yaadein.feature.relive.ReliveUiState
import com.yaadein.feature.relive.YearHighlight
import com.yaadein.feature.relive.YearlySummaryScreen
import com.yaadein.feature.relive.YearlySummaryUiState
import com.yaadein.feature.settings.NotificationsScreen
import com.yaadein.feature.settings.SecurityScreen

private const val PHONE = "spec:width=411dp,height=891dp"

@PreviewTest
@Preview(name = "Relive", device = PHONE, showBackground = true)
@Composable
fun Relive() = ScreenshotTheme {
    Surface {
        ReliveScreen(
            state = ReliveUiState(
                loading = false,
                rewind = RewindResult(RewindScope.WEEK, Fixtures.cards.take(1).map { RewindItem(it, 2) }),
                years = listOf(YearHighlight(2026, 8), YearHighlight(2025, 14), YearHighlight(2024, 11)),
                places = listOf(PlaceHighlight("Pune", 12), PlaceHighlight("Goa", 5)),
                upcomingBirthdays = listOf(Fixtures.rahul to 3, Fixtures.aai to 25),
                totalMemories = 33,
                showShakeHint = true,
            ),
            onOpenMemory = {}, onOpenPerson = {}, onSurprise = {}, onDismissRandom = {}, onRecap = {}, onYear = {}, contentPadding = PaddingValues(),
        )
    }
}

@PreviewTest
@Preview(name = "Together", device = PHONE, showBackground = true)
@Composable
fun Together() = ScreenshotTheme {
    Surface { TogetherScreen(state = TogetherUiState(people = Fixtures.people, selected = setOf(3, 4), cards = Fixtures.cards.filter { c -> c.people.any { it.id == 3L } && c.people.any { it.id == 4L } }), onBack = {}, onToggle = {}, onOpenMemory = {}) }
}

@PreviewTest
@Preview(name = "Relationships map", device = PHONE, showBackground = true)
@Composable
fun RelationshipsMap() = ScreenshotTheme {
    val nodes = Fixtures.people.mapIndexed { i, p -> GraphNode(p, listOf(20, 18, 16, 14, 12, 10, 8, 6)[i], 0f, 0f) }
    val edges = listOf(GraphEdge(1, 2, 15), GraphEdge(1, 3, 12), GraphEdge(2, 3, 10), GraphEdge(3, 4, 9), GraphEdge(3, 6, 8), GraphEdge(6, 7, 6), GraphEdge(3, 7, 5), GraphEdge(1, 5, 7), GraphEdge(1, 8, 6), GraphEdge(4, 8, 4))
    Surface { RelationshipsScreen(state = RelationshipsUiState(loading = false, nodes = RelationshipsViewModel.layout(nodes, edges), edges = edges), onBack = {}, onOpenPerson = {}) }
}

@PreviewTest
@Preview(name = "Yearly summary", device = PHONE, showBackground = true)
@Composable
fun YearlySummary() = ScreenshotTheme {
    Surface {
        YearlySummaryScreen(
            state = YearlySummaryUiState(loading = false, year = 2024, person = Fixtures.rahul, cards = Fixtures.cards.filter { c -> c.people.any { it.id == 3L } }.sortedBy { it.memory.date.startEpochDay }, photoCount = 23, places = listOf("Pune", "Goa"), topPeople = listOf(Fixtures.priya to 4, Fixtures.aai to 3, Fixtures.neha to 2)),
            onBack = {}, onOpenMemory = {}, onOpenPerson = {}, onRecap = {},
        )
    }
}

@PreviewTest
@Preview(name = "Notification settings", device = PHONE, showBackground = true)
@Composable
fun NotificationSettings() = ScreenshotTheme {
    Surface { NotificationsScreen(settings = UserSettings(), onBack = {}, onOnThisDay = {}, onTime = {}, onShake = {}) }
}

@PreviewTest
@Preview(name = "Security settings", device = PHONE, showBackground = true)
@Composable
fun SecuritySettings() = ScreenshotTheme {
    Surface { SecurityScreen(settings = UserSettings(), biometricAvailable = true, onBack = {}, onChangePin = {}, onBiometric = {}, onTimeout = {}, onSecureScreen = {}, onWipe = {}) }
}
