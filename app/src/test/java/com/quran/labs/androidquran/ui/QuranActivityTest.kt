package com.quran.labs.androidquran.ui

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.matcher.ViewMatchers
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.base.TestApplication
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config


@Config(application = TestApplication::class, sdk = [33])
@RunWith(RobolectricTestRunner::class)
class QuranActivityTest {
  private var scenario: ActivityScenario<QuranActivity>? = null

  @Before
  fun setup() {
    // Rattil: the first start shows an onboarding tour that covers the list; mark it as seen
    // before launching (an ActivityScenarioRule would launch before this runs)
    PreferenceManager.getDefaultSharedPreferences(ApplicationProvider.getApplicationContext<Context>())
      .edit()
      .putBoolean("tap_target_shown", true)
      .commit()
    Intents.init()
    scenario = ActivityScenario.launch(QuranActivity::class.java)
  }

  @After
  fun teardown() {
    scenario?.close()
    Intents.release()
  }

  @Test
  fun testClickingOnSuraOnListViewNavigatesToSura() {
    //given
    // Rattil: the app is called Rattil, not "Quran ..."
    Espresso.onView(ViewMatchers.withText(R.string.app_name))
        .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))

    //when
    Espresso.onView(
        Matchers.allOf(
            ViewMatchers.withText("Surah Al-Fātihah"), ViewMatchers.isCompletelyDisplayed()
        )
    ).perform(ViewActions.click())

    //then
    Intents.intended(IntentMatchers.hasComponent(PagerActivity::class.java.name))
    Espresso.onView(ViewMatchers.withText("Surah Al-Fātihah"))
        .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
  }
}
