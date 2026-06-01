package com.appcoholic.notifications

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

object VerseTimeCalculator {
  fun nextTrigger(now: Instant, zone: ZoneId, hour: Int): Instant {
    val nowZ = now.atZone(zone)
    var target = nowZ.withHour(hour).withMinute(0).withSecond(0).withNano(0)
    if (!target.isAfter(nowZ)) target = target.plusDays(1)
    return target.toInstant()
  }
  fun delayMillis(now: Instant, zone: ZoneId, hour: Int): Long =
    nextTrigger(now, zone, hour).toEpochMilli() - now.toEpochMilli()
}
