package com.spendobserver.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.ui.record.RecordFormScreen
import com.spendobserver.ui.tracker.TrackerDetailScreen
import com.spendobserver.ui.trackers.TrackerEditScreen
import com.spendobserver.ui.trackers.TrackerListScreen

@Composable
fun SpendObserverNavHost(
    navController: NavHostController,
    trackerRepository: TrackerRepository,
    recordRepository: RecordRepository,
    tagRepository: TagRepository,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Route.TrackerList,
        modifier = modifier,
    ) {
        composable<Route.TrackerList> {
            TrackerListScreen(
                trackerRepository = trackerRepository,
                recordRepository = recordRepository,
                onTrackerClick = { tracker -> navController.navigate(Route.TrackerDetail(tracker.id)) },
                onEditTrackerClick = { tracker -> navController.navigate(Route.TrackerEdit(tracker.id)) },
                onTrackerCreated = { trackerId -> navController.navigate(Route.TrackerDetail(trackerId)) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable<Route.TrackerDetail> { backStackEntry ->
            val route: Route.TrackerDetail = backStackEntry.toRoute()
            val recordChange by remember(backStackEntry) { backStackEntry.savedStateHandle.recordChanges() }
                .collectAsStateWithLifecycle(initialValue = null)
            TrackerDetailScreen(
                trackerId = route.trackerId,
                trackerRepository = trackerRepository,
                recordRepository = recordRepository,
                onBackClick = { navController.popBackStack() },
                onAddRecordClick = { navController.navigate(Route.RecordForm(trackerId = route.trackerId)) },
                onRecordClick = { record ->
                    navController.navigate(Route.RecordForm(trackerId = route.trackerId, recordId = record.id))
                },
                onEditTrackerClick = { navController.navigate(Route.TrackerEdit(route.trackerId)) },
                // Back to the start screen; the deleted tracker's entry must not stay on the stack.
                onTrackerDeleted = { navController.popBackStack(Route.TrackerList, inclusive = false) },
                recordChange = recordChange,
                onRecordChangeConsumed = { backStackEntry.savedStateHandle.clearRecordChange() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable<Route.RecordForm> { backStackEntry ->
            val route: Route.RecordForm = backStackEntry.toRoute()
            RecordFormScreen(
                trackerId = route.trackerId,
                recordId = route.recordId,
                recordRepository = recordRepository,
                tagRepository = tagRepository,
                onSaved = { change ->
                    // Hand the result to the tracker-detail entry underneath before
                    // popping back to it; plain back navigation sets nothing.
                    navController.previousBackStackEntry?.savedStateHandle?.setRecordChange(change)
                    navController.popBackStack()
                },
                onBackClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable<Route.TrackerEdit> { backStackEntry ->
            val route: Route.TrackerEdit = backStackEntry.toRoute()
            TrackerEditScreen(
                trackerId = route.trackerId,
                trackerRepository = trackerRepository,
                tagRepository = tagRepository,
                onSaved = { navController.popBackStack() },
                onBackClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
