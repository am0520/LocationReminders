package com.udacity.project4.locationreminders.reminderslist

import android.os.Bundle
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.udacity.project4.locationreminders.data.ReminderDataSource
import com.udacity.project4.locationreminders.data.dto.ReminderDTO
import com.udacity.project4.locationreminders.data.local.FakeAndroidDataSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import com.udacity.project4.R
import org.hamcrest.core.IsNot.not
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.test.AutoCloseKoinTest
import org.koin.test.get
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

@RunWith(AndroidJUnit4::class)
@ExperimentalCoroutinesApi
//UI Testing
@MediumTest
class ReminderListFragmentTest : AutoCloseKoinTest() {

    private lateinit var repository: ReminderDataSource

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    /*
    * We will use Koin in testing as a service locator. Initializing Koin code here.
    * */
    @Before
    fun initRepository() {
        // Stop the original app Koin
        stopKoin()
        // Create the Koin module
        val myModule = module {
            viewModel {
                RemindersListViewModel(
                    get(),
                    get() as ReminderDataSource
                )
            }
            // The fake data source as the repository
            single { FakeAndroidDataSource() as ReminderDataSource }
        }
        // Start the Koin module
        startKoin {
            androidContext(getApplicationContext())
            modules(listOf(myModule))
        }

        // Assign the repository
        repository = get()

        // Clear before testing
        runBlocking { repository.deleteAllReminders() }
    }

    /*
    * Test the displayed data on the UI.
    * */
    @Test
    fun reminder_DisplayedInUi() = runTest {
        // GIVEN - Add reminders to the repository
        val reminder1 = ReminderDTO(
            "title1", "description1", "paris", 1.4, 2.3
        )
        val reminder2 = ReminderDTO(
            "title2", "description2", "alaska", 5.6, 7.0
        )
        repository.saveReminder(reminder1)
        repository.saveReminder(reminder2)

        // WHEN - Reminder list fragment launched to display reminders
        val scenario = launchFragmentInContainer<ReminderListFragment>(Bundle(), R.style.AppTheme)
        val navController = mock(NavController::class.java)
        scenario.onFragment {
            Navigation.setViewNavController(it.view!!, navController)
        }

        // THEN - Reminder details are displayed on the screen
        onView(withText(reminder1.title)).check(matches(isDisplayed()))
        onView(withText(reminder1.description)).check(matches(isDisplayed()))
        onView(withText(reminder1.location)).check(matches(isDisplayed()))
        // Check reminder2
        onView(withText(reminder2.title)).check(matches(isDisplayed()))
        onView(withText(reminder2.description)).check(matches(isDisplayed()))
        onView(withText(reminder2.location)).check(matches(isDisplayed()))
        // Make sure no data indicator is not visible
        onView(withId(R.id.noDataTextView)).check(matches(not(isDisplayed())))
    }

    /*
    * Test the navigation of the fragments.
    * */
    @Test
    fun clickAddReminderFab_navigateToSaveFragment() {
        // GIVEN - On the reminder list screen
        val scenario = launchFragmentInContainer<ReminderListFragment>(Bundle(), R.style.AppTheme)
        val navController = mock(NavController::class.java)
        scenario.onFragment {
            Navigation.setViewNavController(it.view!!, navController)
        }

        // WHEN - Click on the "+" button
        onView(withId(R.id.addReminderFAB)).perform(click())

        // THEN - Verify that we navigate to the save fragment
        verify(navController).navigate(ReminderListFragmentDirections.toSaveReminder())
    }

    /*
    * Testing for the error messages.
    * */
    @Test
    fun emptyList_DisplayedNoData() = runTest {
        // GIVEN - Delete all reminders
        repository.deleteAllReminders()

        // WHEN - Reminder list fragment launched to display reminders
        val scenario = launchFragmentInContainer<ReminderListFragment>(Bundle(), R.style.AppTheme)

        // THEN - "No Data" is displayed on the screen
        onView(withId(R.id.noDataTextView)).check(matches(isDisplayed()))
        onView(withText(R.string.no_data)).check(matches(isDisplayed()))
    }
}
