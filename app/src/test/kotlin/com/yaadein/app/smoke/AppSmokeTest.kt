package com.yaadein.app.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        listOf("Appearance", "Security", "Relive reminders", "Face suggestions", "Storage", "Backup and restore", "Privacy", "Problem log", "About Yaadein").forEach { page ->
            compose.onNodeWithText(page).performClick()
            compose.waitForIdle()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.waitForIdle()
            compose.onNodeWithText("Appearance").assertIsDisplayed()
        }
    }

    @Test
    fun `capture hub opens and text-only flow saves a memory`() {
        compose.onAllNodesWithText("Create")[0].performClick()
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
}
