package com.yaadein.app.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.yaadein.app.ui.HomeScaffold
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.settings.UserSettings
import com.yaadein.feature.lock.LockSetupRoute
import com.yaadein.feature.memory.MemoryDetailRoute
import com.yaadein.feature.onboarding.OnboardingRoute
import com.yaadein.feature.people.PersonDetailRoute
import com.yaadein.feature.people.PersonEditRoute
import com.yaadein.app.BuildConfig
import com.yaadein.feature.settings.AboutRoute
import com.yaadein.feature.settings.AppearanceRoute
import com.yaadein.feature.settings.SettingsPage

@Composable
fun AppNavHost(
    navController: NavHostController,
    start: Route,
    settings: UserSettings,
    biometricAvailable: Boolean,
) {
    val motion = Yaadein.motion
    NavHost(
        navController = navController,
        startDestination = start,
        enterTransition = { fadeIn(tween(motion.normalMs)) + slideInHorizontally(tween(motion.normalMs)) { it / 8 } },
        exitTransition = { fadeOut(tween(motion.fastMs)) },
        popEnterTransition = { fadeIn(tween(motion.normalMs)) },
        popExitTransition = { fadeOut(tween(motion.fastMs)) + slideOutHorizontally(tween(motion.normalMs)) { it / 8 } },
    ) {
        composable<Route.Onboarding> {
            OnboardingRoute(onAddFirstPerson = { navController.navigate(Route.PersonEdit(fromOnboarding = true)) })
        }
        composable<Route.LockSetup> {
            LockSetupRoute(
                biometricAvailable = biometricAvailable,
                onDone = { navController.navigate(Route.Home) { popUpTo(0) { inclusive = true } } },
            )
        }
        composable<Route.Home> {
            HomeScaffold(navController = navController, settings = settings)
        }
        composable<Route.MemoryDetail> {
            MemoryDetailRoute(
                onBack = { navController.popBackStack() },
                onOpenMedia = { memoryId, index -> navController.navigate(Route.MediaViewer(memoryId, index)) },
                onOpenPerson = { navController.navigate(Route.PersonDetail(it)) },
                onEdit = { navController.navigate(Route.EventCapture(it)) },
                onAddMedia = { navController.navigate(Route.EventCapture(it)) },
                onRecap = { navController.navigate(Route.RecapSetup(memoryId = it)) },
            )
        }
        composable<Route.PersonDetail> {
            PersonDetailRoute(
                onBack = { navController.popBackStack() },
                onOpenMemory = { navController.navigate(Route.MemoryDetail(it)) },
                onEdit = { navController.navigate(Route.PersonEdit(personId = it)) },
                onTogether = { navController.navigate(Route.Together(it)) },
                onYearSummary = { personId, year -> navController.navigate(Route.YearlySummary(personId, year)) },
                onRecap = { navController.navigate(Route.RecapSetup(personId = it)) },
            )
        }
        composable<Route.PersonEdit> { entry ->
            val route = entry.toRoute<Route.PersonEdit>()
            PersonEditRoute(
                onBack = { navController.popBackStack() },
                onSaved = {
                    if (route.fromOnboarding) {
                        navController.navigate(Route.LockSetup) { popUpTo(0) { inclusive = true } }
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }
        composable<Route.Settings> { entry ->
            val page = SettingsPage.valueOf(entry.toRoute<Route.Settings>().page)
            when (page) {
                SettingsPage.ABOUT -> AboutRoute(onBack = { navController.popBackStack() }, versionName = BuildConfig.VERSION_NAME, debugBuild = BuildConfig.DEBUG)
                else -> AppearanceRoute(onBack = { navController.popBackStack() })
            }
        }
    }
}
