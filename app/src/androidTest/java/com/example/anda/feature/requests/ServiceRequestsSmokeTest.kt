package com.example.anda.feature.requests

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.example.anda.R
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServiceRequestsSmokeTest {

    private fun resetServiceRequestPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("service_requests_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(ServiceRequestsPreferencePolicy.sortPreferenceKey(technicianMode = false), false)
            .putBoolean(ServiceRequestsPreferencePolicy.sortPreferenceKey(technicianMode = true), false)
            .putString(ServiceRequestsPreferencePolicy.statusPreferenceKey(technicianMode = false), "all")
            .putString(ServiceRequestsPreferencePolicy.statusPreferenceKey(technicianMode = true), "all")
            .putString(ServiceRequestsPreferencePolicy.cnpjFilterPreferenceKey(technicianMode = false), "")
            .putString(ServiceRequestsPreferencePolicy.cnpjFilterPreferenceKey(technicianMode = true), "")
            .commit()
    }

    @Test
    fun smoke_checkFiltersAndToggleSort() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val updatedLabel = context.getString(R.string.service_request_sort_updated)
        val latestLabel = context.getString(R.string.service_request_sort_latest_action)

        resetServiceRequestPreferences()

        val scenario = ActivityScenario.launch(ServiceRequestsActivity::class.java)
        try {
            onView(withId(R.id.filterAllButton)).check(matches(isDisplayed()))
            onView(withId(R.id.filterOpenButton)).check(matches(isDisplayed()))
            onView(withId(R.id.filterActiveButton)).check(matches(isDisplayed()))

            onView(withId(R.id.sortLatestActionButton)).check(matches(withText(updatedLabel)))

            // Toggle sort and assert deterministic state transition.
            onView(withId(R.id.sortLatestActionButton)).perform(scrollTo(), click())
            onView(withId(R.id.sortLatestActionButton)).check(matches(withText(latestLabel)))
        } finally {
            scenario.close()
        }
    }
}

