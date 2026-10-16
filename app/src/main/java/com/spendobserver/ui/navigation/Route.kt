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

    /** [recordId] is null when creating a new record, set when editing an existing one. */
    @Serializable
    data class RecordForm(val trackerId: Long, val recordId: Long? = null) : Route

    @Serializable
    data class TrackerEdit(val trackerId: Long) : Route

    @Serializable
    data class TrackerStats(val trackerId: Long) : Route

    @Serializable
    data object Help : Route

    @Serializable
    data object Faq : Route
}
