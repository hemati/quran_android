package com.appcoholic.gpt;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.azure.ai.openai.OpenAIAsyncClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatChoice;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatRequestAssistantMessage;
import com.azure.ai.openai.models.ChatRequestMessage;
import com.azure.ai.openai.models.ChatRequestSystemMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.core.credential.KeyCredential;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.firestore.FirebaseFirestore;
import com.stfalcon.chatkit.messages.MessageInput;
import com.stfalcon.chatkit.messages.MessagesList;
import com.stfalcon.chatkit.messages.MessagesListAdapter;
import com.appcoholic.gpt.data.model.Message;
import com.appcoholic.gpt.data.model.User;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class DefaultMessagesActivity extends AppCompatActivity
        implements MessagesListAdapter.OnLoadMoreListener,
        MessageInput.InputListener
        ,MessagesListAdapter.SelectionListener
{

    private static final String TAG = "DefaultMessagesActivity";
    private static final String SENDER_ID = "User";
    private static final int MAX_CHAT_HISTORY = 6;
    private static final int MAX_MESSAGES_PER_DAY = 6;

    private static final String SUBSCRIPTION_SKU = "qurangpt_subscription";

    private MessagesListAdapter<Message> messagesAdapter;
    private DatabaseHelper db;
    private MessagesList messagesList;
    private List<ChatRequestMessage> chatMessages = new ArrayList<>();
    private FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    private FirebaseAnalytics firebaseAnalytics;

    private OpenAIAsyncClient client;

    private final ChatRequestSystemMessage systemMessage = new ChatRequestSystemMessage(
            "You are a helpful assistant. " +
            "You will talk only about religions, primarily Islam and the Quran. " +
            "You will not break character. " +
            "You will answer in the language of the user. " +
            "You will keep your answers concise and short. " +
            "You will not add textstyles besides linebreaks."
    );

    private Message welcomeMessage;
    private Message isTypingMessage;
    private Message noInternetMessage;
    private Message quotaReachedMessage;
    private boolean quataReachedMessageShown = false;
    private BillingClient billingClient;


    private String reference;
    private boolean isSubscribed = false;

    private Menu menu;
    private Toolbar toolbar;

    private String localiziedPrice = "0.99 $";

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        final int themeId = PCommon.GetPrefThemeId(getApplicationContext());
//        setTheme(themeId);

        setContentView(R.layout.activity_default_messages);

        // Get the intent that started this activity
        Intent intent = getIntent();
        if (intent != null) {
            // Get the extra data from the intent
            reference = intent.getStringExtra("reference");
            Log.i(TAG, "onCreate: " + reference);
            // Now you can use the 'reference' variable in your activity
            // For example, you can set it as a message in your chat
            // Or use it as a context for your AI model
        }

        firebaseAnalytics = FirebaseAnalytics.getInstance(this);
        messagesList = findViewById(R.id.messagesList);
        client = new OpenAIClientBuilder()
                .credential(new KeyCredential(BuildConfig.OPENAI_API_KEY))
                .buildAsyncClient();

        toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setLogo(R.mipmap.ic_launcher_removebg);
            toolbar.setTitle(R.string.qurangpt);
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.accent_color_darker));
        }

        setUserSubscribed(false);

        initializeMessages();
        setupMessageInput();
        setupMessagesAdapter();
        setupBillingClient();
