package com.appcoholic.ads

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import timber.log.Timber

/**
 * Lets users change their ad consent later, as the UMP consent form promises ("a link or button
 * in the app menu") and as Google requires wherever the form applies (EEA, UK, Switzerland).
 */
object AdPrivacyOptions {

  fun isRequired(context: Context): Boolean =
    isRequired(UserMessagingPlatform.getConsentInformation(context).privacyOptionsRequirementStatus)

  /**
   * The status is UNKNOWN in a new process until the consent info is updated, even though UMP
   * stored it last time, so screens that open before any ad screen ask first.
   */
  fun refresh(activity: Activity, onResult: (required: Boolean) -> Unit) {
    val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
    consentInformation.requestConsentInfoUpdate(
      activity,
      ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(false).build(),
      { onResult(isRequired(consentInformation.privacyOptionsRequirementStatus)) },
      { error ->
        Timber.w("Consent info update failed: %s", error.message)
        onResult(isRequired(consentInformation.privacyOptionsRequirementStatus))
      }
    )
  }

  fun isRequired(status: PrivacyOptionsRequirementStatus): Boolean =
    status == PrivacyOptionsRequirementStatus.REQUIRED

  fun show(activity: Activity) {
    UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
      if (formError != null) {
        Timber.w("Ad privacy options form failed: %s", formError.message)
      }
    }
  }
}
