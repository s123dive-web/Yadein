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
import com.yaadein.feature.settings.StorageRoute
import com.yaadein.feature.capture.EventCaptureRoute
import com.yaadein.feature.capture.PastWizardRoute
import com.yaadein.feature.capture.PhotoFirstRoute
import com.yaadein.feature.capture.QuickCaptureRoute
import com.yaadein.feature.capture.TextOnlyRoute
import com.yaadein.feature.memory.MediaViewerRoute
import com.yaadein.feature.people.GroupEditRoute
import com.yaadein.feature.people.RelationshipsRoute
import com.yaadein.feature.people.TogetherRoute
import com.yaadein.feature.relive.YearlySummaryRoute
import com.yaadein.feature.settings.NotificationsRoute
import com.yaadein.feature.settings.SecurityRoute
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
                onDone = {
                    if (settings.pinConfigured && navController.previousBackStackEntry != null) navController.popBackStack()
                    else navController.navigate(Route.Home) { popUpTo(0) { inclusive = true } }
                },
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
        composable<Route.MediaViewer> {
            MediaViewerRoute(onBack = { navController.popBackStack() })
        }
        composable<Route.GroupEdit> {
            GroupEditRoute(onBack = { navController.popBackStack() })
        }
        composable<Route.EventCapture> { entry ->
            val editing = entry.toRoute<Route.EventCapture>().memoryId != 0L
            EventCaptureRoute(
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    if (editing) navController.popBackStack() else navController.navigate(Route.MemoryDetail(id)) { popUpTo<Route.Home>() }
                },
                onAddPerson = { navController.navigate(Route.PersonEdit()) },
            )
        }
        composable<Route.TextOnly> {
            TextOnlyRoute(
                onBack = { navController.popBackStack() },
                onSaved = { id -> navController.navigate(Route.MemoryDetail(id)) { popUpTo<Route.Home>() } },
                onAddPerson = { navController.navigate(Route.PersonEdit()) },
            )
        }
        composable<Route.QuickCapture> {
            QuickCaptureRoute(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
                onAddPerson = { navController.navigate(Route.PersonEdit()) },
            )
        }
        composable<Route.PhotoFirst> {
            PhotoFirstRoute(
                onBack = { navController.popBackStack() },
                onDone = { first -> if (first != null) navController.navigate(Route.MemoryDetail(first)) { popUpTo<Route.Home>() } else navController.popBackStack() },
                onAddPerson = { navController.navigate(Route.PersonEdit()) },
            )
        }
        composable<Route.PastWizard> {
            PastWizardRoute(onBack = { navController.popBackStack() }, onAddPerson = { navController.navigate(Route.PersonEdit()) })
        }
        composable<Route.Together> {
            TogetherRoute(onBack = { navController.popBackStack() }, onOpenMemory = { navController.navigate(Route.MemoryDetail(it)) })
        }
        composable<Route.RelationshipsMap> {
            RelationshipsRoute(onBack = { navController.popBackStack() }, onOpenPerson = { navController.navigate(Route.PersonDetail(it)) })
        }
        composable<Route.YearlySummary> {
            YearlySummaryRoute(
                onBack = { navController.popBackStack() },
                onOpenMemory = { navController.navigate(Route.MemoryDetail(it)) },
                onOpenPerson = { navController.navigate(Route.PersonDetail(it)) },
                onRecap = { personId, year -> navController.navigate(Route.RecapSetup(personId = personId, year = year)) },
            )
        }
        composable<Route.Settings> { entry ->
            val page = SettingsPage.valueOf(entry.toRoute<Route.Settings>().page)
            when (page) {
                SettingsPage.STORAGE -> StorageRoute(onBack = { navController.popBackStack() })
                SettingsPage.NOTIFICATIONS -> NotificationsRoute(onBack = { navController.popBackStack() })
                SettingsPage.SECURITY -> SecurityRoute(biometricAvailable = biometricAvailable, onBack = { navController.popBackStack() }, onChangePin = { navController.navigate(Route.LockSetup) })
                SettingsPage.ABOUT -> AboutRoute(onBack = { navController.popBackStack() }, versionName = BuildConfig.VERSION_NAME, debugBuild = BuildConfig.DEBUG)
                else -> AppearanceRoute(onBack = { navController.popBackStack() })
            }
        }
    }
}
