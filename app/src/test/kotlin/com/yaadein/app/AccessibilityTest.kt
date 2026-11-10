package com.yaadein.app

import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import com.yaadein.core.ui.media.ProvidePreviewImages
import com.yaadein.core.ui.theme.YaadeinTheme
import com.yaadein.core.ui.util.asPagedItems
import com.yaadein.data.media.PickedMedia
import com.yaadein.domain.capture.CaptureEntry
import com.yaadein.domain.capture.CaptureStat
import com.yaadein.domain.capture.DateSource
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.model.Person
import com.yaadein.domain.settings.UserSettings
import com.yaadein.domain.usage.UsageSummary
import com.yaadein.feature.capture.QuickSaveScreen
import com.yaadein.feature.capture.QuickSaveUiState
import com.yaadein.feature.lock.LockScreen
import com.yaadein.feature.lock.LockUiState
import com.yaadein.feature.people.PeopleScreen
import com.yaadein.feature.people.PeopleUiState
import com.yaadein.feature.people.FaceGroup
import com.yaadein.feature.people.FaceSample
import com.yaadein.feature.people.PersonTile
import com.yaadein.feature.people.WhoIsThisCallbacks
import com.yaadein.feature.people.WhoIsThisScreen
import com.yaadein.feature.people.WhoIsThisUiState
import com.yaadein.feature.settings.FacesScreen
import com.yaadein.feature.settings.FacesUiState
import com.yaadein.feature.settings.SettingsScreen
import com.yaadein.feature.settings.UsageScreen
import com.yaadein.feature.timeline.TimelineItem
import com.yaadein.feature.timeline.TimelineScreen
import com.yaadein.feature.timeline.TimelineUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** Every tappable thing on the main screens must announce something to TalkBack (text or content description). */
@RunWith(RobolectricTestRunner::class)
class AccessibilityTest {
    @get:Rule val compose = createComposeRule()

    private val people = listOf(Person(1, "Sunita Deshmukh", "Aai"), Person(2, "Rahul Deshmukh", "Rahul", avatarColorIndex = 2))
    private val cards = listOf(
        MemoryCard(Memory(1, MemoryType.EVENT, "Diwali at home", "lights", Dates.exact(LocalDate.of(2024, 10, 31)), "Pune"), people, null, 0),
        MemoryCard(Memory(2, MemoryType.TEXT_ONLY, "Goa Getaway", "beach", Dates.year(2023)), people.take(1), null, 0),
    )

