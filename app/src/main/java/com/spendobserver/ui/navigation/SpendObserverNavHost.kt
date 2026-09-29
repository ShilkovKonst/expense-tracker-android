package com.spendobserver.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.ui.tracker.TrackerDetailScreen
import com.spendobserver.ui.trackers.TrackerListScreen

@Composable
fun SpendObserverNavHost(
    navController: NavHostController,
    trackerRepository: TrackerRepository,
    recordRepository: RecordRepository,
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
                onTrackerClick = { tracker -> navController.navigate(Route.TrackerDetail(tracker.id)) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        composable<Route.TrackerDetail> { backStackEntry ->
            val route: Route.TrackerDetail = backStackEntry.toRoute()
            TrackerDetailScreen(
                trackerId = route.trackerId,
                trackerRepository = trackerRepository,
                recordRepository = recordRepository,
                onBackClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
