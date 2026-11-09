package com.yaadein.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yaadein.core.ui.theme.Festive
import com.yaadein.core.ui.theme.Midnight
import com.yaadein.core.ui.theme.WarmAlbum
import com.yaadein.core.ui.theme.YaadeinTheme
import com.yaadein.domain.settings.AppTheme
import com.yaadein.domain.settings.DarkMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemeSwitchTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `switching theme and dark mode updates the colour scheme instantly`() {
        var theme by mutableStateOf(AppTheme.WARM_ALBUM)
        var dark by mutableStateOf(DarkMode.LIGHT)
        var observed: Color = Color.Unspecified
        compose.setContent {
            YaadeinTheme(theme = theme, darkMode = dark) {
                observed = MaterialTheme.colorScheme.primary
                Text("switch")
            }
        }
        compose.waitForIdle()
        assertEquals(WarmAlbum.light.primary, observed)
        theme = AppTheme.MIDNIGHT
        compose.waitForIdle()
        assertEquals(Midnight.light.primary, observed)
        dark = DarkMode.DARK
        compose.waitForIdle()
        assertEquals(Midnight.dark.primary, observed)
        theme = AppTheme.FESTIVE
        compose.waitForIdle()
        assertEquals(Festive.dark.primary, observed)
        compose.onNodeWithText("switch").performClick()
    }
}
