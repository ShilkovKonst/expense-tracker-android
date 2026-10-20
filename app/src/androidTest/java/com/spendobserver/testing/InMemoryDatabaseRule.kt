package com.spendobserver.testing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.spendobserver.data.local.AppDatabase
import org.junit.rules.ExternalResource

/**
 * A fresh in-memory database per test. Declare it with a lower `order` than
 * the compose rule, so it wraps it:
 *
 * ```
 * @get:Rule(order = 0) val dbRule = InMemoryDatabaseRule()
 * @get:Rule(order = 1) val composeRule = createComposeRule()
 * ```
 *
 * Closing the database in `@After` instead is too early: JUnit runs `@After`
 * inside the rules, while the screen and its ViewModel are still alive and
 * may be mid-query. A query stuck on a closed database can hold one of Room's
 * process-wide query threads, starving the next tests' queries.
 */
class InMemoryDatabaseRule : ExternalResource() {

    lateinit var db: AppDatabase
        private set

    override fun before() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    override fun after() {
        db.close()
    }
}
