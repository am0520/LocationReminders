package com.udacity.project4.locationreminders.reminderslist

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.udacity.project4.locationreminders.MainCoroutineRule
import com.udacity.project4.locationreminders.data.FakeDataSource
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.getOrAwaitValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.pauseDispatcher
import kotlinx.coroutines.test.resumeDispatcher
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@ExperimentalCoroutinesApi
@SmallTest
class RemindersListViewModelTest {
    // Set the main coroutines dispatcher for unit testing.
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    // Executes each task synchronously using Architecture Components.
    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    // Use a fake repository to be injected into the viewmodel
    private lateinit var fakeRepository: FakeDataSource

    // Class under test
    private lateinit var remindersViewModel: RemindersListViewModel

    @Before
    fun setupViewModel() {
        stopKoin()

        fakeRepository = FakeDataSource()

        remindersViewModel = RemindersListViewModel(
            ApplicationProvider.getApplicationContext(),
            fakeRepository
        )
    }

    @After
    fun tearDown() = runTest {
        fakeRepository.deleteAllReminders()
    }

    @Test
    fun loadingReminders_showsLoading() = mainCoroutineRule.runTest {
        // Given repository contains data
        val reminder1 = ReminderDTO(
            "title1", "description1", "paris", 1.211, 2.22
        )
        val reminder2 = ReminderDTO(
            "title2", "description2", "moscow", 1.21, 4.22
        )
        fakeRepository.saveReminder(reminder1)
        fakeRepository.saveReminder(reminder2)

        // When viewmodel is loading reminders
        // Pausing to run asynchronously to check loading reminders
        mainCoroutineRule.pauseDispatcher()

        remindersViewModel.loadReminders()

        // Then showLoading is true and false when finished
        assertThat(remindersViewModel.showLoading.getOrAwaitValue(), `is`(true))

        mainCoroutineRule.resumeDispatcher()

        assertThat(remindersViewModel.showLoading.getOrAwaitValue(), `is`(false))
    }

    @Test
    fun loadingErrorResult_showsError() = mainCoroutineRule.runTest {
        // Given repository is set to return error
        val reminder1 = ReminderDTO(
            "title1", "description1", "paris", 1.211, 2.22
        )
        fakeRepository.saveReminder(reminder1)
        fakeRepository.setReturnError(true)

        // When viewmodel is loading reminders
        remindersViewModel.loadReminders()

        // Then error is displayed
        assertThat(
            remindersViewModel.showSnackBar.getOrAwaitValue(),
            `is`("Test exception")
        )
    }

    @Test
    fun dataIsEmpty_showsNoData() = mainCoroutineRule.runTest {
        // Given repository data empty
        fakeRepository.deleteAllReminders()

        // When viewmodel is loading reminders
        remindersViewModel.loadReminders()

        // Then showNoData becomes true
        assertThat(
            remindersViewModel.showNoData.getOrAwaitValue(), `is`(true)
        )
    }

}
