package com.appcoholic.notifications

import com.google.common.truth.Truth.assertThat
import com.quran.analytics.AnalyticsProvider
import org.junit.Test

class NotificationAnalyticsTest {
  private val events = mutableListOf<Pair<String, Map<String, Any>>>()
  private val provider = AnalyticsProvider { name, params -> events.add(name to params) }
  private val analytics = NotificationAnalytics(provider)

  @Test fun posted_logs_type_and_int_delay() {
    analytics.posted("daily_verse", 120)

    assertThat(events).hasSize(1)
    val (name, params) = events.single()
    assertThat(name).isEqualTo("notification_posted")
    assertThat(params["notification_type"]).isEqualTo("daily_verse")
    assertThat(params["delay_minutes"]).isEqualTo(120)
    // Guard the upstream FirebaseProvider bug: Long values are silently dropped.
    assertThat(params["delay_minutes"]).isInstanceOf(Int::class.javaObjectType)
  }

  @Test fun skipped_logs_type_and_reason() {
    analytics.skipped("reengage", "quiet_hours")

    val (name, params) = events.single()
    assertThat(name).isEqualTo("notification_skipped")
    assertThat(params["notification_type"]).isEqualTo("reengage")
    assertThat(params["reason"]).isEqualTo("quiet_hours")
  }

  @Test fun opened_logs_type_and_source() {
    analytics.opened("daily_verse")

    val (name, params) = events.single()
    assertThat(name).isEqualTo("notification_opened")
    assertThat(params["notification_type"]).isEqualTo("daily_verse")
    assertThat(params["source"]).isEqualTo("notification")
  }
}
