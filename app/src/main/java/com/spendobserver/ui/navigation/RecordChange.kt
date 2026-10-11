package com.spendobserver.ui.navigation

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The result [Route.RecordForm] hands back to [Route.TrackerDetail] after a
 * record is saved or deleted, so the accordion can open and scroll to it.
 * [year]/[month] are the record's date *after* the change (for a deletion,
 * the month it was removed from). Going back without saving produces no
 * result at all.
 */
@Serializable
data class RecordChange(
    val recordId: Long,
    val year: Int,
    val month: Int,
    val deleted: Boolean,
)

private const val RecordChangeKey = "recordChange"

// SavedStateHandle only stores Bundle-compatible types, so the result
// travels as a JSON string rather than as the data class itself.

fun SavedStateHandle.setRecordChange(change: RecordChange) {
    this[RecordChangeKey] = Json.encodeToString(change)
}

fun SavedStateHandle.recordChanges(): Flow<RecordChange?> =
    getStateFlow<String?>(RecordChangeKey, null).map { json -> json?.let { Json.decodeFromString(it) } }

fun SavedStateHandle.clearRecordChange() {
    remove<String>(RecordChangeKey)
}
