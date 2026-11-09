package com.yaadein.app

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import com.yaadein.core.ui.theme.YaadeinTheme
import com.yaadein.core.ui.util.asPagedItems
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.model.Person
import com.yaadein.domain.settings.UserSettings
import com.yaadein.feature.lock.LockScreen
import com.yaadein.feature.lock.LockUiState
import com.yaadein.feature.people.PeopleScreen
import com.yaadein.feature.people.PeopleUiState
import com.yaadein.feature.people.PersonTile
import com.yaadein.feature.settings.SettingsScreen
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
}
