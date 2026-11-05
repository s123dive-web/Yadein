package com.yaadein.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.yaadein.app.navigation.AppNavHost
import com.yaadein.app.navigation.Route
import com.yaadein.core.ui.theme.YaadeinTheme
import com.yaadein.feature.lock.BiometricLauncher
import com.yaadein.feature.lock.LockRoute
import com.yaadein.domain.settings.UserSettings

@Composable
fun YaadeinApp(
    settings: UserSettings,
    locked: Boolean,
    biometricLauncher: BiometricLauncher?,
    biometricAvailable: Boolean,
) {
    YaadeinTheme(theme = settings.theme, darkMode = settings.darkMode, easyMode = settings.easyMode) {
        val navController = rememberNavController()
        // Start destination is decided once per process from persisted state.
        val start: Route = remember {
            when {
                !settings.onboardingDone -> Route.Onboarding
                !settings.pinConfigured -> Route.LockSetup
                else -> Route.Home
            }
        }
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                AppNavHost(navController = navController, start = start, settings = settings, biometricAvailable = biometricAvailable)
                // Lock overlay sits above everything once a PIN exists.
                AnimatedVisibility(visible = locked && settings.pinConfigured, enter = fadeIn(), exit = fadeOut()) {
                    LockRoute(biometricLauncher = if (settings.biometricEnabled) biometricLauncher else null)
                }
            }
        }
    }
}
