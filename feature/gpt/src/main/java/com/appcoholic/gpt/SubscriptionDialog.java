package com.appcoholic.gpt;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatButton;

import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SubscriptionDialog extends Dialog implements BillingHelper.BillingUpdatesListener{

  private SubscriptionPlan selectedPlan = SubscriptionPlan.YEARLY;
  private boolean isGPTPro;

  private enum SubscriptionPlan {
    MONTHLY, YEARLY
  }

  private String monthlyPlanPrice = "0.99$";
  private String yearlyPlanPrice = "8.49$";

  private boolean isPriceUpUpdated = false;
  private BillingHelper billingHelper;

  private FirebaseAnalytics firebaseAnalytics;

  private OnSubscriptionStatusChangedListener subscriptionStatusChangedListener;

  public interface OnSubscriptionStatusChangedListener {
    void onSubscriptionStatusChanged(boolean subscribed);
  }

  public void setOnSubscriptionStatusChangedListener(OnSubscriptionStatusChangedListener listener) {
    this.subscriptionStatusChangedListener = listener;
  }

  public SubscriptionDialog(@NonNull Activity activity) {
    super(activity);

    requestWindowFeature(Window.FEATURE_NO_TITLE);
    firebaseAnalytics = FirebaseAnalytics.getInstance(activity);

    if (getWindow() != null) {
      getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
      getWindow().setLayout(
          android.view.ViewGroup.LayoutParams.MATCH_PARENT,
          android.view.ViewGroup.LayoutParams.MATCH_PARENT
      );
      getWindow().getAttributes().windowAnimations = R.style.DialogAnimation;
    }

    setCancelable(false);
    billingHelper = BillingHelper.getInstance(activity, this);
  }


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    View view = LayoutInflater.from(getContext()).inflate(R.layout.subscription_overlay, null);
    setContentView(view);

    setupViews();
  }

  @Override
  protected void onStart() {
    super.onStart();
  }

  private void setupViews() {
    final LinearLayout monthlyPlanLayout = findViewById(R.id.monthly_plan_card);
    final LinearLayout yearlyPlanLayout = findViewById(R.id.yearly_plan_card);

    AppCompatButton upgradeButton = findViewById(R.id.upgrade_button);
    TextView closeOverlay = findViewById(R.id.close_overlay);

    highlightSelectedPlan(yearlyPlanLayout, monthlyPlanLayout);

    monthlyPlanLayout.setOnClickListener(view -> {
      selectedPlan = SubscriptionPlan.MONTHLY;
      highlightSelectedPlan(monthlyPlanLayout, yearlyPlanLayout);
    });

    yearlyPlanLayout.setOnClickListener(view -> {
      selectedPlan = SubscriptionPlan.YEARLY;
      highlightSelectedPlan(yearlyPlanLayout, monthlyPlanLayout);
    });

    upgradeButton.setOnClickListener(view -> initiateSubscriptionPurchase(
        selectedPlan == SubscriptionPlan.MONTHLY ? "qurangpt_subscription" : "qurangpt_subscription_yearly"));

    closeOverlay.setOnClickListener(view -> dismiss());
  }

  private void highlightSelectedPlan(LinearLayout selectedLayout, LinearLayout unselectedLayout) {
    selectedLayout.setBackgroundResource(R.drawable.selected_plan_background);
    unselectedLayout.setBackgroundResource(R.drawable.unselected_plan_background);
  }

  @Override
  public void onBillingClientSetupFinished() {
    List<String> productIds = Arrays.asList("qurangpt_subscription", "qurangpt_subscription_yearly");
    billingHelper.queryProductDetails(productIds, productDetailsList -> {
      for (ProductDetails productDetails : productDetailsList) {
        if (productDetails.getProductId().equals("qurangpt_subscription")) {
          monthlyPlanPrice = extractLocalizedPrice(productDetails);
        } else if (productDetails.getProductId().equals("qurangpt_subscription_yearly")) {
          yearlyPlanPrice = extractLocalizedPrice(productDetails);
        }
      }
      isPriceUpUpdated = true;
      updatePriceUI();
    });
    billingHelper.queryPurchases();
  }

  private void initiateSubscriptionPurchase(String subscriptionId) {
    billingHelper.queryProductDetails(Collections.singletonList(subscriptionId), productDetailsList -> {
      for (ProductDetails productDetails : productDetailsList) {
        if (productDetails.getProductId().equals(subscriptionId)) {
          String offerToken = productDetails.getSubscriptionOfferDetails().get(0).getOfferToken();
          billingHelper.launchBillingFlow(productDetails, offerToken);
          return;
        }
      }
    });
  }

  private String extractLocalizedPrice(ProductDetails productDetails) {
    List<ProductDetails.SubscriptionOfferDetails> offerDetails = productDetails.getSubscriptionOfferDetails();
    if (offerDetails != null && !offerDetails.isEmpty()) {
      for (ProductDetails.SubscriptionOfferDetails offer : offerDetails) {
        List<ProductDetails.PricingPhase> pricingPhases = offer.getPricingPhases().getPricingPhaseList();
        ProductDetails.PricingPhase regularPricingPhase = null;

        // Iterate through the phases to find the first non-trial/non-introductory phase
        for (ProductDetails.PricingPhase pricingPhase : pricingPhases) {
          if (pricingPhase.getBillingPeriod().equals("P1M") || pricingPhase.getBillingPeriod().equals("P1Y")) {
            // Assuming that the regular billing period is either 1 month (P1M) or 1 year (P1Y)
            regularPricingPhase = pricingPhase;
            break;
          }
        }

        // Return the formatted price if a regular pricing phase is found
        if (regularPricingPhase != null) {
          return regularPricingPhase.getFormattedPrice();
        }
      }
    }
    return "Unavailable";
  }


  private void updatePriceUI() {
    TextView monthlyPriceTextView = findViewById(R.id.monthly_plan_price);
    TextView yearlyPriceTextView = findViewById(R.id.yearly_plan_price);

    monthlyPriceTextView.setText(monthlyPlanPrice);
    yearlyPriceTextView.setText(yearlyPlanPrice);
  }

  @Override
  public void onPurchasesUpdated(List<Purchase> purchases) {
    for (Purchase purchase : purchases) {
      billingHelper.acknowledgePurchase(purchase);
      this.isGPTPro = true;
      if (subscriptionStatusChangedListener != null) {
        subscriptionStatusChangedListener.onSubscriptionStatusChanged(true);
      }
      dismiss();
    }
  }

  @Override
  public void onPurchaseAcknowledged(Purchase purchase) {
    // Handle acknowledgment if necessary
    Log.d("SubscriptionDialog", "Purchase acknowledged: " + purchase.getOrderId());
  }

  @Override
  public void onPurchaseError(String error) {
    Toast.makeText(getContext(), "Purchase error: " + error, Toast.LENGTH_SHORT).show();
  }

  @Override
  public void show() {
    if (!isGPTPro) {
      super.show();
      firebaseAnalytics.logEvent("subscription_overlay_shown", null);
      updatePriceUI();
    }
  }

  @Override
  public void dismiss() {
    super.dismiss();
  }
}
