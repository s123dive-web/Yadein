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
import com.yaadein.feature.relive.ShakeListener
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.yaadein.app.ui.RandomMemoryViewModel
import com.yaadein.domain.settings.UserSettings

@Composable
fun YaadeinApp(
    settings: UserSettings,
    locked: Boolean,
    biometricLauncher: BiometricLauncher?,
    biometricAvailable: Boolean,
    pendingMemoryId: Long? = null,
    onPendingConsumed: () -> Unit = {},
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
        val unlocked = !locked || !settings.pinConfigured
        // Open the memory a notification pointed at, once the vault is unlocked.
        LaunchedEffect(pendingMemoryId, unlocked, start) {
            if (pendingMemoryId != null && unlocked && start == Route.Home) {
                navController.navigate(Route.MemoryDetail(pendingMemoryId))
                onPendingConsumed()
            }
        }
        // Shake for a random memory (only while unlocked and on the home graph).
        val randomViewModel: RandomMemoryViewModel = hiltViewModel()
        ShakeListener(enabled = settings.shakeForMemory && unlocked && start == Route.Home) {
            randomViewModel.pick { id -> navController.navigate(Route.MemoryDetail(id)) }
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
