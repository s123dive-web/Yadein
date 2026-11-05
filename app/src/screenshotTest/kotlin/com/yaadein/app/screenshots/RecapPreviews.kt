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

private const val PHONE = "spec:width=411dp,height=891dp"

@PreviewTest
@Preview(name = "Recap setup", device = PHONE, showBackground = true)
@Composable
fun RecapSetup() = ScreenshotTheme {
    val memories = listOf(Fixtures.detail)
    val plan = RecapPlanner.plan(memories, RecapTheme.WARM, RecapLength.MEDIUM, "Moments with Rahul", "2024", "Made with Yaadein", { "9 Sep 2024" })
    Surface {
        RecapScreen(
            state = RecapUiState(loading = false, scope = Scope.PERSON, person = Fixtures.rahul, people = Fixtures.people, groups = Fixtures.story.groups, memories = memories, plan = plan, title = "Moments with Rahul", music = MusicChoice.Bundled("warm"), render = RenderStatus.Running(42)),
            onBack = {}, onScope = {}, onPerson = {}, onGroup = {}, onYear = {}, onTheme = {}, onLength = {}, onIncludeVideos = {}, onMusic = {}, onPickOwnMusic = {}, onTitle = {}, onRender = {}, onCancel = {}, onReset = {}, onShare = {}, years = listOf(2026, 2025, 2024),
        )
    }
}
