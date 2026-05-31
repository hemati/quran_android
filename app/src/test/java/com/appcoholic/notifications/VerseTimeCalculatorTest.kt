package com.appcoholic.notifications

import com.google.common.truth.Truth.assertThat
import java.time.*
import org.junit.Test

class VerseTimeCalculatorTest {
  private val zone = ZoneId.of("Europe/Berlin")
  private fun at(d: String, t: String) = ZonedDateTime.of(LocalDate.parse(d), LocalTime.parse(t), zone).toInstant()

  @Test fun target_later_today_returns_today() {
    val now = at("2026-06-01", "08:00")
    val next = VerseTimeCalculator.nextTrigger(now, zone, hour = 18)
    assertThat(next).isEqualTo(at("2026-06-01", "18:00"))
  }
  @Test fun target_already_passed_returns_tomorrow() {
    val now = at("2026-06-01", "19:00")
    val next = VerseTimeCalculator.nextTrigger(now, zone, hour = 18)
    assertThat(next).isEqualTo(at("2026-06-02", "18:00"))
  }
  @Test fun delay_is_difference_in_millis() {
    val now = at("2026-06-01", "17:00")
    assertThat(VerseTimeCalculator.delayMillis(now, zone, 18)).isEqualTo(Duration.ofHours(1).toMillis())
  }
}
