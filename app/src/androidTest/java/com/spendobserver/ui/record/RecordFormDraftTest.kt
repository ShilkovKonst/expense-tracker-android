package com.spendobserver.ui.record

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spendobserver.data.local.entity.Record
import com.spendobserver.data.local.entity.RecordType
import com.spendobserver.data.local.entity.Tracker
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.domain.record.PartDraft
import com.spendobserver.testing.InMemoryDatabaseRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A second ViewModel over the same [SavedStateHandle] is what the app gets after the system killed it. */
@RunWith(AndroidJUnit4::class)
class RecordFormDraftTest {

    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private lateinit var records: RecordRepository
    private var trackerId = 0L

    @Before
    fun setUp() = runBlocking {
        records = RecordRepository(dbRule.db)
        trackerId = dbRule.db.trackerDao().insert(Tracker(title = "Family", createdAt = 0, updatedAt = 0))
    }

    private fun viewModel(handle: SavedStateHandle, recordId: Long? = null): RecordFormViewModel {
        lateinit var viewModel: RecordFormViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val tags = TagRepository(dbRule.db.tagDao(), records::deleteTag)
            viewModel = RecordFormViewModel(trackerId, recordId, records, tags, handle)
        }
        return viewModel
    }

    private suspend fun SavedStateHandle.awaitDraftContaining(text: String) = withTimeout(5_000) {
        while (get<String>(FORM_DRAFT_KEY)?.contains(text) != true) delay(10)
    }

    @Test
    fun aNewRecordsInput_comesBack() = runBlocking {
        val handle = SavedStateHandle()
        val before = viewModel(handle)
        before.setType(RecordType.INCOME)
        before.setDate(LocalDate(2026, 3, 15))
        before.setPartAmount(0, "3450")
        before.split()
        before.setPartAmount(before.parts.value.parts.first().id, "800")
        before.setDescription("Ашан")
        handle.awaitDraftContaining("Ашан")

        val after = viewModel(handle)

        assertEquals(RecordType.INCOME, after.type.value)
        assertEquals(LocalDate(2026, 3, 15), after.date.value)
        assertEquals("Ашан", after.description.value)
        assertEquals(before.parts.value, after.parts.value)
        assertEquals("3450", after.parts.value.receiptTotalExpression)
    }

    @Test
    fun anEditedRecord_keepsTheEdits_overWhatTheDatabaseHolds_andCanStillBeDeleted() = runBlocking {
        val id = records.insertRecord(
            Record(trackerId = trackerId, date = LocalDate(2026, 3, 15), type = RecordType.COST, description = "old"),
            emptySet(),
            listOf(PartDraft(100, emptySet())),
        )
        val handle = SavedStateHandle()
        val before = viewModel(handle, id)
        before.description.first { it == "old" }
        before.setDescription("edited")
        handle.awaitDraftContaining("edited")

        val after = viewModel(handle, id)
        delay(200) // Long enough for a wrong reload from the database to land.
        assertEquals("edited", after.description.value)
        assertEquals("1.00", after.parts.value.parts.single().amountExpression)

        withTimeout(5_000) {
            var deleted = false
            while (!deleted) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync { after.delete { deleted = true } }
                delay(50)
            }
        }
        assertNull(records.getRecord(id))
    }
}
