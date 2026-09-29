package com.spendobserver.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations (Navigation Compose 2.8+: a route is a
 * plain @Serializable type instead of a hand-built string path). The
 * compiler checks every `navController.navigate(...)` call and every
 * `NavBackStackEntry.toRoute<T>()` against these types, so a route's
 * arguments can never drift out of sync with what a screen reads back.
 */
sealed interface Route {

    @Serializable
    data object TrackerList : Route

    @Serializable
    data class TrackerDetail(val trackerId: Long) : Route
}
