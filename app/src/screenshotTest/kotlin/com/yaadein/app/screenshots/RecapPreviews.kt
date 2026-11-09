package com.yaadein.app.screenshots

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.yaadein.domain.recap.RecapLength
import com.yaadein.domain.recap.RecapPlanner
import com.yaadein.domain.recap.RecapTheme
import com.yaadein.feature.recap.MusicChoice
import com.yaadein.feature.recap.RecapScreen
import com.yaadein.feature.recap.RecapUiState
import com.yaadein.feature.recap.RenderStatus
import com.yaadein.feature.recap.Scope
import com.yaadein.feature.recap.ReelsScreen
import com.yaadein.feature.recap.ReelsUiState
import com.yaadein.data.reels.ReelEntry
import com.yaadein.data.reels.ReelKind

private const val PHONE = "spec:width=411dp,height=891dp"

@PreviewTest
@Preview(name = "Recap setup", device = PHONE, showBackground = true)
@Composable
fun RecapSetup() = ScreenshotTheme {
    val memories = listOf(Fixtures.detail)
    val selected = memories.flatMap { it.media }.map { it.id }.toSet()
    val plan = RecapPlanner.plan(memories, RecapTheme.WARM, RecapLength.MEDIUM, "Moments with Rahul", "2024", "Made with Yaadein", { "9 Sep 2024" }, selectedMediaIds = selected)
    Surface {
        RecapScreen(
            state = RecapUiState(loading = false, scope = Scope.PERSON, person = Fixtures.rahul, people = Fixtures.people, groups = Fixtures.story.groups, memories = memories, selectedMediaIds = selected, plan = plan, title = "Moments with Rahul", music = MusicChoice.Bundled("warm"), render = RenderStatus.Running(42)),
            onBack = {}, onScope = {}, onPerson = {}, onGroup = {}, onYear = {}, onTheme = {}, onLength = {}, onIncludeVideos = {}, onMusic = {}, onPickOwnMusic = {}, onTitle = {}, onRender = {}, onCancel = {}, onReset = {}, onShare = {}, years = listOf(2026, 2025, 2024),
        )
    }
}

@PreviewTest
@Preview(name = "Your reels", device = PHONE, showBackground = true)
@Composable
fun YourReels() = ScreenshotTheme {
    fun reel(id: String, title: String, kind: ReelKind, theme: String, slot: String? = null, day: Long) =
        ReelEntry(id = id, title = title, kind = kind, createdAt = 1_788_000_000_000L + day * 86_400_000L, durationMs = 31_000, sizeBytes = 1, videoPath = "", theme = theme, autoSlot = slot)
    Surface {
        ReelsScreen(
            state = ReelsUiState(
                loading = false,
                auto = listOf(
                    reel("a", "Recent moments", ReelKind.AUTO, "WARM", "recent", 3),
                    reel("b", "Moments with Rahul", ReelKind.AUTO, "FESTIVE", "person", 3),
                    reel("c", "This month, years ago", ReelKind.AUTO, "CLASSIC_FILM", "throwback", 3),
                ),
                mine = listOf(reel("d", "Goa Getaway", ReelKind.USER, "WARM", day = 2), reel("e", "Diwali 2025", ReelKind.USER, "FESTIVE", day = 1)),
            ),
            onBack = {}, onPlay = {}, onCreate = {}, onShare = {}, onDelete = {},
        )
    }
}
