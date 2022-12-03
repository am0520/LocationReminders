package com.udacity.project4

import android.app.Application
import android.util.Log
import android.view.View
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.core.app.launchActivity
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingRegistry
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.withDecorView
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.GrantPermissionRule
import com.udacity.project4.locationreminders.RemindersActivity
import com.udacity.project4.locationreminders.data.ReminderDataSource
import com.udacity.project4.locationreminders.data.local.LocalDB
import com.udacity.project4.locationreminders.data.local.RemindersLocalRepository
import com.udacity.project4.locationreminders.reminderslist.RemindersListViewModel
import com.udacity.project4.locationreminders.savereminder.SaveReminderViewModel
import com.udacity.project4.util.DataBindingIdlingResource
import com.udacity.project4.util.monitorActivity
import com.udacity.project4.utils.EspressoIdlingResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.AutoCloseKoinTest
import org.koin.test.get

@RunWith(AndroidJUnit4::class)
@LargeTest
//END TO END test to black box test the app
class RemindersActivityTest :
    AutoCloseKoinTest() {// Extended Koin Test - embed autoclose @after method to close Koin after every test

    private lateinit var repository: ReminderDataSource
    private lateinit var appContext: Application
    private lateinit var decorView: View

    // An Idling Resource that waits for Data Binding to have no pending bindings.
    private val dataBindingIdlingResource = DataBindingIdlingResource()

    @get:Rule
    var backgroundLocationPermission =
        GrantPermissionRule.grant(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    @get:Rule
    var locationPermission =
        GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION)

    /**
     * As we use Koin as a Service Locator Library to develop our code, we'll also use Koin to test our code.
     * at this step we will initialize Koin related code to be able to use it in out testing.
     */
    @Before
    fun init() {
        stopKoin()//stop the original app koin
        appContext = getApplicationContext()
        val myModule = module {
            viewModel {
                RemindersListViewModel(
                    appContext,
                    get() as ReminderDataSource
                )
            }
            single {
                SaveReminderViewModel(
                    appContext,
                    get() as ReminderDataSource
                )
            }
            single { RemindersLocalRepository(get()) as ReminderDataSource }
            single { LocalDB.createRemindersDao(appContext) }
        }
        //declare a new koin module
        startKoin {
            modules(listOf(myModule))
        }
        //Get our real repository
        repository = get()
        //clear the data to start fresh
        runBlocking {
            repository.deleteAllReminders()
        }
    }

    /**
     * Idling resources tell Espresso that the app is idle or busy. This is needed when operations
     * are not scheduled in the main Looper (for example when executed on a different thread).
     */
    @Before
    fun registerIdlingResource() {
        IdlingRegistry.getInstance().register(EspressoIdlingResource.countingIdlingResource)
        IdlingRegistry.getInstance().register(dataBindingIdlingResource)
    }

    /**
     * Unregister your idling resource so it can be garbage collected and does not leak any memory.
     */
    @After
    fun unregisterIdlingResource() {
        IdlingRegistry.getInstance().unregister(EspressoIdlingResource.countingIdlingResource)
        IdlingRegistry.getInstance().unregister(dataBindingIdlingResource)
    }

    @Test
    fun createReminder_checkToast() {
        launchActivity<RemindersActivity>().use { scenario ->
            dataBindingIdlingResource.monitorActivity(scenario)

            scenario.onActivity { activity ->
                decorView = activity.window.decorView
            }

            // click add button
            onView(withId(R.id.addReminderFAB)).perform(click())
            // type title and description
            onView(withId(R.id.reminderTitle)).perform(typeText("title"))
            onView(withId(R.id.reminderDescription)).perform(typeText("description"))
            // hide keyboard
            Espresso.closeSoftKeyboard()
            // click select location
            onView(withId(R.id.selectLocation)).perform(click())

            // wait for the map to load
            runBlocking {
                delay(3000)
            }
            // pick location
            onView(withId(R.id.map)).perform(longClick())
            // tap save
            onView(withId(R.id.save_button)).perform(click())
            // tap save reminder
            onView(withId(R.id.saveReminder)).perform(click())
            // check toast
            onView(withText(R.string.reminder_saved)).inRoot(
                withDecorView(CoreMatchers.not(decorView))
            ).check(matches(isDisplayed()))
            // check list screen
            onView(withText("title")).check(matches(isDisplayed()))
            onView(withText("description")).check(matches(isDisplayed()))
            onView(withText(R.string.no_data)).check(matches(not(isDisplayed())))
        }
    }

    @Test
    fun missTitle_showsTitleErrorSnackBar() {
        launchActivity<RemindersActivity>().use { scenario ->
            dataBindingIdlingResource.monitorActivity(scenario)

            // click add button
            onView(withId(R.id.addReminderFAB)).perform(click())
            // tap save reminder
            onView(withId(R.id.saveReminder)).perform(click())
            // check snackbar for title error
            onView(withId(R.id.snackbar_text))
                .check(matches(withText(R.string.err_enter_title)))
        }
    }

    @Test
    fun missLocation_showsLocationErrorSnackBar() {
        launchActivity<RemindersActivity>().use { scenario ->
            dataBindingIdlingResource.monitorActivity(scenario)

            // click add button
            onView(withId(R.id.addReminderFAB)).perform(click())
            // type title and description
            onView(withId(R.id.reminderTitle)).perform(typeText("title"))
            onView(withId(R.id.reminderDescription)).perform(typeText("description"))
            // hide keyboard
            Espresso.closeSoftKeyboard()
            // tap save reminder
            onView(withId(R.id.saveReminder)).perform(click())
            // check snackbar for location error
            onView(withId(R.id.snackbar_text))
                .check(matches(withText(R.string.err_select_location)))
        }
    }
}
