package com.spendobserver.ui.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendobserver.data.local.entity.TrackerEntity
import com.spendobserver.data.repository.BackupRepository
import com.spendobserver.domain.backup.BackupParseResult
import com.spendobserver.domain.backup.parseBackup
import com.spendobserver.testing.InMemoryDatabaseRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupExporterTest {

    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val exportsDir get() = File(context.cacheDir, "exports")
    private lateinit var exporter: BackupExporter
    private var familyId = 0L
    private var workId = 0L

    @Before
    fun setUp() = runBlocking<Unit> {
        exportsDir.deleteRecursively()
        familyId = dbRule.db.trackerDao().insert(TrackerEntity(title = "Family", createdAt = 0, updatedAt = 0))
        workId = dbRule.db.trackerDao().insert(TrackerEntity(title = "Work", createdAt = 0, updatedAt = 0))
        exporter = BackupExporter(context, BackupRepository(dbRule.db))
    }

    private fun titlesIn(json: String): List<String> = when (val result = parseBackup(json)) {
        is BackupParseResult.Success -> result.trackers.map { it.title }
        is BackupParseResult.Failure -> fail("The export doesn't import back: ${result.error}") as Nothing
    }

    private suspend fun backupAt(id: Long) = dbRule.db.trackerDao().getById(id)!!.backupAt

    @Test
    fun saveTo_writesTheTrackerToTheFile_andMarksOnlyItBackedUp() = runBlocking<Unit> {
        val target = File(context.cacheDir, "saved.json").apply { writeText("older, longer content to be replaced") }

        exporter.saveTo(Uri.fromFile(target), ExportScope.One(familyId, "Family"))

        assertEquals(listOf("Family"), titlesIn(target.readText()))
        assertNotNull(backupAt(familyId))
        assertNull(backupAt(workId))
        target.delete()
    }

    @Test
    fun shareIntent_allTrackers_leavesOneFileInTheCache_andMarksThemAll() = runBlocking<Unit> {
        exporter.shareIntent(ExportScope.One(familyId, "Family"))

        exporter.shareIntent(ExportScope.All)

        val files = exportsDir.listFiles().orEmpty().toList()
        assertEquals(listOf(exportFileName(ExportScope.All)), files.map { it.name })
        assertEquals(listOf("Family", "Work"), titlesIn(files.single().readText()).sorted())
        assertNotNull(backupAt(familyId))
        assertNotNull(backupAt(workId))
    }

    @Test
    fun shareIntent_handsTheFileOverReadable() = runBlocking<Unit> {
        val chooser = exporter.shareIntent(ExportScope.One(familyId, "Family"))

        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals("application/json", send.type)
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val uri = IntentCompat.getParcelableExtra(send, Intent.EXTRA_STREAM, Uri::class.java)!!
        assertEquals("content", uri.scheme)
        val json = context.contentResolver.openInputStream(uri)!!.use { it.reader().readText() }
        assertEquals(listOf("Family"), titlesIn(json))
    }

    @Test
    fun aTrackerDeletedMeanwhile_failsWithoutMarkingAnything() = runBlocking<Unit> {
        val result = runCatching { exporter.shareIntent(ExportScope.One(trackerId = 999, title = "Gone")) }

        assertTrue(result.isFailure)
        assertNull(backupAt(familyId))
        assertFalse(File(exportsDir, exportFileName(ExportScope.One(999, "Gone"))).exists())
    }
}
