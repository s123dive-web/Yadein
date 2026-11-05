package com.yaadein.app.screenshots

import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.yaadein.data.media.PickedMedia
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.polish.PolishEngine
import com.yaadein.feature.capture.CaptureActions
import com.yaadein.feature.capture.CaptureHubContent
import com.yaadein.feature.capture.DraftMemory
import com.yaadein.feature.capture.EventCaptureScreen
import com.yaadein.feature.capture.EventCaptureUiState
import com.yaadein.feature.capture.PastWizardScreen
import com.yaadein.feature.capture.PastWizardUiState
import com.yaadein.feature.capture.PhotoFirstScreen
import com.yaadein.feature.capture.PhotoFirstUiState
import com.yaadein.feature.capture.QuickCaptureScreen
import com.yaadein.feature.capture.QuickCaptureUiState
import com.yaadein.feature.capture.TextOnlyScreen
import com.yaadein.feature.capture.TextOnlyUiState
import com.yaadein.feature.people.GroupEditScreen
import com.yaadein.feature.people.GroupEditUiState
import com.yaadein.feature.settings.StorageScreen
import com.yaadein.feature.settings.StorageUiState
import java.time.LocalDate

private const val PHONE = "spec:width=411dp,height=891dp"

@PreviewTest
@Preview(name = "Capture hub", device = PHONE, showBackground = true)
@Composable
fun CaptureHub() = ScreenshotTheme { Surface { CaptureHubContent(actions = CaptureActions({}, {}, {}, {}, {})) } }

@PreviewTest
@Preview(name = "Event capture", device = PHONE, showBackground = true)
@Composable
fun EventCapture() = ScreenshotTheme {
    Surface {
        EventCaptureScreen(
            state = EventCaptureUiState(loading = false, title = "Diwali at home", place = "Pune", date = Dates.exact(LocalDate.of(2024, 10, 31)), people = Fixtures.people, selectedPeople = setOf(1, 2, 4), description = "Aai's faral, Baba on the ladder with the lights."),
            onBack = {}, onTitle = {}, onDescription = {}, onDate = {}, onPlace = {}, onMood = {}, onCoverStyle = {}, onTogglePerson = {}, onAddPerson = {},
            onPickMedia = {}, onRemovePending = {}, onOptimize = {}, onDismissRejected = {}, onSave = {},
        )
    }
}

@PreviewTest
@Preview(name = "Text only with polish", device = PHONE, showBackground = true)
@Composable
fun TextOnly() = ScreenshotTheme {
    Surface {
        TextOnlyScreen(
            state = TextOnlyUiState(input = "goa trip dec 2024", polished = PolishEngine.polish("goa trip dec 2024", LocalDate.of(2026, 11, 5)), people = Fixtures.people, selectedPeople = setOf(3, 6)),
            onBack = {}, onInput = {}, onDictate = {}, onTogglePerson = {}, onAddPerson = {}, onDate = {}, onSmart = {}, onSave = {},
        )
    }
}

@PreviewTest
@Preview(name = "Quick capture", device = PHONE, showBackground = true)
@Composable
fun QuickCapture() = ScreenshotTheme {
    Surface {
        QuickCaptureScreen(
            state = QuickCaptureUiState(text = "Kabir said 'Aai' for the first time", people = Fixtures.people, selectedPeople = setOf(1, 8)),
            onBack = {}, onText = {}, onPickPhoto = {}, onClearPhoto = {}, onTogglePerson = {}, onAddPerson = {}, onDismissRejected = {}, onSave = {},
        )
    }
}

@PreviewTest
@Preview(name = "Photo first drafts", device = PHONE, showBackground = true)
@Composable
fun PhotoFirst() = ScreenshotTheme {
    val items = (1..4).map { PickedMedia(Uri.parse("content://preview/$it"), false, "image/jpeg", 1000, null, null) }
    Surface {
        PhotoFirstScreen(
            state = PhotoFirstUiState(
                picking = false,
                drafts = listOf(
                    DraftMemory(1, "December 2024", Dates.exact(LocalDate.of(2024, 12, 20), LocalDate.of(2024, 12, 23)), items, setOf(3)),
                    DraftMemory(2, "31 October 2024", Dates.exact(LocalDate.of(2024, 10, 31)), items.take(2)),
                ),
                people = Fixtures.people,
            ),
            onBack = {}, onPickMore = {}, onTitle = { _, _ -> }, onDate = { _, _ -> }, onTogglePerson = { _, _ -> }, onAddPerson = {}, onMerge = {}, onSplit = {}, onRemove = {}, onDismissRejected = {}, onSaveAll = {},
        )
    }
}

@PreviewTest
@Preview(name = "Fill in the past", device = PHONE, showBackground = true)
@Composable
fun PastWizard() = ScreenshotTheme {
    Surface {
        PastWizardScreen(
            state = PastWizardUiState(years = (2025 downTo 2001).toList(), index = 6, input = "Rajmachi trek in the rains", countsByYear = mapOf(2019 to 2), people = Fixtures.people, selectedPeople = setOf(3, 6)),
            onBack = {}, onInput = {}, onDictate = {}, onTogglePerson = {}, onAddPerson = {}, onSaveMore = {}, onSaveNext = {}, onSkip = {}, onPrevious = {},
        )
    }
}

@PreviewTest
@Preview(name = "Group editor", device = PHONE, showBackground = true)
@Composable
fun GroupEditor() = ScreenshotTheme {
    Surface {
        GroupEditScreen(
            state = GroupEditUiState(loading = false, isNew = false, group = com.yaadein.domain.model.Group(1, "College friends", "🎓", listOf(3, 6, 7)), people = Fixtures.people),
            onBack = {}, onName = {}, onEmoji = {}, onToggleMember = {}, onSave = {}, onDelete = {},
        )
    }
}

@PreviewTest
@Preview(name = "Storage", device = PHONE, showBackground = true)
@Composable
fun Storage() = ScreenshotTheme {
    Surface {
        StorageScreen(state = StorageUiState(mediaBytes = 1_340_000_000, databaseBytes = 12_000_000, photoCount = 412, videoCount = 23, freeBytes = 48_000_000_000, loading = false), onBack = {}, onOptimize = {}, onErase = {})
    }
}
