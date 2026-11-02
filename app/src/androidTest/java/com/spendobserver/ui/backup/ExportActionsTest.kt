package com.spendobserver.ui.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendobserver.data.local.entity.Tracker
import com.spendobserver.data.repository.BackupRepository
import com.spendobserver.testing.InMemoryDatabaseRule
import com.spendobserver.testing.waitFor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ExportActionsTest {

    @get:Rule(order = 0)
    val dbRule = InMemoryDatabaseRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    /** Stands in for the system's create-document screen: remembers the launch, answers when told. */
    private class FakeRegistry : ActivityResultRegistry() {
        var lastRequestCode: Int? = null

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            lastRequestCode = requestCode
        }
    }

    @Test
    fun turningThePhoneOnTheSaveScreen_stillWritesTheFile() {
        runBlocking { dbRule.db.trackerDao().insert(Tracker(title = "Family", createdAt = 0, updatedAt = 0)) }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "export-test.json").apply { writeText("") }
        val fakeRegistry = FakeRegistry()
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = fakeRegistry
        }
        val messages = mutableListOf<String>()
        lateinit var actions: ExportActions
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                actions = rememberExportActions(BackupRepository(dbRule.db)) { messages += it }
            }
        }

        composeRule.runOnIdle { actions.save(ExportScope.All) }
        restoration.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle {
            fakeRegistry.dispatchResult(fakeRegistry.lastRequestCode!!, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        }
        composeRule.waitFor { messages.isNotEmpty() }

        assertEquals(1, messages.size)
        assertTrue(file.readText().contains("Family"))
    }
}
