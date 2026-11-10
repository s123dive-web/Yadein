package com.yaadein.feature.relive

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.yaadein.core.ui.theme.YaadeinTheme
import com.yaadein.domain.dates.Dates
import com.yaadein.domain.model.Memory
import com.yaadein.domain.model.MemoryCard
import com.yaadein.domain.model.MemoryType
import com.yaadein.domain.model.Person
import com.yaadein.domain.relive.DatedPhoto
import com.yaadein.domain.relive.Milestone
import com.yaadein.domain.relive.RingKind
import com.yaadein.domain.relive.StoryPage
import com.yaadein.domain.relive.StoryRing
import com.yaadein.domain.relive.ThenNowPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** The full-screen story player: taps, swipes, the buttons that mirror them, autoplay and the page actions. */
@RunWith(RobolectricTestRunner::class)
class StoryPlayerScreenTest {
    @get:Rule val compose = createComposeRule()

    private val aaji = Person(id = 5, name = "Shalini Joshi", nickname = "Aaji")
    private fun card(id: Long, title: String) =
        MemoryCard(Memory(id, MemoryType.EVENT, title, date = Dates.exact(LocalDate.of(2020, 1, id.toInt()))), listOf(aaji), null, 0)
    private val pages = listOf<StoryPage>(
        StoryPage.Moment(card(1, "Wedding in Pune"), yearsAgo = null),
        StoryPage.Moment(card(2, "Diwali at home"), yearsAgo = 3),
        StoryPage.Milestone(Milestone.YearsWith(5, 12), card(3, "Picnic"), person = aaji),
    )
    private val rings = listOf(
        RingUi(StoryRing(RingKind.TODAY, "today"), null, seen = false),
        RingUi(StoryRing(RingKind.PERSON, "person-5", person = aaji), null, seen = false),
    )
    private fun state(pages: List<StoryPage> = this.pages, ringIndex: Int = 0) =
        StoryPlayerUiState(loading = false, rings = rings, ringIndex = ringIndex, pages = pages, pagesLoading = false)

    private class Calls {
        var closed = 0; var nextRing = 0; var previousRing = 0; var sound = 0
        val opened = ArrayList<Long>(); val favorites = ArrayList<Long>(); val notes = ArrayList<Pair<Long, String>>(); val voice = ArrayList<Long>()
        val reels = ArrayList<StoryPage?>(); val music = ArrayList<String?>()
        val actions = StoryPlayerActions(
            onClose = { closed++ }, onNextRing = { nextRing++ }, onPreviousRing = { previousRing++ },
            onOpenMemory = { opened += it }, onToggleFavorite = { favorites += it }, onAddNote = { id, text -> notes += id to text },
            onRecordVoice = { voice += it }, onMakeReel = { reels += it }, onToggleSound = { sound++ }, onChooseMusic = { music += it },
        )
    }

    private fun show(state: StoryPlayerUiState, calls: Calls, motion: Boolean = false, soundOn: Boolean = true) {
        compose.setContent { YaadeinTheme { StoryPlayerScreen(state = state, actions = calls.actions, motion = motion, soundOn = soundOn) } }
    }

    /** The page area (it carries "Today. Story, page 1 of 3" for TalkBack). */
    private fun pageArea() = compose.onNode(hasContentDescription("Story, page", substring = true))
    private fun tapRight() = pageArea().performTouchInput { click(Offset(width * 0.85f, height * 0.5f)) }
    private fun tapLeft() = pageArea().performTouchInput { click(Offset(width * 0.1f, height * 0.5f)) }

    @Test
    fun `tapping right and left moves through the pages and past the ends to the neighbouring stories`() {
        val calls = Calls()
        show(state(), calls)
        compose.onNodeWithText("Wedding in Pune").assertIsDisplayed()
        tapLeft()
        assertEquals("left on the first page asks for the previous story", 1, calls.previousRing)
        tapRight()
        compose.onNodeWithText("Diwali at home").assertIsDisplayed()
        compose.onNodeWithText("3 years ago").assertIsDisplayed()
        tapRight()
        compose.onNodeWithText("12 years with Aaji").assertIsDisplayed()
        tapLeft()
        compose.onNodeWithText("Diwali at home").assertIsDisplayed()
        tapRight(); tapRight()
        assertEquals("right on the last page asks for the next story", 1, calls.nextRing)
        assertEquals(0, calls.closed)
    }

