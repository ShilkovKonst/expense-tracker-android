package com.spendobserver.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
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
import com.spendobserver.ui.common.twoLineTopBarHeight
import com.spendobserver.ui.help.FaqScreen
import com.spendobserver.ui.help.HelpScreen
import com.spendobserver.ui.record.RecordFormScreen
import com.spendobserver.ui.stats.StatsScreen
import com.spendobserver.ui.stats.statsViewModel
import com.spendobserver.ui.tracker.NoTrackerPane
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
    // Whether the window is wide enough for two columns, for the transitions to read when they
    // run; the graph builder below only passes the holder on, so a fold doesn't rebuild the graph.
    val density = LocalDensity.current
    val wideWindow = remember { WideWindow() }
    NavHost(
        navController = navController,
        startDestination = Route.TrackerList,
        // The theme's own background behind every screen, never the window's, even mid-slide.
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .onSizeChanged { size -> wideWindow.isWide = with(density) { size.width.toDp() } >= TwoPaneMinWidth },
        // A back swipe plays these, not the destinations' pop transitions: the same slides
        // as the back button between the start screen and a tracker, the defaults elsewhere.
        predictivePopEnterTransition = { edge ->
            if (isWithinTheListScreen(wideWindow)) {
                EnterTransition.None
            } else {
                slideInToTheStartScreen(SwipeSlideSpec) ?: DefaultNavTransitions.predictivePopEnterTransition(this, edge)
            }
        },
        predictivePopExitTransition = { edge ->
            if (isWithinTheListScreen(wideWindow)) {
                ExitTransition.None
            } else {
                slideOutToTheStartScreen(SwipeSlideSpec) ?: DefaultNavTransitions.predictivePopExitTransition(this, edge)
            }
        },
    ) {
        // A tracker and its full-screen statistics sit to the start screen's right: they slide
        // in from there and back out, and a back swipe drags the start screen in after them.
        // In two columns the list stays put and a tracker opens or closes beside it, unanimated.
        composable<Route.TrackerList>(
            exitTransition = {
                when {
                    isWithinTheListScreen(wideWindow) -> HoldBeneath
                    targetState.isTrackerScreen() -> slideOut(SlideDirection.Start)
                    else -> null
                }
            },
            popEnterTransition = { if (isWithinTheListScreen(wideWindow)) EnterTransition.None else slideInToTheStartScreen() },
        ) { backStackEntry ->
            val navigation = ScreenNavigation(navController, backStackEntry)
            WithPaneLayout { twoPane ->
                if (twoPane) {
                    // The list with no tracker open beside it yet: the tracker screen's own
                    // layout, so picking one leaves the list and the divider exactly in place.
                    TrackerPanes(
                        state = rememberTrackerPanesState(),
                        twoPane = true,
                        statsReady = false,
                        swipeEnabled = false,
                        modifier = Modifier.fillMaxSize(),
                        list = {
                            TrackerList(
                                navigation = navigation,
                                trackerRepository = trackerRepository,
                                recordRepository = recordRepository,
                                backupRepository = backupRepository,
                                themeMode = themeMode,
                                onThemeModeChange = onThemeModeChange,
                                onOpenTracker = { trackerId -> navigation.open(Route.TrackerDetail(trackerId)) },
                                onOpenStats = { trackerId -> navigation.open(Route.TrackerDetail(trackerId, showStats = true)) },
                                topBarHeight = twoLineTopBarHeight(),
                                modifier = Modifier.withoutInsetsAt(WindowInsetsSides.End),
                            )
                        },
                        records = { NoTrackerPane(Modifier.fillMaxSize().withoutInsetsAt(WindowInsetsSides.Start)) },
                        stats = {},
                    )
                } else {
                    TrackerList(
                        navigation = navigation,
                        trackerRepository = trackerRepository,
                        recordRepository = recordRepository,
                        backupRepository = backupRepository,
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        onOpenTracker = { trackerId -> navigation.open(Route.TrackerDetail(trackerId)) },
                        onOpenStats = { trackerId -> navigation.open(Route.TrackerStats(trackerId)) },
                    )
                }
            }
        }
        composable<Route.TrackerDetail>(
            enterTransition = {
                when {
                    isWithinTheListScreen(wideWindow) -> EnterTransition.None
                    initialState.isRoute<Route.TrackerList>() -> slideIn(SlideDirection.Start)
                    else -> null
                }
            },
            exitTransition = { if (isWithinTheListScreen(wideWindow)) HoldBeneath else null },
            popExitTransition = { if (isWithinTheListScreen(wideWindow)) ExitTransition.None else slideOutToTheStartScreen() },
        ) { backStackEntry ->
            val navigation = ScreenNavigation(navController, backStackEntry)
            val route: Route.TrackerDetail = backStackEntry.toRoute()
            val recordChange by remember(backStackEntry) { backStackEntry.savedStateHandle.recordChanges() }
                .collectAsStateWithLifecycle(initialValue = null)
            val panes = rememberTrackerPanesState(if (route.showStats) TrackerPane.Stats else TrackerPane.Records)
            // The list beside the records is the start screen's: its ViewModels live in that
            // entry, always at the bottom of the stack, so both places show one and the same list.
            val listOwner = remember(backStackEntry) { navController.getBackStackEntry<Route.TrackerList>() }
            // Opened beside the list, the tracker stays unseen until its records are in, the screen
            // it replaces held beneath it meanwhile (HoldBeneath), then fades in over that one:
            // no blank moment between two trackers. Kept once shown, through rotations and returns.
            var revealed by rememberSaveable { mutableStateOf(!wideWindow.isWide) }
            val alpha by animateFloatAsState(
                targetValue = if (revealed) 1f else 0f,
                animationSpec = tween(RevealMillis),
                label = "trackerReveal",
            )
            // On a phone the statistics wait off-screen; built once the slide in has finished,
            // so opening a tracker doesn't also build its chart in the slide's first frames.
            var statsBuilt by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                snapshotFlow { transition.isRunning }.first { running -> !running }
                statsBuilt = true
            }
            // A tracker, or its statistics, picked from the list beside. Another tracker takes
            // this one's place on the stack, so back doesn't step through every one looked at.
            val openFromList = { trackerId: Long, showStats: Boolean ->
                when {
                    trackerId != route.trackerId -> navigation.ifCurrent {
                        navController.navigate(Route.TrackerDetail(trackerId, showStats)) {
                            popUpTo<Route.TrackerDetail> { inclusive = true }
                        }
                    }
                    showStats -> panes.show(TrackerPane.Stats)
                    // A tap on the tracker already open: it's right there beside the list.
                    else -> Unit
                }
            }
            WithPaneLayout { twoPane ->
                TrackerPanes(
                    state = panes,
                    twoPane = twoPane,
                    statsReady = statsBuilt,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { this.alpha = alpha },
                    list = {
                        TrackerList(
                            navigation = navigation,
                            trackerRepository = trackerRepository,
                            recordRepository = recordRepository,
                            backupRepository = backupRepository,
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                            onOpenTracker = { trackerId -> openFromList(trackerId, false) },
                            onOpenStats = { trackerId -> openFromList(trackerId, true) },
                            selectedTrackerId = route.trackerId,
                            viewModelStoreOwner = listOwner,
                            topBarHeight = twoLineTopBarHeight(),
                            // The left column: the window's right edge belongs to the column beside it.
                            modifier = Modifier.withoutInsetsAt(WindowInsetsSides.End),
                        )
                    },
                    records = {
                        TrackerDetailScreen(
                            trackerId = route.trackerId,
                            trackerRepository = trackerRepository,
                            recordRepository = recordRepository,
                            backupRepository = backupRepository,
                            // In two columns the list is right there; back is the system's, to the list alone.
                            onBackClick = if (twoPane) null else ({ navigation.back() }),
                            onAddRecordClick = { navigation.open(Route.RecordForm(trackerId = route.trackerId)) },
                            onRecordClick = { record ->
                                navigation.open(Route.RecordForm(trackerId = route.trackerId, recordId = record.id))
                            },
                            onEditTrackerClick = { navigation.open(Route.TrackerEdit(route.trackerId)) },
                            onStatsClick = { panes.show(TrackerPane.Stats) },
                            // Back to the start screen; the deleted tracker's entry must not stay on the stack.
                            onTrackerDeleted = { navigation.ifCurrent { navController.popBackStack(Route.TrackerList, inclusive = false) } },
                            recordChange = recordChange,
                            onRecordChangeConsumed = { backStackEntry.savedStateHandle.clearRecordChange() },
                            onLoaded = { revealed = true },
                            modifier = Modifier
                                .fillMaxSize()
                                // In two columns the records stand beside the list on the right, or beside
                                // the statistics on the left; the window's other edge isn't theirs to pad for.
                                .then(
                                    when {
                                        !twoPane -> Modifier
                                        panes.pane == TrackerPane.Stats -> Modifier.withoutInsetsAt(WindowInsetsSides.End)
                                        else -> Modifier.withoutInsetsAt(WindowInsetsSides.Start)
                                    },
                                ),
                        )
                    },
                    stats = {
                        // Its ViewModel belongs to this back-stack entry, so through a fold,
                        // unfold or rotation the stats keep their period, type and tag.
                        StatsScreen(
                            trackerId = route.trackerId,
                            recordRepository = recordRepository,
                            tagRepository = tagRepository,
                            onBackClick = { panes.show(TrackerPane.Records) },
                            modifier = Modifier
                                .fillMaxSize()
                                .then(if (twoPane) Modifier.withoutInsetsAt(WindowInsetsSides.Start) else Modifier),
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
                // Unfolded: the tracker screen shows the statistics beside its records, so go
                // there in their place, handing the selection over to its ViewModel.
                if (twoPane) {
                    LaunchedEffect(Unit) {
                        navigation.ifCurrent {
                            val selection = statsViewModel(backStackEntry, route.trackerId, recordRepository, tagRepository).selection
                            navController.navigate(Route.TrackerDetail(route.trackerId, showStats = true)) {
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

/**
 * The start screen's tracker list, on its own or as the tracker screen's left
 * column; [onOpenTracker] and [onOpenStats] are what a tap on a tracker or its
 * menu's statistics item lead to there.
 */
@Composable
private fun TrackerList(
    navigation: ScreenNavigation,
    trackerRepository: TrackerRepository,
    recordRepository: RecordRepository,
    backupRepository: BackupRepository,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenTracker: (trackerId: Long) -> Unit,
    onOpenStats: (trackerId: Long) -> Unit,
    modifier: Modifier = Modifier,
    selectedTrackerId: Long? = null,
    viewModelStoreOwner: ViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current),
    topBarHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
) {
    TrackerListScreen(
        trackerRepository = trackerRepository,
        recordRepository = recordRepository,
        backupRepository = backupRepository,
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
        onTrackerClick = { tracker -> onOpenTracker(tracker.id) },
        onEditTrackerClick = { tracker -> navigation.open(Route.TrackerEdit(tracker.id)) },
        onStatsClick = { tracker -> onOpenStats(tracker.id) },
        onTrackerCreated = onOpenTracker,
        onHelpClick = { navigation.open(Route.Help) },
        onFaqClick = { navigation.open(Route.Faq) },
        selectedTrackerId = selectedTrackerId,
        viewModelStoreOwner = viewModelStoreOwner,
        topBarHeight = topBarHeight,
        modifier = modifier.fillMaxSize(),
    )
}

/**
 * How long, at most, the screen a tracker replaces beside the list stays beneath it while
 * that tracker's records come in; they take tens of milliseconds, so this is to spare.
 */
private const val HoldBeneathMillis = 400

/** The tracker's fade in over the screen held beneath, once its records are in. */
private const val RevealMillis = 150

/** Stays fully in place beneath the incoming screen for [HoldBeneathMillis], then goes at once. */
private val HoldBeneath: ExitTransition = fadeOut(tween(durationMillis = 1, delayMillis = HoldBeneathMillis))

/** Whether the window is two columns wide; set from the NavHost's size, read by the transitions. */
private class WideWindow {
    var isWide by mutableStateOf(false)
}

/**
 * Between the start screen and a tracker, or one tracker and another, in two columns:
 * the list stays where it is and the tracker opens or closes beside it, unanimated.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isWithinTheListScreen(wideWindow: WideWindow): Boolean {
    fun NavBackStackEntry.besideTheList() = isRoute<Route.TrackerList>() || isRoute<Route.TrackerDetail>()
    return wideWindow.isWide && initialState.besideTheList() && targetState.besideTheList()
}

/** A column that keeps clear of the window's insets everywhere but at [side], where another column stands. */
@Composable
private fun Modifier.withoutInsetsAt(side: WindowInsetsSides) = consumeWindowInsets(WindowInsets.safeDrawing.only(side))

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
