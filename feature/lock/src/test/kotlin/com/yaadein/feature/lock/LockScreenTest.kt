package com.yaadein.feature.lock

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yaadein.core.ui.theme.YaadeinTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LockScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `typing digits calls onDigit and shows dots`() {
        val typed = StringBuilder()
        compose.setContent {
            YaadeinTheme {
                LockScreen(state = LockUiState(pin = "12"), onDigit = { typed.append(it) }, onBackspace = {}, onBiometric = null)
            }
        }
        compose.onNodeWithText("Welcome back").assertIsDisplayed()
        compose.onNodeWithText("7").performClick()
        assertEquals("7", typed.toString())
        compose.onNodeWithContentDescription("2 of 6 digits entered").assertIsDisplayed()
    }

    @Test
    fun `wrong pin message and lockout countdown are shown`() {
        compose.setContent {
            YaadeinTheme {
                LockScreen(state = LockUiState(error = LockError.WrongPin(2)), onDigit = {}, onBackspace = {}, onBiometric = null)
            }
        }
        compose.onNodeWithText("That PIN isn't right. Try again.").assertIsDisplayed()
    }

    @Test
    fun `lockout disables keys and shows wait`() {
        var pressed = false
        compose.setContent {
            YaadeinTheme {
                LockScreen(state = LockUiState(lockedForMs = 30_000), onDigit = { pressed = true }, onBackspace = {}, onBiometric = null)
            }
        }
        compose.onNodeWithText("Too many tries. Wait 30 seconds.").assertIsDisplayed()
        compose.onNodeWithText("5").performClick()
        assertEquals(false, pressed)
    }

    @Test
    fun `biometric button appears only when available`() {
        var launched = false
        compose.setContent {
            YaadeinTheme {
                LockScreen(state = LockUiState(biometricEnabled = true), onDigit = {}, onBackspace = {}, onBiometric = { launched = true })
            }
        }
        compose.onNodeWithContentDescription("Use fingerprint or face").performClick()
        assertEquals(true, launched)
    }
}
