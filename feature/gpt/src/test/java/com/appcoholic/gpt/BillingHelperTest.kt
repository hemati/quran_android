package com.appcoholic.gpt

import android.app.Activity
import com.android.billingclient.api.Purchase
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BillingHelperTest {

  @After
  fun tearDown() {
    BillingHelper.resetInstanceForTest()
  }

  @Test
  fun `an older activity ending the connection leaves the current one connected`() {
    // a recreated activity (rotation, theme change) registers before the old one is destroyed
    val oldActivity = newActivity()
    val currentActivity = newActivity()
    BillingHelper.getInstance(oldActivity, NoOpListener)
    val helper = BillingHelper.getInstance(currentActivity, NoOpListener)

    helper.endConnection(oldActivity)

    assertTrue(helper.hasBillingClient())
    helper.queryPurchases() // threw a NullPointerException in 2.11.0
  }

  @Test
  fun `the current activity ending the connection closes the client`() {
    val activity = newActivity()
    val helper = BillingHelper.getInstance(activity, NoOpListener)

    helper.endConnection(activity)

    assertFalse(helper.hasBillingClient())
  }

  @Test
  fun `queries after the connection ended start a new client`() {
    val activity = newActivity()
    val helper = BillingHelper.getInstance(activity, NoOpListener)
    helper.endConnection(activity)

    helper.queryPurchases()

    assertTrue(helper.hasBillingClient())
  }

  private fun newActivity(): Activity =
    Robolectric.buildActivity(Activity::class.java).setup().get()

  private object NoOpListener : BillingHelper.BillingUpdatesListener {
    override fun onBillingClientSetupFinished() = Unit
    override fun onPurchasesUpdated(purchases: List<Purchase>) = Unit
    override fun onPurchaseAcknowledged(purchase: Purchase) = Unit
    override fun onPurchaseError(error: String) = Unit
  }
}
