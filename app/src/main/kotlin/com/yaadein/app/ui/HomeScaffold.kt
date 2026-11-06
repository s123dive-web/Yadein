package com.yaadein.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewTimeline
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.window.core.layout.WindowSizeClass
import com.yaadein.app.R
import com.yaadein.app.navigation.HomeTab
import com.yaadein.app.navigation.Route
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.settings.UserSettings
import com.yaadein.feature.capture.CaptureActions
import com.yaadein.feature.capture.CaptureHubSheet
import com.yaadein.feature.people.PeopleRoute
import com.yaadein.feature.relive.ReliveRoute
import com.yaadein.feature.settings.SettingsRoute
import com.yaadein.feature.timeline.TimelineRoute

/**
 * Home: bottom navigation on phones, navigation rail on tablets/foldables (expanded width).
 * Easy mode shows only Timeline and the + button.
 */
@Composable
fun HomeScaffold(navController: NavHostController, settings: UserSettings) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.TIMELINE) }
    var hubOpen by rememberSaveable { mutableStateOf(false) }
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val expanded = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
    val easy = settings.easyMode
    val tabs = if (easy) listOf(HomeTab.TIMELINE, HomeTab.SETTINGS) else listOf(HomeTab.TIMELINE, HomeTab.PEOPLE, HomeTab.CREATE, HomeTab.RELIVE, HomeTab.SETTINGS)
    if (easy && tab !in tabs) tab = HomeTab.TIMELINE

    val captureActions = CaptureActions(
        onEvent = { hubOpen = false; navController.navigate(Route.EventCapture()) },
        onPhotos = { hubOpen = false; navController.navigate(Route.PhotoFirst) },
        onTextOnly = { hubOpen = false; navController.navigate(Route.TextOnly) },
        onQuick = { hubOpen = false; navController.navigate(Route.QuickCapture) },
        onPastWizard = { hubOpen = false; navController.navigate(Route.PastWizard) },
    )

    val content: @Composable (PaddingValues) -> Unit = { padding ->
        Column(Modifier.fillMaxSize()) {
            if (tab == HomeTab.TIMELINE) CrashNoticeBanner()
            when (tab) {
            HomeTab.TIMELINE, HomeTab.CREATE -> TimelineRoute(
                onOpenMemory = { navController.navigate(Route.MemoryDetail(it)) },
                onCreate = { hubOpen = true },
                contentPadding = padding,
                twoPane = expanded && !easy,
            )
            HomeTab.PEOPLE -> PeopleRoute(
                onOpenPerson = { navController.navigate(Route.PersonDetail(it)) },
                onAddPerson = { navController.navigate(Route.PersonEdit()) },
                onOpenGroup = { navController.navigate(Route.GroupEdit(it)) },
                onAddGroup = { navController.navigate(Route.GroupEdit()) },
                onOpenRelationships = { navController.navigate(Route.RelationshipsMap) },
                contentPadding = padding,
            )
            HomeTab.RELIVE -> ReliveRoute(
                onOpenMemory = { navController.navigate(Route.MemoryDetail(it)) },
                onOpenPerson = { navController.navigate(Route.PersonDetail(it)) },
                onRecap = { navController.navigate(Route.RecapSetup()) },
                onYear = { year -> navController.navigate(Route.YearlySummary(0L, year)) },
                contentPadding = padding,
            )
            HomeTab.SETTINGS -> SettingsRoute(
                onOpen = { page -> navController.navigate(Route.Settings(page.name)) },
                contentPadding = padding,
                showUpdate = com.yaadein.app.BuildConfig.DEBUG,
            )
            }
        }
    }

    if (expanded) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail(
                header = {
                    Spacer(Modifier.height(Yaadein.spacing.sm))
                    FloatingActionButton(onClick = { hubOpen = true }, containerColor = MaterialTheme.colorScheme.primary) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.nav_create))
                    }
                },
            ) {
                Spacer(Modifier.height(Yaadein.spacing.lg))
                tabs.filter { it != HomeTab.CREATE }.forEach { t ->
                    NavigationRailItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(t.icon(), contentDescription = null) },
                        label = { Text(t.label()) },
                    )
                }
            }
            VerticalDivider()
            Box(Modifier.fillMaxSize()) { content(PaddingValues()) }
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    tabs.forEach { t ->
                        if (t == HomeTab.CREATE) {
                            NavigationBarItem(
                                selected = false,
                                onClick = { hubOpen = true },
                                icon = {
                                    Box(
                                        modifier = Modifier
                                            .size(Yaadein.sizes.touchTarget)
                                            .padding(Yaadein.spacing.xxs),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        FloatingActionButton(
                                            onClick = { hubOpen = true },
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(Yaadein.sizes.touchTarget),
                                        ) { Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.nav_create)) }
                                    }
                                },
                                label = { Text(stringResource(R.string.nav_create)) },
                            )
                        } else {
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(t.icon(), contentDescription = null) },
                                label = { Text(t.label()) },
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (easy) {
                    LargeFloatingActionButton(onClick = { hubOpen = true }, containerColor = MaterialTheme.colorScheme.primary) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.nav_create), modifier = Modifier.size(Yaadein.sizes.iconLg))
                    }
                }
            },
        ) { padding -> Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) { content(PaddingValues(bottom = padding.calculateBottomPadding())) } }
    }

    if (hubOpen) {
        CaptureHubSheet(actions = captureActions, onDismiss = { hubOpen = false })
    }
}

private fun HomeTab.icon(): ImageVector = when (this) {
    HomeTab.TIMELINE -> Icons.Rounded.ViewTimeline
    HomeTab.PEOPLE -> Icons.Rounded.People
    HomeTab.CREATE -> Icons.Rounded.Add
    HomeTab.RELIVE -> Icons.Rounded.AutoAwesome
    HomeTab.SETTINGS -> Icons.Rounded.Settings
}

@Composable
private fun HomeTab.label(): String = when (this) {
    HomeTab.TIMELINE -> stringResource(R.string.nav_timeline)
    HomeTab.PEOPLE -> stringResource(R.string.nav_people)
    HomeTab.CREATE -> stringResource(R.string.nav_create)
    HomeTab.RELIVE -> stringResource(R.string.nav_relive)
    HomeTab.SETTINGS -> stringResource(R.string.nav_settings)
}
