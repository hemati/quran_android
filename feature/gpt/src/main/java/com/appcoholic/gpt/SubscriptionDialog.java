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
import android.content.Context;
import android.content.ContextWrapper;

import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
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

  private BillingHelper billingHelper;

  private FirebaseAnalytics firebaseAnalytics;

  private OnSubscriptionStatusChangedListener subscriptionStatusChangedListener;

  private RewardedAd rewardedAd;
  private static final String REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917";

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
    billingHelper.queryPurchases();
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
    AppCompatButton watchAdButton = findViewById(R.id.watch_ad_button);
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

    watchAdButton.setOnClickListener(v -> loadRewardedAd());
  }

  private void highlightSelectedPlan(LinearLayout selectedLayout, LinearLayout unselectedLayout) {
    selectedLayout.setBackgroundResource(R.drawable.selected_plan_background);
    unselectedLayout.setBackgroundResource(R.drawable.unselected_plan_background);
  }

  private void loadRewardedAd() {
    AdRequest adRequest = new AdRequest.Builder().build();
    RewardedAd.load(getContext(), REWARDED_AD_UNIT_ID, adRequest, new RewardedAdLoadCallback() {
      @Override
      public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
        rewardedAd = null;
        Log.e("SubscriptionDialog", "Ad failed to load: " + loadAdError.getMessage());
        Toast.makeText(getContext(), "Ad failed to load", Toast.LENGTH_SHORT).show();
      }

      @Override
      public void onAdLoaded(@NonNull RewardedAd ad) {
        rewardedAd = ad;
        showRewardedAd();
      }
    });
  }

  private void showRewardedAd() {
    final Activity activity = getActivityFromContext(getContext());
    if (activity == null || rewardedAd == null) {
      Log.d("SubscriptionDialog", "Unable to show rewarded ad - missing activity or ad not ready.");
      return;
    }

    rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
      @Override
      public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
        rewardedAd = null;
        Log.e("SubscriptionDialog", "Ad failed to show: " + adError.getMessage());
      }

      @Override
      public void onAdDismissedFullScreenContent() {
        rewardedAd = null;
      }
    });

    rewardedAd.show(activity, rewardItem -> {
      if (activity instanceof DefaultMessagesActivity) {
        ((DefaultMessagesActivity) activity).resetDailyChatLimit();
      }
    });
  }

  private Activity getActivityFromContext(Context context) {
    while (context instanceof ContextWrapper) {
      if (context instanceof Activity) {
        return (Activity) context;
      }
      context = ((ContextWrapper) context).getBaseContext();
    }
    return null;
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
    isGPTPro = true;
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
