package com.appcoholic.ads

import com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdPrivacyOptionsTest {

  @Test
  fun `the settings entry shows only where UMP requires it`() {
    assertThat(AdPrivacyOptions.isRequired(PrivacyOptionsRequirementStatus.REQUIRED)).isTrue()
    assertThat(AdPrivacyOptions.isRequired(PrivacyOptionsRequirementStatus.NOT_REQUIRED)).isFalse()
    // before the first consent update the status is unknown: don't offer a form that can't load
    assertThat(AdPrivacyOptions.isRequired(PrivacyOptionsRequirementStatus.UNKNOWN)).isFalse()
  }
}
