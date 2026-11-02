package com.spendobserver.ui.common

import com.spendobserver.data.local.entity.TagEntity
import com.spendobserver.data.local.entity.RecordEntity
import com.spendobserver.data.local.entity.TrackerEntity
import android.database.sqlite.SQLiteFullException
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spendobserver.data.local.dao.RecordDao
import com.spendobserver.data.local.dao.TagDao
import com.spendobserver.domain.model.Tracker
import com.spendobserver.data.repository.RecordRepository
import com.spendobserver.data.repository.TagRepository
import com.spendobserver.data.repository.TrackerRepository
import com.spendobserver.testing.InMemoryDatabaseRule
import com.spendobserver.ui.record.RecordFormViewModel
import com.spendobserver.ui.trackers.TrackerEditViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A full disk, as the database reports it: a message for the user instead of a crash. */
@RunWith(AndroidJUnit4::class)
class WriteFailureTest {

    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private var trackerId = 0L
    private var diskFull = true

    @Before
    fun setUp() = runBlocking {
        trackerId = dbRule.db.trackerDao().insert(TrackerEntity(title = "Family", createdAt = 0, updatedAt = 0))
    }

    private fun onMain(action: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(action)

    private suspend fun kotlinx.coroutines.flow.StateFlow<Boolean>.awaitTrue() = withTimeout(5_000) { first { it } }

    @Test
    fun recordForm_reportsTheFailure_andCanSaveOnceThereIsRoom() = runBlocking<Unit> {
        val db = dbRule.db
        val realDao = db.recordDao()
        val failingDao = object : RecordDao by realDao {
            override suspend fun insert(record: RecordEntity): Long {
                if (diskFull) throw SQLiteFullException()
                return realDao.insert(record)
            }
        }
        val records = RecordRepository(db, recordDao = failingDao)
        val tags = TagRepository(db.tagDao(), records::deleteTag)
        lateinit var viewModel: RecordFormViewModel
        onMain { viewModel = RecordFormViewModel(trackerId, null, records, tags, SavedStateHandle()) }
        viewModel.setPartAmount(0, "5")
        var saved = 0

        onMain { viewModel.save { saved++ } }
        viewModel.writeFailed.awaitTrue()

        assertEquals(0, saved)
        assertEquals("5", viewModel.parts.value.parts.single().amountExpression)

        diskFull = false
        onMain {
            viewModel.clearWriteFailed()
            viewModel.save { saved++ }
        }
        withTimeout(5_000) { records.observeAll(trackerId).first { it.size == 1 } }
    }

    @Test
    fun trackerSettings_reportTheFailure_whenCreatingATag() = runBlocking {
        val realDao = dbRule.db.tagDao()
        val failingDao = object : TagDao by realDao {
            override suspend fun insert(tag: TagEntity): Long = throw SQLiteFullException()
        }
        val tags = TagRepository(failingDao, RecordRepository(dbRule.db)::deleteTag)
        lateinit var viewModel: TrackerEditViewModel
        onMain { viewModel = TrackerEditViewModel(trackerId, TrackerRepository(dbRule.db.trackerDao()), tags) }
        var created = 0

        onMain { viewModel.createTag("кино", isGlobal = false) { created++ } }
        viewModel.writeFailed.awaitTrue()

        assertEquals(0, created)
    }
}
