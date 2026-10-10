package com.appcoholic.gpt;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class BillingHelper {
  private static final String TAG = "BillingHelper";
  private static BillingHelper instance;

  private BillingClient billingClient;
  private Activity activity;
  private BillingUpdatesListener billingUpdatesListener;
  private FirebaseAnalytics firebaseAnalytics;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());


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
    this.activity = activity;
    this.billingUpdatesListener = listener;
    setupBillingClient();
    firebaseAnalytics = FirebaseAnalytics.getInstance(activity);
  }


  public static synchronized BillingHelper getInstance(Activity activity, BillingUpdatesListener listener) {
    if (instance == null) {
      instance = new BillingHelper(activity, listener);
    } else {
      instance.updateActivity(activity);
      instance.billingUpdatesListener = listener;
    }
    instance.startConnection();
    return instance;
  }

  private void updateActivity(Activity activity) {
    this.activity = activity;
  }

  private void setupBillingClient() {
    billingClient = BillingClient.newBuilder(activity)
        .setListener(this::handlePurchaseUpdate)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build();
  }

  /** Billing callbacks can arrive on a background thread, and the listeners update the UI. */
  private void notifyListener(Consumer<BillingUpdatesListener> call) {
    mainHandler.post(() -> {
      if (billingUpdatesListener != null) {
        call.accept(billingUpdatesListener);
      }
    });
  }

  private void startConnection() {
    if (billingClient == null) {
      setupBillingClient();
    }
    if (billingClient.isReady()) {
      // a listener registered after the connection was set up never gets onBillingSetupFinished;
      // post so callers that are still being constructed (getInstance) are fully initialized
      notifyListener(BillingUpdatesListener::onBillingClientSetupFinished);
    } else {
      billingClient.startConnection(billingClientStateListener);
    }
  }

  private final BillingClientStateListener billingClientStateListener = new BillingClientStateListener() {
    @Override
    public void onBillingServiceDisconnected() {
      // the client reconnects on the next request (enableAutoServiceReconnection)
      Log.e(TAG, "Billing service disconnected");
    }

    @Override
    public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
      if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
        Log.d(TAG, "Billing client setup finished");
        firebaseAnalytics.logEvent("billing_setup_finished", null);
        notifyListener(BillingUpdatesListener::onBillingClientSetupFinished);
      } else {
        String errorMsg = "Billing setup failed: " + billingResult.getDebugMessage();
        Log.e(TAG, errorMsg);
        firebaseAnalytics.logEvent("billing_setup_failed", null);
        notifyListener(l -> l.onPurchaseError(errorMsg));
      }
    }
  };

  private void handlePurchaseUpdate(BillingResult billingResult, List<Purchase> purchases) {
    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
      Log.d(TAG, "Purchases updated");
      notifyListener(l -> l.onPurchasesUpdated(purchases));
    } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
      Log.d(TAG, "Purchase canceled by user");
      notifyListener(l -> l.onPurchaseError("Purchase canceled"));
    } else {
      String errorMsg = "Purchase failed: " + billingResult.getDebugMessage();
      Log.e(TAG, errorMsg);
      notifyListener(l -> l.onPurchaseError(errorMsg));
    }
  }

  public void queryPurchases() {
    billingClient.queryPurchasesAsync(
        QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build(),
        (billingResult, purchases) -> {
          if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            notifyListener(l -> l.onPurchasesUpdated(purchases));
          } else {
            Log.e(TAG, "Error querying purchases: " + billingResult.getDebugMessage());
          }
        }
    );
  }

  public void queryProductDetails(List<String> productIds, ProductDetailsResponseListener listener) {
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

    billingClient.queryProductDetailsAsync(params, (billingResult, result) -> {
      if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
        if (!result.getUnfetchedProductList().isEmpty()) {
          Log.e(TAG, "Unfetched products: " + result.getUnfetchedProductList().size());
        }
        if (listener != null) {
          // listeners update the UI
          mainHandler.post(() -> listener.onProductDetailsResponse(result.getProductDetailsList()));
        }
      } else {
        Log.e(TAG, "Error querying product details: " + billingResult.getDebugMessage());
      }
    });
  }

  public void launchBillingFlow(ProductDetails productDetails, String offerToken) {
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

    BillingResult result = billingClient.launchBillingFlow(this.activity, billingFlowParams);
    if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
      Log.e(TAG, "Error launching billing flow: " + result.getDebugMessage());
      notifyListener(l -> l.onPurchaseError(result.getDebugMessage()));
    }
  }

  public void acknowledgePurchase(Purchase purchase) {
    if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
      AcknowledgePurchaseParams acknowledgePurchaseParams =
          AcknowledgePurchaseParams.newBuilder()
              .setPurchaseToken(purchase.getPurchaseToken())
              .build();

      billingClient.acknowledgePurchase(acknowledgePurchaseParams, billingResult -> {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
          notifyListener(l -> l.onPurchaseAcknowledged(purchase));
        } else {
          Log.e(TAG, "Failed to acknowledge purchase: " + billingResult.getDebugMessage());
        }
      });
    }
  }


  public void endConnection() {
    if (billingClient != null) {
      billingClient.endConnection();
      billingClient = null;
    }
  }
}
