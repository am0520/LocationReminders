package com.udacity.project4.locationreminders.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import kotlinx.coroutines.ExperimentalCoroutinesApi;
import kotlinx.coroutines.test.runTest
import net.bytebuddy.pool.TypePool.Empty
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Test

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
//Unit test the DAO
@SmallTest
class RemindersDaoTest {
    // Executes each task synchronously using Architecture Components.
    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: RemindersDatabase

    // Using an in-memory database so that the information stored here disappears when the
    // process is killed.
    @Before
    fun initDb() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RemindersDatabase::class.java
        ).build()
    }

    // Close and clean-up database after each test
    @After
    fun closeDb() = database.close()

    @Test
    fun insertReminderAndGetById() = runTest {
        // GIVEN - Insert a reminder.
        val reminder = ReminderDTO("title", "description", "paris", 1.1, 2.2)
        database.reminderDao().saveReminder(reminder)

        // WHEN - Get the reminder by id from the database.
        val loaded = database.reminderDao().getReminderById(reminder.id)

        // THEN - The loaded data contains the expected values.
        assertThat<ReminderDTO>(loaded as ReminderDTO, Matchers.notNullValue())
        assertThat(loaded.id, `is`(reminder.id))
        assertThat(loaded.title, `is`(reminder.title))
        assertThat(loaded.description, `is`(reminder.description))
        assertThat(loaded.location, `is`(reminder.location))
        assertThat(loaded.latitude, `is`(reminder.latitude))
        assertThat(loaded.longitude, `is`(reminder.longitude))
    }

    @Test
    fun getAllReminders() = runTest {
        // GIVEN - Insert more than one reminder.
        val reminders = arrayOf(
            ReminderDTO("title", "description", "paris", 1.1, 2.2),
            ReminderDTO("title2", "description2", "paris2", 1.21, 2.22),
            ReminderDTO("title3", "description3", "paris3", 1.31, 2.24)
        )
        reminders.forEach { reminder ->
            database.reminderDao().saveReminder(reminder)
        }

        // WHEN - Get all reminders from the database.
        val loaded = database.reminderDao().getReminders()

        // THEN - The loaded data count is the size of inserted reminders.
        assertThat(loaded.size, `is`(3))
    }

    @Test
    fun deleteAllReminders() = runTest {
        // GIVEN - Insert more some reminders.
        val reminders = arrayOf(
            ReminderDTO("title", "description", "paris", 1.1, 2.2),
            ReminderDTO("title2", "description2", "paris2", 1.21, 2.22),
            ReminderDTO("title3", "description3", "paris3", 1.31, 2.24)
        )
        reminders.forEach { reminder ->
            database.reminderDao().saveReminder(reminder)
        }

        // WHEN - Delete all reminders from the database.
        database.reminderDao().deleteAllReminders()

        // THEN - The database is empty.
        val loaded = database.reminderDao().getReminders()
        assertThat(loaded, Matchers.empty())
    }
}
