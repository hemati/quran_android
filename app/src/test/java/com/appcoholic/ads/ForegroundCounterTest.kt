package com.appcoholic.ads

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ForegroundCounterTest {
  @Test fun cold_start_is_not_a_warm_foreground() {
    val c = ForegroundCounter()
    c.onStart() // cold start (first ever foreground)
    assertThat(c.consumeWarmForeground()).isFalse()
  }

  @Test fun real_background_then_foreground_is_warm() {
    val c = ForegroundCounter()
    c.onStart()          // cold start
    c.onStop()           // app to background (count -> 0)
    c.onStart()          // back to foreground -> warm
    assertThat(c.consumeWarmForeground()).isTrue()
  }

  @Test fun warm_flag_is_consumed_once() {
    val c = ForegroundCounter()
    c.onStart(); c.onStop(); c.onStart()
    assertThat(c.consumeWarmForeground()).isTrue()
    assertThat(c.consumeWarmForeground()).isFalse()
  }

  @Test fun intra_app_second_activity_is_not_a_foreground_transition() {
    val c = ForegroundCounter()
    c.onStart()          // cold start (activity A), count 0->1
    c.onStart()          // activity B starts before A stops, count 1->2 (NOT a 0->1 transition)
    c.onStop()           // A stops, 2->1
    assertThat(c.consumeWarmForeground()).isFalse() // never left the app
  }

  @Test fun returns_warm_again_after_each_full_background_cycle() {
    val c = ForegroundCounter()
    c.onStart()                  // cold
    c.onStop(); c.onStart()      // warm #1
    assertThat(c.consumeWarmForeground()).isTrue()
    c.onStop(); c.onStart()      // warm #2
    assertThat(c.consumeWarmForeground()).isTrue()
  }
}
