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
