package com.udacity.project4.locationreminders.data.local

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.udacity.project4.locationreminders.data.ReminderDataSource
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.data.dto.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
//Medium Test to test the repository
@MediumTest
class RemindersLocalRepositoryTest {
    // Executes each task synchronously using Architecture Components.
    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var localRepository: RemindersLocalRepository
    private lateinit var database: RemindersDatabase

    @Before
    fun setup() {
        // Using an in-memory database for testing, because it doesn't survive killing the process.
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RemindersDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        localRepository =
            RemindersLocalRepository(
                database.reminderDao(), Dispatchers.Main
            )
    }

    @After
    fun cleanUp() = database.close()

    @Test
    fun saveReminder_retrievesReminder() = runTest {
        // GIVEN - A new reminder saved in the database.
        val reminder = ReminderDTO(
            "title", "description", "moscow", 2.0, 1.0
        )
        localRepository.saveReminder(reminder)

        // WHEN  - Reminder retrieved by ID.
        val result = localRepository.getReminder(reminder.id)

        // THEN - Same reminder is returned.
        assertThat(result, instanceOf(Result.Success::class.java))
        result as Result.Success
        assertThat(result.data.title, `is`("title"))
        assertThat(result.data.description, `is`("description"))
        assertThat(result.data.location, `is`("moscow"))
        assertThat(result.data.latitude, `is`(2.0))
        assertThat(result.data.longitude, `is`(1.0))
    }

    @Test
    fun deleteAllReminders_returnsEmpty() = runTest {
        // GIVEN - Insert more some reminders.
        val reminders = arrayOf(
            ReminderDTO("title", "description", "paris", 1.1, 2.2),
            ReminderDTO("title2", "description2", "paris2", 1.21, 2.22),
            ReminderDTO("title3", "description3", "paris3", 1.31, 2.24)
        )
        reminders.forEach { reminder ->
            localRepository.saveReminder(reminder)
        }

        // WHEN - Delete all reminders from the repository.
        localRepository.deleteAllReminders()

        // THEN - getReminders returns empty.
        val result = localRepository.getReminders()
        assertThat(result, instanceOf(Result.Success::class.java))
        result as Result.Success
        assertThat(result.data, empty())
    }

    /*
    * Save a reminder, delete it, then attempt to retrieve it
    * Assert Error of not found is returned
    * */
    @Test
    fun getNonExistingReminder_returnsError() = runTest {
        // GIVEN - Insert a reminder.
        val reminder = ReminderDTO("title3", "description3", "paris3", 1.31, 2.24)
        localRepository.saveReminder(reminder)

        // WHEN - Delete all reminders from the repository.
        localRepository.deleteAllReminders()

        // THEN - get the reminder returns "Reminder not found!" Error.
        val result = localRepository.getReminder(reminder.id)
        assertThat(result, instanceOf(Result.Error::class.java))
        result as Result.Error
        assertThat(result.message, `is`("Reminder not found!"))
    }
}