//        findViewById(R.id.messageSendButton).setContentDescription("send message");
    }

    private void initializeMessages() {
        welcomeMessage = createMessage("-1", "Assistant", getString(R.string.chatview_empty_message));
        isTypingMessage = createMessage("-2", "Assistant", "...");
        noInternetMessage = createMessage("-3", "Assistant", getString(R.string.chatview_no_internet));
    }

    private void setupMessageInput() {
        MessageInput input = findViewById(R.id.input);
        input.setInputListener(this);
        if (reference != null) {
            // Set the reference followed by a new line
            input.getInputEditText().setText("(" + reference + ")" + System.lineSeparator());
            input.getInputEditText().requestFocus();
        }
    }

    private void setupMessagesAdapter() {
        messagesAdapter = new MessagesListAdapter<>(SENDER_ID, null);
        messagesAdapter.enableSelectionMode(this);
        messagesAdapter.setLoadMoreListener(this);
        messagesList.setAdapter(messagesAdapter);

        db = new DatabaseHelper(this);
        loadInitialMessages();
        onLoadMore(0, 0);
    }

    private static Message createMessage(String id, String userName, String text) {
        return new Message(id, new User(userName, userName, null, true), text);
    }

    private void loadInitialMessages() {
        chatMessages.add(new ChatRequestAssistantMessage(getString(R.string.chatview_empty_message)));
    }

    static String getRandomId() {
        return Long.toString(UUID.randomUUID().getLeastSignificantBits());
    }

    @Override
    public boolean onSubmit(CharSequence input) {
        // Before sending a message
        if (!updateMessageCount()) {
            // Show a message to the user that they have exceeded the limit
            if(quataReachedMessageShown){
//                Toast.makeText(this, "You have exceeded the limit of 4 messages per day", Toast.LENGTH_SHORT).show();
                startSubscriptionPurchase();
            }
            else {
              quotaReachedMessage = createMessage("-4", "Assistant", getString(R.string.chatview_quota_reached).replace("XXPRICEXX", localiziedPrice));
              runOnUiThread(() -> messagesAdapter.addToStart(quotaReachedMessage, true));
                quataReachedMessageShown = true;
            }
        }
        else{
            String messageText = input.toString();
            Message message = new Message(getRandomId(), new User(SENDER_ID, SENDER_ID, null, false), messageText);

            db.addMessage(message);
            addMessageToFirestore(message);
            messagesAdapter.addToStart(message, true);

            chatMessages.add(new ChatRequestUserMessage(messageText));
            List<ChatRequestMessage> chatMessagesContext = getChatContext();

            chatMessagesContext.add(0, systemMessage);
            if(!isNetworkAvailable()){
                runOnUiThread(() -> messagesAdapter.addToStart(noInternetMessage, true));
                return true;
            }

            isTypingMessage.setCreatedAt(new Date());
            runOnUiThread(() -> messagesAdapter.addToStart(isTypingMessage, true));


            client.getChatCompletions("gpt-4o-mini", new ChatCompletionsOptions(chatMessagesContext))
                    .subscribe(
                            completion -> handleChatCompletion(completion.getChoices()),
                            error -> {
                                Log.e(TAG, "Failed to get chat completions", error);
                                FirebaseCrashlytics.getInstance().recordException(error);
                            },
                            () -> runOnUiThread(() -> messagesAdapter.delete(isTypingMessage))
                    );

            logFirebaseEvent("message_sent", message);

        }
        return true;
    }

    private void handleChatCompletion(List<ChatChoice> choices) {
        if (!choices.isEmpty()) {
            String content = choices.get(0).getMessage().getContent();
            if (content != null) {
                Message response = createMessage(getRandomId(), "Assistant", content);
                db.addMessage(response);
                addMessageToFirestore(response);

                runOnUiThread(() -> messagesAdapter.addToStart(response, true));
                chatMessages.add(new ChatRequestAssistantMessage(content));
            }
        }
    }

    private List<ChatRequestMessage> getChatContext() {
        if (chatMessages.size() > MAX_CHAT_HISTORY) {
            return chatMessages.subList(chatMessages.size() - MAX_CHAT_HISTORY, chatMessages.size());
        }
        return chatMessages;
    }

    private void addMessageToFirestore(Message message) {
        String userId = UniqueIDGenerator.getUniqueID(this);
        Map<String, Object> msg = new HashMap<>();
        msg.put("id", message.getId());
        msg.put("text", message.getText());
        msg.put("createdAt", message.getCreatedAt());
        msg.put("sender", message.getUser().getName());

        firestore.collection("users").document(userId).collection("messages")
                .add(msg)
                .addOnSuccessListener(documentReference -> Log.d("Firebase", "DocumentSnapshot added with ID: " + documentReference.getId()))
                .addOnFailureListener(e -> {
                    Log.w("Firebase", "Error adding document", e);
                    FirebaseCrashlytics.getInstance().recordException(e);
                });
    }

    private void logFirebaseEvent(String eventName, Message message) {
        Bundle params = new Bundle();
        params.putString("user_id", message.getUser().getId());
        firebaseAnalytics.logEvent(eventName, params);
    }

    @Override
    public void onLoadMore(int page, int totalItemsCount) {
        List<Message> messages = db.getMessages(page);
        messagesAdapter.addToEnd(messages, false);
        // reverse messages
        Collections.reverse(messages);
        for (Message message : messages) {
            if (message.getUser().getId().equals("Assistant")) {
                chatMessages.add(new ChatRequestAssistantMessage(message.getText()));
            } else if (message.getUser().getId().equals("User")) {
                chatMessages.add(new ChatRequestUserMessage(message.getText()));
            }
        }
        if (messagesAdapter.isEmpty()) {
            messagesAdapter.addToStart(welcomeMessage, true);
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private String getCurrentDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private boolean updateMessageCount() {
        if (isUserSubscribed()) {
            return true;
        }
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();

        String currentDate = getCurrentDate();
        String storedDate = prefs.getString("date", "");
        int messageCount = prefs.getInt("messageCount", 0);

        if (currentDate.equals(storedDate)) {
            // Same day
            messageCount++;
            if (messageCount > MAX_MESSAGES_PER_DAY) {
                // User has exceeded the limit
                return false;
            }
        } else {
            // Different day, reset the count
            messageCount = 1;
        }

        // Store the new count and date
        editor.putInt("messageCount", messageCount);
        editor.putString("date", currentDate);
        editor.apply();

        return true;
    }

    private void setupBillingClient() {
        billingClient = BillingClient.newBuilder(this)
                .setListener((billingResult, purchases) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                        for (Purchase purchase : purchases) {
                            handlePurchase(purchase);
                        }
                    } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
                        // Handle user canceled
                        Toast.makeText(this, "Purchase canceled", Toast.LENGTH_SHORT).show();
                    } else {
                        // Handle other errors
                        Log.e(TAG, "Purchase failed: " + billingResult.getDebugMessage());
                    }
                })
                .enablePendingPurchases()
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    // BillingClient is ready
                    checkUserSubscription();
                    querySubscriptionPrice();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to Google Play by calling the startConnection() method.
            }
        });
    }

  private void querySubscriptionPrice() {
    List<QueryProductDetailsParams.Product> productList = new ArrayList<>();
    productList.add(QueryProductDetailsParams.Product.newBuilder()
        .setProductId(SUBSCRIPTION_SKU)
        .setProductType(BillingClient.ProductType.SUBS)
        .build());

    QueryProductDetailsParams queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
        .setProductList(productList)
        .build();

    billingClient.queryProductDetailsAsync(queryProductDetailsParams, (billingResult, productDetailsList) -> {
      if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && productDetailsList != null) {
        for (ProductDetails productDetails : productDetailsList) {
          if (productDetails.getProductId().equals(SUBSCRIPTION_SKU)) {
            // Get the localized price and currency
            localiziedPrice = productDetails.getSubscriptionOfferDetails().get(0).getPricingPhases()
                .getPricingPhaseList().get(0).getFormattedPrice();
          }
        }
      } else {
        Log.e(TAG, "Error fetching product details: " + billingResult.getDebugMessage());
      }
    });
  }

    private void checkUserSubscription() {
        billingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
                (billingResult, purchases) -> {
                    if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        for (Purchase purchase : purchases) {
                            if (purchase.getProducts().contains(SUBSCRIPTION_SKU)) {
                                setUserSubscribed(true);
                                acknowledgePurchase(purchase);
                                break;
                            }
                        }
                    }
                });
    }

    private boolean isUserSubscribed() {
        return isSubscribed;
    }

    private void setUserSubscribed(boolean subscribed) {
        isSubscribed = subscribed;
//        if(isSubscribed) {
//            toolbar.setTitle(R.string.qurangpt_pro);
//        }
//        else{
//            toolbar.setTitle(R.string.qurangpt);
//        }
    }

    private void acknowledgePurchase(Purchase purchase) {
        // Acknowledge the purchase
        if (!purchase.isAcknowledged()) {
            AcknowledgePurchaseParams acknowledgePurchaseParams =
                    AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.getPurchaseToken())
                            .build();
            billingClient.acknowledgePurchase(acknowledgePurchaseParams, billingResult -> {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    // Purchase acknowledged
                    Toast.makeText(this, "Subscription successful", Toast.LENGTH_SHORT).show();
                } else {
                    // Handle any errors
                    Log.e(TAG, "Failed to acknowledge purchase: " + billingResult.getDebugMessage());
                }
            });
        } else {
            // Purchase already acknowledged
            Toast.makeText(this, "Subscription successful", Toast.LENGTH_SHORT).show();
        }
    }


    private void handlePurchase(Purchase purchase) {
        if (purchase.getProducts().contains(SUBSCRIPTION_SKU)) {
            setUserSubscribed(true);
            // Grant the subscription to the user
            // Update your backend or local storage if necessary
            Toast.makeText(this, "Subscription successful", Toast.LENGTH_SHORT).show();
            acknowledgePurchase(purchase);
        }
    }

    private void startSubscriptionPurchase() {
        List<QueryProductDetailsParams.Product> productList = new ArrayList<>();
        productList.add(QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SUBSCRIPTION_SKU)
                .setProductType(BillingClient.ProductType.SUBS)
                .build());

        QueryProductDetailsParams queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build();

        billingClient.queryProductDetailsAsync(queryProductDetailsParams, (billingResult, productDetailsList) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && productDetailsList != null) {
                for (ProductDetails productDetails : productDetailsList) {
                    if (productDetails.getProductId().equals(SUBSCRIPTION_SKU)) {
                        String offerToken = productDetails.getSubscriptionOfferDetails().get(0).getOfferToken();

                        // Create ProductDetailsParams
                        List<BillingFlowParams.ProductDetailsParams> productDetailsParamsList = new ArrayList<>();
                        productDetailsParamsList.add(
                                BillingFlowParams.ProductDetailsParams.newBuilder()
                                        .setProductDetails(productDetails)
                                        .setOfferToken(offerToken)
                                        .build()
                        );

                        // Create BillingFlowParams
                        BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                                .setProductDetailsParamsList(productDetailsParamsList)
                                .build();

                        // Ensure launchBillingFlow is called on the main UI thread
                        runOnUiThread(() -> {
                            BillingResult result = billingClient.launchBillingFlow(DefaultMessagesActivity.this, billingFlowParams);
                            if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                                Log.e(TAG, "Error launching billing flow: " + result.getDebugMessage());
                                FirebaseCrashlytics.getInstance().recordException(new Exception("Error launching billing flow: " + result.getDebugMessage()));
                            }
                        });
                        // Launch billing flow
                        return;
                    }
                }
            } else {
                // Handle error
                Log.e(TAG, "Error fetching product details: " + billingResult.getDebugMessage());
                FirebaseCrashlytics.getInstance().recordException(new Exception("Error fetching product details: " + billingResult.getDebugMessage()));
            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chat_actions_menu, menu);
        this.menu = menu;
        menu.findItem(R.id.action_delete).setVisible(true);
        menu.findItem(R.id.action_copy).setVisible(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_delete) {

            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.chatview_delete_confirmation_title))
                    .setMessage(getString(messagesAdapter.getSelectedMessages().isEmpty()?
                            R.string.chatview_delete_confirmation_desc:
                            R.string.chatview_delete_selected_confirmation_desc))
                    .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                        if(messagesAdapter.getSelectedMessages().isEmpty()) {
                            messagesAdapter.clear();
                            db.deleteAllMessages();
                            chatMessages.clear();
                            chatMessages.add(new ChatRequestAssistantMessage(getString(R.string.chatview_empty_message)));
                        }else{
                            for(Message message : messagesAdapter.getSelectedMessages()){
                                messagesAdapter.delete(message);
                                db.deleteMessage(message);
                            }
                        }
                        messagesAdapter.unselectAllItems();
                    })
                    .setNegativeButton(android.R.string.no, null)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
            return true;
        }
        if (item.getItemId() == R.id.action_copy) {
            messagesAdapter.copySelectedMessagesText(this, message -> {
                if(messagesAdapter.getSelectedMessages().size() > 1) {
                    boolean is24HourFormat = android.text.format.DateFormat.is24HourFormat(this);
                    String dateFormatPattern = is24HourFormat ? "dd.MM, HH:mm" : "dd.MM, h:mm a";

                    String createdAt = new SimpleDateFormat(dateFormatPattern, Locale.getDefault())
                            .format(message.getCreatedAt());

                    return String.format(Locale.getDefault(), "[%s] %s: %s",
                            createdAt, message.getUser().getName().equals("User") ? "User" : "QuranGPT", message.getText());
                }
                else{
                    return message.getText();
                }
            }, true);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
        if (billingClient != null) {
            billingClient.endConnection();
        }
    }

//    @Override
//    protected void onStop() {
//        super.onStop();
//        Intent intent = new Intent(this, NotificationService.class);
//        startService(intent);
//    }
//
    @Override
    public void onSelectionChanged(int count) {
        Log.d(TAG, "onSelectionChanged: " + count);
        if (menu != null) {
            MenuItem copyItem = menu.findItem(R.id.action_copy);
            copyItem.setVisible(count > 0);
        }
    }
}
