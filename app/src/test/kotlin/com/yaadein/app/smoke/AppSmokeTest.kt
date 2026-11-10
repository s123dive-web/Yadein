package com.yaadein.app.smoke

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.core.app.ActivityScenario
import com.yaadein.app.MainActivity
import com.yaadein.data.crypto.PinManager
import com.yaadein.data.lock.AppLockState
import com.yaadein.data.seed.DemoSeeder
import com.yaadein.domain.repository.SettingsRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * Launches the real MainActivity (Hilt graph with JVM fakes for Keystore/SQLCipher) and taps
 * through every tab and settings page: the kind of crash a user hits on a phone shows up here.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [35], qualifiers = "w411dp-h891dp-420dpi")
class AppSmokeTest {
    private val hilt = HiltAndroidRule(this)
    private val compose = createEmptyComposeRule()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(hilt).around(compose)

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var lockState: AppLockState
    @Inject lateinit var pinManager: PinManager
    @Inject lateinit var seeder: DemoSeeder
    @Inject lateinit var memories: com.yaadein.domain.repository.MemoryRepository
    @Inject lateinit var persons: com.yaadein.domain.repository.PersonRepository

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        hilt.inject()
        runBlocking {
            settings.update { it.copy(onboardingDone = true, pinConfigured = true, biometricEnabled = false) }
            pinManager.setPin("123456")
        }
        lockState.unlock()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    /** A cold start with other settings (a recreate would restore the old back stack instead of choosing a start screen). */
    private fun relaunchWith(transform: (com.yaadein.domain.settings.UserSettings) -> com.yaadein.domain.settings.UserSettings) {
        scenario.close()
        runBlocking { settings.update(transform) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    /**
     * Waits for a settings write made by the app. The write resumes on the main thread, which Robolectric only runs when
     * the test touches the UI, so each poll idles Compose first (a bare `runBlocking` poll would wait for ever).
     */
    private fun awaitSetting(condition: (com.yaadein.domain.settings.UserSettings) -> Boolean) {
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            condition(runBlocking { settings.current() })
        }
    }

    private fun shown(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `an existing user opens straight to home and is asked nothing new`() {
        runBlocking {
            val aai = persons.upsert(com.yaadein.domain.model.Person(name = "Sunita Deshmukh", nickname = "Aai"))
            memories.create(com.yaadein.domain.model.Memory(type = com.yaadein.domain.model.MemoryType.TEXT_ONLY, title = "Diwali at home", date = com.yaadein.domain.dates.Dates.year(2024)), listOf(aai))
        }
        relaunchWith { it }
        compose.onAllNodesWithText("Timeline")[0].assertIsDisplayed()
        compose.waitUntil(10_000) { shown("Diwali at home") }
        compose.onAllNodesWithText("Pick photos to start").assertCountEquals(0)
        compose.onAllNodesWithText("Protect these memories?").assertCountEquals(0)
        compose.onAllNodesWithText("Your memories have no lock yet").assertCountEquals(0)
        compose.onAllNodesWithText("This is a sample family").assertCountEquals(0)
        val s = runBlocking { settings.current() }
        org.junit.Assert.assertTrue(s.onboardingDone && s.pinConfigured)
    }

    @Test
    fun `a first run shows one welcome screen and skip lands on the empty home without a lock`() {
        relaunchWith { com.yaadein.domain.settings.UserSettings() }
        compose.onNodeWithText("Pick photos to start").assertExists()
        compose.onNodeWithText("See a sample family").assertExists()
        compose.onNode(hasText("Skip") and hasClickAction()).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000) { shown("Timeline") }
        val s = runBlocking { settings.current() }
        org.junit.Assert.assertTrue(s.onboardingDone)
        org.junit.Assert.assertFalse(s.pinConfigured)
        // No person form, no PIN screen, no prompts on an empty vault.
        compose.onAllNodesWithText("Welcome back").assertCountEquals(0)
        compose.onAllNodesWithText("Protect these memories?").assertCountEquals(0)
        compose.onAllNodesWithText("Pick photos to start").assertCountEquals(0)
    }

    @Test
    fun `pick photos on the welcome screen opens the system photo picker`() {
        relaunchWith { com.yaadein.domain.settings.UserSettings() }
        compose.onNode(hasText("Pick photos to start") and hasClickAction()).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        var started: android.content.Intent? = null
        scenario.onActivity { started = org.robolectric.Shadows.shadowOf(it).nextStartedActivityForResult?.intent }
        org.junit.Assert.assertNotNull("the welcome screen's first button must open a picker", started)
    }

    @Test
    fun `the sample family is labelled on home and one tap clears it`() {
        relaunchWith { com.yaadein.domain.settings.UserSettings() }
        compose.onNode(hasText("See a sample family") and hasClickAction()).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(60_000) { shown("This is a sample family") }
        org.junit.Assert.assertTrue(runBlocking { settings.current() }.demoMode)
        org.junit.Assert.assertTrue(runBlocking { memories.count() } > 10)
        compose.onAllNodesWithText("Protect these memories?").assertCountEquals(0)
        compose.onNode(hasText("Clear sample and start mine") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(60_000) { !shown("This is a sample family") && !runBlocking { settings.current() }.demoMode }
        org.junit.Assert.assertEquals(0, runBlocking { memories.count() })
        org.junit.Assert.assertEquals(0, runBlocking { persons.count() })
    }

    private fun oneMemoryWithAPerson() = runBlocking {
        val aai = persons.upsert(com.yaadein.domain.model.Person(name = "Sunita Deshmukh", nickname = "Aai"))
        memories.create(com.yaadein.domain.model.Memory(type = com.yaadein.domain.model.MemoryType.TEXT_ONLY, title = "Diwali at home", date = com.yaadein.domain.dates.Dates.year(2024)), listOf(aai))
    }

    @Test
    fun `without a lock the first memory offers protection over the timeline`() {
        oneMemoryWithAPerson()
        relaunchWith { it.copy(pinConfigured = false) }
        compose.waitUntil(10_000) { shown("Protect these memories?") }
        // The timeline is already there underneath: the sheet does not block the first look, and no lock screen is forced.
        org.junit.Assert.assertTrue(shown("Diwali at home"))
        compose.onAllNodesWithText("Choose a 6-digit PIN").assertCountEquals(0)
        // "Not now" puts it off once: the sheet goes and a card stays on Home.
        compose.onNode(hasText("Not now") and hasClickAction()).performClick()
        awaitSetting { it.lockPromptPostponed }
        compose.waitUntil(10_000) { !shown("Protect these memories?") && shown("Your memories have no lock yet") }
    }

    @Test
    fun `after the lock offer is put off a home card stays and opens lock setup`() {
        oneMemoryWithAPerson()
        relaunchWith { it.copy(pinConfigured = false, lockPromptPostponed = true) }
        compose.waitUntil(10_000) { shown("Your memories have no lock yet") }
        compose.onAllNodesWithText("Protect these memories?").assertCountEquals(0)
        compose.onNode(hasText("Set a PIN") and hasClickAction()).performClick()
        compose.waitUntil(10_000) { shown("Choose a 6-digit PIN") }
    }

    /** The + button inside the bottom bar's Create item (its own node: a long-press target with a TalkBack action). */
    private fun createButton() = compose.onAllNodes(hasContentDescription("Create"), useUnmergedTree = true)[0]

    private fun tab(name: String) {
        compose.onAllNodesWithText(name)[0].performClick()
        compose.waitForIdle()
    }

    @Test
    fun `home tabs render`() {
        compose.onAllNodesWithText("Timeline")[0].assertIsDisplayed()
        tab("People"); compose.onAllNodesWithText("People").fetchSemanticsNodes().isNotEmpty()
        tab("Relive"); compose.onAllNodesWithText("Relive")[0].assertIsDisplayed()
        tab("Settings"); compose.onNodeWithText("Appearance").assertIsDisplayed()
        tab("Timeline")
    }

    @Test
    fun `every settings page opens and closes`() {
        tab("Settings")
        listOf("Appearance", "Security", "Relive reminders", "Face suggestions", "How I use Yaadein", "Storage", "Backup and restore", "Privacy", "Problem log", "Update app", "About Yaadein").forEach { page ->
            try {
                // Click through semantics: on the small Robolectric screen the last rows can sit under the bottom bar.
                compose.onNode(hasText(page) and hasClickAction()).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
                compose.waitForIdle()
                compose.onNodeWithContentDescription("Back").performClick()
                compose.waitForIdle()
                compose.onNodeWithText("Appearance").performScrollTo().assertIsDisplayed()
            } catch (e: AssertionError) {
                throw AssertionError("Settings page \"$page\" did not open and close", e)
            }
        }
    }

    @Test
    fun `how I use yaadein opens from settings with its empty state`() {
        tab("Settings")
        compose.onNode(hasText("How I use Yaadein") and hasClickAction()).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        // The page title (a heading) and, with no captures measured yet, the designed empty state.
        compose.waitUntil(10_000) { compose.onAllNodesWithText("No saves measured yet").fetchSemanticsNodes().isNotEmpty() }
        org.junit.Assert.assertTrue(compose.onAllNodesWithText("How I use Yaadein").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Appearance").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `your reels opens from relive with its two sections`() {
        // Relive shows its actions only once there are memories.
        runBlocking { seeder.seed() }
        scenario.recreate()
        compose.waitForIdle()
        tab("Relive")
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Your reels").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Your reels").performClick()
        compose.waitForIdle()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Made for you").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Made by you").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Relive")[0].assertIsDisplayed()
    }

    @Test
    fun `a story ring on relive opens the full-screen player and close comes back`() {
        runBlocking { seeder.seed() }
        // Animations off, as on a phone with "Remove animations": the story then waits for taps instead of playing on,
        // which also keeps the test clock from running through every story.
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        android.provider.Settings.Global.putFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        scenario.recreate()
        compose.waitForIdle()
        tab("Relive")
        compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Today, new today")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasContentDescription("Today, new today")).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(20_000) { compose.onAllNodes(hasContentDescription("Close story")).fetchSemanticsNodes().isNotEmpty() }
        // Let the story load its pages: closing before anything was shown does not count as watching it.
        compose.waitUntil(20_000) { compose.onAllNodes(hasContentDescription("Story, page", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Close story").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Your reels").fetchSemanticsNodes().isNotEmpty() }
        // Watching it once is remembered for the day.
        awaitSetting { it.storySeen.contains("today") }
    }

    @Test
    fun `plus opens the system photo picker and long-press opens the hub`() {
        // Tap: the Photo Picker (Quick Save's first and only choice before Save).
        createButton().performClick()
        compose.waitForIdle()
        var started: android.content.Intent? = null
        scenario.onActivity { started = org.robolectric.Shadows.shadowOf(it).nextStartedActivityForResult?.intent }
        org.junit.Assert.assertNotNull("tapping + must open a picker", started)
        val action = started!!.action
        org.junit.Assert.assertTrue(
            "unexpected picker intent $action",
            action == android.provider.MediaStore.ACTION_PICK_IMAGES || action == android.content.Intent.ACTION_GET_CONTENT ||
                action == android.content.Intent.ACTION_OPEN_DOCUMENT || action == "com.google.android.gms.provider.action.PICK_IMAGES",
        )
        // Long-press: the hub with every other way in.
        createButton().performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Start from photos").assertIsDisplayed()
        // The half-expanded sheet keeps "More ways" below the fold on the small test screen; it exists in the sheet.
        compose.onNodeWithText("More ways").assertExists()
    }

    @Test
    fun `an empty picker result opens Quick Save in words-only mode`() {
        createButton().performClick()
        compose.waitForIdle()
        scenario.onActivity { activity ->
            val shadow = org.robolectric.Shadows.shadowOf(activity)
            val request = shadow.nextStartedActivityForResult
            org.junit.Assert.assertNotNull(request)
            shadow.receiveResult(request!!.intent, android.app.Activity.RESULT_CANCELED, null)
        }
        compose.waitForIdle()
        compose.onNodeWithText("No photos? Write it instead").assertIsDisplayed()
        compose.onNodeWithText("Add photos").assertIsDisplayed()
    }

    @Test
    fun `capture hub opens and text-only flow saves a memory`() {
        createButton().performTouchInput { longClick() }
        compose.waitForIdle()
        // The hub leads with photos and a line of text; the other ways sit under "More ways".
        compose.onNodeWithText("Start from photos").assertIsDisplayed()
        compose.onNodeWithText("Record a voice memory").assertIsDisplayed()
        compose.onNodeWithText("More ways").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Just words").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("One line is enough").assertIsDisplayed()
    }

    @Test
    fun `demo data renders on the timeline, person and memory detail`() {
        runBlocking { seeder.seed() }
        scenario.recreate()
        compose.waitForIdle()
        compose.onAllNodesWithText("Timeline")[0].assertIsDisplayed()
        tab("People")
        compose.onAllNodesWithText("Aai")[0].performClick()
        compose.waitForIdle()
        // The person story loads from the database; give the coroutine a moment under Robolectric.
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Sunita Deshmukh").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `lock overlay appears after the timeout and pin unlocks it`() {
        lockState.onBackground(0)
        lockState.onForeground(120_000, com.yaadein.domain.settings.LockTimeout.ONE_MINUTE)
        compose.waitForIdle()
        compose.onNodeWithText("Welcome back").assertIsDisplayed()
        "123456".forEach { compose.onNodeWithText(it.toString()).performClick() }
        compose.waitForIdle()
        compose.onAllNodesWithText("Timeline")[0].assertIsDisplayed()
    }

    @Test
    fun `a crash from the previous run shows a banner on home that can be dismissed`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        com.yaadein.data.crash.CrashLog.append(context, "test", Thread.currentThread(), IllegalStateException("boom"))
        scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("Yaadein closed unexpectedly last time").assertIsDisplayed()
        compose.onNodeWithText("Dismiss").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Yaadein closed unexpectedly last time").assertCountEquals(0)
        org.junit.Assert.assertFalse(com.yaadein.data.crash.CrashLog.hasPendingReport(context))
    }
}
