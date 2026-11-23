package com.spendobserver.ui.record

import android.database.sqlite.SQLiteException
import androidx.lifecycle.SavedStateHandle
import com.spendobserver.data.local.dao.TagDao
import com.spendobserver.data.local.entity.TagEntity
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.domain.model.Part
import com.spendobserver.domain.model.Record
import com.spendobserver.domain.model.RecordType
import com.spendobserver.domain.model.Tag
import com.spendobserver.testing.FakeRecordRepository
import com.spendobserver.ui.common.NameError
import com.spendobserver.ui.navigation.RecordChange
import com.spendobserver.ui.trackers.FakeTagDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecordFormViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var records: FakeRecordRepository
    private lateinit var tagDao: FakeTagDao
    private lateinit var tags: TagRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        records = FakeRecordRepository()
        tagDao = FakeTagDao()
        tags = TagRepository(tagDao, deleteTag = { records.deleteTag(it) })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A form as the navigation host opens it; [handle] survives a process death when shared. */
    private fun TestScope.form(
        recordId: Long? = null,
        handle: SavedStateHandle = SavedStateHandle(),
        tagRepository: TagRepository = tags,
    ) = RecordFormViewModel(TRACKER_ID, recordId, records, tagRepository, handle).also { vm ->
        merge(vm.tags, vm.evaluation).launchIn(backgroundScope)
        advanceUntilIdle()
    }

    private suspend fun tag(name: String, isGlobal: Boolean): Tag {
        val tag = Tag(trackerId = TRACKER_ID, name = name, isGlobal = isGlobal)
        return tag.copy(id = tags.insertTag(tag))
    }

    /** Saves and returns what the form reported, or null if it didn't. */
    private fun TestScope.save(vm: RecordFormViewModel): RecordChange? {
        var change: RecordChange? = null
        vm.save { change = it }
        advanceUntilIdle()
        return change
    }

    private fun saved(id: Long = 7, date: LocalDate = LocalDate(2025, 7, 10), tags: List<Tag> = emptyList(), parts: List<Part>) =
        records.add(
            Record(id = id, trackerId = TRACKER_ID, date = date, type = RecordType.INCOME, description = "Зарплата", tags = tags, parts = parts),
        )

    // --- New record ---------------------------------------------------------

    @Test
    fun newRecord_savesEveryField_andReportsWhereItWent() = runTest(dispatcher) {
        val shop = tag("g20", isGlobal = true)
        val vm = form()
        assertFalse(vm.isEditing)

        vm.setType(RecordType.INCOME)
        vm.setDate(LocalDate(2026, 3, 15))
        vm.setDescription("  Магазин ​")
        vm.setPartAmount(0, "2*3")
        vm.toggleGlobalTag(shop.id)
        val change = save(vm)

        val record = records.all.single()
        assertEquals(RecordChange(recordId = record.id, year = 2026, month = 3, deleted = false), change)
        assertEquals(RecordType.INCOME, record.type)
        assertEquals(LocalDate(2026, 3, 15), record.date)
        assertEquals("Магазин", record.description)
        assertEquals(listOf(600L), record.parts.map { it.amountCents })
        assertEquals(listOf(shop.id), record.tags.map { it.id })
    }

    @Test
    fun blankDescription_isSavedAsNone() = runTest(dispatcher) {
        val vm = form()
        vm.setDescription(" ​ ")
        vm.setPartAmount(0, "10")

        save(vm)

        assertNull(records.all.single().description)
    }

    @Test
    fun split_savesTheTypedPart_andTheRemainderOfTheReceipt() = runTest(dispatcher) {
        val alcohol = tag("алко", isGlobal = false)
        val vm = form()
        vm.setDate(LocalDate(2026, 3, 15))
        vm.setPartAmount(0, "10")

        // The typed 10 becomes the receipt total; part 1 is new, part 0 the remainder.
        vm.split()
        vm.setPartAmount(1, "3")
        vm.togglePartTag(1, alcohol.id)
        save(vm)

        val parts = records.all.single().parts
        assertEquals(listOf(300L, 700L), parts.map { it.amountCents })
        assertEquals(listOf(listOf(alcohol.id), emptyList()), parts.map { p -> p.tags.map { it.id } })
    }

    @Test
    fun invalidAmount_showsTheErrors_andSavesNothing() = runTest(dispatcher) {
        val vm = form()
        assertFalse("errors stay hidden before the first save", vm.showErrors.value)

        val change = save(vm)

        assertNull(change)
        assertTrue(vm.showErrors.value)
        assertEquals(0, records.writeCount)
    }

    @Test
    fun secondTapOnSave_storesNoSecondRecord() = runTest(dispatcher) {
        val vm = form()
        vm.setPartAmount(0, "10")

        vm.save { }
        vm.save { }
        advanceUntilIdle()

        assertEquals(1, records.all.size)
    }

    @Test
    fun saveTheDatabaseRefuses_keepsTheForm_andCanBeRetried() = runTest(dispatcher) {
        val vm = form()
        vm.setPartAmount(0, "10")
        records.failWrites = true

        assertNull(save(vm))
        assertTrue(vm.writeFailed.value)
        assertEquals("10", vm.parts.value.parts.single().amountExpression)

        vm.clearWriteFailed()
        records.failWrites = false
        assertTrue("a refused save must not lock the button", save(vm) != null)
        assertEquals(1, records.all.size)
    }

    // --- Global tags --------------------------------------------------------

    @Test
    fun globalTag_atMostOne_andTappingItAgainClearsIt() = runTest(dispatcher) {
        val vm = form()

        vm.toggleGlobalTag(1)
        vm.toggleGlobalTag(2)
        assertEquals(setOf(2L), vm.globalTagIds.value)

        vm.toggleGlobalTag(2)
        assertEquals(emptySet<Long>(), vm.globalTagIds.value)
    }

    // --- Editing ------------------------------------------------------------

    @Test
    fun editing_loadsTheSavedRecord() = runTest(dispatcher) {
        val work = Tag(id = 3, trackerId = TRACKER_ID, name = "работа", isGlobal = true)
        val bonus = Tag(id = 4, trackerId = TRACKER_ID, name = "премия")
        saved(tags = listOf(work), parts = listOf(Part(150_000, emptyList()), Part(2_550, listOf(bonus))))

        val vm = form(recordId = 7)

        assertTrue(vm.isEditing)
        assertEquals(RecordType.INCOME, vm.type.value)
        assertEquals(LocalDate(2025, 7, 10), vm.date.value)
        assertEquals("Зарплата", vm.description.value)
        assertEquals(setOf(3L), vm.globalTagIds.value)
        assertEquals(listOf(150_000L, 2_550L), vm.parts.value.parts.map { vm.evaluation.value.partCents[it.id] })
        assertEquals(listOf(emptySet(), setOf(4L)), vm.parts.value.parts.map { it.tagIds })
    }

    @Test
    fun editing_save_replacesTheRecordInPlace() = runTest(dispatcher) {
        saved(parts = listOf(Part(1000, emptyList())))
        val vm = form(recordId = 7)

        vm.setPartAmount(0, "25")
        vm.setDate(LocalDate(2026, 1, 2))
        val change = save(vm)

        assertEquals(RecordChange(recordId = 7, year = 2026, month = 1, deleted = false), change)
        val record = records.all.single()
        assertEquals(7L, record.id)
        assertEquals(2500L, record.amountCents)
    }

    @Test
    fun delete_reportsWhereTheRecordWas_notWhereTheFormMovedIt() = runTest(dispatcher) {
        saved(parts = listOf(Part(1000, emptyList())))
        val vm = form(recordId = 7)
        vm.setDate(LocalDate(2026, 1, 2))

        var change: RecordChange? = null
        vm.delete { change = it }
        advanceUntilIdle()

        assertEquals(RecordChange(recordId = 7, year = 2025, month = 7, deleted = true), change)
        assertTrue(records.all.isEmpty())
    }

    @Test
    fun delete_ofARecordThatIsGone_doesNothing() = runTest(dispatcher) {
        val vm = form(recordId = 99)
        var called = false

        vm.delete { called = true }
        advanceUntilIdle()

        assertFalse(called)
        assertEquals(0, records.writeCount)
    }

    // --- Draft across process death -----------------------------------------

    @Test
    fun draft_survivesTheSystemKillingTheApp() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        val before = form(handle = handle)
        before.setType(RecordType.INCOME)
        before.setDescription("Аванс")
        before.setPartAmount(0, "12")
        before.toggleGlobalTag(5)
        advanceUntilIdle()

        val after = form(handle = handle)

        assertEquals(RecordType.INCOME, after.type.value)
        assertEquals("Аванс", after.description.value)
        assertEquals("12", after.parts.value.parts.single().amountExpression)
        assertEquals(setOf(5L), after.globalTagIds.value)
    }

    @Test
    fun draft_winsOverTheSavedRecord_whichCanStillBeDeleted() = runTest(dispatcher) {
        saved(parts = listOf(Part(1000, emptyList())))
        val handle = SavedStateHandle()
        form(recordId = 7, handle = handle).setDescription("Правка")
        advanceUntilIdle()

        val after = form(recordId = 7, handle = handle)
        assertEquals("Правка", after.description.value)

        var change: RecordChange? = null
        after.delete { change = it }
        advanceUntilIdle()
        assertEquals(RecordChange(recordId = 7, year = 2025, month = 7, deleted = true), change)
    }

    // --- Creating tags from the form ----------------------------------------

    @Test
    fun createGlobalTag_selectsItOnTheRecord() = runTest(dispatcher) {
        val vm = form()
        var created = false

        vm.createTag(" g20 ", isGlobal = true) { created = true }
        advanceUntilIdle()

        val tag = vm.tags.value.single()
        assertTrue(created)
        assertEquals(Tag(id = tag.id, trackerId = TRACKER_ID, name = "g20", isGlobal = true), tag)
        assertEquals(setOf(tag.id), vm.globalTagIds.value)
    }

    @Test
    fun createLocalTag_putsItOnTheAskingPart() = runTest(dispatcher) {
        val vm = form()
        vm.split()

        vm.createTag("алко", isGlobal = false, forPartId = 1)
        advanceUntilIdle()

        val id = vm.tags.value.single().id
        assertEquals(listOf(setOf(id), emptySet()), vm.parts.value.parts.map { it.tagIds })
        assertTrue(vm.globalTagIds.value.isEmpty())
    }

    @Test
    fun createTag_withATakenOrEmptyName_isRefused() = runTest(dispatcher) {
        tag("Алко", isGlobal = false)
        val vm = form()
        var created = false

        vm.createTag("алко", isGlobal = true) { created = true }
        advanceUntilIdle()
        assertEquals("a global tag can't take a local one's name", NameError.TAKEN, vm.tagNameError.value)

        vm.createTag("​ ", isGlobal = false) { created = true }
        advanceUntilIdle()
        assertEquals(NameError.EMPTY, vm.tagNameError.value)

        assertFalse(created)
        assertEquals(1, vm.tags.value.size)
        vm.clearTagNameError()
        assertNull(vm.tagNameError.value)
    }

    @Test
    fun createTag_theDatabaseRefuses_isReported() = runTest(dispatcher) {
        val refusing = object : TagDao by tagDao {
            override suspend fun insert(tag: TagEntity): Long = throw SQLiteException("fake: write refused")
        }
        val vm = form(tagRepository = TagRepository(refusing, deleteTag = { }))
        var created = false

        vm.createTag("g20", isGlobal = true) { created = true }
        advanceUntilIdle()

        assertFalse(created)
        assertTrue(vm.writeFailed.value)
        assertTrue(vm.globalTagIds.value.isEmpty())
    }

    private companion object {
        const val TRACKER_ID = 1L
    }
}
