package com.appcoholic.gpt;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Local preference helper scoped to the GPT feature module.
 *
 * <p>This intentionally mirrors the subset of behaviour that the
 * application level {@code com.quran.labs.androidquran.util.SharedPrefHelper}
 * provides so that we can persist consent across both modules without creating
 * a dependency on the app module.</p>
 */
final class GptPreferenceHelper {

  private static final String PREF_NAME = "AppUsagePref";
  private static final String KEY_PANGLE_GDPR_CONSENT = "pangleGdprConsent";
  private static final int CONSENT_UNSET = Integer.MIN_VALUE;

  private final SharedPreferences sharedPreferences;

  GptPreferenceHelper(Context context) {
    this.sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
  }

  void setPangleGdprConsent(int consentValue) {
    sharedPreferences.edit().putInt(KEY_PANGLE_GDPR_CONSENT, consentValue).apply();
  }

  int getPangleGdprConsent() {
    return sharedPreferences.getInt(KEY_PANGLE_GDPR_CONSENT, CONSENT_UNSET);
  }
}
