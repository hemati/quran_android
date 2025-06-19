package com.appcoholic.gpt;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.appcoholic.gpt.data.model.Message;
import com.appcoholic.gpt.data.model.User;
import com.google.android.gms.tasks.Task;
import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.openai.client.OpenAIClientAsync;
import com.openai.client.okhttp.OpenAIOkHttpClientAsync;
import com.openai.models.ChatCompletion;
import com.openai.models.ChatCompletionAssistantMessageParam;
import com.openai.models.ChatCompletionCreateParams;
import com.stfalcon.chatkit.messages.MessageInput;
import com.stfalcon.chatkit.messages.MessagesList;
import com.stfalcon.chatkit.messages.MessagesListAdapter;
import com.appcoholic.gpt.MessageQuotaManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

class ChatMessage {
  enum MessageType {
    USER, ASSISTANT, SYSTEM
  }

  MessageType type;
  String message;

  ChatMessage(MessageType type, String message) {
    this.type = type;
    this.message = message;
  }
}

public class DefaultMessagesActivity extends AppCompatActivity
    implements MessagesListAdapter.OnLoadMoreListener,
    MessageInput.InputListener
    , MessagesListAdapter.SelectionListener {

  private static final String TAG = "DefaultMessagesActivity";
  private static final String SENDER_ID = "User";
  private static final int MAX_CHAT_HISTORY = 6;
  private static int MAX_MESSAGES_PER_DAY = 6;

  private MessagesListAdapter<Message> messagesAdapter;
  private DatabaseHelper db;
  private MessagesList messagesList;
  private List<ChatMessage> chatMessages = new ArrayList<>();
  private FirebaseFirestore firestore = FirebaseFirestore.getInstance();
  private FirebaseAnalytics firebaseAnalytics;
  private FirebaseRemoteConfig mFirebaseRemoteConfig;
  private MessageQuotaManager quotaManager;

  //    private OpenAIAsyncClient client;
  private OpenAIClientAsync client;


  private String modelKey = "gpt-4.1-mini";
  private final ChatMessage systemMessage = new ChatMessage(
      ChatMessage.MessageType.SYSTEM,
      "You are QuranGPT, a helpful assistant built by Muslims to focus on religious queries, especially Islam and the Quran. " +
          "You will not break character. You will only answer questions related to Islam or religion; for any other topic, politely refuse. " +
          "You will respond using generally accepted interpretations from different Islamic schools of thought, without favoring a specific theology. " +
          "Your responses must be concise, written in the user's language, and avoid any text styling besides line breaks."
  );

  private Message welcomeMessage;
  private Message isTypingMessage;
  private Message noInternetMessage;


  private String reference;
  private boolean isSubscribed = false;

  private Menu menu;
  private Toolbar toolbar;

  private MessageInput messageInput;  // Add this line

  private SubscriptionDialog subscriptionDialog;  // Add this line

  @SuppressLint("MissingInflatedId")
  @Override
  protected void onCreate(Bundle savedInstanceState) {

    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_default_messages);

    messagesList = findViewById(R.id.messagesList);

    // Get the intent that started this activity
    Intent intent = getIntent();
    if (intent != null) {
      // Get the extra data from the intent
      reference = intent.getStringExtra("reference");
      // Now you can use the 'reference' variable in your activity
      // For example, you can set it as a message in your chat
      // Or use it as a context for your AI model
    }

    firebaseAnalytics = FirebaseAnalytics.getInstance(this);

    // Initialize Firebase Remote Config
    mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
    FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
        .setMinimumFetchIntervalInSeconds(86400)  // Adjust based on your needs
        .build();
    mFirebaseRemoteConfig.setConfigSettingsAsync(configSettings);

    toolbar = findViewById(R.id.toolbar);
    if (toolbar != null) {
      setSupportActionBar(toolbar);
      toolbar.setLogo(R.mipmap.ic_launcher_removebg);
      toolbar.setTitle(R.string.qurangpt);
      getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.accent_color_darker));
    }

    setUserSubscribed(false);
    quotaManager = new MessageQuotaManager(this, MAX_MESSAGES_PER_DAY);
    fetchAndActivateConfig();

    initializeMessages();
    setupMessageInput();
    setupMessagesAdapter();
    subscriptionDialog = new SubscriptionDialog(this);  // Add this line
    subscriptionDialog.setOnSubscriptionStatusChangedListener(this::setUserSubscribed);
  }

  private void fetchAndActivateConfig() {
    if (isNetworkAvailable()) {
      mFirebaseRemoteConfig.fetchAndActivate()
          .addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
              // Fetch and activate succeeded
              String openAiApiKey = mFirebaseRemoteConfig.getString("openai_api_key");
              String _modelKey = mFirebaseRemoteConfig.getString("openai_model_key");
              if (!modelKey.isEmpty())
                this.modelKey = _modelKey;
              initializeOpenAIClient(openAiApiKey);

              long _max_messages_per_day = mFirebaseRemoteConfig.getLong("max_messages_per_day");
              if (_max_messages_per_day > 0) {
                MAX_MESSAGES_PER_DAY = (int) _max_messages_per_day;
                quotaManager.setMaxMessagesPerDay(MAX_MESSAGES_PER_DAY);
              }
            } else {
              // Fetch failed, handle specific reasons
              Exception exception = task.getException();
              if (exception != null) {
                Log.e(TAG, "Fetch failed: " + exception.getMessage());
                FirebaseCrashlytics.getInstance().recordException(exception);
              }
              FirebaseCrashlytics.getInstance().recordException(new Exception("Fetch failed"));
            }
          });
    } else {
      Log.d(TAG, "No internet connection");
      //FirebaseCrashlytics.getInstance().recordException(new Exception("No internet connection"));
      Toast.makeText(this, "No internet connection", Toast.LENGTH_LONG).show();
    }
  }

  private void initializeOpenAIClient(String openAiApiKey) {
    // Initialize the OpenAI client using the fetched API key
    client = OpenAIOkHttpClientAsync.builder()
        .apiKey(openAiApiKey)
        .build();
  }

  private void initializeMessages() {
    welcomeMessage = createMessage("-1", "Assistant", getString(R.string.chatview_empty_message));
    isTypingMessage = createMessage("-2", "Assistant", "...");
    noInternetMessage = createMessage("-3", "Assistant", getString(R.string.chatview_no_internet));
  }

  private void setupMessageInput() {
    messageInput = findViewById(R.id.input);
    messageInput.setInputListener(this);
    if (reference != null) {
      // Set the reference followed by a new line
      messageInput.getInputEditText().setText(reference + System.lineSeparator());
      messageInput.getInputEditText().requestFocus();
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
    chatMessages.add(new ChatMessage(ChatMessage.MessageType.ASSISTANT, getString(R.string.chatview_empty_message)));
  }

  static String getRandomId() {
    return Long.toString(UUID.randomUUID().getLeastSignificantBits());
  }

  private ChatCompletionCreateParams.Builder getChatCompletionBuilder(List<ChatMessage> chatMessages) {

    ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
        .model(modelKey);
    for (ChatMessage chatMessage : chatMessages) {
      if (chatMessage.type == ChatMessage.MessageType.USER) {
        builder.addUserMessage(chatMessage.message);
      } else if (chatMessage.type == ChatMessage.MessageType.ASSISTANT) {
        builder.addMessage(ChatCompletionAssistantMessageParam.builder().content(chatMessage.message).build());
      } else if (chatMessage.type == ChatMessage.MessageType.SYSTEM) {
        builder.addSystemMessage(chatMessage.message);
      }
    }
    return builder;
  }

  @Override
  public boolean onSubmit(CharSequence input) {
    // Before sending a message
    if (!isUserSubscribed() && !quotaManager.incrementAndCheck()) {
      Toast.makeText(this, getString(R.string.chatview_quota_reached), Toast.LENGTH_LONG).show();
      subscriptionDialog.show();
      return false;
    } else {
      String messageText = input.toString();
      Message message = new Message(getRandomId(), new User(SENDER_ID, SENDER_ID, null, false), messageText);

      db.addMessage(message);
      addMessageToFirestore(message);
      messagesAdapter.addToStart(message, true);

      chatMessages.add(new ChatMessage(ChatMessage.MessageType.USER, messageText));
      List<ChatMessage> chatMessagesContext = getChatContext();

      chatMessagesContext.add(0, systemMessage);
      if (!isNetworkAvailable()) {
        runOnUiThread(() -> messagesAdapter.addToStart(noInternetMessage, true));
        return true;
      }

      isTypingMessage.setCreatedAt(new Date());
      runOnUiThread(() -> messagesAdapter.addToStart(isTypingMessage, true));


      if (client != null) {
        ChatCompletionCreateParams.Builder createParamsBuilder = getChatCompletionBuilder(chatMessagesContext);
        client.chat().completions().create(createParamsBuilder.build())
            .thenAcceptAsync(completion -> {
              handleChatCompletion(completion.choices());
            })
            .exceptionally(error -> {
              Log.e(TAG, "Failed to get chat completions", error);
              FirebaseCrashlytics.getInstance().recordException(error);
              return null;
            })
            .thenRunAsync(() -> {
              runOnUiThread(() -> messagesAdapter.delete(isTypingMessage));
            });
      } else {
        Log.e(TAG, "OpenAI client is not initialized");
        FirebaseCrashlytics.getInstance().recordException(new Exception("OpenAI client is not initialized"));
        Toast.makeText(this, "QuranGPT is not available at the moment. Please try again later.", Toast.LENGTH_LONG).show();
      }
      logFirebaseEvent("message_sent", message);
      return true;
    }
  }

  private void handleChatCompletion(List<ChatCompletion.Choice> choices) {
    if (!choices.isEmpty()) {
      Optional<String> content = choices.get(0).message().content();
      if (content.isPresent()) {
        Message response = createMessage(getRandomId(), "Assistant", content.get());
        db.addMessage(response);
        addMessageToFirestore(response);

        runOnUiThread(() -> messagesAdapter.addToStart(response, true));
        chatMessages.add(new ChatMessage(ChatMessage.MessageType.ASSISTANT, content.get()));
      }
    }
  }

  private List<ChatMessage> getChatContext() {
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
        .addOnSuccessListener(documentReference -> Log.d("Firebase", "DocumentSnapshot added. "))
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
        chatMessages.add(new ChatMessage(ChatMessage.MessageType.ASSISTANT, message.getText()));
      } else if (message.getUser().getId().equals("User")) {
        chatMessages.add(new ChatMessage(ChatMessage.MessageType.USER, message.getText()));
      }
    }
    if (messagesAdapter.isEmpty()) {
      messagesAdapter.addToStart(welcomeMessage, true);
    }
  }

  private boolean isNetworkAvailable() {
    ConnectivityManager connectivityManager =
        (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    if (connectivityManager == null) {
      return false;
    }

    Network network = connectivityManager.getActiveNetwork();
    if (network == null) {
      return false;
    }

    NetworkCapabilities networkCapabilities =
        connectivityManager.getNetworkCapabilities(network);
    if (networkCapabilities == null) {
      return false;
    }

    // Check for common transports that typically provide internet access
    return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        || networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        || networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        || networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH);
  }



  private boolean isUserSubscribed() {
    return isSubscribed;
  }

  private void setUserSubscribed(boolean subscribed) {
    isSubscribed = subscribed;
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
          .setMessage(getString(messagesAdapter.getSelectedMessages().isEmpty() ?
              R.string.chatview_delete_confirmation_desc :
              R.string.chatview_delete_selected_confirmation_desc))
          .setPositiveButton(android.R.string.yes, (dialog, which) -> {
            if (messagesAdapter.getSelectedMessages().isEmpty()) {
              messagesAdapter.clear();
              db.deleteAllMessages();
              chatMessages.clear();
              chatMessages.add(new ChatMessage(ChatMessage.MessageType.ASSISTANT, getString(R.string.chatview_empty_message)));
            } else {
              for (Message message : messagesAdapter.getSelectedMessages()) {
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
        if (messagesAdapter.getSelectedMessages().size() > 1) {
          boolean is24HourFormat = android.text.format.DateFormat.is24HourFormat(this);
          String dateFormatPattern = is24HourFormat ? "dd.MM, HH:mm" : "dd.MM, h:mm a";

          String createdAt = new SimpleDateFormat(dateFormatPattern, Locale.getDefault())
              .format(message.getCreatedAt());

          return String.format(Locale.getDefault(), "[%s] %s: %s",
              createdAt, message.getUser().getName().equals("User") ? "User" : "QuranGPT", message.getText());
        } else {
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
  }

  @Override
  public void onSelectionChanged(int count) {
    Log.d(TAG, "onSelectionChanged: " + count);
    if (menu != null) {
      MenuItem copyItem = menu.findItem(R.id.action_copy);
      copyItem.setVisible(count > 0);
    }
  }

  private void showRatingDialog() {
    // Code to show Google Play rating dialog
    ReviewManager manager = ReviewManagerFactory.create(this);
    Task<ReviewInfo> request = manager.requestReviewFlow();

    request.addOnCompleteListener(task -> {
      if (task.isSuccessful()) {
        // We got the ReviewInfo object
        ReviewInfo reviewInfo = task.getResult();
        Task<Void> flow = manager.launchReviewFlow(this, reviewInfo);

        flow.addOnCompleteListener(task1 -> {
          // Handle completion of review flow if needed
        });
      } else {
        // Handle error if needed
        Log.d(TAG, "Failed to get review flow");
      }
    });
  }

}