    private fun assertAllClickablesLabelled(nodes: SemanticsNodeInteractionCollection) {
        val unlabeled = nodes.fetchSemanticsNodes().filter { node ->
            val config = node.config
            val hasText = config.getOrNull(SemanticsProperties.Text)?.any { it.text.isNotBlank() } == true ||
                config.getOrNull(SemanticsProperties.EditableText)?.text?.isNotBlank() == true
            val hasDescription = config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.isNotBlank() } == true
            val mergedText = node.children.any { c -> c.config.getOrNull(SemanticsProperties.Text)?.isNotEmpty() == true }
            !(hasText || hasDescription || mergedText)
        }
        assertTrue("unlabelled clickable nodes: ${unlabeled.map { it.id }}", unlabeled.isEmpty())
    }

    @Test
    fun `timeline clickables are labelled`() {
        compose.setContent {
            YaadeinTheme {
                val items = listOf<TimelineItem>(TimelineItem.YearHeader(2024, java.time.Month.OCTOBER), TimelineItem.Card(cards[0]), TimelineItem.Card(cards[1])).asPagedItems()
                TimelineScreen(state = TimelineUiState(people = people, totalMemories = 2), items = items, onOpenMemory = {}, onCreate = {}, onQueryChange = {}, onSearchActive = {}, onFilterChange = {}, onClearFilter = {}, contentPadding = PaddingValues())
            }
        }
        assertAllClickablesLabelled(compose.onAllNodes(hasClickAction(), useUnmergedTree = false))
    }

    @Test
    fun `people clickables are labelled`() {
        compose.setContent {
            YaadeinTheme {
                PeopleScreen(state = PeopleUiState(false, people.map { PersonTile(it, 3) }, emptyList()), onOpenPerson = {}, onAddPerson = {}, onOpenGroup = {}, onAddGroup = {}, onOpenRelationships = {}, contentPadding = PaddingValues())
            }
        }
        assertAllClickablesLabelled(compose.onAllNodes(hasClickAction()))
    }

    @Test
    fun `lock clickables are labelled`() {
        compose.setContent { YaadeinTheme { LockScreen(state = LockUiState(biometricAvailable = true), onDigit = {}, onBackspace = {}, onBiometric = {}) } }
        assertAllClickablesLabelled(compose.onAllNodes(hasClickAction()))
    }

    @Test
    fun `settings clickables are labelled`() {
        compose.setContent { YaadeinTheme { SettingsScreen(settings = UserSettings(), onToggleEasyMode = {}, onOpen = {}, contentPadding = PaddingValues()) } }
        assertAllClickablesLabelled(compose.onAllNodes(hasClickAction()))
    }

    // ---- Fast & Rich round: Faces v2, Quick Save, How I use Yaadein ----

    private fun faces(enabled: Boolean) = FacesUiState(enabled = enabled, unknownFaces = 3, unscannedPhotos = 5)

    @Test
    fun `face suggestions clickables are labelled when enabled`() {
        compose.setContent { YaadeinTheme { FacesScreen(state = faces(enabled = true), onBack = {}, onEnabled = {}, onScan = {}, onWhoIsThis = {}, onDeleteAll = {}) } }
        assertAllClickablesLabelled(compose.onAllNodes(hasClickAction()))
    }

    @Test
    fun `face suggestions clickables are labelled when off`() {
        compose.setContent { YaadeinTheme { FacesScreen(state = faces(enabled = false), onBack = {}, onEnabled = {}, onScan = {}, onWhoIsThis = {}, onDeleteAll = {}) } }
        assertAllClickablesLabelled(compose.onAllNodes(hasClickAction()))
    }

    private val whoCallbacks = WhoIsThisCallbacks(onBack = {}, onSelectPerson = {}, onAddPerson = {}, onNameThem = {}, onNotTheSame = {}, onSkip = {})
    private val faceGroup = FaceGroup(
        faces = listOf(
            FaceSample(faceId = 1, mediaId = 10, left = 0.2f, top = 0.1f, right = 0.5f, bottom = 0.5f, mediaWidth = 1200, mediaHeight = 900),
            FaceSample(faceId = 2, mediaId = 11, left = 0.4f, top = 0.2f, right = 0.7f, bottom = 0.6f, mediaWidth = 1200, mediaHeight = 900),
        ),
    )

    @Test
    fun `who is this clickables are labelled`() {
        compose.setContent {
            YaadeinTheme {
                // Face crops come from the encrypted vault; on the JVM they are drawn with a flat painter instead.
                ProvidePreviewImages(painter = { ColorPainter(Color.Gray) }) {
                    WhoIsThisScreen(state = WhoIsThisUiState(loading = false, group = faceGroup, remaining = 2, people = people, selectedPersonId = 1), callbacks = whoCallbacks)
                }
            }
        }
        assertAllClickablesLabelled(compose.onAllNodes(hasClickAction()))
    }

    @Test
    fun `who is this empty state clickables are labelled`() {
        compose.setContent { YaadeinTheme { WhoIsThisScreen(state = WhoIsThisUiState(loading = false, group = null), callbacks = whoCallbacks) } }
        assertAllClickablesLabelled(compose.onAllNodes(hasClickAction()))
    }

    private fun quickSave(state: QuickSaveUiState) {
        compose.setContent {
            YaadeinTheme {
                QuickSaveScreen(
                    state = state, onBack = {}, onTitle = {}, onDate = {}, onTogglePerson = {}, onAddPerson = {}, onPlace = {}, onOpenPlace = {},
                    onSetCover = {}, onRemove = {}, onSplit = {}, onDismissSplit = {}, onWords = {}, onAddPhotos = {}, onMoreWays = {}, onDismissRejected = {}, onSave = {},
                )
            }
        }
    }

    @Test
    fun `quick save clickables are labelled`() {
        val items = listOf(
            PickedMedia(uri = Uri.parse("content://media/picker/1"), isVideo = false, mimeType = "image/jpeg", sizeBytes = 1_000, takenAtMillis = null, displayName = "a.jpg"),
            PickedMedia(uri = Uri.parse("content://media/picker/2"), isVideo = true, mimeType = "video/mp4", sizeBytes = 2_000, takenAtMillis = null, displayName = "b.mp4"),
        )
        quickSave(
            QuickSaveUiState(
                items = items, date = Dates.exact(LocalDate.of(2024, 12, 20)), dateSource = DateSource.PHOTOS, suggestedTitle = "20 Dec 2024 with Aai",
                people = people, shortlist = people, selectedPeople = setOf(1L), suggestedPeople = setOf(2L), splitInto = 3,
            ),
        )
        assertAllClickablesLabelled(compose.onAllNodes(hasClickAction()))
    }

    @Test
    fun `quick save words-only clickables are labelled`() {
        quickSave(QuickSaveUiState(items = emptyList(), words = "Aai made modak for Ganpati", people = people, shortlist = people))
        assertAllClickablesLabelled(compose.onAllNodes(hasClickAction()))
    }

    private fun usageSummary(): UsageSummary {
        val now = 1_700_000_000_000L
        val stats = (0 until 5).map { i ->
            CaptureStat(
                id = i + 1L, entry = if (i % 2 == 0) CaptureEntry.FAB else CaptureEntry.SHARE, itemCount = i + 1, tapToSaveMs = 4_000L + i * 3_000L,
                saveToEncryptedMs = if (i == 4) null else 2_500L + i * 500L, createdAt = now - i * 2L * 24 * 60 * 60 * 1000,
            )
        }
        return UsageSummary.from(stats, now)
    }

    @Test
    fun `how I use yaadein clickables are labelled`() {
        compose.setContent { YaadeinTheme { UsageScreen(summary = usageSummary(), onBack = {}) } }
        assertAllClickablesLabelled(compose.onAllNodes(hasClickAction()))
    }

    @Test
    fun `how I use yaadein empty state clickables are labelled`() {
        compose.setContent { YaadeinTheme { UsageScreen(summary = UsageSummary.from(emptyList(), 0L), onBack = {}) } }
        assertAllClickablesLabelled(compose.onAllNodes(hasClickAction()))
    }
}
