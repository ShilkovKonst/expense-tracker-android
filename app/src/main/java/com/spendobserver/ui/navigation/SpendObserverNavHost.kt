package com.spendobserver.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.DefaultNavTransitions
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.spendobserver.data.repository.BackupRepository
import kotlinx.coroutines.flow.first
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.data.settings.ThemeMode
import com.spendobserver.ui.common.BackSwipeArea
import com.spendobserver.ui.help.FaqScreen
import com.spendobserver.ui.help.HelpScreen
import com.spendobserver.ui.record.RecordFormScreen
import com.spendobserver.ui.stats.StatsScreen
import com.spendobserver.ui.stats.statsViewModel
import com.spendobserver.ui.tracker.TrackerDetailScreen
import com.spendobserver.ui.tracker.TrackerPane
import com.spendobserver.ui.tracker.TrackerPanes
import com.spendobserver.ui.tracker.rememberTrackerPanesState
import com.spendobserver.ui.trackers.TrackerEditScreen
import com.spendobserver.ui.trackers.TrackerListScreen

@Composable
fun SpendObserverNavHost(
    navController: NavHostController,
    trackerRepository: TrackerRepository,
    recordRepository: RecordRepository,
    tagRepository: TagRepository,
    backupRepository: BackupRepository,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Route.TrackerList,
        // The theme's own background behind every screen, never the window's, even mid-slide.
        modifier = modifier.background(MaterialTheme.colorScheme.background),
        // A back swipe plays these, not the destinations' pop transitions: the same slides
        // as the back button between the start screen and a tracker, the defaults elsewhere.
        predictivePopEnterTransition = { edge ->
            slideInToTheStartScreen(SwipeSlideSpec) ?: DefaultNavTransitions.predictivePopEnterTransition(this, edge)
        },
        predictivePopExitTransition = { edge ->
            slideOutToTheStartScreen(SwipeSlideSpec) ?: DefaultNavTransitions.predictivePopExitTransition(this, edge)
        },
    ) {
        // A tracker and its full-screen statistics sit to the start screen's right: they slide
        // in from there and back out, and a back swipe drags the start screen in after them.
        composable<Route.TrackerList>(
            exitTransition = { if (targetState.isTrackerScreen()) slideOut(SlideDirection.Start) else null },
            popEnterTransition = { slideInToTheStartScreen() },
        ) { backStackEntry ->
            val navigation = ScreenNavigation(navController, backStackEntry)
            WithPaneLayout { twoPane ->
                TrackerListScreen(
                    trackerRepository = trackerRepository,
                    recordRepository = recordRepository,
                    backupRepository = backupRepository,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onTrackerClick = { tracker -> navigation.open(Route.TrackerDetail(tracker.id)) },
                    onEditTrackerClick = { tracker -> navigation.open(Route.TrackerEdit(tracker.id)) },
                    // In two columns the tracker screen already shows its statistics.
                    onStatsClick = { tracker ->
                        navigation.open(if (twoPane) Route.TrackerDetail(tracker.id) else Route.TrackerStats(tracker.id))
                    },
                    onTrackerCreated = { trackerId -> navigation.open(Route.TrackerDetail(trackerId)) },
                    onHelpClick = { navigation.open(Route.Help) },
                    onFaqClick = { navigation.open(Route.Faq) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        composable<Route.TrackerDetail>(
            enterTransition = { if (initialState.isRoute<Route.TrackerList>()) slideIn(SlideDirection.Start) else null },
            popExitTransition = { slideOutToTheStartScreen() },
        ) { backStackEntry ->
            val navigation = ScreenNavigation(navController, backStackEntry)
            val route: Route.TrackerDetail = backStackEntry.toRoute()
            val recordChange by remember(backStackEntry) { backStackEntry.savedStateHandle.recordChanges() }
                .collectAsStateWithLifecycle(initialValue = null)
            val panes = rememberTrackerPanesState()
            // On a phone the statistics wait off-screen; built once the slide in has finished,
            // so opening a tracker doesn't also build its chart in the slide's first frames.
            var statsBuilt by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                snapshotFlow { transition.isRunning }.first { running -> !running }
                statsBuilt = true
            }
            WithPaneLayout { twoPane ->
                TrackerPanes(
                    state = panes,
                    twoPane = twoPane,
                    statsReady = twoPane || statsBuilt,
                    modifier = Modifier.fillMaxSize(),
                    records = {
                        TrackerDetailScreen(
                            trackerId = route.trackerId,
                            trackerRepository = trackerRepository,
                            recordRepository = recordRepository,
                            backupRepository = backupRepository,
                            onBackClick = { navigation.back() },
                            onAddRecordClick = { navigation.open(Route.RecordForm(trackerId = route.trackerId)) },
                            onRecordClick = { record ->
                                navigation.open(Route.RecordForm(trackerId = route.trackerId, recordId = record.id))
                            },
                            onEditTrackerClick = { navigation.open(Route.TrackerEdit(route.trackerId)) },
                            // In two columns the statistics are already on screen.
                            onStatsClick = if (twoPane) null else ({ panes.show(TrackerPane.Stats) }),
                            // Back to the start screen; the deleted tracker's entry must not stay on the stack.
                            onTrackerDeleted = { navigation.ifCurrent { navController.popBackStack(Route.TrackerList, inclusive = false) } },
                            recordChange = recordChange,
                            onRecordChangeConsumed = { backStackEntry.savedStateHandle.clearRecordChange() },
                            modifier = Modifier
                                .fillMaxSize()
                                // The window's far edge belongs to the stats column, which pads for it itself.
                                .then(if (twoPane) Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.End)) else Modifier),
                        )
                    },
                    stats = {
                        // Its ViewModel belongs to this back-stack entry, so through a fold,
                        // unfold or rotation the stats keep their period, type and tag.
                        StatsScreen(
                            trackerId = route.trackerId,
                            recordRepository = recordRepository,
                            tagRepository = tagRepository,
                            onBackClick = if (twoPane) null else ({ panes.show(TrackerPane.Records) }),
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (twoPane) Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Start)) else Modifier),
                        )
                    },
                )
            }
        }
        composable<Route.RecordForm> { backStackEntry ->
            val navigation = ScreenNavigation(navController, backStackEntry)
            val route: Route.RecordForm = backStackEntry.toRoute()
            RecordFormScreen(
                trackerId = route.trackerId,
                recordId = route.recordId,
                recordRepository = recordRepository,
                tagRepository = tagRepository,
                onSaved = { change ->
                    // Hand the result to the tracker-detail entry underneath before
                    // popping back to it; plain back navigation sets nothing.
                    navigation.ifCurrent {
                        navController.previousBackStackEntry?.savedStateHandle?.setRecordChange(change)
                        navController.popBackStack()
                    }
                },
                onBackClick = { navigation.back() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable<Route.TrackerEdit> { backStackEntry ->
            val navigation = ScreenNavigation(navController, backStackEntry)
            val route: Route.TrackerEdit = backStackEntry.toRoute()
            TrackerEditScreen(
                trackerId = route.trackerId,
                trackerRepository = trackerRepository,
                tagRepository = tagRepository,
                onBackClick = { navigation.back() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Statistics opened from the start screen's tracker menu; from a tracker,
        // they're its screen's second pane instead.
        composable<Route.TrackerStats>(
            enterTransition = { if (initialState.isRoute<Route.TrackerList>()) slideIn(SlideDirection.Start) else null },
            popExitTransition = { slideOutToTheStartScreen() },
        ) { backStackEntry ->
            val navigation = ScreenNavigation(navController, backStackEntry)
            val route: Route.TrackerStats = backStackEntry.toRoute()
            WithPaneLayout { twoPane ->
                // Unfolded: the tracker screen shows the statistics in its second column, so
                // go there in their place, handing the selection over to its ViewModel.
                if (twoPane) {
                    LaunchedEffect(Unit) {
                        navigation.ifCurrent {
                            val selection = statsViewModel(backStackEntry, route.trackerId, recordRepository, tagRepository).selection
                            navController.navigate(Route.TrackerDetail(route.trackerId)) {
                                popUpTo<Route.TrackerStats> { inclusive = true }
                            }
                            navController.currentBackStackEntry?.let { entry ->
                                statsViewModel(entry, route.trackerId, recordRepository, tagRepository).restore(selection)
                            }
                        }
                    }
                }
                BackSwipeArea(modifier = Modifier.fillMaxSize()) {
                    StatsScreen(
                        trackerId = route.trackerId,
                        recordRepository = recordRepository,
                        tagRepository = tagRepository,
                        onBackClick = { navigation.back() },
                        modifier = Modifier.fillMaxSize(),
                        viewModelStoreOwner = backStackEntry,
                    )
                }
            }
        }
        composable<Route.Help> { backStackEntry ->
            val navigation = ScreenNavigation(navController, backStackEntry)
            HelpScreen(
                onBackClick = { navigation.back() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable<Route.Faq> { backStackEntry ->
            val navigation = ScreenNavigation(navController, backStackEntry)
            FaqScreen(
                onBackClick = { navigation.back() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * A tap's or back button's slide. It holds still for the first frames, which build the
 * incoming screen and can take 40-50 ms, so the motion doesn't start with a jump;
 * then it moves off fast and eases into place.
 */
private val TapSlideSpec = tween<IntOffset>(durationMillis = 300, delayMillis = 50, easing = FastOutSlowInEasing)

/** A back swipe's slide: linear and undelayed, so the screens stay exactly under the finger as it scrubs. */
private val SwipeSlideSpec = tween<IntOffset>(durationMillis = 300, easing = LinearEasing)

/** The incoming screen slides in moving [towards] that side: Start comes in from the end edge. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideIn(
    towards: SlideDirection,
    spec: FiniteAnimationSpec<IntOffset> = TapSlideSpec,
): EnterTransition = slideIntoContainer(towards, spec)

/** The outgoing screen slides off moving [towards] that side. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideOut(
    towards: SlideDirection,
    spec: FiniteAnimationSpec<IntOffset> = TapSlideSpec,
): ExitTransition = slideOutOfContainer(towards, spec)

private inline fun <reified T : Any> NavBackStackEntry.isRoute() = destination.hasRoute<T>()

private fun NavBackStackEntry.isTrackerScreen() = isRoute<Route.TrackerDetail>() || isRoute<Route.TrackerStats>()

/** Back from a tracker or its statistics: the start screen slides in from the left... */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideInToTheStartScreen(
    spec: FiniteAnimationSpec<IntOffset> = TapSlideSpec,
): EnterTransition? =
    if (initialState.isTrackerScreen() && targetState.isRoute<Route.TrackerList>()) slideIn(SlideDirection.End, spec) else null

/** ...as the tracker slides off to the right. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.slideOutToTheStartScreen(
    spec: FiniteAnimationSpec<IntOffset> = TapSlideSpec,
): ExitTransition? =
    if (initialState.isTrackerScreen() && targetState.isRoute<Route.TrackerList>()) slideOut(SlideDirection.End, spec) else null

/** From this width on (tablets, unfolded book-style foldables) a tracker shows its statistics beside the records. */
private val TwoPaneMinWidth = 600.dp

/**
 * Tells [content] whether the window is wide enough for two columns. Measured
 * here, inside the destination, rather than around the NavHost: a builder lambda
 * that read it would rebuild the navigation graph on every fold and unfold.
 */
@Composable
private fun WithPaneLayout(content: @Composable (twoPane: Boolean) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        content(maxWidth >= TwoPaneMinWidth)
    }
}

/**
 * Navigation on behalf of one screen, ignored once that screen isn't the
 * current one. A screen keeps taking taps during its closing animation, so a
 * double tap on "back" would otherwise pop the screen beneath it too, down to
 * an empty stack and a blank app; on a link it would open the target twice.
 */
private class ScreenNavigation(
    private val navController: NavHostController,
    private val entry: NavBackStackEntry,
) {
    fun ifCurrent(action: () -> Unit) {
        if (navController.currentBackStackEntry?.id == entry.id) action()
    }

    fun back() = ifCurrent { navController.popBackStack() }

    fun <T : Any> open(route: T) = ifCurrent { navController.navigate(route) }
}
