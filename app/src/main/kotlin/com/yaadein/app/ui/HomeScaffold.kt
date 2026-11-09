package com.yaadein.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.window.core.layout.WindowSizeClass
import com.yaadein.app.R
import com.yaadein.app.navigation.HomeTab
import com.yaadein.app.navigation.Route
import com.yaadein.core.ui.theme.Yaadein
import com.yaadein.domain.settings.UserSettings
import com.yaadein.feature.capture.CaptureActions
import com.yaadein.feature.capture.CaptureHubSheet
import com.yaadein.feature.capture.QuickSaveLaunchViewModel
import com.yaadein.feature.people.PeopleRoute
import com.yaadein.feature.relive.ReliveRoute
import com.yaadein.feature.settings.SettingsPage
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
    var placeFilter by rememberSaveable { mutableStateOf<String?>(null) }
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val expanded = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
    val easy = settings.easyMode
    val tabs = if (easy) listOf(HomeTab.TIMELINE, HomeTab.SETTINGS) else listOf(HomeTab.TIMELINE, HomeTab.PEOPLE, HomeTab.CREATE, HomeTab.RELIVE, HomeTab.SETTINGS)
    if (easy && tab !in tabs) tab = HomeTab.TIMELINE

    // Tap + → the system Photo Picker → Quick Save (3 taps to a saved memory). Long-press + → the hub with every other way.
    val quickSave: QuickSaveLaunchViewModel = hiltViewModel()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        val hasPhotos = quickSave.onPicked(uris)
        navController.navigate(Route.QuickSave(wordsOnly = !hasPhotos))
    }
    val openQuickSave: () -> Unit = {
        quickSave.beforePicker()
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }
    val openHub: () -> Unit = { hubOpen = true }

    val captureActions = CaptureActions(
        onEvent = { hubOpen = false; navController.navigate(Route.EventCapture()) },
        onPhotos = { hubOpen = false; navController.navigate(Route.PhotoFirst) },
        onTextOnly = { hubOpen = false; navController.navigate(Route.TextOnly) },
        onQuick = { hubOpen = false; navController.navigate(Route.QuickCapture) },
        onPastWizard = { hubOpen = false; navController.navigate(Route.PastWizard) },
        onVoice = { hubOpen = false; navController.navigate(Route.VoiceCapture()) },
        onImport = { hubOpen = false; navController.navigate(Route.ImportExport) },
        onBulk = { hubOpen = false; navController.navigate(Route.BulkUpload) },
    )

    val content: @Composable (PaddingValues) -> Unit = { padding ->
        Column(Modifier.fillMaxSize()) {
            if (tab == HomeTab.TIMELINE) {
                CrashNoticeBanner()
                BackupNudgeBanner(onSetUp = { navController.navigate(Route.Settings(SettingsPage.BACKUP.name)) })
            }
            when (tab) {
            HomeTab.TIMELINE, HomeTab.CREATE -> TimelineRoute(
                onOpenMemory = { navController.navigate(Route.MemoryDetail(it)) },
                onCreate = { hubOpen = true },
                onAbout = { navController.navigate(Route.Settings(SettingsPage.APP_INFO.name)) },
                contentPadding = padding,
                twoPane = expanded && !easy,
                placeFilter = placeFilter,
                onPlaceFilterConsumed = { placeFilter = null },
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
                onReels = { navController.navigate(Route.Reels) },
                onPlayReel = { navController.navigate(Route.ReelPlayer(it)) },
                onRecordPrompt = { text, personId -> navController.navigate(Route.VoiceCapture(prompt = text, personId = personId)) },
                onPlace = { place -> placeFilter = place; tab = HomeTab.TIMELINE },
                onYear = { year -> navController.navigate(Route.YearlySummary(0L, year)) },
                onBirthdayReel = { personId -> navController.navigate(Route.RecapSetup(personId = personId)) },
                contentPadding = padding,
            )
            HomeTab.SETTINGS -> SettingsRoute(
                onOpen = { page -> navController.navigate(Route.Settings(page.name)) },
                contentPadding = padding,
                showUpdate = com.yaadein.app.BuildConfig.SIDELOAD_UPDATES,
            )
            }
        }
    }

    if (expanded) {
        Row(Modifier.fillMaxSize()) {
            NavigationRail(
                header = {
                    Spacer(Modifier.height(Yaadein.spacing.sm))
                    CreateButton(onClick = openQuickSave, onLongClick = openHub, size = Yaadein.sizes.fabSize - Yaadein.spacing.sm)
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
                                onClick = openQuickSave,
                                icon = {
                                    Box(
                                        modifier = Modifier
                                            .size(Yaadein.sizes.touchTarget)
                                            .padding(Yaadein.spacing.xxs),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CreateButton(onClick = openQuickSave, onLongClick = openHub, size = Yaadein.sizes.touchTarget)
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
                    CreateButton(onClick = openQuickSave, onLongClick = openHub, size = Yaadein.sizes.fabSize + Yaadein.spacing.xxl, iconSize = Yaadein.sizes.iconLg)
                }
            },
        ) { padding -> Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) { content(PaddingValues(bottom = padding.calculateBottomPadding())) } }
    }

    if (hubOpen) {
        CaptureHubSheet(actions = captureActions, onDismiss = { hubOpen = false })
    }
}

/**
 * The one + button. Tap: Quick Save (Photo Picker first). Long-press, or the TalkBack action "More ways to add a
 * memory": the hub with every other way in. Always at least 48 dp.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CreateButton(onClick: () -> Unit, onLongClick: () -> Unit, size: Dp, iconSize: Dp = Yaadein.sizes.icon) {
    val create = stringResource(R.string.nav_create)
    val moreWays = stringResource(R.string.nav_create_more_ways)
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = Yaadein.elevation.raised,
        modifier = Modifier
            .size(size.coerceAtLeast(Yaadein.sizes.touchTarget))
            .semantics {
                contentDescription = create
                customActions = listOf(CustomAccessibilityAction(moreWays) { onLongClick(); true })
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(iconSize))
        }
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
