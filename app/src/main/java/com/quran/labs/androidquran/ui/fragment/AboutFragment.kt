package com.quran.labs.androidquran.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.quran.labs.androidquran.BuildConfig
import com.quran.labs.androidquran.R
import com.google.android.play.core.review.ReviewManagerFactory
import com.quran.labs.androidquran.util.SharedPrefHelper

class AboutFragment : PreferenceFragmentCompat() {

  private lateinit var sharedPrefHelper: SharedPrefHelper

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    addPreferencesFromResource(R.xml.about)

    sharedPrefHelper = SharedPrefHelper(requireContext())

    val flavor = BuildConfig.FLAVOR + "Images"
    val parent = findPreference("aboutDataSources") as PreferenceCategory?
    imagePrefKeys.filter { it != flavor }.map {
      val pref: Preference? = findPreference(it)
      if (pref != null) {
        parent?.removePreference(pref)
      }
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val view = super.onCreateView(inflater, container, savedInstanceState)
    val recyclerView = listView
    recyclerView.clipToPadding = false
    ViewCompat.setOnApplyWindowInsetsListener(recyclerView) { view, windowInsets ->
      val insets = windowInsets.getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
      )
      recyclerView.updateLayoutParams<ViewGroup.LayoutParams> {
        // top, left, right are handled by QuranActivity
        view.setPadding(0, 0, 0, insets.bottom)
      }

      windowInsets
    }
    return view
  }

  override fun onPreferenceTreeClick(preference: Preference): Boolean {
    if (preference.key == "rate_app") {
      showRatingDialog()
      return true
    }
    return super.onPreferenceTreeClick(preference)
  }

  private fun showRatingDialog() {
    val manager = ReviewManagerFactory.create(requireContext())
    val request = manager.requestReviewFlow()
    request.addOnCompleteListener { task ->
      if (task.isSuccessful) {
        val reviewInfo = task.result
        val flow = manager.launchReviewFlow(requireActivity(), reviewInfo)
        flow.addOnCompleteListener { }
      }
      sharedPrefHelper.saveOpenCount(0)
      sharedPrefHelper.saveUsageTime(0)
      sharedPrefHelper.saveLastRatingTime(System.currentTimeMillis())
    }
  }

  companion object {
    private val imagePrefKeys = arrayOf("madaniImages", "naskhImages", "qaloonImages")
  }
}