    @Test
    fun `swiping sideways changes story and swiping down closes`() {
        val calls = Calls()
        show(state(), calls)
        pageArea().performTouchInput { swipeLeft() }
        assertEquals(1, calls.nextRing)
        pageArea().performTouchInput { swipeRight() }
        assertEquals(1, calls.previousRing)
        pageArea().performTouchInput { swipeDown() }
        assertEquals(1, calls.closed)
        // A swipe is not a tap: the page did not change.
        compose.onNodeWithText("Wedding in Pune").assertIsDisplayed()
    }

    @Test
    fun `the story plays by itself, six seconds a page, and hands over to the next story at the end`() {
        val calls = Calls()
        compose.mainClock.autoAdvance = false
        show(state(), calls, motion = true)
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithText("Wedding in Pune").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(STORY_PAGE_MILLIS.toLong())
        compose.onNodeWithText("Diwali at home").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(2L * STORY_PAGE_MILLIS + 1_000)
        assertEquals(1, calls.nextRing)
    }

    @Test
    fun `with animations off nothing advances by itself`() {
        val calls = Calls()
        compose.mainClock.autoAdvance = false
        show(state(), calls, motion = false)
        compose.mainClock.advanceTimeBy(5L * STORY_PAGE_MILLIS)
        compose.onNodeWithText("Wedding in Pune").assertIsDisplayed()
        assertEquals(0, calls.nextRing)
    }

    @Test
    fun `page actions act on the memory on screen`() {
        val calls = Calls()
        show(state().copy(favorites = setOf(2L)), calls)
        compose.onNodeWithContentDescription("Add to favourites").performClick()
        compose.onNodeWithContentDescription("Open").performClick()
        compose.onNodeWithContentDescription("Make a reel from this story").performClick()
        tapRight()
        // Memory 2 is already a favourite: the heart offers to remove it.
        compose.onNodeWithContentDescription("Remove from favourites").performClick()
        assertEquals(listOf(1L, 2L), calls.favorites)
        assertEquals(listOf(1L), calls.opened)
        assertEquals(1L, (calls.reels.single() as StoryPage.Moment).card.memory.id)
    }

    @Test
    fun `sound and close have buttons, and the speaker shows the state it is in`() {
        val calls = Calls()
        show(state(), calls, soundOn = false)
        compose.onNodeWithContentDescription("Turn music on").performClick()
        compose.onNodeWithContentDescription("Close story").performClick()
        assertEquals(1, calls.sound)
        assertEquals(1, calls.closed)
        assertTrue(compose.onAllNodesWithText("Today").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `add what you remember saves a note to the memory or goes to voice recording`() {
        val calls = Calls()
        show(state(), calls)
        compose.onNodeWithContentDescription("Add what you remember").performClick()
        compose.onNodeWithText("A line, a name, a smell… anything you remember").performTextInput("Aaji made puran poli")
        compose.onNodeWithText("Save to this memory").performClick()
        assertEquals(listOf(1L to "Aaji made puran poli"), calls.notes)
        compose.onNodeWithContentDescription("Add what you remember").performClick()
        compose.onNodeWithText("Record a voice note instead").performClick()
        assertEquals(listOf(1L), calls.voice)
    }

    @Test
    fun `then and now shows both years and how far apart they are`() {
        val calls = Calls()
        val pair = ThenNowPair(DatedPhoto(1, 10, LocalDate.of(2012, 5, 1).toEpochDay()), DatedPhoto(2, 11, LocalDate.of(2026, 3, 2).toEpochDay()), yearsApart = 13)
        compose.setContent {
            YaadeinTheme {
                com.yaadein.core.ui.media.ProvidePreviewImages(painter = { androidx.compose.ui.graphics.painter.ColorPainter(androidx.compose.ui.graphics.Color.Gray) }) {
                    StoryPlayerScreen(state = state(pages = listOf(StoryPage.ThenNow(pair, person = aaji)), ringIndex = 1), actions = calls.actions, motion = false)
                }
            }
        }
        compose.onNodeWithText("Then · 2012").assertIsDisplayed()
        compose.onNodeWithText("Now · 2026").assertIsDisplayed()
        compose.onNodeWithText("Aaji, then and now").assertIsDisplayed()
        compose.onNodeWithText("13 years apart").assertIsDisplayed()
        // The actions go to the older photo's memory.
        compose.onNodeWithContentDescription("Open").performClick()
        assertEquals(listOf(10L), calls.opened)
    }

    @Test
    fun `an empty story says so and can still be left`() {
        val calls = Calls()
        show(state(pages = emptyList()), calls)
        compose.onNodeWithText("Nothing to show in this story yet").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close story").performClick()
        assertEquals(1, calls.closed)
    }
}
