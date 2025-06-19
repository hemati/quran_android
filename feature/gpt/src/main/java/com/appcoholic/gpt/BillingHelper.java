package com.appcoholic.gpt;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class BillingHelper {
  private static final String TAG = "BillingHelper";
  private static BillingHelper instance;

  private BillingClient billingClient;
  private WeakReference<Activity> activityRef;
  private BillingUpdatesListener billingUpdatesListener;
  private FirebaseAnalytics firebaseAnalytics;


  public interface BillingUpdatesListener {
    void onBillingClientSetupFinished();
    void onPurchasesUpdated(List<Purchase> purchases);
    void onPurchaseAcknowledged(Purchase purchase);
    void onPurchaseError(String error);
  }

  public interface ProductDetailsResponseListener {
    void onProductDetailsResponse(List<ProductDetails> productDetailsList);
  }

  public BillingHelper(Activity activity, BillingUpdatesListener listener) {
    this.activityRef = new WeakReference<>(activity);
    this.billingUpdatesListener = listener;
    firebaseAnalytics = FirebaseAnalytics.getInstance(activity.getApplicationContext());
    setupBillingClient();
  }


  public static synchronized BillingHelper getInstance(Activity activity, BillingUpdatesListener listener) {
    if (instance == null) {
      instance = new BillingHelper(activity, listener);
    } else {
      instance.activityRef = new WeakReference<>(activity);
      instance.billingUpdatesListener = listener;
    }
    return instance;
  }

  private void setupBillingClient() {
    Activity activity = activityRef.get();
    if (activity == null) return;
    billingClient = BillingClient.newBuilder(activity.getApplicationContext())
        .setListener(this::handlePurchaseUpdate)
        .enablePendingPurchases()
        .build();
  }

  private void startConnection() {
    if (billingClient != null && !billingClient.isReady()) {
      billingClient.startConnection(billingClientStateListener);
    }
  }

  private boolean ensureConnected() {
    if (billingClient == null) {
      setupBillingClient();
    }
    if (!billingClient.isReady()) {
      startConnection();
      return false;
    }
    return true;
  }

  private final BillingClientStateListener billingClientStateListener = new BillingClientStateListener() {
    @Override
    public void onBillingServiceDisconnected() {
      Log.e(TAG, "Billing service disconnected");
      // Optionally implement retry logic
    }

    @Override
    public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
      if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
        Log.d(TAG, "Billing client setup finished");
        if (billingUpdatesListener != null) {
          billingUpdatesListener.onBillingClientSetupFinished();
          firebaseAnalytics.logEvent("billing_setup_finished", null);
        }
      } else {
        String errorMsg = "Billing setup failed: " + billingResult.getDebugMessage();
        Log.e(TAG, errorMsg);
        firebaseAnalytics.logEvent("billing_setup_failed", null);
        if (billingUpdatesListener != null) {
          billingUpdatesListener.onPurchaseError(errorMsg);
        }
      }
    }
  };

  private void handlePurchaseUpdate(BillingResult billingResult, List<Purchase> purchases) {
    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
      Log.d(TAG, "Purchases updated");
      if (billingUpdatesListener != null) {
        billingUpdatesListener.onPurchasesUpdated(purchases);
      }
    } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
      Log.d(TAG, "Purchase canceled by user");
      if (billingUpdatesListener != null) {
        billingUpdatesListener.onPurchaseError("Purchase canceled");
      }
    } else {
      String errorMsg = "Purchase failed: " + billingResult.getDebugMessage();
      Log.e(TAG, errorMsg);
      if (billingUpdatesListener != null) {
        billingUpdatesListener.onPurchaseError(errorMsg);
      }
    }
  }

  public void queryPurchases() {
    if (!ensureConnected()) return;
    billingClient.queryPurchasesAsync(
        QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build(),
        (billingResult, purchases) -> {
          if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            if (billingUpdatesListener != null) {
              billingUpdatesListener.onPurchasesUpdated(purchases);
            }
          } else {
            Log.e(TAG, "Error querying purchases: " + billingResult.getDebugMessage());
          }
        }
    );
  }

  public void queryProductDetails(List<String> productIds, ProductDetailsResponseListener listener) {
    if (!ensureConnected()) return;
    List<QueryProductDetailsParams.Product> products = new ArrayList<>();
    for (String productId : productIds) {
      products.add(QueryProductDetailsParams.Product.newBuilder()
          .setProductId(productId)
          .setProductType(BillingClient.ProductType.SUBS)
          .build());
    }

    QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
        .setProductList(products)
        .build();

    billingClient.queryProductDetailsAsync(params, (billingResult, productDetailsList) -> {
      if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
        if (listener != null) {
          listener.onProductDetailsResponse(productDetailsList);
        }
      } else {
        Log.e(TAG, "Error querying product details: " + billingResult.getDebugMessage());
      }
    });
  }

  public void launchBillingFlow(ProductDetails productDetails, String offerToken) {
    if (!ensureConnected()) return;
    List<BillingFlowParams.ProductDetailsParams> productDetailsParamsList = new ArrayList<>();
    productDetailsParamsList.add(
        BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build()
    );

    BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
        .setProductDetailsParamsList(productDetailsParamsList)
        .build();

    Activity activity = activityRef.get();
    if (activity == null) {
      Log.e(TAG, "Activity reference lost, cannot launch billing flow");
      return;
    }
    BillingResult result = billingClient.launchBillingFlow(activity, billingFlowParams);
    if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
      Log.e(TAG, "Error launching billing flow: " + result.getDebugMessage());
      if (billingUpdatesListener != null) {
        billingUpdatesListener.onPurchaseError(result.getDebugMessage());
      }
    }
  }

  public void acknowledgePurchase(Purchase purchase) {
    if (!ensureConnected()) return;
    if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
      AcknowledgePurchaseParams acknowledgePurchaseParams =
          AcknowledgePurchaseParams.newBuilder()
              .setPurchaseToken(purchase.getPurchaseToken())
              .build();

      billingClient.acknowledgePurchase(acknowledgePurchaseParams, billingResult -> {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
          if (billingUpdatesListener != null) {
            billingUpdatesListener.onPurchaseAcknowledged(purchase);
          }
        } else {
          Log.e(TAG, "Failed to acknowledge purchase: " + billingResult.getDebugMessage());
        }
      });
    }
  }


  public void endConnection() {
    if (billingClient != null) {
      billingClient.endConnection();
    }
  }

  public void release() {
    endConnection();
    if (activityRef != null) {
      activityRef.clear();
    }
    billingUpdatesListener = null;
  }
}
