package com.spendobserver.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.spendobserver.data.repository.BackupRepository
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.data.settings.ThemeMode
import com.spendobserver.ui.help.FaqScreen
import com.spendobserver.ui.help.HelpScreen
import com.spendobserver.ui.record.RecordFormScreen
import com.spendobserver.ui.stats.StatsScreen
import com.spendobserver.ui.tracker.TrackerDetailScreen
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
        modifier = modifier,
    ) {
        composable<Route.TrackerList> { backStackEntry ->
            val navigation = ScreenNavigation(navController, backStackEntry)
            TrackerListScreen(
                trackerRepository = trackerRepository,
                recordRepository = recordRepository,
                backupRepository = backupRepository,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onTrackerClick = { tracker -> navigation.open(Route.TrackerDetail(tracker.id)) },
                onEditTrackerClick = { tracker -> navigation.open(Route.TrackerEdit(tracker.id)) },
                onTrackerCreated = { trackerId -> navigation.open(Route.TrackerDetail(trackerId)) },
                onHelpClick = { navigation.open(Route.Help) },
                onFaqClick = { navigation.open(Route.Faq) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable<Route.TrackerDetail> { backStackEntry ->
            val navigation = ScreenNavigation(navController, backStackEntry)
            val route: Route.TrackerDetail = backStackEntry.toRoute()
            val recordChange by remember(backStackEntry) { backStackEntry.savedStateHandle.recordChanges() }
                .collectAsStateWithLifecycle(initialValue = null)
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
                onStatsClick = { navigation.open(Route.TrackerStats(route.trackerId)) },
                // Back to the start screen; the deleted tracker's entry must not stay on the stack.
                onTrackerDeleted = { navigation.ifCurrent { navController.popBackStack(Route.TrackerList, inclusive = false) } },
                recordChange = recordChange,
                onRecordChangeConsumed = { backStackEntry.savedStateHandle.clearRecordChange() },
                modifier = Modifier.fillMaxSize(),
            )
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
        composable<Route.TrackerStats> { backStackEntry ->
            val navigation = ScreenNavigation(navController, backStackEntry)
            val route: Route.TrackerStats = backStackEntry.toRoute()
            StatsScreen(
                trackerId = route.trackerId,
                recordRepository = recordRepository,
                tagRepository = tagRepository,
                onBackClick = { navigation.back() },
                modifier = Modifier.fillMaxSize(),
            )
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
