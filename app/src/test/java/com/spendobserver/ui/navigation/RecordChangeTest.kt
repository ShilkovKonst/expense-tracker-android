package com.spendobserver.ui.navigation

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordChangeTest {

    private val change = RecordChange(recordId = 42, year = 2026, month = 3, deleted = false)

    @Test
    fun nothingSet_readsAsNull() = runTest {
        assertNull(SavedStateHandle().recordChanges().first())
    }

    @Test
    fun aSetChange_comesBackWhole() = runTest {
        val handle = SavedStateHandle()

        handle.setRecordChange(change)

        assertEquals(change, handle.recordChanges().first())
    }

    @Test
    fun aDeletion_keepsItsFlag() = runTest {
        val handle = SavedStateHandle()
        val deletion = change.copy(deleted = true)

        handle.setRecordChange(deletion)

        assertEquals(deletion, handle.recordChanges().first())
    }

    @Test
    fun aClearedChange_isGone() = runTest {
        val handle = SavedStateHandle()
        handle.setRecordChange(change)

        handle.clearRecordChange()

        assertNull(handle.recordChanges().first())
    }

    @Test
    fun theChange_survivesAsBundleSafeState() = runTest {
        // A recreated screen gets its handle back from the saved values, which must hold only Bundle types.
        val first = SavedStateHandle()
        first.setRecordChange(change)
        val restored = SavedStateHandle(first.keys().associateWith { first.get<Any>(it) })

        assertEquals(change, restored.recordChanges().first())
    }
}
