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
import android.widget.ProgressBar;
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
import com.google.ads.mediation.pangle.PangleMediationAdapter;
import com.bytedance.sdk.openadsdk.api.PAGConstant;
import com.google.firebase.analytics.FirebaseAnalytics;

// UMP imports
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

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
  private static final String REWARDED_AD_UNIT_ID = "ca-app-pub-8655759847032068/3445692813";

  private AppCompatButton watchAdButton;
  private ProgressBar watchAdProgress;

  private final GptPreferenceHelper sharedPrefHelper;

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
    sharedPrefHelper = new GptPreferenceHelper(activity);
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
    watchAdButton = findViewById(R.id.watch_ad_button);
    watchAdProgress = findViewById(R.id.watch_ad_progress);
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

    watchAdButton.setOnClickListener(v -> {
      watchAdButton.setEnabled(false);
      watchAdButton.setVisibility(View.GONE);
      if (watchAdProgress != null) {
        watchAdProgress.setVisibility(View.VISIBLE);
      }
      // Wrap rewarded ad flow with UMP consent like PagerActivity
      requestConsentThenLoadRewarded();
    });
  }

  private void highlightSelectedPlan(LinearLayout selectedLayout, LinearLayout unselectedLayout) {
    selectedLayout.setBackgroundResource(R.drawable.selected_plan_background);
    unselectedLayout.setBackgroundResource(R.drawable.unselected_plan_background);
  }

  private void requestConsentThenLoadRewarded() {
    final Activity activity = getActivityFromContext(getContext());
    if (activity == null) {
      Log.w("SubscriptionDialog", "No activity available for consent flow.");
      loadRewardedAd();
      return;
    }

    // (b) Request-Parameter
    ConsentRequestParameters params = new ConsentRequestParameters.Builder()
        .setTagForUnderAgeOfConsent(false)
        .build();

    // (c) Info abrufen
    ConsentInformation consentInformation = UserMessagingPlatform.getConsentInformation(activity);
    consentInformation
        .requestConsentInfoUpdate(
            activity,
            params,
            () -> {
              syncPangleConsent(consentInformation.getConsentStatus());
              // (d) Falls nötig, Consent-Form automatisch laden & zeigen
              UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                  activity,
                  formError -> {
                    // formError != null ist kein Blocker; Ads dürfen (non-personalized) geladen werden
                    ConsentInformation updatedConsentInformation =
                        UserMessagingPlatform.getConsentInformation(activity);
                    syncPangleConsent(updatedConsentInformation.getConsentStatus());
                    loadRewardedAd();
                  }
              );
            },
            requestError -> {
              // Fallback: bei Fehler unpersonalisiert laden
              Log.w("UMP", "Consent request failed: " + requestError.getMessage());
              syncPangleConsent(consentInformation.getConsentStatus());
              loadRewardedAd();
            }
        );
  }

  private void syncPangleConsent(@ConsentInformation.ConsentStatus int consentStatus) {
    int pangleConsent = mapToPangleConsent(consentStatus);
    PangleMediationAdapter.setGDPRConsent(pangleConsent);
    sharedPrefHelper.setPangleGdprConsent(pangleConsent);
  }

  private int mapToPangleConsent(@ConsentInformation.ConsentStatus int consentStatus) {
    switch (consentStatus) {
      case ConsentInformation.ConsentStatus.OBTAINED:
        return PAGConstant.PAGGDPRConsentType.PAG_GDPR_CONSENT_TYPE_CONSENT;
      case ConsentInformation.ConsentStatus.REQUIRED:
        return PAGConstant.PAGGDPRConsentType.PAG_GDPR_CONSENT_TYPE_NO_CONSENT;
      case ConsentInformation.ConsentStatus.NOT_REQUIRED:
        return PAGConstant.PAGGDPRConsentType.PAG_GDPR_CONSENT_TYPE_DEFAULT;
      case ConsentInformation.ConsentStatus.UNKNOWN:
        return PAGConstant.PAGGDPRConsentType.PAG_GDPR_CONSENT_TYPE_NO_CONSENT;
      default:
        return PAGConstant.PAGGDPRConsentType.PAG_GDPR_CONSENT_TYPE_DEFAULT;
    }
  }

  private void loadRewardedAd() {
    AdRequest adRequest = new AdRequest.Builder().build();
    RewardedAd.load(getContext(), REWARDED_AD_UNIT_ID, adRequest, new RewardedAdLoadCallback() {
      @Override
      public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
        rewardedAd = null;
        Log.e("SubscriptionDialog", "Ad failed to load: " + loadAdError.getMessage());
        Toast.makeText(getContext(), R.string.ad_not_available, Toast.LENGTH_SHORT).show();
        resetWatchAdUi();
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
      Toast.makeText(getContext(), R.string.ad_not_available, Toast.LENGTH_SHORT).show();
      resetWatchAdUi();
      return;
    }

    rewardedAd.setFullScreenContentCallback(new FullScreenContentCallback() {
      @Override
      public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
        rewardedAd = null;
        Log.e("SubscriptionDialog", "Ad failed to show: " + adError.getMessage());
        Toast.makeText(getContext(), R.string.ad_not_available, Toast.LENGTH_SHORT).show();
        resetWatchAdUi();
      }

      @Override
      public void onAdDismissedFullScreenContent() {
        rewardedAd = null;
        resetWatchAdUi();
      }
    });

    rewardedAd.show(activity, rewardItem -> {
      if (activity instanceof DefaultMessagesActivity) {
        ((DefaultMessagesActivity) activity).resetDailyChatLimit();
      }
      dismiss();
      Toast.makeText(activity, "Limit reset! You can now send more messages.", Toast.LENGTH_SHORT).show();
    });
  }

  private void resetWatchAdUi() {
    if (watchAdProgress != null) {
      watchAdProgress.setVisibility(View.GONE);
    }
    if (watchAdButton != null) {
      watchAdButton.setEnabled(true);
      watchAdButton.setVisibility(View.VISIBLE);
    }
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
